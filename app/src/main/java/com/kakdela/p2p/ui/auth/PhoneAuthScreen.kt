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
import com.kakdela.p2p.data.IdentityRepository
import kotlinx.coroutines.launch

@Composable
fun PhoneAuthScreen(
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val identityRepo = remember { IdentityRepository(context) }

    // Поля ввода
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var sentCode by remember { mutableStateOf<String?>(null) }
    var inputCode by remember { mutableStateOf("") }

    // Состояния UI
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    // --- Проверка разрешения на SMS ---
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
        if (!granted) smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Регистрация", style = MaterialTheme.typography.headlineMedium)
        Text("Ваш номер будет проверен через SMS", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(16.dp))

        // Ввод имени и телефона
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Имя (Display Name)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Номер телефона") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        if (sentCode == null) {
            // --- Шаг 1: Получить код ---
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    error = null
                    if (name.isBlank() || phone.isBlank()) {
                        error = "Заполните все поля"
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
                    sentCode = code
                    SmsCodeManager.sendCode(context, phone, code)
                }
            ) {
                Text("Получить код")
            }
        } else {
            // --- Шаг 2: Ввести код ---
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
                    if (inputCode == sentCode) {
                        isLoading = true
                        scope.launch {
                            try {
                                val success = identityRepo.publishIdentity(phone, name)
                                if (success) {
                                    context.getSharedPreferences("app_prefs", 0)
                                        .edit().putString("my_phone", phone).apply()
                                    onSuccess()
                                } else {
                                    error = "Ошибка при создании цифровой личности"
                                    isLoading = false
                                }
                            } catch (e: Exception) {
                                error = "Ошибка регистрации: ${e.message}"
                                isLoading = false
                                e.printStackTrace()
                            }
                        }
                    } else {
                        error = "Неверный код"
                    }
                }
            ) {
                if (isLoading) CircularProgressIndicator() else Text("Создать цифровую личность")
            }
        }

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
                Text("Разрешить отправку SMS в настройках")
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
