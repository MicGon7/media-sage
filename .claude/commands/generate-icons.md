# /generate-icons — Resize a reference image into iOS and Android icon assets

Accepts a source image path and generates all required sizes using ImageMagick.
No Gradle task or compilation required.

**Invocation:** `/generate-icons /absolute/path/to/icon.png`

---

## Steps

### 1. Identify the source image

The argument passed after the skill name is the source image path. If no path was provided,
ask the user: "What is the path to the source image?"

Store the path as `SOURCE`.

### 2. Validate prerequisites

Confirm the source file exists:

```bash
test -f "$SOURCE" && echo "OK" || echo "ERROR: $SOURCE not found"
```

Stop and report the error if the file does not exist.

Confirm ImageMagick is available:

```bash
which convert
```

If `convert` is not found, stop and tell the user: "ImageMagick is not installed.
Run `brew install imagemagick` on macOS, then re-invoke the skill."
Do not attempt to install it automatically.

### 3. Write the iOS icon

Target: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png` (1024×1024)

```bash
convert "$SOURCE" -resize 1024x1024! \
  iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png
```

### 4. Write Android legacy mipmap PNGs

Write `ic_launcher.png` and `ic_launcher_round.png` at each density:

| Density folder    | Size |
|-------------------|------|
| mipmap-mdpi       | 48×48 |
| mipmap-hdpi       | 72×72 |
| mipmap-xhdpi      | 96×96 |
| mipmap-xxhdpi     | 144×144 |
| mipmap-xxxhdpi    | 192×192 |

Run these commands from the project root:

```bash
RES=composeApp/src/androidMain/res

convert "$SOURCE" -resize 48x48!   $RES/mipmap-mdpi/ic_launcher.png
convert "$SOURCE" -resize 48x48!   $RES/mipmap-mdpi/ic_launcher_round.png
convert "$SOURCE" -resize 72x72!   $RES/mipmap-hdpi/ic_launcher.png
convert "$SOURCE" -resize 72x72!   $RES/mipmap-hdpi/ic_launcher_round.png
convert "$SOURCE" -resize 96x96!   $RES/mipmap-xhdpi/ic_launcher.png
convert "$SOURCE" -resize 96x96!   $RES/mipmap-xhdpi/ic_launcher_round.png
convert "$SOURCE" -resize 144x144! $RES/mipmap-xxhdpi/ic_launcher.png
convert "$SOURCE" -resize 144x144! $RES/mipmap-xxhdpi/ic_launcher_round.png
convert "$SOURCE" -resize 192x192! $RES/mipmap-xxxhdpi/ic_launcher.png
convert "$SOURCE" -resize 192x192! $RES/mipmap-xxxhdpi/ic_launcher_round.png
```

### 5. Write the Android adaptive foreground PNG

The foreground canvas is 432×432 px (xxxhdpi = 4× density for the 108dp adaptive slot).
The safe zone is 72/108 of 432 = 288px. Center the artwork with 72px padding on each side.

```bash
convert "$SOURCE" -resize 288x288! \
  -gravity Center -background none -extent 432x432 \
  composeApp/src/androidMain/res/drawable/ic_launcher_foreground.png
```

### 6. Remove the old vector XML foreground (if present)

```bash
rm -f composeApp/src/androidMain/res/drawable-v24/ic_launcher_foreground.xml
```

### 7. Write the brand background color

```bash
cat > composeApp/src/androidMain/res/drawable/ic_launcher_background.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<color xmlns:android="http://schemas.android.com/apk/res/android">#1C1A14</color>
EOF
```

### 8. Verify output

Confirm all expected files were written:

```bash
echo "=== iOS ===" && ls -lh iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png
echo "=== Android mipmap ===" && find composeApp/src/androidMain/res -name "ic_launcher*.png" | sort
echo "=== Adaptive foreground ===" && ls -lh composeApp/src/androidMain/res/drawable/ic_launcher_foreground.png
echo "=== Background ===" && cat composeApp/src/androidMain/res/drawable/ic_launcher_background.xml
```

Report the file sizes to the user and confirm:
> "All icon assets written. Build the app and verify the icon on device/emulator to complete the AC."
