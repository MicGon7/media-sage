package com.mediasage.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import com.mediasage.LocalAppVersion
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasage.theme.DarkBackground
import com.mediasage.theme.InkLight
import com.mediasage.theme.MediaSageTheme
import com.mediasage.theme.Navy
import com.mediasage.theme.NavyLight
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.login_bypass
import mediasage.composeapp.generated.resources.login_email_label
import mediasage.composeapp.generated.resources.login_hide_password
import mediasage.composeapp.generated.resources.login_masthead_line1
import mediasage.composeapp.generated.resources.login_masthead_line2
import mediasage.composeapp.generated.resources.login_member_edition
import mediasage.composeapp.generated.resources.login_password_label
import mediasage.composeapp.generated.resources.login_remember_email
import mediasage.composeapp.generated.resources.login_show_password
import mediasage.composeapp.generated.resources.login_sign_in_button
import mediasage.composeapp.generated.resources.login_subtitle
import org.jetbrains.compose.resources.stringResource

private val OnGradient = InkLight
private val OnGradientMuted = Color(0xFFB0A898)
private val FieldBorder = Color(0xFF6B7A8D)

@Composable
fun LoginScreen(
    state: LoginContract.UiState,
    onIntent: (LoginContract.Intent) -> Unit
) {
    LoginScreenContent(state = state, onIntent = onIntent)
}

@Composable
private fun LoginScreenContent(
    state: LoginContract.UiState,
    onIntent: (LoginContract.Intent) -> Unit,
    backgroundColors: List<Color> = listOf(NavyLight, Navy)
) {
    // Login screen is intentionally always dark - the masthead is a brand moment
    MediaSageTheme(darkTheme = true) {
        val focusManager = LocalFocusManager.current
        var email by rememberSaveable(state.rememberedEmail) { mutableStateOf(state.rememberedEmail) }
        var password by rememberSaveable { mutableStateOf("") }
        var passwordVisible by rememberSaveable { mutableStateOf(false) }
        var localError by rememberSaveable { mutableStateOf("") }

        val isLoading = state.isLoading
        val serverError = state.error ?: ""

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(backgroundColors))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Masthead
                HorizontalDivider(color = OnGradientMuted, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.login_member_edition),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                        letterSpacing = 3.sp,
                        color = OnGradientMuted
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = OnGradient, thickness = 2.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(Res.string.login_masthead_line1),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 5.sp,
                        color = OnGradient
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(Res.string.login_masthead_line2),
                    style = MaterialTheme.typography.displayMedium.copy(
                        letterSpacing = 12.sp,
                        color = OnGradient
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = OnGradient, thickness = 2.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.login_subtitle),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = OnGradientMuted
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = OnGradientMuted, thickness = 0.5.dp)

                Spacer(modifier = Modifier.height(36.dp))

                // Form
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OnGradient,
                    unfocusedTextColor = OnGradient,
                    focusedBorderColor = OnGradient,
                    unfocusedBorderColor = FieldBorder,
                    focusedLabelColor = OnGradient,
                    unfocusedLabelColor = OnGradientMuted,
                    cursorColor = OnGradient,
                    errorBorderColor = MaterialTheme.colorScheme.error,
                    errorLabelColor = MaterialTheme.colorScheme.error,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; localError = "" },
                    label = { Text(stringResource(Res.string.login_email_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    isError = localError.isNotEmpty() || serverError.isNotEmpty(),
                    enabled = !isLoading,
                    colors = fieldColors
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; localError = "" },
                    label = { Text(stringResource(Res.string.login_password_label)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            submitSignIn(email, password, onIntent) { localError = it }
                        }
                    ),
                    isError = localError.isNotEmpty() || serverError.isNotEmpty(),
                    enabled = !isLoading,
                    colors = fieldColors
                )
                val displayError = localError.ifEmpty { serverError }
                if (displayError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.login_remember_email),
                        style = MaterialTheme.typography.bodySmall.copy(color = OnGradientMuted)
                    )
                    Switch(
                        checked = state.rememberEmail,
                        onCheckedChange = { onIntent(LoginContract.Intent.ToggleRememberEmail(it)) },
                        enabled = !isLoading,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Navy,
                            checkedTrackColor = OnGradientMuted,
                            uncheckedThumbColor = OnGradientMuted,
                            uncheckedTrackColor = Color.Transparent,
                            uncheckedBorderColor = FieldBorder,
                        )
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        submitSignIn(email, password, onIntent) { localError = it }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = OnGradient,
                        disabledContentColor = OnGradientMuted
                    ),
                    border = BorderStroke(1.dp, if (isLoading) FieldBorder else OnGradient)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = OnGradientMuted)
                    } else {
                        Text(
                            text = stringResource(Res.string.login_sign_in_button),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { onIntent(LoginContract.Intent.BypassAuth) },
                    enabled = !isLoading
                ) {
                    Text(
                        text = stringResource(Res.string.login_bypass),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            color = OnGradientMuted
                        )
                    )
                }
            }
            val appVersion = LocalAppVersion.current
            if (appVersion.isNotEmpty()) {
                Text(
                    text = appVersion,
                    style = MaterialTheme.typography.labelSmall.copy(color = OnGradientMuted),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .navigationBarsPadding()
                        .fillMaxWidth(),

                )
            }
        }
    }
}

