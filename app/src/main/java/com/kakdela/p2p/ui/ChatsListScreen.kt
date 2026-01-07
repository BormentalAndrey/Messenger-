package com.kakdela.p2p.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.ui.navigation.Routes

@Composable
fun ChatListItem(chat: ChatDisplay, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(chat.title.first().toString(), color = Color.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.title, fontWeight = FontWeight.Bold, color = Color.White)
            Text(chat.lastMessage, color = Color.Gray, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    navController: NavHostController,
    identityRepository: IdentityRepository
) {
    val vm: ChatsListViewModel = viewModel()
    val chats by vm.chats.collectAsState()
    val uid = Firebase.auth.currentUser?.uid.orEmpty()

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) vm.loadChats(uid)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Как дела?", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.CONTACTS) }
            ) { Icon(Icons.Default.Add, null) }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
        ) {
            items(chats, key = { it.id }) { chat ->
                ChatListItem(chat) {
                    navController.navigate("chat/${chat.id}")
                }
            }
        }
    }
}
