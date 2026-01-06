package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kakdela.p2p.data.IdentityRepository
import kotlinx.coroutines.launch

@Composable
fun EmailAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val identityRepo = remember { IdentityRepository(context) }

    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Проверяем, есть ли уже привязанный номер телефона
    val myPhone = remember { 
        context.getSharedPreferences("identity_prefs", 0).getString("my_phone", null) 
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Привязка Email",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Cyan // Неоновый стиль вашего приложения
        )

        Text(
            text = "Ваш Email будет использоваться как вторичный ID в P2P сети",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(32.dp))

        if (myPhone == null) {
            // Предупреждение, если номер еще не подтвержден
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    "Внимание: Сначала подтвердите номер телефона для создания ключей безопасности.",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim().lowercase() },
            label = { Text("Введите Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && myPhone != null,
            singleLine = true
        )

        Spacer(Modifier.height(24.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && myPhone != null && email.contains("@"),
            onClick = {
                isLoading = true
                error = null

                scope.launch {
                    try {
                        // 1. Привязываем email к существующей личности локально
                        // 2. Генерируем анонс для DHT (Hash(Email) -> MyPublicKey)
                        val success = identityRepo.updateEmail(email)
                        
                        if (success) {
                            isLoading = false
                            onAuthSuccess()
                        } else {
                            error = "Не удалось обновить локальный профиль"
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        error = "Ошибка: ${e.localizedMessage}"
                        isLoading = false
                    }
                }
            }
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Привязать к личности")
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        
        Spacer(Modifier.height(16.dp))
        
        TextButton(onClick = { onAuthSuccess() }) {
            Text("Пропустить", color = Color.Gray)
        }
    }
}

