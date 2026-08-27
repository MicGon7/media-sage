#!/usr/bin/env bash
# Freezes an Android emulator/device's wall clock at a chosen time, for manually testing
# time-boundary behavior (MS-706's 5pm/midnight tone refresh, MS-712's evening notification,
# and anything else keyed off System.currentTimeMillis()).
#
# WHY THIS EXISTS: `adb shell date <args>` looks like it works — it echoes back the time you
# asked for — but on a non-rooted ("user" build) emulator image, shell lacks CAP_SYS_TIME, so
# the underlying settimeofday() call silently fails and the clock snaps back to real wall-clock
# time within seconds. This bit us during MS-706/MS-712 manual testing: `adb root` reported
# "adbd cannot run as root in production builds", confirming shell can never set the clock
# directly on this image. The only path that actually works is the Settings app's Date & time
# UI, which runs with system privileges — so this script drives it via uiautomator dumps
# instead of hardcoded tap coordinates (coordinates broke once already, from a mis-tap on the
# radial clock picker; resource-id/text lookups survive screen-size/density differences).
#
# `settings put global auto_time 0/1` is a plain Settings.Global write (no CAP_SYS_TIME needed)
# and does work directly over adb — so only the actual clock-setting step needs the UI dance.
#
# Usage:
#   scripts/set-device-time.sh [-s SERIAL] HH:MM       Freeze the clock at HH:MM (24-hour).
#   scripts/set-device-time.sh [-s SERIAL] --restore   Re-enable automatic time.
#   scripts/set-device-time.sh [-s SERIAL] --check     Print current time + auto_time; no changes.
#
# Examples:
#   scripts/set-device-time.sh 16:58     # a couple minutes before the 5pm tone boundary
#   scripts/set-device-time.sh --restore
#
# Exit codes:
#   0 — succeeded
#   1 — bad usage, no device, or a required UI element could not be found (Settings layout
#       changed, or the emulator was on an unexpected screen)
set -euo pipefail

SERIAL=""
MODE=""
TARGET_TIME=""

usage() {
    sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'
}

while [ $# -gt 0 ]; do
    case "$1" in
        -s|--serial) SERIAL="$2"; shift 2 ;;
        --restore) MODE="restore"; shift ;;
        --check) MODE="check"; shift ;;
        -h|--help) usage; exit 0 ;;
        [0-9]*:[0-9]*) TARGET_TIME="$1"; MODE="set"; shift ;;
        *) echo "ERROR: unrecognized argument: $1" >&2; usage; exit 1 ;;
    esac
done

if [ -z "$MODE" ]; then
    usage
    exit 1
fi

if [ -z "$SERIAL" ]; then
    # mapfile (bash 4+) isn't available on macOS's stock bash 3.2, so collect into a
    # newline-delimited string instead of an array.
    DEVICES="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
    DEVICE_COUNT="$(printf '%s\n' "$DEVICES" | grep -c . || true)"
    if [ "$DEVICE_COUNT" -eq 0 ]; then
        echo "ERROR: no adb device attached." >&2
        exit 1
    elif [ "$DEVICE_COUNT" -gt 1 ]; then
        echo "ERROR: multiple devices attached; pass -s <serial>:" >&2
        printf '%s\n' "$DEVICES" >&2
        exit 1
    fi
    SERIAL="$DEVICES"
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT
DUMP_XML="$WORKDIR/dump.xml"
FINDER_PY="$WORKDIR/finder.py"

cat > "$FINDER_PY" <<'PYEOF'
import sys
import re
import xml.etree.ElementTree as ET


def parse_bounds(b):
    m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", b or "")
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return x1, y1, x2, y2


