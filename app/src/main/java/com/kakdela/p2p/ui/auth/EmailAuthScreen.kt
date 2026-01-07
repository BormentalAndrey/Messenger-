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
import kotlinx.coroutines.launch

/**
 * Экран входа/восстановления через Email.
 * В P2P это работает как загрузка зашифрованного ключа из сети.
 */
@Composable
fun EmailAuthScreen(
    identityRepository: IdentityRepository,
    onAuthSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
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
                value = email,
                onValueChange = { 
                    email = it.trim().lowercase() 
                    error = null 
                },
                label = { Text("Email (логин бэкапа)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { 
                    password = it
                    error = null 
                },
                label = { Text("Пароль для расшифровки") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black),
                enabled = !isLoading && email.isNotEmpty() && password.length >= 6,
                onClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        // 1. Ищем в сети бэкап по хешу email
                        // 2. Если найден, пытаемся расшифровать его паролем
                        val success = identityRepository.updateEmailBackup(email, password)
                        
                        if (success) {
                            onAuthSuccess()
                        } else {
                            error = "Профиль не найден в сети или пароль неверен"
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

