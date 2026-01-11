package com.kakdela.p2p.ui.chat

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.data.MessageType
import com.kakdela.p2p.ui.call.CallActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val NeonCyan = Color(0xFF00FFFF)
private val NeonMagenta = Color(0xFFFF00FF)
private val DarkBackground = Color(0xFF0A0A0A)
private val SurfaceGray = Color(0xFF1E1E1E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatPartnerId: String,
    messages: List<Message>,
    identityRepository: IdentityRepository,
    onSendMessage: (String) -> Unit,
    onSendFile: (Uri, String) -> Unit,
    onSendAudio: (Uri, Int) -> Unit,
    onScheduleMessage: (String, Long) -> Unit,
    onBack: () -> Unit
) {
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val displayName by rememberContactName(chatPartnerId)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileName(context, it)
            onSendFile(it, fileName)
        }
    }

    Scaffold(
        // Очищаем стандартные инсеты, чтобы обрабатывать их вручную через padding и Modifier
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier
            .fillMaxSize()
            .imePadding(), // ГЛАВНОЕ: поднимает весь экран при появлении клавиатуры
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = displayName, color = NeonCyan, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("P2P E2EE Connection", fontSize = 10.sp, color = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBackground),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = NeonCyan)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(context, CallActivity::class.java).apply {
                            putExtra("chatId", chatPartnerId)
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = NeonCyan)
                    }
                }
            )
        },
        bottomBar = {
            // Добавляем отступ для системной навигации
            Box(modifier = Modifier.navigationBarsPadding()) {
                ChatInputArea(
                    text = textState,
                    onTextChange = { textState = it },
                    onSend = {
                        if (textState.isNotBlank()) {
                            onSendMessage(textState)
                            textState = ""
                        }
                    },
                    onAttachFile = { filePickerLauncher.launch("*/*") },
                    onSendAudio = onSendAudio,
                    onScheduleMessage = { scheduledTime ->
                        if (textState.isNotBlank()) {
                            onScheduleMessage(textState, scheduledTime)
                            textState = ""
                        }
                    }
                )
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(message)
            }
        }
    }
}

// ... (ChatBubble, ImageAttachmentView, FileAttachmentView остаются без изменений) ...

@Composable
fun ChatBubble(message: Message) {
    val isMe = message.isMe
    val bubbleColor = if (isMe) Color(0xFF003D3D) else Color(0xFF262626)
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (isMe) 16.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 16.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            border = borderStrokeFor(isMe),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column {
                when (message.type) {
                    MessageType.IMAGE -> ImageAttachmentView(message.fileUrl)
                    MessageType.FILE -> FileAttachmentView(message.fileName ?: "Document")
                    MessageType.AUDIO -> AudioPlayerView(message.fileUrl ?: "", message.durationSeconds)
                    else -> {}
                }
                
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ImageAttachmentView(url: String?) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    )
}

@Composable
fun FileAttachmentView(fileName: String) {
    val extension = fileName.substringAfterLast('.', "").uppercase()
    Row(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(NeonCyan.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.FilePresent, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (extension.isNotEmpty()) "$extension Document" else "Document",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        
        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ChatInputArea(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: () -> Unit,
    onSendAudio: (Uri, Int) -> Unit,
    onScheduleMessage: (Long) -> Unit
) {
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowColor by infiniteTransition.animateColor(
        initialValue = NeonCyan.copy(alpha = 0.2f),
        targetValue = NeonCyan,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .background(DarkBackground), // Чтобы не было прозрачности при подъеме
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            color = SurfaceGray,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .weight(1f)
                .border(1.dp, glowColor.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = onAttachFile) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = NeonCyan)
                }
                
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Сообщение...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4, // Позволяем полю расти вверх
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        cursorColor = NeonCyan,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                IconButton(onClick = { onScheduleMessage(System.currentTimeMillis() + 3600000) }) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Color.Gray)
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        val isTyping = text.isNotBlank()
        
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(NeonCyan)
                .clickable {
                    if (isTyping) onSend() 
                    else if (!recording) {
                        val file = File(context.cacheDir, "v_${System.currentTimeMillis()}.m4a")
                        audioFile = file
                        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()).apply {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setOutputFile(file.absolutePath)
                            prepare()
                            start()
                        }
                        recording = true
                    } else {
                        try { recorder?.stop(); recorder?.release() } catch (e: Exception) {}
                        recording = false
                        audioFile?.let { onSendAudio(Uri.fromFile(it), 0) }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isTyping -> Icons.Default.Send
                    recording -> Icons.Default.Stop
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                tint = Color.Black
            )
        }
    }
}

// ... (остальные функции rememberContactName, getFileName, AudioPlayerView, borderStrokeFor) ...
@Composable
fun borderStrokeFor(isMe: Boolean) = androidx.compose.foundation.BorderStroke(
    width = 0.5.dp,
    color = if (isMe) NeonCyan.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
)

@SuppressLint("Range")
@Composable
fun rememberContactName(identifier: String): State<String> {
    val context = LocalContext.current
    val contactName = remember { mutableStateOf("ID: ${identifier.take(6)}") }

    LaunchedEffect(identifier) {
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(identifier))
            val cursor = contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    contactName.value = it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        } catch (e: Exception) { }
    }
    return contactName
}

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = it.getString(index)
            }
        }
    }
    return result ?: "file_${System.currentTimeMillis()}"
}

@Composable
fun AudioPlayerView(url: String, duration: Int) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
        IconButton(onClick = {
            try {
                if (isPlaying) {
                    mediaPlayer?.pause(); isPlaying = false
                } else {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(context, Uri.parse(url))
                        prepare(); start()
                        setOnCompletionListener { isPlaying = false }
                    }
                    isPlaying = true
                }
            } catch (e: Exception) { }
        }, modifier = Modifier.size(36.dp)) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = NeonCyan)
        }
        
        LinearProgressIndicator(
            progress = 0f, 
            modifier = Modifier.weight(1f).height(2.dp).padding(horizontal = 8.dp),
            color = NeonCyan,
            trackColor = Color.Gray
        )
        
        Text("Voice", color = Color.Gray, fontSize = 11.sp)
    }
}