def center(bounds):
    x1, y1, x2, y2 = bounds
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    dump_path = sys.argv[1]
    args = sys.argv[2:]
    resid = None
    text = None
    nearest_clickable = False
    want = "coords"

    i = 0
    while i < len(args):
        a = args[i]
        if a == "--resid":
            resid, i = args[i + 1], i + 2
        elif a == "--text":
            text, i = args[i + 1], i + 2
        elif a == "--nearest-clickable":
            nearest_clickable, i = True, i + 1
        elif a == "--want":
            want, i = args[i + 1], i + 2
        else:
            i += 1

    root = ET.parse(dump_path).getroot()

    parent_map = {}
    for parent in root.iter():
        for child in parent:
            parent_map[child] = parent

    match = None
    for node in root.iter("node"):
        if resid is not None and node.get("resource-id", "").endswith(resid):
            match = node
            break
        if text is not None and node.get("text", "") == text:
            match = node
            break

    if match is None:
        sys.exit(1)

    if nearest_clickable:
        cur = match
        while cur is not None and cur.get("clickable") != "true":
            cur = parent_map.get(cur)
        if cur is not None:
            match = cur

    if want == "checked":
        print(match.get("checked", "false"))
        return

    if want == "text":
        t = match.get("text", "")
        if not t:
            # Container nodes (e.g. a Spinner) usually carry their visible text on a
            # descendant TextView rather than on the container itself.
            for n in match.iter("node"):
                if n is not match and n.get("text"):
                    t = n.get("text")
                    break
        print(t)
        return

    bounds = parse_bounds(match.get("bounds", ""))
    if bounds is None:
        sys.exit(1)
    cx, cy = center(bounds)
    print(cx, cy)


if __name__ == "__main__":
    main()
PYEOF

adb_shell() { adb -s "$SERIAL" shell "$@"; }

dump_ui() {
    # `uiautomator dump` needs the UI to report idle and fails intermittently right after a
    # tap that triggers an animated transition (e.g. a dialog opening) — this bit the very
    # first version of this script: a swallowed dump failure silently re-served the PREVIOUS
    # dump file, so the poll loop below kept "seeing" the pre-tap screen and timed out even
    # though the tap itself worked. Retry the dump command itself, not just the outer poll.
    local tries=0
    while [ "$tries" -lt 5 ]; do
        if adb_shell uiautomator dump /sdcard/window_dump.xml 2>/dev/null | grep -q "UI hi.*dumped"; then
            adb -s "$SERIAL" exec-out cat /sdcard/window_dump.xml > "$DUMP_XML" 2>/dev/null
            return 0
        fi
        sleep 0.3
        tries=$((tries + 1))
    done
    echo "WARNING: uiautomator dump failed $tries times in a row; using last available dump." >&2
    adb -s "$SERIAL" exec-out cat /sdcard/window_dump.xml > "$DUMP_XML" 2>/dev/null
}

ui_find() { python3 "$FINDER_PY" "$DUMP_XML" "$@"; }

find_or_die() {
    local out
    if ! out=$(ui_find "$@"); then
        echo "ERROR: could not find UI element matching: $*" >&2
        echo "       (Settings layout may differ on this Android version/device — inspect" >&2
        echo "       $DUMP_XML manually, or set the time by hand via Settings > Date & time.)" >&2
        exit 1
    fi
    echo "$out"
}

tap() {
    adb_shell input tap "$1" "$2"
    sleep 0.4
}

device_date() { adb_shell date | tr -d '\r'; }
device_epoch() { adb_shell date +%s | tr -d '\r'; }

# Polls (re-dumping each time) until any of the given resource-id suffixes shows up, instead
# of trusting a single fixed sleep. A one-shot dump right after a tap raced the dialog's open
# animation during testing — it caught the previous screen and failed the next lookup.
wait_for_any_resid() {
    local tries=0
    while [ "$tries" -lt 8 ]; do
        dump_ui
        for r in "$@"; do
            if ui_find --resid "$r" >/dev/null 2>&1; then
                return 0
            fi
        done
        sleep 0.4
        tries=$((tries + 1))
    done
    return 1
}

check_mode() {
    echo "auto_time  = $(adb_shell settings get global auto_time | tr -d '\r')"
    echo "device date = $(device_date)"
}

restore_mode() {
    adb_shell settings put global auto_time 1
    sleep 2
    echo "auto_time restored to automatic. Device date is now: $(device_date)"
    echo "NOTE: emulators without a real network/GPS time source (no SIM, no location fix)" >&2
    echo "      can snap to a stale fallback instead of the real current time — if the date" >&2
    echo "      above looks wrong, re-run this script in 'set' mode with the correct time." >&2
}

