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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = Firebase.auth.currentUser?.uid ?: ""

    var contacts by remember { mutableStateOf<List<AppContact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val requestPermission = rememberContactsPermissionLauncher {
        scope.launch {
            isLoading = true
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                contacts.isEmpty() -> {
                    Text(
                        "Контакты не найдены",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn {
                        items(contacts) { contact ->
                            ContactItem(contact) {
                                if (contact.isRegistered && contact.uid != null) {
                                    val chatId = listOf(
                                        currentUserId,
                                        contact.uid
                                    ).sorted().joinToString("_")

                                    navController.navigate("chat/$chatId")
                                }
                            }
                        }
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
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (contact.isRegistered) Color(0xFF00C853) else Color.Gray)
        )

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                contact.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                contact.phoneNumber,
                color = Color.Gray,
                fontSize = 14.sp
            )
        }

        Text(
            if (contact.isRegistered) "В приложении" else "Пригласить",
            color = if (contact.isRegistered)
                MaterialTheme.colorScheme.primary
            else Color.Gray,
            fontSize = 14.sp
        )
    }

    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
}
