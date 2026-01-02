package com.kakdela.p2p.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kakdela.p2p.data.ContactManager
import com.kakdela.p2p.data.ContactModel

@Composable
fun ContactsScreen(onContactClick: (String) -> Unit) {

    val context = LocalContext.current
    val contactManager = remember { ContactManager(context) }

    var contacts by remember { mutableStateOf<List<ContactModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            contacts = contactManager.fetchAndSyncContacts()
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            Text(
                "Выберите контакт",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.headlineSmall
            )
        }
    ) { padding ->

        when {
            !hasPermission -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Нужен доступ к контактам")
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = {
                            permissionLauncher.launch(
                                Manifest.permission.READ_CONTACTS
                            )
                        }) {
                            Text("Разрешить")
                        }
                    }
                }
            }

            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    items(contacts) { contact ->
                        ListItem(
                            headlineContent = { Text(contact.name) },
                            supportingContent = { Text(contact.phoneNumber) },
                            trailingContent = {
                                if (contact.isRegistered) {
                                    Text(
                                        "В сети",
                                        color = Color(0xFF00FFF0)
                                    )
                                } else {
                                    TextButton(onClick = {
                                        // TODO: SMS invite
                                    }) {
                                        Text("Пригласить")
                                    }
                                }
                            },
                            modifier = Modifier.clickable(
                                enabled = contact.isRegistered
                            ) {
                                contact.userId?.let(onContactClick)
                            }
                        )
                    }
                }
            }
        }
    }
}
