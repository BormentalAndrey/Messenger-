package com.kakdela.p2p.ui

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.data.MessageType
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
    onSendFile: (Uri, MessageType) -> Unit,
    onSendAudio: (Uri, Int) -> Unit,
    onScheduleMessage: (String, Long) -> Unit
) {
    var textState by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()

    fun scheduleWithSelectedTime() {
        val selectedDateMillis = datePickerState.selectedDateMillis
        if (selectedDateMillis != null && textState.isNotBlank()) {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selectedDateMillis
            calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
            calendar.set(Calendar.MINUTE, timePickerState.minute)
            onScheduleMessage(textState, calendar.timeInMillis)
            textState = ""
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "ЧАТ: $chatId",
                        color = NeonCyan,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black),
                modifier = Modifier.shadow(8.dp, spotColor = NeonCyan)
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
                onAttachFile = { uri ->
                    onSendFile(uri, MessageType.FILE)
                },
                onScheduleClick = {
                    if (textState.isNotBlank()) showDatePicker = true
                },
                onSendAudio = { uri, duration ->
                    onSendAudio(uri, duration)
                }
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->

        // Диалоги выбора даты и времени
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        showDatePicker = false
                        showTimePicker = true
                    }) { Text("Далее", color = NeonCyan) }
                },
                colors = DatePickerDefaults.colors(
                    containerColor = SurfaceGray,
                    titleContentColor = NeonCyan,
                    headlineContentColor = Color.White,
                    dayContentColor = Color.White,
                    selectedDayContainerColor = NeonCyan,
                    selectedDayContentColor = Color.Black
                )
            ) { DatePicker(state = datePickerState) }
        }

        if (showTimePicker) {
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                containerColor = SurfaceGray,
                confirmButton = {
                    TextButton(onClick = {
                        showTimePicker = false
                        scheduleWithSelectedTime()
                    }) { Text("Запланировать", color = NeonCyan) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text("Отмена", color = Color.Gray) }
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Выберите время", color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
                        TimeInput(
                            state = timePickerState,
                            colors = TimePickerDefaults.colors(
                                timeSelectorSelectedContainerColor = NeonMagenta.copy(alpha = 0.2f),
                                timeSelectorUnselectedContainerColor = Color.Black,
                                timeSelectorSelectedContentColor = NeonMagenta,
                                timeSelectorUnselectedContentColor = Color.White
                            )
                        )
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            reverseLayout = false
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val isMe = message.senderId == Firebase.auth.currentUser?.uid
    val neonColor = if (isMe) NeonCyan else NeonMagenta
    val boxAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val columnAlignment = if (isMe) Alignment.End else Alignment.Start

    val bubbleShape = if (isMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = boxAlignment
    ) {
        Column(horizontalAlignment = columnAlignment) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .border(1.dp, neonColor.copy(alpha = 0.5f), bubbleShape)
                    .shadow(elevation = 10.dp, shape = bubbleShape, spotColor = neonColor),
                color = if (isMe) Color.Black else SurfaceGray,
                shape = bubbleShape
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    when (message.type) {
                        MessageType.TEXT -> Text(message.text, color = Color.White)
                        MessageType.IMAGE -> AsyncImage(
                            model = message.fileUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        MessageType.AUDIO -> AudioPlayerView(url = message.fileUrl ?: "", duration = message.durationSeconds)
                        MessageType.FILE -> Row(
                            modifier = Modifier
                                .clickable { /* TODO: Скачать файл */ }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(message.fileName ?: "Файл", color = Color.White)
                        }
                        else -> Text("Неподдерживаемый формат", color = Color.Red)
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
                if (isPlaying) {
                    mediaPlayer.pause()
                    isPlaying = false
                } else {
                    if (!mediaPlayer.isPlaying) {
                        mediaPlayer.reset()
                        mediaPlayer.setDataSource(url)
                        mediaPlayer.prepareAsync()
                        mediaPlayer.setOnPreparedListener { it.start() }
                        mediaPlayer.setOnCompletionListener { isPlaying = false }
                    } else mediaPlayer.start()
                    isPlaying = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White
            )
        }
        LinearProgressIndicator(progress = 0.5f, modifier = Modifier.width(100.dp))
        Spacer(Modifier.width(8.dp))
        Text("$duration с.", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun ChatInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: (Uri) -> Unit,
    onScheduleClick: () -> Unit,
    onSendAudio: (Uri, Int) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    val recorder = remember { MediaRecorder() }
    var outputFile: java.io.File? by remember { mutableStateOf(null) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onAttachFile(it) }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        color = Color.Black,
        tonalElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка выбора файла
            IconButton(onClick = { fileLauncher.launch("*/*") }) {
                Icon(Icons.Filled.AttachFile, "Attach", tint = NeonCyan)
            }

            // Текстовое поле
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                placeholder = { Text("Сообщение...", color = Color.DarkGray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceGray,
                    unfocusedContainerColor = SurfaceGray,
                    focusedIndicatorColor = NeonCyan,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            )

            // Кнопка записи аудио
            IconButton(onClick = {
                if (!isRecording) {
                    // Начать запись
                    outputFile = java.io.File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setOutputFile(outputFile!!.absolutePath)
                    recorder.prepare()
                    recorder.start()
                    isRecording = true
                } else {
                    // Остановить и отправить
                    try { recorder.stop() } catch (e: Exception) {}
                    recorder.reset()
                    isRecording = false
                    outputFile?.let { file ->
                        onSendAudio(Uri.fromFile(file), 10) // TODO: вычислить реальную длительность
                    }
                }
            }) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    tint = if (isRecording) Color.Red else NeonCyan,
                    contentDescription = "Mic"
                )
            }

            // Кнопка планирования
            IconButton(onClick = onScheduleClick) {
                Icon(Icons.Outlined.Schedule, "Schedule", tint = NeonMagenta)
            }

            // Кнопка отправки текста
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(NeonCyan, CircleShape)
                    .shadow(8.dp, CircleShape, spotColor = NeonCyan),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onSend) {
                    Icon(Icons.Filled.Send, "Send", tint = Color.Black)
                }
            }
        }
    }
}
