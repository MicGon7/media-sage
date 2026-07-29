package com.mediasage.feature.login

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.mediasage.theme.CardBorder
import com.mediasage.theme.ComicBrown
import com.mediasage.theme.ComicTan
import com.mediasage.theme.DarkBackground
import com.mediasage.theme.Ink
import com.mediasage.theme.InkLight
import com.mediasage.theme.MediaSageTheme
import com.mediasage.theme.Navy
import com.mediasage.theme.NavyLight
import com.mediasage.theme.Slate
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.login_background_comic
import mediasage.composeapp.generated.resources.login_bypass
import mediasage.composeapp.generated.resources.login_email_label
import mediasage.composeapp.generated.resources.login_hide_password
import mediasage.composeapp.generated.resources.login_masthead_line1
import mediasage.composeapp.generated.resources.login_masthead_line2
import mediasage.composeapp.generated.resources.login_member_edition
import mediasage.composeapp.generated.resources.login_paper_white
import mediasage.composeapp.generated.resources.login_password_label
import mediasage.composeapp.generated.resources.login_remember_email
import mediasage.composeapp.generated.resources.login_show_password
import mediasage.composeapp.generated.resources.login_sign_in_button
import mediasage.composeapp.generated.resources.login_subtitle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
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
    backgroundColors: List<Color> = listOf(NavyLight, Navy),
    backgroundImage: DrawableResource? = Res.drawable.login_background_comic,
    formOnPaper: Boolean = true,
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

        // OnGradientMuted (a mid-tone tan-gray) has too little contrast against the photo
        // background, which is itself entirely warm sepia — a competing muted hue reads as
        // camouflaged rather than muted. Deriving muted text from OnGradient's own alpha keeps
        // it legible against every region of the image instead.
        val mutedTextColor = if (backgroundImage != null) OnGradient.copy(alpha = 0.72f) else OnGradientMuted

        // formOnPaper renders the sign-in form on a white "newspaper page" panel over the photo
        // background instead of light-on-dark text directly on the image — dark ink on white
        // needs its own color set, unrelated to the cream/muted-cream pairing used everywhere else.
        val formTextColor = if (formOnPaper) Ink else OnGradient
        val formMutedColor = if (formOnPaper) Slate else mutedTextColor
        val formAccentColor = if (formOnPaper) Navy else OnGradient
        val formBorderColor = if (formOnPaper) {
            CardBorder
        } else if (backgroundImage != null) {
            mutedTextColor
        } else {
            FieldBorder
        }
        val appVersion = LocalAppVersion.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (backgroundImage != null) {
                        Modifier
                    } else {
                        Modifier.background(Brush.verticalGradient(backgroundColors))
                    }
                )
        ) {
            if (backgroundImage != null) {
                Image(
                    painter = painterResource(backgroundImage),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (formOnPaper) 24.dp else 32.dp,
                        vertical = if (formOnPaper) 40.dp else 0.dp,
                    )
                    .imePadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // formOnPaper wraps the whole masthead-to-version block as one "newspaper page"
                // panel, rather than just the input fields — the masthead and version text need
                // the same ink-on-white treatment as the form once they're printed on the page.
                val paperModifier = if (formOnPaper) {
                    // The image's own alpha channel defines the deckled paper silhouette now,
                    // so no clip shape here — a rounded-rect clip would slice its natural
                    // irregular edges into a hard rounded corner.
                    Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 8.dp, shape = MaterialTheme.shapes.medium, clip = false)
                        .paint(
                            painter = painterResource(Res.drawable.login_paper_white),
                            contentScale = ContentScale.FillBounds,
                            alpha = 0.95f,
                        )
                        .padding(14.dp)
                } else {
                    Modifier.fillMaxWidth()
                }
                Column(modifier = paperModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                // Masthead
                HorizontalDivider(color = formMutedColor, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.login_member_edition),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                        letterSpacing = 3.sp,
                        color = formMutedColor
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = formAccentColor, thickness = 2.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(Res.string.login_masthead_line1),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 5.sp,
                        color = formTextColor
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(Res.string.login_masthead_line2),
                    style = MaterialTheme.typography.displayMedium.copy(
                        letterSpacing = 12.sp,
                        color = formTextColor
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = formAccentColor, thickness = 2.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.login_subtitle),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = formMutedColor
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = formMutedColor, thickness = 0.5.dp)

                Spacer(modifier = Modifier.height(36.dp))

                // Form
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = formTextColor,
                    unfocusedTextColor = formTextColor,
                    focusedBorderColor = formAccentColor,
                    unfocusedBorderColor = formBorderColor,
                    focusedLabelColor = formAccentColor,
                    unfocusedLabelColor = formMutedColor,
                    cursorColor = formAccentColor,
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
                                tint = formMutedColor
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
                        style = MaterialTheme.typography.bodySmall.copy(color = formMutedColor)
                    )
                    Switch(
                        checked = state.rememberEmail,
                        onCheckedChange = { onIntent(LoginContract.Intent.ToggleRememberEmail(it)) },
                        enabled = !isLoading,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ComicBrown,
                            checkedTrackColor = ComicTan,
                            checkedBorderColor = ComicBrown,
                            uncheckedThumbColor = formMutedColor,
                            uncheckedTrackColor = Color.Transparent,
                            uncheckedBorderColor = formBorderColor,
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
                        contentColor = formAccentColor,
                        disabledContentColor = formMutedColor
                    ),
                    border = BorderStroke(1.dp, if (isLoading) formBorderColor else formAccentColor)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = formMutedColor)
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
                            color = formMutedColor
                        )
                    )
                }
                // On paper, the version reads like a colophon line printed at the bottom of the
                // page rather than a separate overlay pinned to the screen edge.
                if (formOnPaper && appVersion.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = appVersion,
                        style = MaterialTheme.typography.labelSmall.copy(color = formMutedColor),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                }
            }
            if (!formOnPaper && appVersion.isNotEmpty()) {
                Text(
                    text = appVersion,
                    style = MaterialTheme.typography.labelSmall.copy(color = mutedTextColor),
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

@Preview(showBackground = true, name = "Theme E - Comic city photo")
@Composable
private fun LoginThemeEPreview() {
    LoginScreenContent(
        state = LoginContract.UiState(),
        onIntent = {},
        backgroundImage = Res.drawable.login_background_comic,
    )
}
