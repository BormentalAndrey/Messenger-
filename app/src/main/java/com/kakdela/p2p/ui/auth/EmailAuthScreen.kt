package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@Composable
fun EmailAuthScreen(
    navController: NavHostController,
    onAuthSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }  // true = вход, false = регистрация
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val auth = Firebase.auth

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isLogin) "Вход в аккаунт" else "Регистрация",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    error = "Заполните все поля"
                    return@Button
                }
                loading = true
                error = null

                if (isLogin) {
                    // Вход
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { onAuthSuccess() }
                        .addOnFailureListener { e ->
                            loading = false
                            error = e.message ?: "Ошибка входа"
                        }
                } else {
                    // Регистрация
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { onAuthSuccess() }
                        .addOnFailureListener { e ->
                            loading = false
                            error = e.message ?: "Ошибка регистрации"
                        }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(if (isLogin) "Войти" else "Зарегистрироваться")
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { isLogin = !isLogin }) {
            Text(if (isLogin) "Нет аккаунта? Зарегистрироваться" else "Уже есть аккаунт? Войти")
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
