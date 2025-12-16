package com.kakdela.p2p.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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

    // 🔐 Launcher для разрешения SMS
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            error = "Разрешите отправку SMS для подтверждения номера"
        }
    }

    // Проверка разрешения
    fun ensureSmsPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }

        return granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Номер телефона") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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

                if (!ensureSmsPermission()) {
                    return@Button
                }

                val code = SmsCodeManager.generateCode()
                generatedCode = code
                SmsCodeManager.sendCode(phone, code)
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
