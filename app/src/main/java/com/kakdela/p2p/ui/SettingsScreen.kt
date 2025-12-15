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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var status by remember { mutableStateOf("") }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val auth = Firebase.auth
    val currentUser = auth.currentUser
    val db = Firebase.firestore
    val storage = Firebase.storage

    // Загружаем текущий avatarUrl из Firestore при открытии экрана
    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    avatarUrl = document.getString("avatarUrl")
                }
        }
    }

    // Лаунчер для выбора фото из галереи
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && currentUser != null) {
            isUploading = true
            scope.launch {
                try {
                    val storageRef = storage.reference.child("avatars/${currentUser.uid}.jpg")
                    storageRef.putFile(uri)
                        .addOnSuccessListener {
                            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                val newUrl = downloadUri.toString()
                                avatarUrl = newUrl

                                // Сохраняем URL в Firestore
                                db.collection("users").document(currentUser.uid)
                                    .set(mapOf("avatarUrl" to newUrl), com.google.firebase.firestore.SetOptions.merge())
                            }
                        }
                } finally {
                    isUploading = false
                }
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
            text = "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(32.dp))

        // === АВАТАРКА ===
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
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = "Нажмите,\nчтобы выбрать\nаватар",
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            if (isUploading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Изменить аватар", color = Color.Gray)

        Spacer(Modifier.height(32.dp))

        // Кнопки дополнительных способов авторизации
        Button(
            onClick = { navController.navigate("auth_phone") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Добавить/сменить вход по номеру телефона")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { navController.navigate("auth_email") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Добавить/сменить вход по email")
        }

        Spacer(Modifier.height(32.dp))

        // Поле статуса
        OutlinedTextField(
            value = status,
            onValueChange = { status = it },
            label = { Text("Статус") },
            placeholder = { Text("В сети") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        // Переключатели уведомлений
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Оповещения о сообщениях", color = Color.White)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Беззвучные уведомления для ЧёКаВо?", color = Color.White)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = true,
                onCheckedChange = null,
                enabled = false
            )
        }

        Spacer(Modifier.height(48.dp))

        // Кнопка выхода из аккаунта
        OutlinedButton(
            onClick = {
                Firebase.auth.signOut()
                navController.navigate("auth_phone") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
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
