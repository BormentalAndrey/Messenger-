package com.kakdela.p2p.ui

import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.data.MessageType
import com.kakdela.p2p.ui.call.CallActivity
import java.text.SimpleDateFormat
import java.util.*

// Константы дизайна (Neon/Dark Theme)
private val NeonCyan = Color(0xFF00FFFF)
private val NeonMagenta = Color(0xFFFF00FF)
private val DarkBackground = Color(0xFF0A0A0A)
private val SurfaceGray = Color(0xFF1A1A1A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    messages: List<Message>,
    onSendMessage: (String) -> Unit,
    onSendFile: (Uri, MessageType) -> Unit = { _, _ -> },
    onSendAudio: (Uri, Int) -> Unit = { _, _ -> },
    onScheduleMessage: (String, Long) -> Unit
) {
    var textState by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    val context = LocalContext.current

    // Логика планирования сообщения
    fun scheduleMessage() {
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
                    Text(
                        "ЧАТ: $chatId",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black
                ),
                modifier = Modifier.shadow(4.dp)
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
                    context.startActivity(
                        Intent(context, CallActivity::class.java)
                            .putExtra("targetId", chatId)
                    )
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->

        // Выбор даты
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        showDatePicker = false
                        showTimePicker = true
                    }) { Text("ДАЛЕЕ", color = NeonCyan) }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // Выбор времени
        if (showTimePicker) {
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        showTimePicker = false
                        scheduleMessage()
                    }) { Text("ОК", color = NeonCyan) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text("ОТМЕНА") }
                },
                text = { TimeInput(state = timePickerState) }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            reverseLayout = false // Список идет сверху вниз (новые внизу)
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val isMe = message.isMe // Используем свойство из Message.kt
    val bubbleColor = if (isMe) Color(0xFF002B2B) else SurfaceGray
    val neonEdge = if (isMe) NeonCyan else NeonMagenta
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp, 
                topEnd = 16.dp, 
                bottomStart = if (isMe) 16.dp else 2.dp, 
                bottomEnd = if (isMe) 2.dp else 16.dp
            ),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .border(0.5.dp, neonEdge.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .shadow(4.dp, spotColor = neonEdge)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.type) {
                    MessageType.TEXT ->
                        Text(message.text, color = Color.White, fontSize = 15.sp)

                    MessageType.IMAGE ->
                        AsyncImage(
                            model = message.fileUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                    MessageType.AUDIO ->
                        AudioPlayerView(message.fileUrl ?: "", message.durationSeconds)

                    MessageType.FILE ->
                        Row(
                            modifier = Modifier
                                .background(Color.Black.copy(0.3f), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .clickable { /* Скачивание */ },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AttachFile, null, tint = NeonCyan, size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                message.fileName ?: "Document", 
                                color = Color.White, 
                                fontSize = 14.sp
                            )
                        }

                    else ->
                        Text("Service message", color = Color.Gray, fontSize = 12.sp)
                }

                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = Color.Gray.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AudioPlayerView(url: String, duration: Int) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { android.media.MediaPlayer() }

    DisposableEffect(url) {
        onDispose { mediaPlayer.release() }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        IconButton(
            onClick = {
                try {
                    if (isPlaying) {
                        mediaPlayer.pause()
                    } else {
                        mediaPlayer.reset()
                        mediaPlayer.setDataSource(url)
                        mediaPlayer.prepare()
                        mediaPlayer.start()
                    }
                    isPlaying = !isPlaying
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка аудио", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.size(32.dp).background(NeonCyan, CircleShape)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("$duration сек.", color = Color.White, fontSize = 13.sp)
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
    val recorder = remember { MediaRecorder() }
    var audioFile by remember { mutableStateOf<java.io.File?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(onAttachFile) }

    Surface(
        color = Color.Black,
        modifier = Modifier.imePadding().navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { filePicker.launch("*/*") }) {
                Icon(Icons.Default.AttachFile, "Attach", tint = NeonCyan)
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

            // Кнопка аудио/стоп
            IconButton(onClick = {
                if (!recording) {
                    audioFile = java.io.File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
                    recorder.apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(audioFile!!.absolutePath)
                        prepare()
                        start()
                    }
                    recording = true
                } else {
                    try { recorder.stop() } catch (e: Exception) {}
                    recorder.reset()
                    recording = false
                    audioFile?.let { onSendAudio(Uri.fromFile(it), 5) }
                }
            }) {
                Icon(
                    if (recording) Icons.Default.Stop else Icons.Default.Mic,
                    null,
                    tint = if (recording) Color.Red else NeonCyan
                )
            }

            IconButton(onClick = onStartCall) {
                Icon(Icons.Default.VideoCall, null, tint = NeonCyan)
            }

            IconButton(onClick = onScheduleClick) {
                Icon(Icons.Outlined.Schedule, null, tint = NeonMagenta)
            }

            FloatingActionButton(
                onClick = onSend,
                containerColor = NeonCyan,
                contentColor = Color.Black,
                modifier = Modifier.size(42.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Send, null)
            }
        }
    }
}

