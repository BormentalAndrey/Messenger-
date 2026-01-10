package com.kakdela.p2p.ui

import android.net.Uri
import android.util.Log
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
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
    val user = FirebaseAuth.getInstance().currentUser
    val clipboardManager = LocalClipboardManager.current
    
    // Получаем ID синхронно, так как это SharedPreferences
    val myP2PId = remember { identityRepository.getMyId() }
    
    var photoUrl by remember { mutableStateOf(user?.photoUrl?.toString()) }
    var isUploading by remember { mutableStateOf(false) }
    
    // Загрузка списка узлов для отладки P2P (исправление ошибки с take)
    val db = remember { ChatDatabase.getDatabase(context) }
    var nodes by remember { mutableStateOf<List<NodeEntity>>(emptyList()) }
    
    // Функция обновления списка узлов
    fun refreshNodes() {
        scope.launch(Dispatchers.IO) {
            // Берем последние 50 узлов
            val nodeList = db.nodeDao().getAllNodes().take(50)
            withContext(Dispatchers.Main) {
                nodes = nodeList
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshNodes()
    }

    val storage = Firebase.storage
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploading = true
            val ref = storage.reference.child("avatars/${user?.uid}.jpg")
            ref.putFile(it)
                .addOnSuccessListener { _ ->
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        photoUrl = downloadUri.toString()
                        isUploading = false
                    }
                }
                .addOnFailureListener { e ->
                    isUploading = false
                    Log.e("Settings", "Upload failed: ${e.message}")
                    Toast.makeText(context, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Настройки P2P", color = Color.Cyan, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                actions = {
                    IconButton(onClick = { refreshNodes() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Cyan)
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            
            // --- СЕКЦИЯ АВАТАРА ---
            Box(contentAlignment = Alignment.BottomEnd) {
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A))
                            .padding(24.dp)
                    )
                }
                
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(120.dp),
                        color = Color.Cyan,
                        strokeWidth = 3.dp
                    )
                }

                SmallFloatingActionButton(
                    onClick = { photoPicker.launch("image/*") },
                    containerColor = Color.Cyan,
                    contentColor = Color.Black,
                    modifier = Modifier.offset(x = (-4).dp, y = (-4).dp),
                    shape = CircleShape
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = user?.email ?: user?.phoneNumber ?: "Анонимный узел",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(Modifier.height(24.dp))
            
            // --- P2P IDENTITY CARD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ваш Security Hash (ID):", color = Color.Gray, fontSize = 12.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (myP2PId.isNotBlank()) myP2PId else "Не сгенерирован",
                            color = Color.Cyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        IconButton(onClick = { 
                            if(myP2PId.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(myP2PId))
                                Toast.makeText(context, "ID скопирован", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            // --- СЕТЕВАЯ ОТЛАДКА ---
            Text(
                "Известные узлы (Кэш DHT): ${nodes.size}",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
            ) {
                items(nodes) { node ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Hash: ${node.userHash.take(8)}...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "IP: ${node.ip}:${node.port}",
                                color = Color.DarkGray,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = if((System.currentTimeMillis() - node.lastSeen) < 60000) "ONLINE" else "OFFLINE",
                            color = if((System.currentTimeMillis() - node.lastSeen) < 60000) Color.Green else Color.Red,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(color = Color(0xFF222222))
                }
            }

            Spacer(Modifier.height(16.dp))
            
            // --- КНОПКА ВЫХОДА ---
            Button(
                onClick = {
                    identityRepository.stopNetwork()
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate("choice") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220000)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.Red)
                Spacer(Modifier.width(8.dp))
                Text("Отключиться и выйти", color = Color.Red, fontWeight = FontWeight.Bold)
            }
            
            Text(
                text = "KakDela P2P v1.0.5-release",
                color = Color.DarkGray,
                fontSize = 10.sp,
                modifier = Modifier.padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
