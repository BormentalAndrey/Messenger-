package com.kakdela.p2p.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.IdentityRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    identityRepository: IdentityRepository // Исправлено: теперь соответствует NavGraph
) {
    val user = FirebaseAuth.getInstance().currentUser
    val clipboardManager = LocalClipboardManager.current
    val myP2PId = remember { identityRepository.getMyId() }
    
    var photoUrl by remember { mutableStateOf(user?.photoUrl?.toString()) }
    var isUploading by remember { mutableStateOf(false) }
    
    val storage = Firebase.storage
    
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploading = true
            val ref = storage.reference.child("avatars/${user?.uid}.jpg")
            ref.putFile(it)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        photoUrl = downloadUri.toString()
                        isUploading = false
                    }
                }
                .addOnFailureListener {
                    isUploading = false
                    Log.e("Settings", "Upload failed: ${it.message}")
                }
        }
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Опции", color = Color.Cyan, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
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
            Spacer(Modifier.height(32.dp))
            
            // Секция аватара
            Box(contentAlignment = Alignment.BottomEnd) {
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(140.dp)
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
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A))
                            .padding(30.dp)
                    )
                }
                
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(140.dp),
                        color = Color.Cyan,
                        strokeWidth = 2.dp
                    )
                }

                SmallFloatingActionButton(
                    onClick = { photoPicker.launch("image/*") },
                    containerColor = Color.Cyan,
                    contentColor = Color.Black,
                    modifier = Modifier.offset(x = (-4).dp, y = (-4).dp),
                    shape = CircleShape
                ) {
                    Text("+", fontSize = 20.sp)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Информация о пользователе
            Text(
                text = user?.email ?: "Анонимный узел",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(Modifier.height(8.dp))
            
            // P2P ID Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ваш P2P Fingerprint:", color = Color.Gray, fontSize = 12.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = myP2PId.take(16) + "...",
                            color = Color.Cyan,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { 
                            clipboardManager.setText(AnnotatedString(myP2PId))
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            
            // Кнопка выхода
            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate("choice") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.Red)
                Spacer(Modifier.width(8.dp))
                Text("Выйти из сети", color = Color.Red, fontWeight = FontWeight.Bold)
            }
            
            Text(
                text = "KakDela P2P v1.0.4-stable",
                color = Color.DarkGray,
                fontSize = 10.sp,
                modifier = Modifier.padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
