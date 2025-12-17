package com.kakdela.p2p.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.data.WebRtcClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(chatId: String, currentUserId: String) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel()
    val messages by viewModel.messages.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var text by remember { mutableStateOf("") }
    var p2pBytes by remember { mutableStateOf<ByteArray?>(null) }

    val rtcClient = remember { WebRtcClient(context, chatId, currentUserId) { p2pBytes = it } }

    // Отслеживание присутствия (Presence)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val ref = Firebase.firestore.collection("chats").document(chatId)
            if (event == Lifecycle.Event.ON_RESUME) ref.update("status_$currentUserId", "online")
            else if (event == Lifecycle.Event.ON_PAUSE) ref.update("status_$currentUserId", "offline")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(it)?.readBytes()
                bytes?.let { b ->
                    rtcClient.queueFile(b)
                    viewModel.send(chatId, Message("📁 Ожидание получателя для P2P...", currentUserId, isP2P = true))
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages) { msg ->
                val isOwn = msg.senderId == currentUserId
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = if (isOwn) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(shape = RoundedCornerShape(12.dp), color = if (isOwn) Color(0xFF005C4B) else Color(0xFF202C33)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            if (msg.isP2P && !isOwn && p2pBytes != null) {
                                val bmp = BitmapFactory.decodeByteArray(p2pBytes, 0, p2pBytes!!.size)
                                bmp?.let { Image(it.asImageBitmap(), null, modifier = Modifier.size(200.dp)) }
                            }
                            Text(msg.text, color = Color.White)
                        }
                    }
                }
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { launcher.launch("image/*") }) { Icon(Icons.Default.AttachFile, null, tint = Color.Cyan) }
            BasicTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f).padding(8.dp).background(Color.DarkGray, RoundedCornerShape(12.dp)).padding(8.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White))
            IconButton(onClick = { viewModel.send(chatId, Message(text, currentUserId)); text = "" }) { Icon(painterResource(android.R.drawable.ic_menu_send), null, tint = Color.Cyan) }
        }
    }
}

