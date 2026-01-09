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
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Экран входа/регистрации через Email.
 * Реализует логику восстановления личности или создания нового P2P узла.
 */
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "P2P Личность",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Cyan
            )
            Text(
                text = "Вход восстановит ваши ключи шифрования",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.trim() },
                label = { Text("Номер телефона") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim().lowercase() },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Cyan,
                    contentColor = Color.Black
                ),
                enabled = email.isNotEmpty() && password.isNotEmpty() && phone.isNotEmpty() && !isLoading,
                onClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        try {
                            // 1. Пытаемся войти (проверка существования аккаунта)
                            val isLoginSuccessful = authManager.login(email, password)
                            
                            if (isLoginSuccessful) {
                                onAuthSuccess()
                            } else {
                                // 2. Если вход не удался, пробуем зарегистрировать новый P2P узел
                                val myPubKey = CryptoManager.getMyPublicKeyStr()
                                val myHash = generateUserHash(phone, email, password)
                                
                                val registrationSuccess = authManager.register(
                                    email = email,
                                    password = password,
                                    phone = phone
                                )

                                if (registrationSuccess) {
                                    // ИСПРАВЛЕНО: передаем пустые строки вместо null, чтобы удовлетворить String-типу
                                    identityRepository.savePeerPublicKey(myHash, myPubKey)
                                    onAuthSuccess()
                                } else {
                                    error = "Не удалось создать или найти профиль"
                                }
                            }
                        } catch (e: Exception) {
                            error = "Ошибка сети: ${e.localizedMessage}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                } else {
                    Text("Войти / Создать")
                }
            }

            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Генерация уникального идентификатора узла.
 * Используется SHA-256 для детерминированного хеширования учетных данных.
 */
private fun generateUserHash(phone: String, email: String, pass: String): String {
    val input = "$phone|$email|$pass"
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