private fun submitSignIn(
    email: String,
    password: String,
    onIntent: (LoginContract.Intent) -> Unit,
    onLocalError: (String) -> Unit
) {
    if (email.isBlank() || password.isBlank()) {
        onLocalError("Please enter your email and password")
        return
    }
    onIntent(LoginContract.Intent.SignInWithEmail(email, password))
}

// State previews
@Preview(showBackground = true, name = "Idle")
@Composable
private fun LoginScreenPreview() {
    LoginScreenContent(state = LoginContract.UiState(), onIntent = {})
}

@Preview(showBackground = true, name = "Remember Email On")
@Composable
private fun LoginScreenRememberEmailPreview() {
    LoginScreenContent(
        state = LoginContract.UiState(rememberedEmail = "user@example.com", rememberEmail = true),
        onIntent = {}
    )
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun LoginScreenLoadingPreview() {
    LoginScreenContent(state = LoginContract.UiState(isLoading = true), onIntent = {})
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun LoginScreenErrorPreview() {
    LoginScreenContent(
        state = LoginContract.UiState(error = "Invalid email or password"),
        onIntent = {}
    )
}

// Theme comparisons - reference only, Theme C is the active default
@Preview(showBackground = true, name = "Theme A - Navy → Dark")
@Composable
private fun LoginThemeAPreview() {
    LoginScreenContent(
        state = LoginContract.UiState(),
        onIntent = {},
        backgroundColors = listOf(Navy, DarkBackground)
    )
}

@Preview(showBackground = true, name = "Theme B - Navy → NavyLight")
@Composable
private fun LoginThemeBPreview() {
    LoginScreenContent(
        state = LoginContract.UiState(),
        onIntent = {},
        backgroundColors = listOf(Navy, NavyLight)
    )
}

@Preview(showBackground = true, name = "Theme C - NavyLight → Navy (active)")
@Composable
private fun LoginThemeCPreview() {
    LoginScreenContent(
        state = LoginContract.UiState(),
        onIntent = {},
        backgroundColors = listOf(NavyLight, Navy)
    )
}

@Preview(showBackground = true, name = "Theme D - Flat Navy")
@Composable
private fun LoginThemeDPreview() {
    LoginScreenContent(
        state = LoginContract.UiState(),
        onIntent = {},
        backgroundColors = listOf(Navy, Navy)
    )
}

// ─── Exploration previews ────────────────────────────────────────────────────
// Exploration only — replace with MediaSageTheme before implementing.
// Based on reference image: dark olive background, globe hero, lime CTA, social sign-in.

@Preview(showBackground = true, name = "Exploration — Dark Olive / Lime")
@Composable
private fun LoginExplorationPreview() {
    LoginExplorationContent()
}

private val ExplorationDarkOlive = Color(0xFF12140A)
private val ExplorationLimeGreen = Color(0xFFCCFF33)
private val ExplorationFieldSurface = Color(0xFF1E2010)
private val ExplorationMutedText = Color(0xFF8A9070)
private val ExplorationOnBackground = Color(0xFFF0F0E8)
private val ExplorationOutline = Color(0xFF3A3D28)

@Composable
private fun LoginExplorationContent() {
    // Exploration only — replace with MediaSageTheme before implementing
    val darkOlive = ExplorationDarkOlive
    val limeGreen = ExplorationLimeGreen
    val fieldSurface = ExplorationFieldSurface
    val mutedText = ExplorationMutedText

    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            background = darkOlive,
            surface = fieldSurface,
            primary = limeGreen,
            onPrimary = Color(0xFF0A0C04),
            onBackground = Color(0xFFF0F0E8),
            onSurface = Color(0xFFF0F0E8),
            outline = Color(0xFF3A3D28)
        )
    ) {
        Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(darkOlive)
        ) {
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(48.dp)
                )

                // Globe hero placeholder — swap for real asset when implementing
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .height(120.dp)
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = androidx.compose.ui.Modifier
                            .height(100.dp)
                            .fillMaxWidth()
                    ) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF4A5C1A), Color(0xFF1A2008), Color(0xFF0A0C04)),
                                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
                                radius = size.minDimension / 2f
                            ),
                            radius = size.minDimension / 2f
                        )
                    }
                }

                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(16.dp)
                )

                // Headline
                Text(
                    text = "Welcome Back!",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF0F0E8)
                    ),
                    textAlign = TextAlign.Center
                )
                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(8.dp)
                )
                Text(
                    text = "Sign in to access your daily news and theological reflections.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = mutedText),
                    textAlign = TextAlign.Center
                )

                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(32.dp)
                )

                // Email field
                Text(
                    text = "Email address*",
                    style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFF0F0E8)),
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(4.dp)
                )
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("example@gmail.com", color = mutedText) },
                    singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFF3A3D28),
                        focusedBorderColor = limeGreen,
                        unfocusedContainerColor = fieldSurface,
                        focusedContainerColor = fieldSurface,
                        unfocusedTextColor = Color(0xFFF0F0E8),
                        focusedTextColor = Color(0xFFF0F0E8)
                    )
                )

                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(12.dp)
                )

                // Password field
                Text(
                    text = "Password*",
                    style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFF0F0E8)),
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(4.dp)
                )
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("••••••••", color = mutedText) },
                    singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFF3A3D28),
                        focusedBorderColor = limeGreen,
                        unfocusedContainerColor = fieldSurface,
                        focusedContainerColor = fieldSurface,
                        unfocusedTextColor = Color(0xFFF0F0E8),
                        focusedTextColor = Color(0xFFF0F0E8)
                    )
                )

                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(12.dp)
                )

                // Remember me + Forgot password row
                Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = false,
                            onCheckedChange = {},
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                uncheckedColor = mutedText,
                                checkmarkColor = darkOlive,
                                checkedColor = limeGreen
                            )
                        )
                        Text("Remember me", style = MaterialTheme.typography.bodySmall.copy(color = mutedText))
                    }
                    TextButton(onClick = {}) {
                        Text("Forgot Password?", style = MaterialTheme.typography.bodySmall.copy(color = mutedText))
                    }
                }

                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(16.dp)
                )

                // Lime CTA button
                androidx.compose.material3.Button(
                    onClick = {},
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = limeGreen,
                        contentColor = Color(0xFF0A0C04)
                    )
                ) {
                    Text(
                        "Sign in",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(20.dp)
                )

                // "Or continue with" divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = androidx.compose.ui.Modifier.weight(1f), color = Color(0xFF3A3D28))
                    Text(
                        "  Or continue with  ",
                        style = MaterialTheme.typography.bodySmall.copy(color = mutedText)
                    )
                    HorizontalDivider(modifier = androidx.compose.ui.Modifier.weight(1f), color = Color(0xFF3A3D28))
                }

                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(12.dp)
                )

                // Social buttons — text only (no brand icons available)
                Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {},
                        modifier = androidx.compose.ui.Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, Color(0xFF3A3D28)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF0F0E8))
                    ) {
                        Text("G  Google", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {},
                        modifier = androidx.compose.ui.Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, Color(0xFF3A3D28)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF0F0E8))
                    ) {
                        Text("  Apple", style = MaterialTheme.typography.labelMedium)
                    }
                }

                androidx.compose.foundation.layout.Spacer(
                    modifier = androidx.compose.ui.Modifier.height(24.dp)
                )

                // Footer
                Row {
                    Text(
                        "Don't have an account? ",
                        style = MaterialTheme.typography.bodySmall.copy(color = mutedText)
                    )
                    Text(
                        "Sign up",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF0F0E8)
                        )
                    )
                }
            }
        }
    }
}
