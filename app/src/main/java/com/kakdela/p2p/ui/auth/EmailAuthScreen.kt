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

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color.Cyan,
        unfocusedBorderColor = Color.DarkGray,
        focusedLabelColor = Color.Cyan,
        unfocusedLabelColor = Color.Gray,
        cursorColor = Color.Cyan
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("P2P Личность", style = MaterialTheme.typography.headlineMedium, color = Color.Cyan)
            Text("Вход восстановит доступ к сети", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' } },
                label = { Text("Номер телефона") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim().lowercase() },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors
            )

            Spacer(Modifier.height(32.dp))

            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black),
                enabled = email.isNotBlank() && password.isNotBlank() && phone.isNotBlank() && !isLoading,
                onClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        try {
                            val securityHash = identityRepository.generateSecurityHash(phone, email, password)
                            val phoneDiscoveryHash = identityRepository.generatePhoneDiscoveryHash(phone)
                            
                            val success = authManager.registerOrLogin(email, password, phone)
                            
                            if (success) {
                                val payload = UserPayload(
                                    hash = securityHash,
                                    phone_hash = phoneDiscoveryHash,
                                    publicKey = CryptoManager.getMyPublicKeyStr(),
                                    phone = phone,
                                    email = email
                                )
                                identityRepository.announceMyself(UserRegistrationWrapper(securityHash, payload))
                                onAuthSuccess()
                            } else {
                                error = "Ошибка авторизации: проверьте данные"
                            }
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "Неизвестная ошибка"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(size = 24.dp, color = Color.Black, strokeWidth = 3.dp)
                } else {
                    Text("Войти / Создать", fontWeight = FontWeight.Bold)
                }
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(text = it, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
