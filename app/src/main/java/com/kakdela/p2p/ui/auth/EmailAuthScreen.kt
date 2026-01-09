package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kakdela.p2p.data.AuthManager
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.UserRegistrationWrapper
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.launch

@Composable
fun EmailAuthScreen(
    identityRepository: IdentityRepository,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { AuthManager(context) }

    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("P2P Личность", style = MaterialTheme.typography.headlineMedium, color = Color.Cyan)
            Text("Вход восстановит ваши ключи шифрования", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { char -> char.isDigit() || char == '+' } },
                label = { Text("Номер телефона") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim().lowercase() },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )

            Spacer(Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black),
                enabled = email.isNotEmpty() && password.isNotEmpty() && phone.isNotEmpty() && !isLoading,
                onClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        try {
                            // 1. Генерация хэшей согласно ТЗ (п. 4)
                            val securityHash = identityRepository.generateSecurityHash(phone, email, password)
                            val phoneDiscoveryHash = identityRepository.generatePhoneDiscoveryHash(phone)
                            val myPubKey = CryptoManager.getMyPublicKeyStr()

                            // 2. Регистрация/Вход через AuthManager
                            val success = authManager.registerOrLogin(email, password, phone)
                            
                            if (success) {
                                // 3. Анонс себя на сервер API.php для Discovery
                                val payload = UserPayload(
                                    hash = securityHash,
                                    phone_hash = phoneDiscoveryHash,
                                    publicKey = myPubKey,
                                    phone = phone,
                                    email = email
                                )
                                identityRepository.announceMyself(UserRegistrationWrapper(securityHash, payload))
                                
                                onAuthSuccess()
                            } else {
                                error = "Ошибка авторизации"
                            }
                        } catch (e: Exception) {
                            error = "Ошибка: ${e.localizedMessage}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Text("Войти / Создать")
                }
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
