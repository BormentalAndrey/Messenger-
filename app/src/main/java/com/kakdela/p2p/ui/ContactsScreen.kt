package com.kakdela.p2p.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kakdela.p2p.data.AppContact
import com.kakdela.p2p.data.ContactP2PManager
import com.kakdela.p2p.data.IdentityRepository

@Composable
fun ContactsScreen(
    identityRepository: IdentityRepository,
    onContactClick: (AppContact) -> Unit
) {
    val context = LocalContext.current
    val contactManager = remember { ContactP2PManager(context, identityRepository) }

    var contacts by remember { mutableStateOf<List<AppContact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
            == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            contacts = contactManager.syncContacts()
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "P2P Контакты",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Cyan
                )
                Text(
                    "Поиск пользователей в распределенной сети...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        containerColor = Color.Black
    ) { padding ->
        when {
            !hasPermission -> {
                PermissionRequestUI(padding) {
                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }
            isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Cyan)
                }
            }
            else -> {
                ContactList(padding, contacts, onContactClick)
            }
        }
    }
}

@Composable
fun ContactList(
    padding: PaddingValues, 
    contacts: List<AppContact>, 
    onContactClick: (AppContact) -> Unit
) {
    LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
        items(contacts) { contact ->
            ListItem(
                headlineContent = { Text(contact.name, color = Color.White) },
                supportingContent = { Text(contact.phoneNumber, color = Color.Gray) },
                trailingContent = {
                    if (contact.isRegistered) {
                        Surface(
                            color = Color(0xFF00FFF0),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "P2P", 
                                color = Color.Black, 
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        Text("OFFLINE", color = Color.DarkGray, style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(enabled = contact.isRegistered) {
                    onContactClick(contact)
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp), 
                thickness = 0.5.dp, 
                color = Color(0xFF1A1A1A)
            )
        }
    }
}

@Composable
fun PermissionRequestUI(padding: PaddingValues, onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Доступ к контактам нужен для поиска друзей в сети", color = Color.White)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Cyan, 
                    contentColor = Color.Black
                )
            ) { Text("Разрешить") }
        }
    }
}

