package com.kakdela.p2p.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.kakdela.p2p.R // <-- ВАЖНО: Убедитесь, что этот путь совпадает с вашим applicationId
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
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
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val clipboardManager = LocalClipboardManager.current
    
    // Получаем ID. Если пустой — пытаемся сгенерировать/получить заново
    val myP2PId by remember { mutableStateOf(identityRepository.getMyId()) }
    
    var photoUrl by remember { mutableStateOf(user?.photoUrl?.toString()) }
    var isUploading by remember { mutableStateOf(false) }
    
    var manualHashInput by remember { mutableStateOf("") }
    var isAddingNode by remember { mutableStateOf(false) }

    val db = remember { ChatDatabase.getDatabase(context) }
    var nodes by remember { mutableStateOf<List<NodeEntity>>(emptyList()) }
    
    // Эффективное обновление списка узлов
    val refreshNodes: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val nodeList = db.nodeDao().getAllNodes()
            withContext(Dispatchers.Main) {
                nodes = nodeList.sortedByDescending { it.lastSeen }.take(50)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshNodes()
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploading = true
            val storageRef = Firebase.storage.reference.child("avatars/${user?.uid}.jpg")
            storageRef.putFile(it)
                .addOnSuccessListener { _ ->
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        photoUrl = downloadUri.toString()
                        isUploading = false
                    }
                }
                .addOnFailureListener {
                    isUploading = false
                    Toast.makeText(context, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Настройки узла", color = Color.Cyan, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                actions = {
                    IconButton(onClick = { refreshNodes() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.Cyan)
                    }
                }
            ) 
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            
            // --- АВАТАР ---
            Box(contentAlignment = Alignment.BottomEnd) {
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(50.dp), tint = Color.Gray)
                    }
                }
                
                SmallFloatingActionButton(
                    onClick = { photoPicker.launch("image/*") },
                    containerColor = Color.Cyan,
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text("+", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = user?.email ?: user?.phoneNumber ?: "Анонимный узел",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 12.dp),
                fontWeight = FontWeight.Medium
            )
            
            Spacer(Modifier.height(24.dp))

            // --- КАРТОЧКА МОЕГО ID ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Ваш Security Hash (ID):", color = Color.Gray, fontSize = 12.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = myP2PId.ifBlank { "Генерация..." },
                            color = Color.Cyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Мой ID в KakDela P2P:\n$myP2PId")
                            }
                            context.startActivity(Intent.createChooser(intent, "Поделиться ID"))
                        }) {
                            Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(myP2PId))
                            Toast.makeText(context, "ID скопирован", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- ДОБАВЛЕНИЕ ПО КОДУ ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Добавить узел по коду:", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualHashInput,
                        onValueChange = { manualHashInput = it },
                        placeholder = { Text("Вставьте хеш друга", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Cyan,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (manualHashInput.length < 10) return@Button
                            isAddingNode = true
                            scope.launch {
                                val success = identityRepository.addNodeByHash(manualHashInput.trim())
                                withContext(Dispatchers.Main) {
                                    isAddingNode = false
                                    if (success) {
                                        Toast.makeText(context, "Успешно добавлено", Toast.LENGTH_SHORT).show()
                                        manualHashInput = ""
                                        refreshNodes()
                                    } else {
                                        Toast.makeText(context, "Узел не найден в сети", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                        enabled = !isAddingNode && manualHashInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isAddingNode) {
                            CircularProgressIndicator(size = 20.dp, color = Color.Black)
                        } else {
                            Text("Синхронизировать узел", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            
            // --- СПИСОК УЗЛОВ ---
            Text(
                "Известные пиры (${nodes.size}):",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF080808))
            ) {
                items(nodes) { node ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                node.userHash.take(16) + "...",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Text(
                                "Адрес: ${node.ip}:${node.port}",
                                color = Color.DarkGray,
                                fontSize = 10.sp
                            )
                        }
                        
                        val isOnline = (System.currentTimeMillis() - node.lastSeen) < 300_000
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color.Green else Color.Gray)
                        )
                    }
                    HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 0.5.dp)
                }
            }

            // --- ВЫХОД ---
            TextButton(
                onClick = {
                    identityRepository.stopNetwork()
                    auth.signOut()
                    navController.navigate("choice") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Icon(Icons.Default.ExitToApp, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Отключить узел и выйти", color = Color.Red, fontSize = 14.sp)
            }
        }
    }
}
