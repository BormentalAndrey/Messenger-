package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    
    // Состояние скролла для небольших экранов
    val scrollState = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() // Отступы от статус-бара
                .imePadding() // Отступ от клавиатуры
                .verticalScroll(scrollState) // Скролл, если экран маленький
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Вход Email/Pass", style = MaterialTheme.typography.headlineMedium, color = Color.Cyan)
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Телефон (для поиска)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        val success = authManager.registerOrLogin(email, password, phone)
                        if (success) onAuthSuccess() else error = "Ошибка входа"
                        isLoading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp), // Гибкая высота
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                else Text("Войти", color = Color.Black)
            }
            error?.let { Text(it, color = Color.Red, modifier = Modifier.padding(top = 16.dp)) }
        }
    }
}
