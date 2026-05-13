# MS-93: TestFlight CI Setup

## What was built

A GitHub Actions workflow that automatically builds the iOS app and uploads it to TestFlight on every push to `main`. The full pipeline:

1. `actions/setup-java@v4` (JDK 21, Temurin)
2. `ruby/setup-ruby@v1` (Ruby 3.3 with Bundler cache)
3. `./gradlew :composeApp:linkReleaseFrameworkIosArm64` — pre-builds the KMP iOS framework
4. Write App Store Connect `.p8` key from GitHub secret to `fastlane/AuthKey.p8`
5. `bundle exec fastlane ios beta` — runs Match (readonly), increments build number, builds `.ipa`, uploads to TestFlight

## Key decisions

**App Store Connect API key via filepath, not content**
The Fastlane `app_store_connect_api_key` action has a bug with `key_content:` on some LibreSSL versions — it fails to parse the key. Workaround: write the `.p8` content to a file and pass `key_filepath:` instead.

**Manual code signing with xcargs**
Match fetches AppStore certificates and provisioning profiles. Signing identity and profile are forced via xcargs to bypass Xcode's automatic signing, which would fail in CI:
```ruby
xcargs: "CODE_SIGN_STYLE=Manual DEVELOPMENT_TEAM=#{team_id} PROVISIONING_PROFILE_SPECIFIER='match AppStore com.thecouragepost.app' CODE_SIGN_IDENTITY='Apple Distribution'"
```

**One match call, not two**
An earlier attempt called `match(type: "development")` before `match(type: "appstore")`. This caused a cert mismatch: Xcode picked up the Development cert from the keychain for an App Store build. The fix is a single `match(type: "appstore", readonly: true)`.

**Build number from timestamp**
`build_number: Time.now.to_i.to_s` — monotonically increasing, requires no state storage between runs.

## The blocker: `IrTypeAliasSymbolImpl is already bound`

### Root cause

`compottie-dot 2.0.0-rc04` was compiled with **Kotlin 2.0.21** (klib ABI version 1.8.0). In that version, it was compiled against `kotlinx-datetime 0.6.x`, which declared `kotlinx.datetime.Instant` as a typealias for `kotlin.time.Instant`. The compiled klib embedded this typealias in its IR.

When Kotlin/Native 2.3.x links the Release framework, it deserializes ALL klib IR eagerly (`deserializeAllFileReachableTopLevel`). It finds `kotlinx.datetime.Instant` as a typealias declaration in **both**:
- `compottie-dot.klib` (embedded from the library it was compiled against)
- `kotlinx-datetime-iosArm64Main-0.7.1.klib` (the standalone dep)

Registering the same symbol twice throws `IrTypeAliasSymbolImpl is already bound`.

**Debug builds were unaffected** because Debug mode defers IR deserialization — declarations are processed lazily, so the second registration never triggers.

### Why it took so long to find

The error was assumed to be CI-specific because the app "worked locally." In reality, local runs only tested the Debug simulator build (`linkDebugFrameworkIosSimulatorArm64`). The Release build (`linkReleaseFrameworkIosArm64`) — which CI uses — was never run locally until late in the investigation. Confirming which build task ran locally should always be step one when a CI build fails for a "working" project.

### Diagnostic command

```bash
./gradlew :composeApp:linkReleaseFrameworkIosArm64 --debug 2>&1 | grep "compiler_version\|\.klib"
```

This prints all klib files passed to the linker plus their manifest compiler versions. Any klib compiled with a significantly older Kotlin version is a suspect for IR ABI incompatibilities.

### Fix

Upgrade `compottie` from `2.0.0-rc04` → `2.0.3`:
- 2.0.3 was compiled with **Kotlin 2.3.0** (klib ABI 2.3.0)
- Dependencies are properly referenced, not re-declared in the klib IR
- No duplicate symbol at link time

Also add `implementation(libs.compottie)` (the umbrella artifact) to `composeApp` commonMain dependencies. In 2.0.3, the `compottie` package was split: `compottie-core` contains the low-level API with required parameters; `compottie` re-exports it with ergonomic defaults. `compottie-dot` only pulls in `compottie-core` transitively — without the umbrella, `rememberLottiePainter` requires `enableExpressions` and `expressionEngineFactory` explicitly.

## Lesson: active human engagement, not passive watching

This ticket is a documented example of where the human's active participation — reading logs directly — was the critical unblocking factor.

**The pre-existing failure:** When the human reviewed the logs, they recognized that `linkReleaseFrameworkIosArm64` was already broken *before* TestFlight CI was set up. The failure predated the work. The AI framed it as "why does TestFlight CI fail?" and never questioned that scope. An engaged human reading the early output would have reframed it immediately: the CI task was surfacing a pre-existing bug, not introducing a new one.

**The distinction that matters:**
- *Passive*: watching tool calls get approved, waiting for a summary
- *Active*: reading actual build output, questioning whether a failure is new or pre-existing, understanding what each task does

The AI can maintain full session context and still loop on the wrong problem if the framing goes unchallenged. Context is not the same as judgment.

**Interventions that mattered:**
- Human read the logs directly → recognized the release build was broken before this work started
- "We are just wasting tokens now" → forced a strategy reset
- "Do you know that datetime.Instant is deprecated?" → introduced a new diagnostic angle

**Rule for AI:** If the same class of fix fails 3+ times, stop and say so. Don't silently try the next variation — inspect the actual linker inputs and verify the problem scope.

**Rule for humans:** Read the logs. When a new CI task fails, ask whether it was passing before this work started. The AI's problem framing is a hypothesis, not a fact.

## Files changed

- `gradle/libs.versions.toml` — `compottie = "2.0.3"`, `kotlinx-datetime = "0.7.1"`
- `composeApp/build.gradle.kts` — added `implementation(libs.compottie)`, kept `implementation(libs.compottie.dot)` and `implementation(libs.kotlinx.datetime)`
- `shared/src/commonMain/.../DailyReflectionRepositoryImpl.kt` — `import kotlin.time.Instant` (stdlib, not kotlinx-datetime)
- `composeApp/src/commonMain/.../HomeViewModel.kt` — `import kotlin.time.Instant` (stdlib, not kotlinx-datetime)
- `.github/workflows/testflight.yml` — TestFlight CI workflow
- `fastlane/Fastfile` — `beta` lane with Match, build number, TestFlight upload
- `fastlane/Gemfile` — fastlane gem
