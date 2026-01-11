package com.kakdela.p2p.ui

import android.content.Intent
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
import androidx.compose.material.icons.filled.Add
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
    
    val myP2PId = remember { identityRepository.getMyId() }
    
    var photoUrl by remember { mutableStateOf(user?.photoUrl?.toString()) }
    var isUploading by remember { mutableStateOf(false) }
    
    // UI state for adding new node
    var manualHashInput by remember { mutableStateOf("") }
    var isAddingNode by remember { mutableStateOf(false) }

    val db = remember { ChatDatabase.getDatabase(context) }
    var nodes by remember { mutableStateOf<List<NodeEntity>>(emptyList()) }
    
    fun refreshNodes() {
        scope.launch(Dispatchers.IO) {
            val nodeList = db.nodeDao().getAllNodes().take(50)
            withContext(Dispatchers.Main) { nodes = nodeList }
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
                    Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Настройки", color = Color.Cyan, fontWeight = FontWeight.Bold) },
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
            
            // --- AVATAR & INFO ---
            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(
                    model = photoUrl ?: R.drawable.ic_launcher_foreground, // Ensure a placeholder exists
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )
                SmallFloatingActionButton(
                    onClick = { photoPicker.launch("image/*") },
                    containerColor = Color.Cyan,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("+")
                }
            }
            Text(
                text = user?.email ?: "Анонимный P2P Узел",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(Modifier.height(24.dp))

            // --- MY ID CARD ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Ваш P2P ID:", color = Color.Gray, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = myP2PId,
                            color = Color.Cyan,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, myP2PId)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Отправить ID"))
                        }) {
                            Icon(Icons.Default.Share, "Share", tint = Color.White)
                        }
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(myP2PId))
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- ADD NODE MANUALLY ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Добавить друга по ID:", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualHashInput,
                        onValueChange = { manualHashInput = it },
                        placeholder = { Text("Вставьте хеш...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Cyan,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (manualHashInput.isBlank()) return@Button
                            isAddingNode = true
                            scope.launch {
                                val success = identityRepository.addNodeByHash(manualHashInput.trim())
                                isAddingNode = false
                                if (success) {
                                    Toast.makeText(context, "Узел добавлен!", Toast.LENGTH_SHORT).show()
                                    manualHashInput = ""
                                    refreshNodes()
                                } else {
                                    Toast.makeText(context, "Узел не найден на сервере", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                        enabled = !isAddingNode
                    ) {
                        if (isAddingNode) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Найти и добавить", color = Color.Black)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            
            // --- NETWORK DEBUG LIST ---
            Text("Активные узлы в кэше:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
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
                            Text(node.userHash.take(12) + "...", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            Text("${node.ip}:${node.port}", color = Color.Gray, fontSize = 10.sp)
                        }
                        // Status indicator
                        val isOnline = (System.currentTimeMillis() - node.lastSeen) < 300_000 // 5 mins
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if(isOnline) Color.Green else Color.Red, CircleShape)
                        )
                    }
                    Divider(color = Color(0xFF222222))
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- LOGOUT ---
            Button(
                onClick = {
                    identityRepository.stopNetwork()
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate("choice") { popUpTo(0) }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF330000))
            ) {
                Icon(Icons.Default.ExitToApp, null, tint = Color.Red)
                Spacer(Modifier.width(8.dp))
                Text("Выйти", color = Color.Red)
            }
        }
    }
}
