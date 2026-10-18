package com.pooriapm.momentum.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.ui.components.MomentumField

private val EmailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

/**
 * Email sign-in and sign-up. Email and password are typed left to right (they are always Latin);
 * validation explains itself instead of silently disabling the button, and the keyboard's next/done
 * keys move through the form.
 */
@Composable
fun SignInScreen(state: AppState) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    // Errors appear once the user has tried, not while they are still typing.
    var tried by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }

    val cleanEmail = email.trim()
    val emailError = when {
        cleanEmail.isEmpty() -> "ایمیلت را بنویس."
        !EmailPattern.matches(cleanEmail) -> "ایمیل درست نیست؛ مثل name@example.com"
        else -> null
    }
    val passwordError = when {
        password.isEmpty() -> "رمز عبور را بنویس."
        password.length < 8 -> "رمز حداقل ۸ کاراکتر است (${password.length.fa()} از ۸)."
        else -> null
    }
    fun submit() {
        tried = true
        if (emailError == null && passwordError == null && !state.loading) {
            focus.clearFocus()
            state.signIn(cleanEmail, password, create)
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        LiveLogo(Modifier.size(104.dp).align(Alignment.CenterHorizontally))
        Text(
            "Momentum",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Momentum: small steps that keep the movement going.
            Text(
                "قدم‌های کوچک، هر روز؛ حرکت ادامه دارد.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (create) {
                Text(
                    "حسابت را بساز؛ چند سؤال کوتاه بعدی، برنامه ۳۰ روزه‌ات را می‌سازد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        MomentumField(
            value = email,
            onValueChange = { email = it.replace(" ", "") },
            label = "ایمیل",
            placeholder = "name@example.com",
            ltr = true,
            error = if (tried) emailError else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
        MomentumField(
            value = password,
            onValueChange = { password = it },
            label = "رمز عبور",
            placeholder = "••••••••",
            ltr = true,
            error = if (tried) passwordError else null,
            supporting = if (create && !tried) "حداقل ۸ کاراکتر، ترکیبی از حرف و عدد" else null,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailing = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "پنهان کردن رمز" else "نمایش رمز",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
        )
        AnimatedVisibility(state.error != null || state.notice != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                state.notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        // Always tappable: a tap with missing input shows what is wrong instead of doing nothing.
        Button(
            onClick = { submit() },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            if (state.loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else Text(if (create) "ساخت حساب" else "ورود")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(if (create) "حساب داری؟" else "حساب نداری؟", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                create = !create
                tried = false
                state.error = null
                state.notice = null
            }) { Text(if (create) "وارد شو" else "حساب بساز") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Account help: needs a valid email, and says so when it is missing.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = {
                tried = true
                if (emailError == null) state.sendAuthEmail(cleanEmail, reset = true)
            }, enabled = !state.loading) { Text("فراموشی رمز", style = MaterialTheme.typography.bodyMedium) }
            TextButton(onClick = {
                tried = true
                if (emailError == null) state.sendAuthEmail(cleanEmail, reset = false)
            }, enabled = !state.loading) { Text("ارسال دوباره ایمیل تأیید", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
