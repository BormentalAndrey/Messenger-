package com.kakdela.p2p.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakdela.p2p.data.WebRtcClient
import com.kakdela.p2p.data.local.ChatDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String, currentUserId: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dao = remember { ChatDatabase.getDatabase(context).messageDao() }
    val messages by dao.getMessagesForChat(chatId).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    // Инициализация WebRTC
    val rtcClient = remember { WebRtcClient(context, chatId, currentUserId) }
    var text by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(it)?.readBytes()
                bytes?.let { rtcClient.sendP2P("", it) }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("P2P Chat: $chatId") }) },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { launcher.launch("image/*") }) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f).padding(8.dp).background(Color.LightGray.copy(0.3f), RoundedCornerShape(20.dp)).padding(12.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                    )
                    IconButton(onClick = {
                        if (text.isNotBlank()) {
                            rtcClient.sendP2P(text)
                            text = ""
                        }
                    }) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F5F5)),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(messages) { msg ->
                val isOwn = msg.senderId == currentUserId
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Surface(
                        color = if (isOwn) Color(0xFFDCF8C6) else Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(if (isOwn) Alignment.CenterEnd else Alignment.CenterStart).widthIn(max = 280.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            msg.fileBytes?.let {
                                val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                                bitmap?.let { b ->
                                    Image(
                                        bitmap = b.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().height(200.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            if (msg.text.isNotEmpty()) {
                                Text(text = msg.text, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

