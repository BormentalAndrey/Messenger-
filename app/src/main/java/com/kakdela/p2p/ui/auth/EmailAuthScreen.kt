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

    val auth = Firebase.auth
    val db = Firebase.firestore

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = if (isLogin) "Вход по Email" else "Регистрация Email",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
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

                fun finishAuth() {
                    val uid = auth.currentUser?.uid
                    if (uid == null) {
                        loading = false
                        error = "Ошибка авторизации"
                        return
                    }

                    db.collection("users")
                        .document(uid)
                        .set(
                            mapOf("hasEmailAuth" to true),
                            SetOptions.merge()
                        )
                        .addOnSuccessListener {
                            onAuthSuccess()
                        }
                        .addOnFailureListener {
                            loading = false
                            error = "Ошибка сохранения профиля"
                        }
                }

                if (isLogin) {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { finishAuth() }
                        .addOnFailureListener {
                            loading = false
                            error = it.message
                        }
                } else {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { finishAuth() }
                        .addOnFailureListener {
                            loading = false
                            error = it.message
                        }
                }
            }
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            } else {
                Text(if (isLogin) "Войти" else "Зарегистрироваться")
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { isLogin = !isLogin }) {
            Text(
                if (isLogin)
                    "Нет аккаунта? Регистрация"
                else
                    "Уже есть аккаунт? Войти"
            )
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}
