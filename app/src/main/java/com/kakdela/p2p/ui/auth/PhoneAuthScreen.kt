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

    // --- Автозаполнение кода из SMS ---
    LaunchedEffect(sentCode) {
        if (sentCode != null) {
            while (true) {
                val received = SmsCodeStore.lastReceivedCode
                if (received != null && received == sentCode) {
                    inputCode = received
                    SmsCodeStore.lastReceivedCode = null // Очищаем после использования
                    break 
                }
                delay(1000) // Проверка раз в секунду
            }
        }
    }

    // --- Проверка разрешений ---
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionDenied = !allGranted
        if (!allGranted) {
            error = "Необходимы разрешения на SMS для верификации"
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
        Text("Цифровая Личность", style = MaterialTheme.typography.headlineMedium)
        Text("P2P верификация через ваш номер", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Ваше имя") },
            modifier = Modifier.fillMaxWidth(),
            enabled = sentCode == null
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Номер телефона (+7...)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = sentCode == null
        )

        Spacer(Modifier.height(16.dp))

        if (sentCode == null) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    error = null
                    if (name.isBlank() || phone.isBlank()) {
                        error = "Заполните данные"
                        return@Button
                    }
                    
                    val code = SmsCodeManager.generateCode()
                    sentCode = code
                    SmsCodeManager.sendCode(context, phone, code)
                }
            ) {
                Text("Проверить номер")
            }
        } else {
            OutlinedTextField(
                value = inputCode,
                onValueChange = { inputCode = it },
                label = { Text("Код подтверждения") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Код будет перехвачен автоматически") }
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
                                val success = identityRepo.publishIdentity(phone, name)
                                if (success) {
                                    context.getSharedPreferences("app_prefs", 0)
                                        .edit().putString("my_phone", phone).apply()
                                    onSuccess()
                                } else {
                                    error = "Ошибка публикации в P2P сеть"
                                    isLoading = false
                                }
                            } catch (e: Exception) {
                                error = e.localizedMessage
                                isLoading = false
                            }
                        }
                    } else {
                        error = "Неверный код"
                    }
                }
            ) {
                if (isLoading) CircularProgressIndicator(size = 24.dp) 
                else Text("Подтвердить и войти")
            }
            
            TextButton(onClick = { sentCode = null; inputCode = "" }) {
                Text("Изменить номер")
            }
        }

        if (permissionDenied) {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Дать разрешения в настройках")
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

