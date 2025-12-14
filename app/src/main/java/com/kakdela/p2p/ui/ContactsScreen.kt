package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.AppContact
import com.kakdela.p2p.data.ContactSyncManager
import com.kakdela.p2p.utils.rememberContactsPermissionLauncher

@Composable
fun ContactsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val currentUserId = Firebase.auth.currentUser?.uid ?: ""
    var contacts by remember { mutableStateOf<List<AppContact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val requestPermission = rememberContactsPermissionLauncher {
        LaunchedEffect(Unit) {
            contacts = ContactSyncManager(context).syncContacts()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        requestPermission()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Контакты", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn {
                    items(contacts) { contact ->
                        ContactItem(
                            contact = contact,
                            onClick = {
                                if (contact.isRegistered && contact.uid != null) {
                                    val participants = listOf(currentUserId, contact.uid).sorted()
                                    val chatId = participants.joinToString("_")
                                    navController.navigate("chat/$chatId")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactItem(contact: AppContact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = contact.isRegistered, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Индикатор статуса
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (contact.isRegistered) Color(0xFF00FF00) else Color.Gray)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = contact.phoneNumber.let { "+7 ${it.drop(1).chunked(3).joinToString(" ")}" },
                color = Color.Gray,
                fontSize = 14.sp
            )
        }

        if (contact.isRegistered) {
            Text("В приложении", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        } else {
            Text("Пригласить", color = Color.Gray, fontSize = 14.sp)
        }
    }

    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
}
