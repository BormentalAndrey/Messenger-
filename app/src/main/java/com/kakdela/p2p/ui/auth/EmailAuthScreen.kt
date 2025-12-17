package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@Composable
fun EmailAuthScreen(
    navController: NavHostController,
    onAuthSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) } // Флаг успешного входа

    val auth = Firebase.auth
    val db = Firebase.firestore

    // Если авторизация прошла успешно, вызываем callback перехода
    LaunchedEffect(success) {
        if (success) {
            onAuthSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = if (isLogin) "Вход по Email" else "Регистрация Email",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            singleLine = true
        )

        Spacer(Modifier.height(24.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    error = "Заполните все поля"
                    return@Button
                }

                if (password.length < 6) {
                    error = "Пароль минимум 6 символов"
                    return@Button
                }

                loading = true
                error = null

                fun saveUserToFirestore() {
                    val user = auth.currentUser
                    if (user == null) {
                        loading = false
                        error = "Пользователь не найден"
                        return
                    }

                    val userData = mapOf(
                        "uid" to user.uid,
                        "email" to user.email,
                        "hasEmailAuth" to true
                    )

                    db.collection("users")
                        .document(user.uid)
                        .set(userData, SetOptions.merge())
                        .addOnSuccessListener {
                            loading = false
                            success = true // Триггер для LaunchedEffect
                        }
                        .addOnFailureListener {
                            loading = false
                            error = "Ошибка профиля: ${it.localizedMessage}"
                        }
                }

                if (isLogin) {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { saveUserToFirestore() }
                        .addOnFailureListener {
                            loading = false
                            error = "Ошибка входа: ${it.localizedMessage}"
                        }
                } else {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { saveUserToFirestore() }
                        .addOnFailureListener {
                            loading = false
                            error = "Ошибка регистрации: ${it.localizedMessage}"
                        }
                }
            }
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(if (isLogin) "Войти" else "Зарегистрироваться")
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(
            onClick = { isLogin = !isLogin },
            enabled = !loading
        ) {
            Text(
                if (isLogin) "Нет аккаунта? Регистрация" else "Уже есть аккаунт? Войти",
                color = MaterialTheme.colorScheme.primary
            )
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

