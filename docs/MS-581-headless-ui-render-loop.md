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
