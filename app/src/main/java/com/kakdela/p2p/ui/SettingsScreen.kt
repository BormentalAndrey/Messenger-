package com.kakdela.p2p.ui

import android.content.Context
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

    val myP2PId = remember { identityRepository.getMyId() }

    // ===== Аватар (локально) =====
    // ИСПРАВЛЕНО: Убран аргумент context
    var avatarUri by remember { mutableStateOf(identityRepository.getLocalAvatarUri()) }
    var isUploading by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploading = true
            scope.launch(Dispatchers.IO) {
                // ИСПРАВЛЕНО: Передаем it.toString(), убран context
                identityRepository.saveLocalAvatar(it.toString())
                withContext(Dispatchers.Main) {
                    // ИСПРАВЛЕНО: Сохраняем строку в стейт
                    avatarUri = it.toString()
                    isUploading = false
                }
            }
        }
    }

    // ===== Остальной код без изменений =====
    val db = remember { ChatDatabase.getDatabase(context) }
    var nodes by remember { mutableStateOf<List<NodeEntity>>(emptyList()) }

    val refreshNodes: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val list = db.nodeDao().getAllNodes()
            withContext(Dispatchers.Main) {
                nodes = list.sortedByDescending { it.lastSeen }.take(50)
            }
        }
    }

    LaunchedEffect(Unit) { refreshNodes() }

    var manualHash by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Настройки узла", color = Color.Cyan, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { refreshNodes() }) {
                        Icon(Icons.Default.Refresh, null, tint = Color.Cyan)
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

            // ===== Аватар =====
            Box(contentAlignment = Alignment.BottomEnd) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape),
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
                        Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
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
                        Text("+", color = Color.Black)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("P2P Node", color = Color.White, fontSize = 16.sp)

            Spacer(Modifier.height(24.dp))

            // ===== Security Hash =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Ваш Security Hash:", color = Color.Gray, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            myP2PId,
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
                            context.startActivity(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, myP2PId)
                            })
                        }) { Icon(Icons.Default.Share, null, tint = Color.White) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Добавить узел по ID", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualHash,
                        onValueChange = { manualHash = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Security Hash") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            isAdding = true
                            scope.launch {
                                val ok = identityRepository.addNodeByHash(manualHash.trim())
                                withContext(Dispatchers.Main) {
                                    isAdding = false
                                    if (ok) {
                                        manualHash = ""
                                        refreshNodes()
                                        Toast.makeText(context, "Узел добавлен", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Узел не найден", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = manualHash.length > 8 && !isAdding,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black)
                    ) {
                        if (isAdding) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                        else Text("Синхронизировать")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Известные пиры (${nodes.size})",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(nodes) { node ->
                    val online = System.currentTimeMillis() - node.lastSeen < 300_000
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                node.userHash.take(16) + "...",
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text("${node.ip}:${node.port}", color = Color.Gray, fontSize = 10.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (online) Color.Green else Color.Gray)
                        )
                    }
                    Divider(color = Color(0xFF1A1A1A))
                }
            }

            TextButton(
                onClick = {
                    identityRepository.stopNetwork()
                    navController.navigate("choice") { popUpTo(0) { inclusive = true } }
                }
            ) {
                Icon(Icons.Default.ExitToApp, null, tint = Color.Red)
                Spacer(Modifier.width(8.dp))
                Text("Остановить узел", color = Color.Red)
            }
        }
    }
}
