# Headless UI render loop

## Problem

Autonomous workers build UI blind. The Cloud Run worker has no emulator, simulator,
or device, so it cannot see what it renders — layout and padding mistakes are only
caught when a human pulls the branch. Workers also cannot show completed UI on the PR,
so a reviewer has no visual to inspect either.

The fix is a feedback loop, not a test gate: the worker renders the composable it just
wrote, **looks at the image** (the Claude Code `Read` tool is vision-capable), critiques
its own spacing/layout, iterates, and attaches the final PNG to the PR.

## Why not `@Preview` / a screenshot gate

The original instinct was to reuse `@Preview` composables via Compose Preview Screenshot
Testing (a golden-image regression gate). Two reasons that is the wrong tool here:

1. **A regression gate needs a known-good golden.** For net-new UI the worker generates
   the code *and* the golden from the same output, so the test only proves the render is
   deterministic — a bug gets baked into the reference and passes. The gate adds the least
   value exactly where the worker is weakest (new UI).
2. **`@Preview` bundles discovery + sample state.** The self-critique loop needs neither:
   the worker knows which composable it just changed, so it renders that one thing directly
   with state *it* chooses (ideally the ugly cases — overflow, missing image, empty list).

So this ticket builds a **direct render**, not a gate. The golden-image regression gate
(MS-145) remains a separate, later concern for when screens stabilise.

## Mechanism: Roborazzi + Robolectric, direct capture

`captureRoboImage("…png") { MediaSageTheme { Screen(state) } }` renders a composable to a
PNG on the JVM via Robolectric — no emulator, no device. Chosen over the first-party
`com.android.compose.screenshot` plugin because that plugin's KMP-module support is still
being refined, while Roborazzi explicitly supports Compose Multiplatform and headless CI.

Key setup decisions:

- **Bare `Application`.** `@Config(application = android.app.Application::class)` — the
  render must not boot the app's `MediaSageApplication`, which starts Koin. Booting it also
  throws `KoinApplicationAlreadyStartedException` on the second capture in the same JVM. A
  UI render needs only the composable, not the app runtime.
- **Pinned SDK.** `@Config(sdk = [34])` keeps the render off the Android 36 / JDK 21
  toolchain requirement, for portability to the worker container.
- **Multiple screens are free.** One `captureRoboImage { }` block per screen → one PNG each;
  `recordRoborazziDebug` runs them all. A main + detail ticket just adds a second block. The
  smoke test proves this with light + dark captures.

## Cost control: gate the iOS targets on the worker

`:composeApp` and `:shared` register iOS targets. Merely *configuring* them forces the
Kotlin/Native toolchain (~3 GB extracted) to download — even on Linux, where iOS can't be
built. There is **no Gradle property** that suppresses this (`kotlin.native.toolchain.enabled`
only changes the download mechanism). The working mechanism is conditional target
registration:

```kotlin
val buildIosTargets = providers.gradleProperty("mediasage.worker").orNull != "true"
// ... if (buildIosTargets) { iosArm64(); iosSimulatorArm64() … }
```

The worker passes `-Pmediasage.worker=true` and skips iOS entirely; local and CI builds
leave the property unset and build all targets normally.

## Warm caches (why the worker image must pre-bake)

Cold, the first render took **15 minutes** (Robolectric's 144 MB `android-all` jar +
androidx/compose deps). Warm, it is **~10 seconds**. Cloud Run Job executions are ephemeral,
so without pre-baking, *every* run would re-download. The worker image (MS-583) therefore
pre-bakes these into an image layer. Note the `android-all` jar is fetched at test *runtime*
into `~/.m2`, so warming it requires actually running a render at image-build time, not just
resolving dependencies.

Rendering also needs a **compile-time** Android SDK (`android.jar` + `aapt2`): Robolectric
swaps `android.jar` at runtime but not at compile time. That SDK install (compile-only, no
emulator) is part of MS-583, which is why the worker enablement is a separate ticket.

## The mechanical/judgement split

`scripts/capture-ui.sh` does the parts that need no reasoning (run the render, collect PNGs,
stage them, print the PR Markdown), so they cost the worker zero turns. The worker keeps the
judgement: which screens to capture, writing the capture block with representative state, and
looking at the PNG to critique it. When no Android SDK is present the script skips loudly
(exit 3, non-fatal) rather than breaking the run.

## PR attachment

`gh` cannot upload loose images. The script copies the PNGs into `docs/ui-screenshots/`,
stages them, and emits `![name](https://raw.githubusercontent.com/<repo>/<branch>/…)` — the
branch is pushed at ship time, so the raw URLs resolve once the PR exists. (Committing review
screenshots to the repo is a known cleanliness tradeoff; a dedicated hosting path can replace
it later.)

## Worker enablement (MS-583)

MS-581 proved the loop locally but left the Cloud Run worker unable to run it — the worker
image was deliberately built with no Android SDK. MS-583 enables it in `Dockerfile.worker`:

