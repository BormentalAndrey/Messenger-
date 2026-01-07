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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kakdela.p2p.auth.SmsCodeManager
import com.kakdela.p2p.auth.SmsCodeStore
import com.kakdela.p2p.data.IdentityRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhoneAuthScreen(
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Используем remember для сохранения репозитория при перерисовках
    val identityRepo = remember { IdentityRepository(context) }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var sentCode by remember { mutableStateOf<String?>(null) }
    var inputCode by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    // Автоматический перехват кода из SMS
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

    // Запрос разрешений на SMS
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionDenied = !allGranted
        if (!allGranted) {
            error = "Разрешите чтение SMS для автоматического входа"
        }
    }

    LaunchedEffect(Unit) {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            smsPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Создание P2P Личности", 
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Cyan
        )
        Text(
            text = "Ваш номер телефона станет вашим адресом в сети", 
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Ваше имя (псевдоним)") },
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
                onClick = {
                    error = null
                    if (name.isBlank() || phone.length < 10) {
                        error = "Введите корректное имя и номер"
                        return@Button
                    }
                    
                    val code = SmsCodeManager.generateCode()
                    sentCode = code
                    SmsCodeManager.sendCode(context, phone, code)
                }
            ) {
                Text("Получить код подтверждения")
            }
        } else {
            OutlinedTextField(
                value = inputCode,
                onValueChange = { inputCode = it },
                label = { Text("Код из SMS") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Ожидаем автоматического перехвата...") }
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
                                // Публикуем личность в локальный DHT и создаем ключи
                                val success = identityRepo.publishIdentity(phone, name)
                                if (success) {
                                    onSuccess()
                                } else {
                                    error = "Не удалось инициализировать ключи"
                                    isLoading = false
                                }
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp), // ИСПРАВЛЕНО
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
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
                Text("Открыть настройки разрешений")
            }
        }

        error?.let {
            Text(
                text = it, 
                color = MaterialTheme.colorScheme.error, 
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

