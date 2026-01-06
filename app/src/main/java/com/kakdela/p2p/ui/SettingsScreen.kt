package com.kakdela.p2p.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage // Важно!
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val user = FirebaseAuth.getInstance().currentUser
    var photoUrl by remember { mutableStateOf(user?.photoUrl?.toString()) }
    val storage = Firebase.storage
    
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val ref = storage.reference.child("avatars/${user?.uid}.jpg")
            ref.putFile(it).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    photoUrl = downloadUri.toString()
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Настройки", color = Color.Cyan) }) },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            
            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Button(onClick = { photoPicker.launch("image/*") }) {
                    Text("Изменить")
                }
            }
            
            Text(user?.email ?: "P2P User", color = Color.White, modifier = Modifier.padding(16.dp))
            
            Button(onClick = {
                FirebaseAuth.getInstance().signOut()
                navController.navigate("choice") {
                    popUpTo(0)
                }
            }) {
                Text("Выйти из аккаунта", color = Color.Red)
            }
        }
    }
}

