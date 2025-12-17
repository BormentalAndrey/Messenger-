package com.kakdela.p2p.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kakdela.p2p.data.ContactManager
import com.kakdela.p2p.data.ContactModel

@Composable
fun ContactsScreen(onContactClick: (String) -> Unit) {
    val context = LocalContext.current
    val contactManager = remember { ContactManager(context) }
    var contacts by remember { mutableStateOf<List<ContactModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        contacts = contactManager.fetchAndSyncContacts()
        isLoading = false
    }

    Scaffold(topBar = { Text("Выберите контакт", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall) }) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(contacts) { contact ->
                    ListItem(
                        headlineContent = { Text(contact.name) },
                        supportingContent = { Text(contact.phoneNumber) },
                        trailingContent = {
                            if (contact.isRegistered) {
                                Text("В сети", color = Color(0xFF075E54))
                            } else {
                                Button(onClick = { /* Логика отправки инвайта по SMS */ }) {
                                    Text("Пригласить")
                                }
                            }
                        },
                        modifier = Modifier.clickable(enabled = contact.isRegistered) {
                            contact.userId?.let { onContactClick(it) }
                        }
                    )
                }
            }
        }
    }
}

