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
    val auth = remember { Firebase.auth }
    val db = remember { Firebase.firestore }
    val storage = remember { Firebase.storage }
    val user = auth.currentUser

    val hasPhone = user?.phoneNumber != null
    val hasEmail = user?.email != null

    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            db.collection("users").document(uid).get()
                .addOnSuccessListener { avatarUrl = it.getString("avatarUrl") }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && user != null) {
            isUploading = true
            val ref = storage.reference.child("avatars/${user.uid}.jpg")
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { download ->
                    avatarUrl = download.toString()
                    db.collection("users").document(user.uid)
                        .set(mapOf("avatarUrl" to avatarUrl), com.google.firebase.firestore.SetOptions.merge())
                    isUploading = false
                }
            }.addOnFailureListener { isUploading = false }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl != null) {
                AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text("Нажмите,\nчтобы выбрать\nаватар", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
            }
            if (isUploading) CircularProgressIndicator(color = Color.White)
        }

        Spacer(Modifier.height(32.dp))
        InfoRow("📱 Телефон", hasPhone, "Добавить номер") { navController.navigate("auth_phone") }
        Spacer(Modifier.height(16.dp))
        InfoRow("✉️ Email", hasEmail, "Добавить Email") { navController.navigate("auth_email") }

        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = {
                auth.signOut()
                navController.navigate("choice") { popUpTo(0) { inclusive = true } }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Text("Выйти из аккаунта") }
    }
}

@Composable
fun InfoRow(label: String, isConfirmed: Boolean, actionText: String, onAction: () -> Unit) {
    if (isConfirmed) {
        Text("$label подтверждён", color = Color.Green)
    } else {
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionText) }
    }
}

