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
import com.kakdela.p2p.ui.call.CallActivity // Убедитесь, что этот файл существует
import java.text.SimpleDateFormat
import java.util.*

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
                title = { Text("ЧАТ: $chatId", color = NeonCyan, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
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
                    // Важно: Проверьте, добавлен ли CallActivity в AndroidManifest.xml
                    try {
                        val intent = Intent(context, CallActivity::class.java).apply {
                            putExtra("targetId", chatId)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Модуль звонков не найден", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        showDatePicker = false
                        showTimePicker = true
                    }) { Text("ДАЛЕЕ", color = NeonCyan) }
                }
            ) { DatePicker(state = datePickerState) }
        }

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
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { message -> ChatBubble(message) }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val isMe = message.isMe
    val bubbleColor = if (isMe) Color(0xFF002B2B) else SurfaceGray
    val neonEdge = if (isMe) NeonCyan else NeonMagenta
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .border(0.5.dp, neonEdge.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.type) {
                    MessageType.TEXT -> Text(message.text, color = Color.White)
                    MessageType.IMAGE -> AsyncImage(
                        model = message.fileUrl,
                        contentDescription = null,
                        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp))
                    )
                    MessageType.AUDIO -> AudioPlayerView(message.fileUrl ?: "", message.durationSeconds)
                    MessageType.FILE -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { }
                    ) {
                        // ИСПРАВЛЕНО: size передается через Modifier
                        Icon(
                            imageVector = Icons.Default.AttachFile, 
                            contentDescription = null, 
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp) 
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(message.fileName ?: "Файл", color = Color.White, fontSize = 14.sp)
                    }
                    else -> Text("Служебное сообщение", color = Color.Gray, fontSize = 12.sp)
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
fun AudioPlayerView(url: String, duration: Int) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { android.media.MediaPlayer() }

    DisposableEffect(url) { onDispose { mediaPlayer.release() } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            try {
                if (isPlaying) mediaPlayer.pause()
                else {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(url)
                    mediaPlayer.prepare()
                    mediaPlayer.start()
                }
                isPlaying = !isPlaying
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка аудио", Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = NeonCyan)
        }
        Text("$duration сек.", color = Color.White, fontSize = 12.sp)
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

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onAttachFile) }

    Surface(color = Color.Black, modifier = Modifier.imePadding()) {
        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { filePicker.launch("*/*") }) {
                Icon(Icons.Default.AttachFile, null, tint = NeonCyan)
            }
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceGray, unfocusedContainerColor = SurfaceGray, focusedTextColor = Color.White)
            )
            IconButton(onClick = {
                if (!recording) {
                    audioFile = java.io.File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setOutputFile(audioFile!!.absolutePath)
                    recorder.prepare()
                    recorder.start()
                    recording = true
                } else {
                    recorder.stop()
                    recorder.reset()
                    recording = false
                    audioFile?.let { onSendAudio(Uri.fromFile(it), 5) }
                }
            }) {
                Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null, tint = if (recording) Color.Red else NeonCyan)
            }
            IconButton(onClick = onStartCall) { Icon(Icons.Default.VideoCall, null, tint = NeonCyan) }
            IconButton(onClick = onScheduleClick) { Icon(Icons.Outlined.Schedule, null, tint = NeonMagenta) }
            IconButton(onClick = onSend) { Icon(Icons.Default.Send, null, tint = NeonCyan) }
        }
    }
}

