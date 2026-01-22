package com.kakdela.p2p.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    identityRepository: IdentityRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val db = remember { ChatDatabase.getDatabase(context) }

    // Данные текущего пользователя
    val myP2PId = remember { identityRepository.getMyId() }
    val prefs = remember { context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) }
    val myPhone: String = remember { prefs.getString("my_phone", "Не указан") ?: "Не указан" }

    // Состояние UI
    var avatarUriString by remember { mutableStateOf(identityRepository.getLocalAvatarUri()) }
    val avatarUri = avatarUriString?.let { Uri.parse(it) }
    var isUploading by remember { mutableStateOf(false) }
    var nodes by remember { mutableStateOf<List<NodeEntity>>(emptyList()) }
    var isSyncing by remember { mutableStateOf(false) }
    var manualHash by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }

    // Функция обновления списка узлов из локальной БД
    val loadLocalNodes: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val list = db.nodeDao().getAllNodes()
            withContext(Dispatchers.Main) {
                nodes = list.sortedByDescending { it.lastSeen }
            }
        }
    }

    // Полная синхронизация с сервером
    val performFullSync: () -> Unit = {
        scope.launch {
            isSyncing = true
            try {
                val serverUsers = identityRepository.fetchAllNodesFromServer()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Обновлено: ${serverUsers.size} узлов", Toast.LENGTH_SHORT).show()
                }
                loadLocalNodes()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка синхронизации: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isSyncing = false
            }
        }
    }

    // Загрузка при старте
    LaunchedEffect(Unit) { loadLocalNodes() }

    // Пикер аватара
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploading = true
            scope.launch(Dispatchers.IO) {
                identityRepository.saveLocalAvatar(it.toString())
                withContext(Dispatchers.Main) {
                    avatarUriString = it.toString()
                    isUploading = false
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Настройки узла", color = Color.Cyan, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { loadLocalNodes() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // ===== 1. Блок Аватара =====
            Box(contentAlignment = Alignment.BottomEnd) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .clickable { avatarPicker.launch("image/*") },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A))
                            .clickable { avatarPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    }
                }

                SmallFloatingActionButton(
                    onClick = { avatarPicker.launch("image/*") },
                    containerColor = Color.Cyan,
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Телефон: $myPhone", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Статус: Онлайн", color = Color.Green, fontSize = 12.sp)

            Spacer(Modifier.height(24.dp))

            // ===== 2. Блок ID =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Ваш уникальный P2P ID:", color = Color.Gray, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = myP2PId,
                            modifier = Modifier.weight(1f),
                            color = Color.Cyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(myP2PId))
                            Toast.makeText(context, "ID скопирован", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, null, tint = Color.White) }

                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Привет! Мой ID в KakDela: $myP2PId")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Поделиться ID"))
                        }) { Icon(Icons.Default.Share, null, tint = Color.White) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 3. Кнопка синхронизации =====
            Button(
                onClick = performFullSync,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Cyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Загрузка...", color = Color.Gray)
                } else {
                    Icon(Icons.Default.CloudSync, null, tint = Color.Cyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Синхронизировать с сервером", color = Color.Cyan)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===== 4. Поле ручного добавления =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Добавить контакт вручную", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualHash,
                            onValueChange = { manualHash = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Введите Hash", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Cyan,
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val trimmedHash = manualHash.trim()
                                if (trimmedHash.isBlank()) {
                                    Toast.makeText(context, "Введите валидный hash", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isAdding = true
                                scope.launch {
                                    // Сначала пытаемся подтянуть с сервера (если узел существует)
                                    val fetched = identityRepository.fetchAllNodesFromServer()
                                    val exists = fetched.any { it.hash.equals(trimmedHash, ignoreCase = true) }

                                    // Вставляем/обновляем локально в любом случае
                                    db.nodeDao().upsert(
                                        NodeEntity(
                                            userHash = trimmedHash.lowercase(),
                                            ip = "0.0.0.0",
                                            port = 8888,
                                            publicKey = "",
                                            lastSeen = System.currentTimeMillis()
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        isAdding = false
                                        manualHash = ""
                                        loadLocalNodes()
                                        Toast.makeText(
                                            context,
                                            if (exists) "Контакт обновлён с сервера!" else "Контакт добавлен локально!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            enabled = manualHash.length > 10 && !isAdding, // минимальная валидация длины hash
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.width(50.dp)
                        ) {
                            if (isAdding) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                            else Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== 5. Список контактов =====
            Text("Ваша сеть (${nodes.size})", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(nodes) { node ->
                    val isOnline = System.currentTimeMillis() - node.lastSeen < 300_000
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .clickable {
                                // Открываем чат по хэшу
                                navController.navigate(Routes.buildChatRoute(node.userHash))
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222222)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(node.userHash.take(1).uppercase(), color = Color.Cyan, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            val displayName = if (!node.phone.isNullOrEmpty()) node.phone else "User ${node.userHash.take(4)}"
                            Text(displayName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(node.userHash.take(16) + "...", fontFamily = FontFamily.Monospace, color = Color.Gray, fontSize = 10.sp)
                        }

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color.Green else Color.Gray)
                        )
                    }
                    HorizontalDivider(color = Color(0xFF1A1A1A))
                }
            }

            Spacer(Modifier.height(8.dp))

            // ===== 6. Кнопка выхода =====
            TextButton(
                onClick = {
                    identityRepository.stopNetwork()
                    // Очистка аватара и других данных при выходе (опционально)
                    prefs.edit().clear().apply()
                    navController.navigate("choice") { 
                        popUpTo(0) { inclusive = true } 
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.ExitToApp, null, tint = Color.Red)
                Spacer(Modifier.width(8.dp))
                Text("Выйти из аккаунта", color = Color.Red)
            }
        }
    }
}
