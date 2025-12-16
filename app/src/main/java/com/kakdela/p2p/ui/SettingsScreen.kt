package com.kakdela.p2p.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

@Composable
fun SettingsScreen(navController: NavHostController) {

    val context = LocalContext.current
    val auth = Firebase.auth
    val user = auth.currentUser

    val hasPhone = user?.phoneNumber != null
    val hasEmail = user?.email != null

    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val db = Firebase.firestore
    val storage = Firebase.storage

    // Загружаем аватар
    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener {
                    avatarUrl = it.getString("avatarUrl")
                }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && user != null) {
            isUploading = true
            val ref = storage.reference.child("avatars/${user.uid}.jpg")

            ref.putFile(uri)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { download ->
                        avatarUrl = download.toString()
                        db.collection("users")
                            .document(user.uid)
                            .set(mapOf("avatarUrl" to avatarUrl), com.google.firebase.firestore.SetOptions.merge())
                        isUploading = false
                    }
                }
                .addOnFailureListener {
                    isUploading = false
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(32.dp))

        // Аватар
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                .clickable {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Аватар",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    "Нажмите,\nчтобы выбрать\nаватар",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isUploading) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        Spacer(Modifier.height(32.dp))

        // Привязка телефона
        if (!hasPhone) {
            Button(
                onClick = { navController.navigate("auth_phone") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Добавить номер телефона")
            }
        } else {
            Text("📱 Телефон подтверждён", color = Color.Green)
        }

        Spacer(Modifier.height(16.dp))

        // Привязка email
        if (!hasEmail) {
            Button(
                onClick = { navController.navigate("auth_email") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Добавить Email")
            }
        } else {
            Text("✉️ Email подтверждён", color = Color.Green)
        }

        Spacer(Modifier.height(24.dp))

        // Итоговый доступ
        if (hasPhone && hasEmail) {
            Text(
                "✅ Доступны ВСЕ функции приложения",
                color = Color.Green
            )
        } else {
            Text(
                "⚠ Некоторые функции ограничены",
                color = Color.Yellow
            )
        }

        Spacer(Modifier.height(32.dp))

        // Выход
        OutlinedButton(
            onClick = {
                auth.signOut()
                navController.navigate("choice") {
                    popUpTo(0) { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Выйти из аккаунта")
        }
    }
}
