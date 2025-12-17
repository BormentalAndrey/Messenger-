package com.kakdela.p2p.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.kakdela.p2p.R
import com.kakdela.p2p.data.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    currentUserId: String = Firebase.auth.currentUser?.uid ?: ""
) {
    val viewModel: ChatViewModel = viewModel()
    val messages by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    val storage = Firebase.storage.reference

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Диалог выбора времени
    if (showTimePicker) {
        val calendar = Calendar.getInstance()
        android.app.TimePickerDialog(
            context,
            { _, hour, minute ->
                val scheduledCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    // Если время уже прошло сегодня, ставим на завтра
                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                viewModel.send(chatId, Message(text = text.trim(), senderId = currentUserId, scheduledTime = scheduledCal.timeInMillis))
                text = ""
                showTimePicker = false
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).apply {
            setOnDismissListener { showTimePicker = false }
            show()
        }
    }

    val photoVideoLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { uploadAndSendFile(it, chatId, viewModel, storage, currentUserId, coroutineScope) }
    }

    val documentLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadAndSendFile(it, chatId, viewModel, storage, currentUserId, coroutineScope) }
    }

    var showAttachmentSheet by remember { mutableStateOf(false) }

    if (showAttachmentSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachmentSheet = false }, containerColor = Color(0xFF1A1A1A)) {
            Column(modifier = Modifier.padding(16.dp)) {
                ListItem(
                    headlineContent = { Text("Фото или видео") },
                    leadingContent = { Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { photoVideoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)); showAttachmentSheet = false }
                )
                ListItem(
                    headlineContent = { Text("Документ или файл") },
                    leadingContent = { Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { documentLauncher.launch("*/*"); showAttachmentSheet = false }
                )
            }
        }
    }

    LaunchedEffect(chatId) { viewModel.start(chatId) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch { listState.animateScrollToItem(messages.lastIndex) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(painter = painterResource(id = R.drawable.chat_background), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.6f)

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(messages) { message ->
                    val isOwn = message.senderId == currentUserId
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start) {
                        if (!isOwn) { AvatarPlaceholder("С"); Spacer(Modifier.width(8.dp)) }
                        Column(horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start) {
                            if (!message.fileUrl.isNullOrBlank()) {
                                Image(painter = rememberAsyncImagePainter(message.fileUrl), contentDescription = null, modifier = Modifier.size(250.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                                Spacer(Modifier.height(4.dp))
                            }
                            if (message.text.isNotBlank()) {
                                Surface(shape = RoundedCornerShape(16.dp), color = if (isOwn) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A)) {
                                    Text(message.text, modifier = Modifier.padding(12.dp), color = if (isOwn) Color.Black else Color.White, fontSize = 16.sp)
                                }
                            }
                            Text(text = timeFormat.format(Date(message.timestamp)), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (isOwn) { Spacer(Modifier.width(8.dp)); AvatarPlaceholder("Я") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color(0xFF1A1A1A), RoundedCornerShape(24.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showAttachmentSheet = true }) {
                    Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary)
                }

                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).padding(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(stringResource(R.string.enter_message), color = Color.Gray)
                        inner()
                    }
                )

                // Кастомная кнопка отправки с поддержкой долгого нажатия
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = text.isNotBlank(),
                            onClick = {
                                viewModel.send(chatId, Message(text = text.trim(), senderId = currentUserId))
                                text = ""
                            },
                            onLongClick = {
                                showTimePicker = true
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_send),
                        contentDescription = null,
                        tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }
    }
}

private fun uploadAndSendFile(uri: Uri, chatId: String, viewModel: ChatViewModel, storageRef: com.google.firebase.storage.StorageReference, currentUserId: String, coroutineScope: CoroutineScope) {
    coroutineScope.launch {
        try {
            val fileName = "file_${System.currentTimeMillis()}_${uri.lastPathSegment ?: "unknown"}"
            val fileRef = storageRef.child("chats/$chatId/$fileName")
            fileRef.putFile(uri).await()
            val downloadUrl = fileRef.downloadUrl.await().toString()
            viewModel.send(chatId, Message(senderId = currentUserId, fileUrl = downloadUrl))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

@Composable
fun AvatarPlaceholder(name: String) {
    Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
        Text(text = name.firstOrNull()?.uppercase() ?: "?", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

