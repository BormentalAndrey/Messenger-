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

@Composable
fun EmailAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val identityRepo = remember { IdentityRepository(LocalContext.current) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Вход в P2P сеть", style = MaterialTheme.typography.headlineMedium, color = Color.Cyan)
        
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim().lowercase() },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            onClick = {
                isLoading = true
                scope.launch {
                    // Логика:
                    // 1. Найти в DHT зашифрованный профиль по хешу(email)
                    // 2. Попробовать расшифровать его введенным паролем
                    val success = identityRepo.signInWithEmailP2P(email, password)
                    if (success) {
                        onAuthSuccess()
                    } else {
                        error = "Профиль не найден или пароль неверен"
                        isLoading = false
                    }
                }
            }
        ) {
            if (isLoading) CircularProgressIndicator(size = 20.dp) else Text("Войти")
        }
        
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

