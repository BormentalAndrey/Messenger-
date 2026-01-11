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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
private val SurfaceGray = Color(0xFF1A1A1A)

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
    
    // Получаем имя из контактов
    val displayName by rememberContactName(chatPartnerId)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Лаунчер для выбора любого файла
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileName(context, it)
            onSendFile(it, fileName)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = displayName, 
                            color = NeonCyan, 
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text("P2P E2EE Protected", fontSize = 10.sp, color = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBackground),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = NeonCyan)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Инфо о контакте */ }) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan)
                    }
                }
            )
        },
        bottomBar = {
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
                },
                onStartCall = {
                    val intent = Intent(context, CallActivity::class.java).apply {
                        putExtra("chatId", chatPartnerId)
                    }
                    context.startActivity(intent)
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(message)
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val isMe = message.isMe
    val bubbleColor = if (isMe) NeonCyan.copy(alpha = 0.2f) else NeonMagenta.copy(alpha = 0.2f)
    val borderColor = if (isMe) NeonCyan else NeonMagenta

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Column {
                when (message.type) {
                    MessageType.TEXT -> Text(message.text, color = Color.White, fontSize = 14.sp)
                    MessageType.IMAGE -> AsyncImage(
                        model = message.fileUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    MessageType.AUDIO -> AudioPlayerView(message.fileUrl ?: "", message.durationSeconds)
                    MessageType.FILE -> FileP2PView(message.fileName ?: "Файл")
                    else -> Text(message.text, color = Color.White)
                }

                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatInputArea(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: () -> Unit,
    onSendAudio: (Uri, Int) -> Unit,
    onScheduleMessage: (Long) -> Unit,
    onStartCall: () -> Unit
) {
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowColor by infiniteTransition.animateColor(
        initialValue = NeonCyan.copy(alpha = 0.4f),
        targetValue = NeonCyan,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttachFile) { 
            Icon(Icons.Default.AttachFile, contentDescription = null, tint = NeonCyan) 
        }

        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Введите...", color = Color.Gray) },
            modifier = Modifier
                .weight(1f)
                .border(1.dp, glowColor, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceGray,
                unfocusedContainerColor = SurfaceGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = NeonCyan,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(Modifier.width(8.dp))

        IconButton(onClick = {
            if (!recording) {
                val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
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
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (e: Exception) { e.printStackTrace() }
                recording = false
                audioFile?.let { onSendAudio(Uri.fromFile(it), 0) }
            }
        }) {
            Icon(
                if (recording) Icons.Default.Stop else Icons.Default.Mic, 
                contentDescription = null, 
                tint = if (recording) Color.Red else NeonCyan
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onSend,
                    onLongClick = { onScheduleMessage(System.currentTimeMillis() + 3600000) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Send, contentDescription = null, tint = NeonCyan)
        }

        IconButton(onClick = onStartCall) { 
            Icon(Icons.Default.Call, contentDescription = null, tint = NeonCyan) 
        }
    }
}

// Вспомогательные функции

@SuppressLint("Range")
@Composable
fun rememberContactName(identifier: String): State<String> {
    val context = LocalContext.current
    val contactName = remember { mutableStateOf("Узел: ${identifier.take(8)}...") }

    LaunchedEffect(identifier) {
        val contentResolver: ContentResolver = context.contentResolver
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(identifier)
        )
        val cursor = contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
        
        cursor?.use {
            if (it.moveToFirst()) {
                contactName.value = it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
            }
        }
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
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    return result ?: "file_${System.currentTimeMillis()}"
}

@Composable
fun FileP2PView(fileName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.FilePresent, null, tint = NeonCyan, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(fileName, color = Color.White, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun AudioPlayerView(url: String, duration: Int) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
            } else {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, Uri.parse(url))
                    prepare()
                    start()
                    setOnCompletionListener { isPlaying = false }
                }
                isPlaying = true
            }
        }) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = NeonCyan)
        }
        Text("Голосовое сообщение", color = Color.White, fontSize = 12.sp)
    }
}
