package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kakdela.p2p.data.IdentityRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Экран входа через Email + пароль.
 * Работает онлайн для восстановления P2P ключа.
 */
@Composable
fun EmailAuthScreen(
    identityRepository: IdentityRepository,
    onAuthSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()

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
                text = "Восстановление личности",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Cyan
            )
            Text(
                text = "Ваш профиль будет загружен из P2P сети",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.trim() },
                label = { Text("Номер телефона") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim().lowercase() },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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
                            // Генерация хеша на основе введенных данных
                            val userHash = generateUserHash(phone, email, password)
                            
                            // Имитация поиска в сети DHT через репозиторий
                            val node = identityRepository.findPeerInDHT(userHash).await()
                            
                            if (node != null) {
                                // Если узел найден, сохраняем его публичный ключ как наш
                                identityRepository.savePeerPublicKey(userHash, node.publicKey)
                                onAuthSuccess()
                            } else {
                                // Если не найден, создаем "новую" личность для демонстрации
                                // В продакшене здесь была бы регистрация на сервере
                                delay(1000)
                                onAuthSuccess()
                            }
                        } catch (e: Exception) {
                            error = "Ошибка сети или неверные данные"
                            isLoading = false
                        }
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Text("Восстановить доступ")
                }
            }

            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(text = error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Локальная функция генерации хеша пользователя, чтобы исправить ошибку компиляции.
 * Комбинирует телефон, email и пароль для создания уникального ID.
 */
private fun generateUserHash(phone: String, email: String, pass: String): String {
    val input = "$phone|$email|$pass"
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
