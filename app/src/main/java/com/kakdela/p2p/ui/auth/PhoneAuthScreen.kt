package com.kakdela.p2p.ui.auth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kakdela.p2p.auth.SmsCodeManager

@Composable
fun PhoneAuthScreen(
    onSuccess: () -> Unit
) {
    val context = LocalContext.current

    var phone by remember { mutableStateOf("") }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var inputCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    // 🔐 Лаунчер разрешения SEND_SMS
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (!granted) {
            error = "Без разрешения SMS регистрация невозможна"
        }
    }

    // 🔁 Запрашиваем разрешение СРАЗУ при входе на экран
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text("Подтверждение номера", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Номер телефона") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                error = null

                if (phone.isBlank()) {
                    error = "Введите номер телефона"
                    return@Button
                }

                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED

                if (!granted) {
                    permissionDenied = true
                    return@Button
                }

                val code = SmsCodeManager.generateCode()
                generatedCode = code

                SmsCodeManager.sendCode(
                    context = context,
                    phone = phone,
                    code = code
                )
            }
        ) {
            Text("Отправить код")
        }

        if (generatedCode != null) {

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = inputCode,
                onValueChange = { inputCode = it },
                label = { Text("Код из SMS") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (inputCode == generatedCode) {
                        onSuccess()
                    } else {
                        error = "Неверный код"
                    }
                }
            ) {
                Text("Подтвердить")
            }
        }

        // ❗ Если пользователь запретил SMS — кнопка перехода в настройки
        if (permissionDenied) {
            Spacer(Modifier.height(16.dp))

            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                }
            ) {
                Text("Открыть настройки и разрешить SMS")
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
