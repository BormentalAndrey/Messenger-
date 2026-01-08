package com.kakdela.p2p.ui.auth

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kakdela.p2p.auth.SmsCodeManager
import com.kakdela.p2p.auth.SmsCodeStore
import com.kakdela.p2p.data.IdentityRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Экран регистрации через телефон с SMS
 * Создает уникальную P2P личность оффлайн.
 */
@Composable
fun PhoneAuthScreen(
    identityRepository: IdentityRepository,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var sentCode by remember { mutableStateOf<String?>(null) }
    var inputCode by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    // Перехват кода из SmsCodeStore
    LaunchedEffect(sentCode) {
        if (sentCode != null) {
            while (true) {
                val received = SmsCodeStore.lastReceivedCode
                if (received != null && received == sentCode) {
                    inputCode = received
                    SmsCodeStore.lastReceivedCode = null
                    break
                }
                delay(1000)
            }
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionDenied = !permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        val needed = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) smsPermissionLauncher.launch(needed.toTypedArray())
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Создание P2P Личности", style = MaterialTheme.typography.headlineMedium, color = Color.Cyan)
            Text("Номер телефона — ваш уникальный адрес", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Псевдоним") },
                modifier = Modifier.fillMaxWidth(),
                enabled = sentCode == null,
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Номер телефона (+...)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = sentCode == null,
                singleLine = true
            )

            Spacer(Modifier.height(24.dp))

            if (sentCode == null) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black),
                    onClick = {
                        if (name.length < 2 || phone.length < 10) {
                            error = "Проверьте имя и номер"
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
                OutlinedTextField(
                    value = inputCode,
                    onValueChange = { inputCode = it },
                    label = { Text("Код подтверждения") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Ожидаем SMS...") }
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    onClick = {
                        if (inputCode == sentCode) {
                            isLoading = true
                            scope.launch {
                                try {
                                    // Генерация локального P2P ID
                                    identityRepository.generateUserHash(phone, name, inputCode)
                                    onSuccess()
                                } catch (e: Exception) {
                                    error = "Сбой P2P: ${e.localizedMessage}"
                                    isLoading = false
                                }
                            }
                        } else {
                            error = "Неверный код"
                        }
                    }
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("Создать профиль")
                    }
                }

                TextButton(onClick = { sentCode = null; inputCode = "" }) {
                    Text("Назад", color = Color.Gray)
                }
            }

            if (permissionDenied) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Разрешить SMS в настройках")
                }
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
