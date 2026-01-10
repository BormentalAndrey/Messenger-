package com.kakdela.p2p.ui.auth

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import com.kakdela.p2p.auth.SmsCodeManager
import com.kakdela.p2p.auth.SmsCodeStore
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.UserRegistrationWrapper
import com.kakdela.p2p.security.CryptoManager
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

    var phone by remember { mutableStateOf("") }
    var sentCode by remember { mutableStateOf<String?>(null) }
    var inputCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Лаунчер для запроса разрешений на SMS
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.SEND_SMS] == true &&
                        permissions[Manifest.permission.RECEIVE_SMS] == true
        if (isGranted) {
            // Если разрешили, генерируем и отправляем код
            val code = SmsCodeManager.generateCode()
            sentCode = code
            SmsCodeManager.sendCode(context, phone, code)
        } else {
            error = "Необходимо разрешение на SMS для регистрации"
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color.Cyan,
        unfocusedBorderColor = Color.DarkGray,
        focusedLabelColor = Color.Cyan,
        unfocusedLabelColor = Color.Gray
    )

    // Авто-подстановка кода (слушаем SmsCodeStore)
    LaunchedEffect(sentCode) {
        if (sentCode != null) {
            while (isActive) {
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

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("P2P Личность", style = MaterialTheme.typography.headlineMedium, color = Color.Cyan)
            Text("Подтвердите номер для генерации ключей", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            Spacer(Modifier.height(32.dp))

            if (sentCode == null) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Номер телефона (+...)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors,
                    singleLine = true
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black),
                    onClick = {
                        if (phone.length >= 10) {
                            // ПРОВЕРКА РАЗРЕШЕНИЙ ПЕРЕД ОТПРАВКОЙ
                            val hasSendPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                            val hasReceivePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

                            if (hasSendPermission && hasReceivePermission) {
                                val code = SmsCodeManager.generateCode()
                                sentCode = code
                                SmsCodeManager.sendCode(context, phone, code)
                            } else {
                                // Запрашиваем разрешения, если их нет
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
                                )
                            }
                        } else { 
                            error = "Некорректный номер телефона" 
                        }
                    }
                ) { 
                    Text("Получить SMS код", fontWeight = FontWeight.Bold) 
                }
            } else {
                // ... (оставшаяся часть экрана с вводом кода подтверждения без изменений)
                OutlinedTextField(
                    value = inputCode,
                    onValueChange = { inputCode = it },
                    label = { Text("Код подтверждения") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors,
                    singleLine = true
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black),
                    onClick = {
                        if (inputCode == sentCode) {
                            isLoading = true
                            scope.launch {
                                try {
                                    val securityHash = identityRepository.generateSecurityHash(phone, "p2p_direct", inputCode)
                                    val discoveryHash = identityRepository.generatePhoneDiscoveryHash(phone)
                                    val payload = UserPayload(
                                        hash = securityHash,
                                        phone_hash = discoveryHash,
                                        publicKey = CryptoManager.getMyPublicKeyStr(),
                                        phone = phone
                                    )
                                    identityRepository.announceMyself(UserRegistrationWrapper(securityHash, payload))
                                    onSuccess()
                                } catch (e: Exception) {
                                    error = e.localizedMessage
                                    isLoading = false
                                }
                            }
                        } else { error = "Неверный код" }
                    }
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                    } else {
                        Text("Подтвердить и войти", fontWeight = FontWeight.Bold)
                    }
                }
            }

            error?.let { 
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) 
            }
        }
    }
}
