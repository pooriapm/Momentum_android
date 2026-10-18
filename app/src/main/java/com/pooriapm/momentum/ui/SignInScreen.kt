package com.pooriapm.momentum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import com.pooriapm.momentum.ui.components.MomentumField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SignInScreen(state: AppState) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text("Momentum", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(
            if (create) "یک ماه غذا و تمرین، چیده‌شده برای خودت." else "به برنامه‌ات خوش برگشتی.",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            if (create) "حسابت را بساز؛ چند سؤال کوتاه بعدی، برنامه ۳۰ روزه‌ات را می‌سازد." else "با همان حساب سایت وارد شو.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MomentumField(
            value = email,
            onValueChange = { email = it },
            label = "ایمیل",
            placeholder = "name@example.com",
            ltr = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        var showPassword by rememberSaveable { mutableStateOf(false) }
        MomentumField(
            value = password,
            onValueChange = { password = it },
            label = "رمز عبور",
            placeholder = "دست‌کم ۸ نویسه",
            ltr = password.isNotEmpty(),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailing = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "پنهان کردن رمز" else "نمایش رمز",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        Button(
            onClick = { state.signIn(email, password, create) },
            enabled = !state.loading && email.contains('@') && password.length >= 8,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            if (state.loading) CircularProgressIndicator(strokeWidth = 2.dp) else Text(if (create) "ساخت حساب" else "ورود")
        }
        TextButton(onClick = { create = !create; state.error = null; state.notice = null }, modifier = Modifier.fillMaxWidth()) {
            Text(if (create) "حساب داری؟ وارد شو" else "حساب نداری؟ بساز")
        }
        val emailReady = email.contains('@') && !state.loading
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { state.sendAuthEmail(email, reset = true) }, enabled = emailReady) { Text("رمز را فراموش کردی؟") }
            TextButton(onClick = { state.sendAuthEmail(email, reset = false) }, enabled = emailReady) { Text("ایمیل تأیید نیامد؟") }
        }
        if (!email.contains('@')) {
            Text("برای این دو گزینه اول ایمیلت را بنویس.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        }
    }
}
