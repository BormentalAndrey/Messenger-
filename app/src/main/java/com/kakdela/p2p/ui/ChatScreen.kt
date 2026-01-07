package com.kakdela.p2p.ui

import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onSendFile: (Uri, MessageType) -> Unit,
    onSendAudio: (Uri, Int) -> Unit,
    onScheduleMessage: (String, Long) -> Unit,
    onBack: () -> Unit
) {
    var textState by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Прокрутка вниз при получении новых сообщений
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun scheduleAndSend() {
        val date = datePickerState.selectedDateMillis ?: return
        val cal = Calendar.getInstance().apply {
            timeInMillis = date
            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
            set(Calendar.MINUTE, timePickerState.minute)
        }
        onScheduleMessage(textState, cal.timeInMillis)
        textState = ""
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Узел: ${chatPartnerId.take(8)}...", color = NeonCyan, fontWeight = FontWeight.Bold)
                        Text("P2P E2EE Protected", fontSize = 10.sp, color = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = NeonCyan)
                    }
                }
            )
        },
        bottomBar = {
            ChatInputField(
                text = textState,
                onTextChange = { textState = it },
                onSend = {
                    if (textState.isNotBlank()) {
                        onSendMessage(textState)
                        textState = ""
                    }
                },
                onAttachFile = { uri -> onSendFile(uri, MessageType.FILE) },
                onScheduleClick = { if (textState.isNotBlank()) showDatePicker = true },
                onSendAudio = onSendAudio,
                onStartCall = {
                    val intent = Intent(context, CallActivity::class.java).apply {
                        putExtra("targetIp", "auto")
                        putExtra("chatId", chatPartnerId)
                    }
                    context.startActivity(intent)
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = { showDatePicker = false; showTimePicker = true }) {
                        Text("ДАЛЕЕ", color = NeonCyan)
                    }
                }
            ) { DatePicker(state = datePickerState) }
        }

        if (showTimePicker) {
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(onClick = { showTimePicker = false; scheduleAndSend() }) {
                        Text("ОК", color = NeonCyan)
                    }
                },
                text = { TimeInput(state = timePickerState) }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            val currentTime = System.currentTimeMillis()
            val visibleMessages = messages.filter { 
                it.isMe || it.scheduledTime == null || it.scheduledTime!! <= currentTime 
            }
            
            items(visibleMessages) { message -> 
                ChatBubble(message) 
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val isMe = message.isMe
    val bubbleColor = if (isMe) Color(0xFF002B2B) else SurfaceGray
    val neonEdge = if (isMe) NeonCyan else NeonMagenta
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .border(0.5.dp, neonEdge.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.scheduledTime != null && message.isMe && message.scheduledTime!! > System.currentTimeMillis()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(12.dp), tint = NeonMagenta)
                        Text(" Запланировано", fontSize = 10.sp, color = NeonMagenta)
                    }
                }

                when (message.type) {
                    MessageType.TEXT -> Text(message.text, color = Color.White)
                    MessageType.IMAGE -> AsyncImage(
                        model = message.fileUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    MessageType.AUDIO -> AudioPlayerView(message.fileUrl ?: "", message.durationSeconds)
                    MessageType.FILE -> FileP2PView(message.fileName ?: "Файл")
                    else -> Text("Системное сообщение: ${message.type}", color = Color.Gray, fontSize = 12.sp)
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

@Composable
fun FileP2PView(fileName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.FileDownload, null, tint = NeonCyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(fileName, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun ChatInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: (Uri) -> Unit,
    onScheduleClick: () -> Unit,
    onSendAudio: (Uri, Int) -> Unit,
    onStartCall: () -> Unit
) {
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onAttachFile(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.release()
            recorder = null
        }
    }

    Surface(color = Color.Black, modifier = Modifier.imePadding()) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { filePicker.launch("*/*") }) { 
                Icon(Icons.Default.AttachFile, null, tint = NeonCyan) 
            }
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение...", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceGray,
                    unfocusedContainerColor = SurfaceGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = NeonCyan,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(onClick = {
                if (!recording) {
                    try {
                        val file = File(context.cacheDir, "p2p_voice_${System.currentTimeMillis()}.m4a")
                        audioFile = file
                        
                        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            MediaRecorder(context)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaRecorder()
                        }.apply {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setOutputFile(file.absolutePath)
                            prepare()
                            start()
                        }
                        recorder = newRecorder
                        recording = true
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ошибка записи: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    try {
                        recorder?.apply {
                            stop()
                            release()
                        }
                        recorder = null
                        recording = false
                        audioFile?.let { onSendAudio(Uri.fromFile(it), 0) }
                    } catch (e: Exception) {
                        recording = false
                    }
                }
            }) {
                Icon(
                    imageVector = if (recording) Icons.Default.Stop else Icons.Default.Mic, 
                    contentDescription = null, 
                    tint = if (recording) Color.Red else NeonCyan
                )
            }
            IconButton(onClick = onStartCall) { Icon(Icons.Default.VideoCall, null, tint = NeonCyan) }
            IconButton(onClick = onScheduleClick) { Icon(Icons.Outlined.Schedule, null, tint = NeonMagenta) }
            IconButton(onClick = onSend) { Icon(Icons.Default.Send, null, tint = NeonCyan) }
        }
    }
}

@Composable
fun AudioPlayerView(url: String, duration: Int) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { 
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
            } else {
                try {
                    if (mediaPlayer == null) {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(context, Uri.parse(url))
                            prepare()
                            setOnCompletionListener { isPlaying = false }
                        }
                    }
                    mediaPlayer?.start()
                    isPlaying = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
                }
            }
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                contentDescription = null, 
                tint = NeonCyan
            )
        }
        Text(
            text = if (duration > 0) "$duration сек" else "Голосовое", 
            color = Color.White, 
            fontSize = 12.sp
        )
    }
}

