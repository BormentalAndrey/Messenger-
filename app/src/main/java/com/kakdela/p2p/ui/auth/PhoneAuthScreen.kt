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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kakdela.p2p.auth.SmsCodeManager
import com.kakdela.p2p.auth.SmsCodeStore
import com.kakdela.p2p.data.AuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PhoneAuthScreen(onSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { AuthManager(context) }

    var phone by remember { mutableStateOf("") }
    var sentCode by remember { mutableStateOf<String?>(null) }
    var inputCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Внутренняя функция для отправки
    fun handleSendSms() {
        val code = SmsCodeManager.generateCode()
        val success = SmsCodeManager.sendCode(context, phone, code)
        if (success) {
            sentCode = code
            error = null
        } else {
            error = "Не удалось отправить SMS. Проверьте баланс или номер."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            handleSendSms()
        } else {
            error = "Без разрешений на SMS регистрация невозможна"
        }
    }

    // Автозаполнение
    LaunchedEffect(sentCode) {
        if (sentCode != null) {
            while (isActive) {
                SmsCodeStore.lastReceivedCode?.let { received ->
                    if (received == sentCode) {
                        inputCode = received
                        SmsCodeStore.clear()
                    }
                }
                delay(1000)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Вход по номеру", style = MaterialTheme.typography.headlineMedium, color = Color.Cyan)
            Spacer(Modifier.height(32.dp))

            if (sentCode == null) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Номер телефона (+7...)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (phone.length >= 10) {
                            val hasSend = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                            val hasReceive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                            
                            if (hasSend && hasReceive) {
                                handleSendSms()
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS))
                            }
                        } else {
                            error = "Введите корректный номер"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                ) {
                    Text("Получить код", color = Color.Black)
                }
            } else {
                OutlinedTextField(
                    value = inputCode,
                    onValueChange = { inputCode = it },
                    label = { Text("Код подтверждения") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (inputCode == sentCode) {
                            isLoading = true
                            scope.launch {
                                val success = authManager.registerOrLoginByPhone(phone, inputCode)
                                if (success) onSuccess() else error = "Ошибка авторизации"
                                isLoading = false
                            }
                        } else {
                            error = "Неверный код"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                    else Text("Войти", color = Color.Black)
                }
            }

            error?.let {
                Text(it, color = Color.Red, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
