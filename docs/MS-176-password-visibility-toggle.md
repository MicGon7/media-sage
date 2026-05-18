# MS-176: Password Visibility Toggle on Login Screen

## What Changed

Added a show/hide password toggle to the `LoginScreen.kt` password field.

## Implementation

**State:**

```kotlin
var passwordVisible by rememberSaveable { mutableStateOf(false) }
```

`rememberSaveable` survives configuration changes (rotation) and resets on navigation — the right scope for ephemeral form UI state.

**Visual transformation:**

```kotlin
visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
```

**Trailing icon:**

```kotlin
trailingIcon = {
    IconButton(onClick = { passwordVisible = !passwordVisible }) {
        Icon(
            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = stringResource(
                if (passwordVisible) Res.string.login_hide_password else Res.string.login_show_password
            ),
            tint = OnGradientMuted
        )
    }
}
```

**String resources added to `strings.xml`:**

- `login_show_password` → `"Show password"`
- `login_hide_password` → `"Hide password"`

The `materialIconsExtended` dependency was already present in `composeApp/build.gradle.kts`, so no dependency changes were needed.

## Key Patterns

- Local UI state that doesn't belong in the ViewModel (no business logic) goes in the composable with `rememberSaveable`.
- Accessibility `contentDescription` on the icon must reflect the *current action* (what will happen on tap), not the current state. When visible, the icon is VisibilityOff and the description is "Hide password".
- The `OnGradientMuted` tint keeps the icon consistent with the muted label style on the dark gradient background.
