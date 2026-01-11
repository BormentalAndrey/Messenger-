package com.kakdela.p2p.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.local.MessageEntity
import com.kakdela.p2p.ui.call.CallActivity
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

private val NeonCyan = Color(0xFF00FFFF)
private val NeonGreen = Color(0xFF00FF9D)
private val NeonPurple = Color(0xFFB042FF)
private val DarkBackground = Color(0xFF0A0A0A)
private val SurfaceGray = Color(0xFF1E1E1E)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatPartnerId: String,
    messages: List<MessageEntity>,
    identityRepository: IdentityRepository,
    onSendMessage: (String) -> Unit,
    onSendFile: (Uri, String) -> Unit,
    onSendAudio: (Uri, Int) -> Unit,
    onScheduleMessage: (String, Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var textState by remember { mutableStateOf("") }

    val contactName by rememberContactName(chatPartnerId)
    val displayName = remember(contactName, chatPartnerId) {
        if (contactName.isBlank() || contactName == chatPartnerId) {
            "ID: ${chatPartnerId.take(8)}"
        } else {
            contactName
        }
    }
    val contactAvatar by rememberContactAvatar(chatPartnerId)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onSendFile(it, getFileName(context, it)) }
    }

    Scaffold(
        containerColor = DarkBackground,
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = NeonCyan)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedAvatar(contactAvatar)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = displayName,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("P2P E2EE Connection", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { startCall(context, chatPartnerId, isVideo = false) }) {
                        Icon(Icons.Default.Call, "Call", tint = NeonCyan)
                    }
                }
            )
        },
        bottomBar = {
            ChatInputArea(
                text = textState,
                onTextChange = { textState = it },
                onSend = {
                    onSendMessage(textState)
                    textState = ""
                },
                onAttachFile = { filePicker.launch("*/*") },
                onSendAudio = onSendAudio,
                onScheduleMessage = { scheduledTime ->
                    onScheduleMessage(textState, scheduledTime)
                    textState = ""
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.messageId }) { message ->
                ChatBubble(
                    message = message,
                    contactAvatar = contactAvatar,
                    modifier = Modifier.animateItemPlacement()
                )
            }
        }
    }
}

