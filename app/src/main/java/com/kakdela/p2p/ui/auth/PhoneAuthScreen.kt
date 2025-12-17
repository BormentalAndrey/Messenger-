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
import com.kakdela.p2p.data.AuthManager // Наш новый менеджер
import kotlinx.coroutines.launch

@Composable
fun PhoneAuthScreen(
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { AuthManager() }

    var name by remember { mutableStateOf("") } // Добавили поле Имя
    var phone by remember { mutableStateOf("") }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var inputCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isRegistering by remember { mutableStateOf(false) } // Индикатор загрузки
    var permissionDenied by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (!granted) {
            error = "Без разрешения SMS регистрация невозможна"
        }
    }

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
        Text("Регистрация", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

        // Поле Имени (WhatsApp стиль)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Ваше имя") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Номер телефона (например 7999...)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        if (generatedCode == null) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    error = null
                    if (name.isBlank() || phone.isBlank()) {
                        error = "Заполните все поля"
                        return@Button
                    }

                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        permissionDenied = true
                        return@Button
                    }

                    val code = SmsCodeManager.generateCode()
                    generatedCode = code
                    SmsCodeManager.sendCode(context, phone, code)
                }
            ) {
                Text("Получить код")
            }
        }

        if (generatedCode != null) {
            OutlinedTextField(
                value = inputCode,
                onValueChange = { inputCode = it },
                label = { Text("Код подтверждения") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            if (isRegistering) {
                CircularProgressIndicator()
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (inputCode == generatedCode) {
                            isRegistering = true
                            scope.launch {
                                // 🚀 КРИТИЧЕСКИЙ МОМЕНТ: Сохраняем в Firestore
                                val success = authManager.completeSignIn(name, phone)
                                if (success) {
                                    onSuccess()
                                } else {
                                    error = "Ошибка при создании профиля"
                                    isRegistering = false
                                }
                            }
                        } else {
                            error = "Неверный код"
                        }
                    }
                ) {
                    Text("Подтвердить и войти")
                }
            }
        }

        if (permissionDenied) {
            Spacer(Modifier.height(16.dp))
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    context.startActivity(intent)
                }
            ) {
                Text("Разрешить отправку SMS в настройках")
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

