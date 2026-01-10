package com.kakdela.p2p.ui.auth

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kakdela.p2p.auth.SmsCodeManager
import com.kakdela.p2p.auth.SmsCodeStore
import com.kakdela.p2p.data.AuthManager
import com.kakdela.p2p.data.IdentityRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PhoneAuthScreen(
    identityRepository: IdentityRepository,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // FIX: Используем AuthManager
    val authManager = remember { AuthManager(context) }

    var phone by remember { mutableStateOf("") }
    var sentCode by remember { mutableStateOf<String?>(null) }
    var inputCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            val code = SmsCodeManager.generateCode()
            sentCode = code
            SmsCodeManager.sendCode(context, phone, code)
        } else {
            error = "Нужны разрешения на SMS"
        }
    }

    LaunchedEffect(sentCode) {
        if (sentCode != null) {
            while (isActive) {
                SmsCodeStore.lastReceivedCode?.let {
                    if (it == sentCode) {
                        inputCode = it
                        SmsCodeStore.lastReceivedCode = null
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
                    label = { Text("Телефон") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (phone.length >= 10) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                ) { Text("Получить код", color = Color.Black) }
            } else {
                OutlinedTextField(
                    value = inputCode,
                    onValueChange = { inputCode = it },
                    label = { Text("Код из SMS") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (inputCode == sentCode) {
                            isLoading = true
                            scope.launch {
                                // FIX: Вызываем AuthManager вместо ручной генерации хэшей
                                val success = authManager.registerOrLogin(
                                    email = "phone_user", // Заглушка для email при входе по телефону
                                    password = inputCode, // Используем код как временный пароль
                                    phone = phone
                                )
                                if (success) onSuccess() else error = "Ошибка сети или регистрации"
                                isLoading = false
                            }
                        } else error = "Неверный код"
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                    enabled = !isLoading
                ) { 
                     if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                     else Text("Войти", color = Color.Black) 
                }
            }
            error?.let { Text(it, color = Color.Red, modifier = Modifier.padding(top = 16.dp)) }
        }
    }
}
