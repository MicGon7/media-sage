# MS-212 — Pre-bake :composeApp and :shared Gradle Deps into Worker Image

## What Was Built

Extended the Gradle dependency pre-baking in `Dockerfile.worker` to cover all four
modules: `:agent`, `:server`, `:composeApp`, and `:shared`. Also added `.mcp.json`
to `.gitignore` to prevent accidental credential commits from autonomous agent runs.

Before this, `:composeApp` and `:shared` deps were downloaded cold on every Cloud Run
worker execution — adding 4+ minutes to the first Gradle invocation. When a test
failed and required a retry, that cost was paid twice. In the MS-180 autonomous run,
this contributed to the container timing out 48 seconds before completion.

## Investigation Findings (Phase 1)

Three measurements taken in fresh `eclipse-temurin:21-jdk-jammy` containers with no
pre-existing Gradle cache:

| Task | Time | Notes |
|---|---|---|
| `./gradlew :shared:dependencies --configure-on-demand` | 4m 10s | Kotlin/Native prebuilt downloaded |
| `./gradlew :composeApp:dependencies --configure-on-demand` | 5m 18s | AGP works without Android SDK |
| `.konan` directory after deps task | **Not created** | Toolchain stays as Maven artifact |

### Why Single-Stage (No Multi-Stage Build Needed)

The initial assumption was that `:shared`'s `iosArm64`/`iosSimulatorArm64` targets
would require the full Kotlin/Native toolchain (~3 GB extracted) in the image, forcing
a multi-stage build to avoid image bloat.

The key finding: **the `dependencies` task does not extract the toolchain to `.konan`**.
It only downloads `kotlin-native-prebuilt:2.3.20` as a Maven artifact (~245 MB) to
`.gradle/caches/modules-2`. The 3 GB extraction only happens when native compilation is
actually executed — which the worker never does.

At runtime, if Gradle needs to configure native targets (e.g. for detekt on `:shared`),
it extracts from the cached artifact via local disk I/O rather than downloading over
the network. Local extraction is significantly faster than a cold network download.

### Why No Android SDK Needed

AGP resolves `:composeApp` dependencies without the Android SDK at configuration time.
The SDK is only required for actual Android compilation (APK/AAB builds, instrumented
tests). The worker never compiles Android code — it runs JVM-only quality gates.

The failure `android.useAndroidX` property is not enabled revealed that `gradle.properties`
must be copied into the build context. AGP reads this at configuration time to determine
AndroidX migration mode. Without it, the dependency resolution fails.

## What Changed

### `Dockerfile.worker`

Added to the COPY block:
```dockerfile
COPY --chown=agent:agent gradle.properties gradle.properties
COPY --chown=agent:agent composeApp/build.gradle.kts composeApp/build.gradle.kts
COPY --chown=agent:agent shared/build.gradle.kts shared/build.gradle.kts
```

Extended the pre-bake command:
```dockerfile
RUN chmod +x gradlew && \
    ./gradlew help --no-daemon && \
    ./gradlew :agent:dependencies :server:dependencies :composeApp:dependencies :shared:dependencies \
        --no-daemon --configure-on-demand && \
    rm -f gradlew && rm -rf gradle
```

### `.gitignore`

Added `.mcp.json` under `# Environment/Secrets`. Claude Code writes MCP server
configuration (including API credentials) to `.mcp.json` during worker runs. Without
this entry, an autonomous agent could detect the file as a staged credential change
and stall before committing. Making it untrackable removes the decision entirely.

## Image Size

| | Size |
|---|---|
| Before (MS-209, :agent + :server only) | ~488 MB |
| After (MS-212, all four modules) | 917 MB |
| Delta | +429 MB |

The 780 MB Gradle cache baked into the image includes `kotlin-native-prebuilt`,
all `androidx.*` libraries, Compose Multiplatform, Room, Ktor Client, and all
`:agent`/`:server` deps from MS-209.

## Expected Impact

Eliminates the 4+ minute cold Gradle dep download on every worker execution.
Combined with the existing `:agent`/`:server` pre-baking, the first `./gradlew`
invocation should resolve all dependencies from the image layer with zero network I/O.

A test failure requiring a retry no longer re-pays the cold download cost — the second
Gradle run is as fast as the first.

## Key Learnings

1. **`gradle.properties` must be in the build context** — AGP reads `android.useAndroidX`
   at configuration time. Missing it causes `allInstrumentedTestSourceSetsCompileDependenciesMetadata`
   to fail with a hard error. Always copy `gradle.properties` when pre-baking Android modules.

2. **`dependencies` task ≠ toolchain extraction** — Kotlin/Native toolchain is only extracted
   to `.konan` when native compilation tasks run. Dep pre-baking only downloads the Maven
   artifact. The image does not need to contain the extracted 3 GB toolchain.

3. **Multi-stage builds are for compilation artifacts, not dep pre-baking** — The web
   recommendation for multi-stage KMP Docker builds applies when building iOS/Android
   binaries. For dep pre-baking only, single-stage with a bare JDK is sufficient.

4. **`--configure-on-demand` works across all four modules** — Gradle only evaluates
   the build files of the explicitly requested modules. `:scripts` is never configured,
   and its source code is not needed.

5. **`.mcp.json` must be gitignored** — Claude Code creates this file during sessions.
   Any credential-containing config file that can be created at runtime must be in
   `.gitignore` before autonomous agents run, not discovered during a commit.