fun startCall(context: Context, id: String, isVideo: Boolean) {
    val intent = Intent(context, CallActivity::class.java).apply {
        putExtra("chatId", id)
        putExtra("isVideo", isVideo)
    }
    context.startActivity(intent)
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
    val calendar = remember { Calendar.getInstance() }
    
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableLongStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTime = 0L
            while (isRecording) {
                delay(1000)
                recordingTime++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.release()
            recorder = null
        }
    }

    fun openSchedulePicker() {
        DatePickerDialog(context, { _, y, m, d ->
            calendar.set(y, m, d)
            TimePickerDialog(context, { _, h, min ->
                calendar.set(Calendar.HOUR_OF_DAY, h)
                calendar.set(Calendar.MINUTE, min)
                if (calendar.timeInMillis > System.currentTimeMillis()) {
                    onScheduleMessage(calendar.timeInMillis)
                } else {
                    Toast.makeText(context, "Выберите время в будущем", Toast.LENGTH_SHORT).show()
                }
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    Column(Modifier.navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachFile) {
                Icon(Icons.Default.Add, "Attach", tint = NeonPurple)
            }

            Box(modifier = Modifier.weight(1f)) {
                if (isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(SurfaceGray, RoundedCornerShape(25.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).background(Color.Red, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = String.format("%02d:%02d", recordingTime / 60, recordingTime % 60),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text("Запись...", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        placeholder = { Text("Сообщение...", color = Color.Gray, fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SurfaceGray.copy(alpha = 0.5f),
                            unfocusedContainerColor = SurfaceGray.copy(alpha = 0.5f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            if (text.isNotBlank()) {
                                IconButton(onClick = { openSchedulePicker() }) {
                                    Icon(Icons.Outlined.Schedule, "Schedule", tint = Color.Gray)
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            FloatingActionButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSend()
                    } else if (!isRecording) {
                        val audioDir = File(context.filesDir, "audios")
                        audioDir.mkdirs()
                        val file = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")
                        audioFile = file
                        recorder = MediaRecorder().apply {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setOutputFile(file.absolutePath)
                            try {
                                prepare()
                                start()
                                isRecording = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Ошибка микрофона", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        try {
                            recorder?.stop()
                            recorder?.release()
                            recorder = null
                            isRecording = false
                            audioFile?.let { onSendAudio(Uri.fromFile(it), recordingTime.toInt()) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            isRecording = false
                        }
                    }
                },
                containerColor = if (isRecording) Color.Red else NeonGreen,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = when {
                        text.isNotBlank() -> Icons.Default.Send
                        isRecording -> Icons.Default.Stop
                        else -> Icons.Default.Mic
                    },
                    contentDescription = null,
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: MessageEntity,
    contactAvatar: Uri?,
    modifier: Modifier = Modifier
) {
    val isMe = message.isMe
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMe) 16.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 16.dp
    )
    val bubbleColor = if (isMe) Color(0xFF003D3D).copy(alpha = 0.95f) else Color(0xFF2D1442).copy(alpha = 0.95f)
    val borderColor = if (isMe) NeonCyan.copy(alpha = 0.3f) else NeonPurple.copy(alpha = 0.3f)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = alignment) {
        if (!isMe) {
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedAvatar(contactAvatar, size = 36.dp)
                Spacer(Modifier.width(8.dp))
                MessageBubbleContent(
                    message = message,
                    shape = shape,
                    color = bubbleColor,
                    borderColor = borderColor
                )
            }
        } else {
            MessageBubbleContent(
                message = message,
                shape = shape,
                color = bubbleColor,
                borderColor = borderColor
            )
        }
    }
}

@Composable
fun MessageBubbleContent(
    message: MessageEntity,
    shape: RoundedCornerShape,
    color: Color,
    borderColor: Color
) {
    val context = LocalContext.current

    Surface(
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 280.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            val content = message.text ?: ""

            when {
                content.startsWith("AUDIO:") -> AudioPlayerBubble(content)
                content.startsWith("FILE:") -> FileBubble(content)
                else -> Text(
                    text = content,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            }

            Row(
                modifier = Modifier.align(Alignment.End).padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.scheduledTime != null) {
                    Icon(Icons.Outlined.Schedule, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = Color.Gray.copy(0.8f)
                )
            }
        }
    }
}

@Composable
fun AudioPlayerBubble(content: String) {
    val context = LocalContext.current
    val trimmed = content.removePrefix("AUDIO:").trim()
    val parts = trimmed.split(" ", limit = 2)
    val fileName = parts[0]
    val storedDuration = if (parts.size > 1) parts[1].removeSuffix("s").toIntOrNull() ?: 0 else 0

    val audioDir = remember { File(context.filesDir, "audios") }
    val audioFile = remember(fileName) { File(audioDir, fileName) }

    if (!audioFile.exists()) {
        Text("Аудио недоступно", color = Color.Red.copy(alpha = 0.8f), fontSize = 13.sp)
        return
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(storedDuration.coerceAtLeast(1)) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(audioFile) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(audioFile))
                prepare()
                duration = this.duration / 1000
                isLoaded = true
                setOnCompletionListener {
                    isPlaying = false
                    currentPosition = 0
                    seekTo(0)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(isPlaying, isLoaded) {
        if (isPlaying && isLoaded) {
            mediaPlayer?.start()
            while (isPlaying && isLoaded) {
                currentPosition = mediaPlayer?.currentPosition?.div(1000) ?: 0
                delay(500)
            }
            mediaPlayer?.pause()
        }
    }

    val onToggle = {
        if (isLoaded) {
            isPlaying = !isPlaying
            if (isPlaying) mediaPlayer?.start() else mediaPlayer?.pause()
        }
    }

    val onSeek = { progress: Float ->
        val pos = (progress * duration * 1000).toInt()
        mediaPlayer?.seekTo(pos)
        currentPosition = pos / 1000
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.background(NeonCyan, CircleShape).size(40.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Slider(
                value = progress,
                onValueChange = onSeek,
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(currentPosition), color = Color.Gray, fontSize = 11.sp)
                Text(formatTime(duration), color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun FileBubble(content: String) {
    val context = LocalContext.current
    val trimmed = content.removePrefix("FILE:").trim()
    val parts = trimmed.split(" ", limit = 2)
    val displayName = parts[0]
    val savedName = if (parts.size > 1) parts[1] else displayName

    val fileDir = remember { File(context.filesDir, "files").apply { mkdirs() } }
    val file = remember(savedName) { File(fileDir, savedName) }
    val uri = Uri.fromFile(file)

    val extension = displayName.substringAfterLast(".", "").lowercase()
    val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
    val isImage = extension in imageExtensions

    if (isImage && file.exists()) {
        AsyncImage(
            model = uri,
            contentDescription = displayName,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = file.exists() && !isImage) {
                    if (file.exists()) {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Нет приложения для открытия", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                Icons.Default.Attachment,
                contentDescription = null,
                tint = NeonPurple,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(displayName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(
                    if (file.exists()) "Нажмите для открытия" else "Файл недоступен",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun formatTime(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return String.format("%02d:%02d", min, sec)
}

@Composable
fun AnimatedAvatar(avatarUri: Uri?, size: Dp = 34.dp) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Surface(
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = SurfaceGray,
            border = BorderStroke(2.dp, NeonCyan.copy(alpha = 0.5f))
        ) {
            if (avatarUri != null) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Default Avatar",
                    tint = Color.Gray,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // Пульсирующее свечение
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(4.dp)
                .background(NeonCyan.copy(alpha = pulseAlpha), CircleShape)
        )
    }
}
@SuppressLint("Range")
@Composable
fun rememberContactName(id: String): State<String> {
    val context = LocalContext.current
    val state = remember { mutableStateOf("") }
    LaunchedEffect(id) {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(id))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) state.value = it.getString(0) ?: ""
            }
        } catch (e: Exception) {
            state.value = ""
        }
    }
    return state
}

@SuppressLint("Range")
@Composable
fun rememberContactAvatar(id: String): State<Uri?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(id) {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(id))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val photoUri = it.getString(0)
                    if (photoUri != null) state.value = Uri.parse(photoUri)
                }
            }
        } catch (e: Exception) { }
    }
    return state
}

fun getFileName(context: Context, uri: Uri): String {
    var name = "file_${System.currentTimeMillis()}"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && idx != -1) name = it.getString(idx)
        }
    } catch (e: Exception) { }
    return name
}
