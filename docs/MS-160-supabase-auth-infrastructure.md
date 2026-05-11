# MS-160: Supabase Auth Infrastructure

## What changed

Added Supabase authentication infrastructure for KMP (Android + iOS), including sign-in, sign-out, remember email, and a bypass for dev environments without credentials.

## Architecture decisions

### Nullable SupabaseClient via conditional Koin registration

`SupabaseClient` is only registered in Koin when `supabaseUrl` and `supabaseAnonKey` are non-blank. `AuthRepositoryImpl` receives a nullable `SupabaseClient?` via `getOrNull<SupabaseClient>()`. When null, `observeAuthState()` returns `flowOf(null)` and `signInWithEmail()` throws "Supabase not configured". Koin does not support `single<T?>` — registering a nullable type fails at runtime. The fix is to register the non-null type conditionally and use `getOrNull()` at the injection site.

### `AuthUiState.Loading` prevents flash-of-login-screen

`stateIn(SharingStarted.Eagerly, AuthUiState.Loading)` means `App.kt` renders nothing (`Unit`) until auth status resolves. Without this, the login screen flashes briefly on every cold start for authenticated users.

### Auth gate lives in `App.kt`, not in the scaffold

Login is a full-screen takeover outside `MediaSageScaffold`. The nav stack and bottom bar only exist for authenticated users. Keeping the gate in `App.kt` avoids leaking auth concerns into navigation.

### `SessionStatus.Initializing` filter (supabase-kt 3.6.0)

supabase-kt 3.6.0 renamed `LoadingFromStorage` to `Initializing`. Filtering it out means `observeAuthState()` only emits once Supabase finishes reading from storage — preventing an `Unauthenticated` pulse on cold start.

### No BOM — version pinned directly

KMP's `commonMain.dependencies {}` block treats `platform()` as a Kotlin error in 2.3. Since `supabase-auth` has its own `version.ref` entry in the version catalog, the BOM is unnecessary.

### `_authBypass` + `bypassAuth()` / `resetBypass()` in AppViewModel

When Supabase credentials are not configured, users can tap "Continue without signing in." `AppViewModel` holds a `_authBypass: MutableStateFlow<Boolean>` combined with `observeAuthState()`. `bypassAuth()` sets it true (forces `Authenticated`); `resetBypass()` sets it false (lets real auth state apply). This allows sign-out to work correctly even when no Supabase session exists.

### `AppViewModel` does not follow the MVI contract pattern

`AppViewModel` is a composition root orchestrator, not a feature screen. `darkMode` and `authState` are collected independently in `App.kt` for different purposes (theme vs auth gate). `bypassAuth()` and `resetBypass()` are lifecycle events, not user intents. The contract pattern is for feature screens — `AppViewModel` is correctly different.

### `koinViewModel<AppViewModel>()` in MediaSageScaffold creates a different instance

Nav3 `NavEntry` sets its own `LocalViewModelStoreOwner`. Calling `koinViewModel<AppViewModel>()` inside a NavEntry creates a NavEntry-scoped instance, not the Activity-scoped one in `App.kt`. Fix: pass `onSignedOut: () -> Unit` from `App.kt` into `MediaSageScaffold` so `resetBypass()` is always called on the correct instance.

### `LoginViewModel` state persists across sign-out

`LoginViewModel` is Activity-scoped. After a successful sign-in, the state holds `isLoading = true`. When the user signs out and the login screen reappears, the button renders in loading state. Fix: reset `isLoading = false` on sign-in success before sending `NavigateToHome`.

### UserPreferencesRepository — separate from ThemePreferencesRepository

Account-level preferences (remembered email) live in a separate `UserPreferencesRepository` with its own `user.preferences_pb` DataStore file. Theme is a UI concern; user preferences are an account concern. Mixing them makes both harder to reason about as the app grows.

### Login screen is intentionally always dark

The masthead aesthetic is a brand moment. Wrapping in `isSystemInDarkTheme()` and inverting colors adds complexity and dilutes the effect. `LoginScreen` wraps its content in `MediaSageTheme(darkTheme = true)` unconditionally. The public `LoginScreen` function takes no theme parameter — `LoginScreenContent` is private and owns the gradient via `backgroundColors`.

## Files changed

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Added `supabase = "3.6.0"`, `android-desugar-jdk = "2.1.4"`, library entries |
| `shared/build.gradle.kts` | Added `supabase-auth` to `commonMain.dependencies` |
| `composeApp/build.gradle.kts` | Added `SUPABASE_URL`/`SUPABASE_ANON_KEY` BuildConfig fields, core library desugaring |
| `shared/.../domain/model/UserSession.kt` | New: `userId`, `email` |
| `shared/.../domain/repository/AuthRepository.kt` | New interface: `observeAuthState`, `currentSession`, `signInWithEmail`, `signOut` |
| `shared/.../data/repository/AuthRepositoryImpl.kt` | Supabase-backed implementation; null-safe for unconfigured credentials |
| `shared/.../di/SharedModule.kt` | Conditional `SupabaseClient` + `getOrNull` for `AuthRepository` |
| `composeApp/.../di/AppModule.kt` | `AppViewModel`, `LoginViewModel`, `SettingsViewModel`, `YouViewModel` wiring |
| `composeApp/.../AppViewModel.kt` | `AuthUiState`, `authState`, `bypassAuth()`, `resetBypass()` |
| `composeApp/.../App.kt` | Auth gate; passes `onSignedOut` lambda to `MediaSageScaffold` |
| `composeApp/.../navigation/MediaSageScaffold.kt` | `onSignedOut` param; `SettingsViewModel` side effect collection |
| `composeApp/.../feature/login/LoginContract.kt` | MVI contract with `rememberEmail` toggle and `BypassAuth` intent |
| `composeApp/.../feature/login/LoginViewModel.kt` | Sign-in, bypass, remember email via `UserPreferencesRepository` |
| `composeApp/.../feature/login/LoginScreen.kt` | Newspaper masthead design, always-dark, 4 state + 4 theme previews |
| `composeApp/.../feature/settings/SettingsContract.kt` | New: `SignOut` intent, `SignedOut` side effect |
| `composeApp/.../feature/settings/SettingsViewModel.kt` | New: calls `authRepository.signOut()` |
| `composeApp/.../feature/settings/SettingsScreen.kt` | Replaced shell with sign-out row |
| `composeApp/.../data/UserPreferencesRepository.kt` | New: `rememberedEmail` DataStore key |
| `composeApp/.../di/UserModule.android.kt` | New: Android DataStore wiring for `UserPreferencesRepository` |
| `composeApp/.../di/UserModule.ios.kt` | New: iOS DataStore wiring for `UserPreferencesRepository` |
| `composeApp/src/androidMain/.../MediaSageApplication.kt` | Added `userModule` |
| `composeApp/src/iosMain/.../MainViewController.kt` | Added `userModule`, named Supabase params |

## Core library desugaring

supabase-kt 3.x uses Java APIs from API 26+. With `minSdk = 24`, added `isCoreLibraryDesugaringEnabled = true` and `coreLibraryDesugaring(libs.android.desugar.jdk)` to `composeApp/build.gradle.kts`.

## Next steps

- Add Supabase URL and anon key to iOS `MainViewController.kt` once iOS credentials are available
- Sign-up and password reset flows
- `compose-auth` (native Google/Apple buttons) deferred until credentials are available