set_mode() {
    local hh="${TARGET_TIME%%:*}" mm="${TARGET_TIME##*:}"
    hh=$((10#$hh))
    mm=$((10#$mm))
    if [ "$hh" -gt 23 ] || [ "$mm" -gt 59 ]; then
        echo "ERROR: time must be 24-hour HH:MM, e.g. 16:58" >&2
        exit 1
    fi

    # This write does NOT need the UI dance — Settings.Global puts are a plain adb-permitted
    # write, unlike the actual clock value.
    adb_shell settings put global auto_time 0

    # Dismiss any TimePicker dialog left open by a previous crashed/interrupted run — `am
    # start` re-delivers the intent to Settings' already-running instance without dismissing
    # it, so a stale dialog hides the "Time" row this script looks for next and fails
    # confusingly. One BACK is enough to close just the dialog (a second would exit Settings
    # entirely), and it's a no-op if nothing was open.
    adb_shell input keyevent KEYCODE_BACK
    sleep 0.3

    adb_shell am start -a android.settings.DATE_SETTINGS >/dev/null
    sleep 1.5

    dump_ui
    # NOTE: must be a plain assignment, not embedded in `<<<`/other redirection — under `set
    # -e`, a command substitution's failure is only caught by the enclosing statement when
    # it's a direct assignment. Burying find_or_die's exit-1 inside `read <<< "$(...)"`
    # silently swallowed the failure once already and fed empty coordinates to `adb shell
    # input tap`, producing a confusing "Argument expected after tap" Java exception instead
    # of this script's own error message.
    out="$(find_or_die --text "Time" --nearest-clickable)"
    read -r cx cy <<< "$out"
    tap "$cx" "$cy"

    if ! wait_for_any_resid "toggle_mode" "input_hour"; then
        echo "ERROR: time picker dialog never opened after tapping 'Time'." >&2
        exit 1
    fi

    # The dialog usually opens in radial-clock mode; switch to text input if that toggle is
    # present (skip quietly if the device/version defaults to text-input mode already).
    if out=$(ui_find --resid "toggle_mode" 2>/dev/null); then
        read -r cx cy <<< "$out"
        tap "$cx" "$cy"
        if ! wait_for_any_resid "input_hour"; then
            echo "ERROR: text-input time fields never appeared after switching modes." >&2
            exit 1
        fi
    fi

    local has_ampm=0
    if ui_find --resid "am_pm_spinner" >/dev/null 2>&1; then has_ampm=1; fi

    local hour_to_type="$hh" desired_ampm=""
    if [ "$has_ampm" -eq 1 ]; then
        if [ "$hh" -ge 12 ]; then desired_ampm="PM"; else desired_ampm="AM"; fi
        hour_to_type=$(( hh % 12 ))
        [ "$hour_to_type" -eq 0 ] && hour_to_type=12
    fi

    out="$(find_or_die --resid "input_hour")"
    read -r cx cy <<< "$out"
    tap "$cx" "$cy"
    adb_shell input text "$hour_to_type"

    dump_ui
    out="$(find_or_die --resid "input_minute")"
    read -r cx cy <<< "$out"
    tap "$cx" "$cy"
    printf -v mm_padded "%02d" "$mm"
    adb_shell input text "$mm_padded"

    if [ "$has_ampm" -eq 1 ]; then
        dump_ui
        local current_ampm
        current_ampm=$(ui_find --resid "am_pm_spinner" --want text || echo "")
        if [ "$current_ampm" != "$desired_ampm" ]; then
            out="$(find_or_die --resid "am_pm_spinner")"
            read -r cx cy <<< "$out"
            tap "$cx" "$cy"
            sleep 0.5
            dump_ui
            out="$(find_or_die --text "$desired_ampm")"
            read -r cx cy <<< "$out"
            tap "$cx" "$cy"
        fi
    fi

    dump_ui
    out="$(find_or_die --resid "button1")"
    read -r cx cy <<< "$out"
    tap "$cx" "$cy"
    sleep 1

    # A real clock keeps ticking during the 3s sleep below, so exact-string equality is the
    # wrong check (that's what caught this the first time this script was tested). Compare
    # epoch-second deltas instead: ~3s of drift is the clock correctly running; tens of
    # minutes/hours is the silent-revert failure mode this script exists to work around.
    local first_epoch second_epoch delta
    first_epoch=$(device_epoch)
    sleep 3
    second_epoch=$(device_epoch)
    delta=$(( second_epoch - first_epoch ))
    echo "Device date right after set: $(device_date)"
    if [ "$delta" -ge 0 ] && [ "$delta" -le 10 ]; then
        echo "OK: clock is holding steady (advanced ${delta}s over a 3s wait, auto_time is off)."
    else
        echo "WARNING: clock advanced ${delta}s over a 3s wait — it likely reverted to real time." >&2
        echo "         Re-run '$0 --check' to inspect, or set it by hand via Settings." >&2
        exit 1
    fi
}

case "$MODE" in
    check) check_mode ;;
    restore) restore_mode ;;
    set) set_mode ;;
esac