- **Compile-only Android SDK.** `cmdline-tools` + `platforms;android-36` + `build-tools;36.0.0`
  + `platform-tools`, installed under `/opt/android-sdk` (exported as `ANDROID_HOME`). No
  emulator or system image — the render is pure JVM. Robolectric swaps `android.jar` at test
  *runtime*, but compiling the `:composeApp` Android target still needs a compile-time SDK.
- **Pre-bake by actually rendering once.** The existing dependency warm-up (`:agentruntime` /
  `:appServer`) only *resolves* dependencies. That is not enough here: Robolectric's 144 MB
  `android-all` jar is fetched at test *runtime* into `~/.m2`, so the pre-bake runs a real
  `./gradlew :composeApp:recordRoborazziDebug -Pmediasage.worker=true --no-daemon` at image-build
  time, then deletes the copied source in the same layer. Only the warmed `~/.gradle` / `~/.m2`
  caches survive. Cold first render ≈ 15 min → warm per-ticket render ≈ 10 s.
- **iOS stays gated.** `-Pmediasage.worker=true` skips the iOS target registration in both
  `:composeApp` and `:shared`, so the ~3 GB Kotlin/Native toolchain is never downloaded on the
  Linux image. Net image growth ≈ 500 MB (SDK + Robolectric jar + androidx/compose), and the
  build must show **no** `kotlin-native-prebuilt` download.
- **Runner disk.** `build-worker-image.yml` reclaims the runner's preinstalled toolchains before
  building, since the render pre-bake pulls the SDK and caches into the image at build time.

## Worker sizing: raising CPU/memory to speed the render (MS-589)

A UI ticket-work run takes ~8 min vs ~1 min for a non-UI run; almost the entire gap is the
`:composeApp` compile inside the render. Compiling the changed module is unavoidable (Kotlin
compiles at module granularity, and the worker clones fresh so there is no incremental state to
reuse), but the compile *duration* is compute-bound, not fixed. Kotlin + Compose compilation
parallelises across cores, and 4 GiB is tight for it (GC pressure). Because Cloud Run bills per
vCPU-second, a wider-but-shorter run is roughly cost-neutral while cutting wall-clock — so the
first lever tried was simply a bigger machine.

The worker Cloud Run job sizing lives in `.github/workflows/build-worker-image.yml`, which is the
declarative source of truth for the job (`--cpu` / `--memory` in the deploy step). That workflow
redeploys the job on every merge to `main`, so a sizing change only takes effect after merge.

### Sizing chosen: 8 CPU / 16 GiB

The ticket suggested trying `4 CPU / 8 GiB` first. We went straight to **`8 CPU / 16 GiB`** (up
from `2 CPU / 4 GiB`). Tradeoff noted: a larger jump is more likely to produce a clearly visible
wall-clock cut, but it makes the gain harder to attribute to any single factor (added cores vs. GC
headroom) — if a later, cheaper sizing is wanted, `4 CPU / 8 GiB` is the untested middle point.
`8 CPU / 16 GiB` is within Cloud Run job limits, so there is no quota concern.

### Measurement: before / after

Measured on a real UI render triggered via `/ui-pipeline-test`. The render-build wall-clock is read
from the Cloud Run execution logs (the Gradle `recordRoborazziDebug` build); total run duration is
the job execution wall-clock. The advisor's `duration_ms` is *not* used as the render-build proxy —
it measures the whole agent session and does not isolate the compile (e.g. non-UI MS-585 showed a
larger `duration_ms` than UI MS-587).

| Metric | Before (2 CPU / 4 GiB) | After (8 CPU / 16 GiB) |
|---|---|---|
| Render build wall-clock | ~4m14s (MS-587) | _pending post-deploy measurement_ |
| Total run duration | ~5m50s (MS-587, advisor `duration_ms` 349569) | _pending post-deploy measurement_ |
| Non-UI run duration | ~1 min (unchanged baseline) | _confirm unchanged_ |

The **before** row uses MS-587 — a real UI render that ran at the current `2 CPU / 4 GiB` sizing,
so no redundant baseline run was needed. The **after** row is filled in once this change merges and
the worker redeploys at the new sizing (a fresh `/ui-pipeline-test` is run and the render-build time
read from its Cloud Run logs). A non-UI run is also confirmed to still complete in ~1 min and not be
made materially more expensive.

### Follow-up levers (out of scope here)

If the CPU/RAM bump alone is insufficient, the next levers — noted here but not implemented in
MS-589 — are:

- **Persisting Kotlin incremental-compile state** for same-branch re-renders, which would help the
  self-critique iterate-on-a-fix loop (each fix currently recompiles from cold).
- **A warm Gradle daemon.** `--no-daemon` was chosen only for the 4 GiB limit; with memory raised, a
  reused daemon becomes viable.
- **Gradle configuration-cache reuse.**
