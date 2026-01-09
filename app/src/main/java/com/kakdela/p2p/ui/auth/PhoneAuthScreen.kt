package com.kakdela.p2p.ui.auth

import android.Manifest
import android.content.Intent
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
import com.kakdela.p2p.auth.SmsCodeManager
import com.kakdela.p2p.auth.SmsCodeStore
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.UserRegistrationWrapper
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // Авто-подстановка кода из SMS
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

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("P2P Личность", style = MaterialTheme.typography.headlineMedium, color = Color.Cyan)
            
            Spacer(Modifier.height(32.dp))

            if (sentCode == null) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Номер телефона (+...)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                    onClick = {
                        if (phone.length > 10) {
                            val code = SmsCodeManager.generateCode()
                            sentCode = code
                            SmsCodeManager.sendCode(context, phone, code)
                        } else { error = "Неверный номер" }
                    }
                ) { Text("Получить SMS код", color = Color.Black) }
            } else {
                OutlinedTextField(
                    value = inputCode,
                    onValueChange = { inputCode = it },
                    label = { Text("Код из SMS") },
                    modifier = Modifier.fillMaxWidth()
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
                                    val securityHash = identityRepository.generateSecurityHash(phone, "no-email", inputCode)
                                    val discoveryHash = identityRepository.generatePhoneDiscoveryHash(phone)
                                    val pubKey = CryptoManager.getMyPublicKeyStr()

                                    // Анонс на сервер для Discovery (Борменталь оживает тут)
                                    val payload = UserPayload(
                                        hash = securityHash,
                                        phone_hash = discoveryHash,
                                        publicKey = pubKey,
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
                ) { Text("Подтвердить") }
            }

            error?.let { Text(it, color = Color.Red, modifier = Modifier.padding(top = 16.dp)) }
        }
    }
}
