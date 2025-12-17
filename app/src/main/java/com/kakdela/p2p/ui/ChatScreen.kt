package com.kakdela.p2p.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) } // Храним выбранный файл
    var isUploading by remember { mutableStateOf(false) } // Состояние загрузки
    
    val context = LocalContext.current
    val storage = Firebase.storage.reference
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Логика отправки (общая для обычных и отложенных сообщений)
    fun handleSend(scheduledTime: Long = 0L) {
        coroutineScope.launch {
            isUploading = true
            var finalFileUrl: String? = null

            // 1. Если есть файл, сначала загружаем его
            selectedFileUri?.let { uri ->
                try {
                    val fileName = "file_${System.currentTimeMillis()}"
                    val fileRef = storage.child("chats/$chatId/$fileName")
                    fileRef.putFile(uri).await()
                    finalFileUrl = fileRef.downloadUrl.await().toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Отправляем сообщение
            viewModel.send(
                chatId, 
                Message(
                    text = text.trim(),
                    senderId = currentUserId,
                    fileUrl = finalFileUrl,
                    scheduledTime = scheduledTime
                )
            )

            // 3. Сбрасываем поля
            text = ""
            selectedFileUri = null
            isUploading = false
        }
    }

    // Выбор времени для отложенной отправки
    if (showTimePicker) {
        val calendar = Calendar.getInstance()
        android.app.TimePickerDialog(context, { _, h, m ->
            val scheduledCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            handleSend(scheduledCal.timeInMillis)
            showTimePicker = false
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).apply {
            setOnDismissListener { showTimePicker = false }
            show()
        }
    }

    val photoVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { 
        selectedFileUri = it 
    }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { 
        selectedFileUri = it 
    }

    var showAttachmentSheet by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Image(painter = painterResource(id = R.drawable.chat_background), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.4f)

            Column(modifier = Modifier.fillMaxSize()) {
                // Список сообщений
                LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    items(messages) { message ->
                        val isOwn = message.senderId == currentUserId
                        ChatBubble(message, isOwn, timeFormat)
                    }
                }

                // Панель ввода
                Column(modifier = Modifier.background(Color(0xFF1A1A1A)).padding(8.dp)) {
                    
                    // ПРЕДПРОСМОТР выбранного файла перед отправкой
                    selectedFileUri?.let { uri ->
                        Box(modifier = Modifier.padding(8.dp).size(100.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(uri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { selectedFileUri = null },
                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF2A2A2A), RoundedCornerShape(24.dp)),
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
                                if (text.isEmpty() && selectedFileUri == null) Text("Сообщение", color = Color.Gray)
                                inner()
                            }
                        )

                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                        } else {
                            Box(
                                modifier = Modifier.padding(end = 4.dp).size(48.dp).clip(CircleShape)
                                    .combinedClickable(
                                        enabled = text.isNotBlank() || selectedFileUri != null,
                                        onClick = { handleSend() },
                                        onLongClick = { showTimePicker = true }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(painter = painterResource(android.R.drawable.ic_menu_send), contentDescription = null, 
                                    tint = if (text.isNotBlank() || selectedFileUri != null) MaterialTheme.colorScheme.primary else Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachmentSheet = false }, containerColor = Color(0xFF1A1A1A)) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ListItem(headlineContent = { Text("Фото или видео") }, leadingContent = { Icon(Icons.Default.Image, null) },
                    modifier = Modifier.clickable { photoVideoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)); showAttachmentSheet = false })
                ListItem(headlineContent = { Text("Документ") }, leadingContent = { Icon(Icons.Default.InsertDriveFile, null) },
                    modifier = Modifier.clickable { documentLauncher.launch("*/*"); showAttachmentSheet = false })
            }
        }
    }

    LaunchedEffect(chatId) { viewModel.start(chatId) }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
}

@Composable
fun ChatBubble(message: Message, isOwn: Boolean, timeFormat: SimpleDateFormat) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start) {
        if (!isOwn) AvatarPlaceholder("С")
        Column(horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start, modifier = Modifier.padding(horizontal = 8.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = if (isOwn) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (!message.fileUrl.isNullOrBlank()) {
                        Image(
                            painter = rememberAsyncImagePainter(message.fileUrl),
                            contentDescription = null,
                            modifier = Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 200.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (message.text.isNotBlank()) {
                        Text(message.text, color = if (isOwn) Color.Black else Color.White, fontSize = 16.sp, modifier = Modifier.padding(4.dp))
                    }
                }
            }
            Text(timeFormat.format(Date(message.timestamp)), fontSize = 10.sp, color = Color.Gray)
        }
        if (isOwn) AvatarPlaceholder("Я")
    }
}

