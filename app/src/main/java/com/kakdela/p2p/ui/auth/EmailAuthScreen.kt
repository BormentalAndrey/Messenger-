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
            text = if (isLogin) "Вход в аккаунт" else "Регистрация",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
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

        if (isLogin) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    if (email.isBlank()) {
                        error = "Введите email для восстановления"
                        return@TextButton
                    }
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            error = "Ссылка для восстановления отправлена на $email"
                        }
                        .addOnFailureListener { e ->
                            error = "Ошибка: ${e.message}"
                        }
                }
            ) {
                Text("Забыли пароль?")
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    error = "Заполните все поля"
                    return@Button
                }
                if (password.length < 6) {
                    error = "Пароль должен быть не менее 6 символов"
                    return@Button
                }
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    error = "Неверный формат email"
                    return@Button
                }

                loading = true
                error = null

                val saveEmailAuthAndFinish = {
                    val uid = auth.currentUser?.uid
                    if (uid == null) {
                        loading = false
                        error = "Ошибка авторизации"
                        return@Button
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
                }

                if (isLogin) {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { saveEmailAuthAndFinish() }
                        .addOnFailureListener { e ->
                            loading = false
                            error = e.message ?: "Ошибка входа"
                        }
                } else {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { saveEmailAuthAndFinish() }
                        .addOnFailureListener { e ->
                            loading = false
                            error = e.message ?: "Ошибка регистрации"
                        }
                }
            }
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.Black
                )
            } else {
                Text(if (isLogin) "Войти" else "Зарегистрироваться")
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { isLogin = !isLogin }) {
            Text(
                if (isLogin)
                    "Нет аккаунта? Зарегистрироваться"
                else
                    "Уже есть аккаунт? Войти"
            )
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                color = if (it.contains("отправлена"))
                    Color.Green
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}
