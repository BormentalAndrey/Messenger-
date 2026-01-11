package com.kakdela.p2p.ui.chat

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
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
import java.text.SimpleDateFormat
import java.util.*

private val NeonCyan = Color(0xFF00FFFF)
private val NeonGreen = Color(0xFF00FF9D)
private val NeonPurple = Color(0xFFB042FF)
private val DarkBackground = Color(0xFF0A0A0A)
private val SurfaceGray = Color(0xFF1E1E1E)

@OptIn(ExperimentalMaterial3Api::class)
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
    val displayName = if (contactName == chatPartnerId.take(10)) "ID: ${chatPartnerId.take(8)}" else contactName
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
                    IconButton(onClick = { startCall(context, chatPartnerId, isVideo = true) }) {
                        Icon(Icons.Outlined.Videocam, "Video Call", tint = NeonCyan)
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
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.messageId }) { message ->
                ChatBubble(message)
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
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachFile) {
                Icon(Icons.Default.Add, "Attach", tint = NeonPurple, modifier = Modifier.rotate(45f))
            }

            Box(modifier = Modifier.weight(1f)) {
                if (isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(SurfaceGray, RoundedCornerShape(28.dp))
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
                    // Поле ввода с неоновым свечением (Glow Effect)
                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        placeholder = { Text("Запрос в нейросеть...", color = Color.Gray, fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(28.dp), ambientColor = NeonGreen, spotColor = NeonGreen)
                            .border(1.5.dp, NeonGreen, RoundedCornerShape(28.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(28.dp),
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
                        val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
                        audioFile = file
                        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()).apply {
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
fun ChatBubble(message: MessageEntity) {
    val isMe = message.isMe
    val bubbleColor = if (isMe) Color(0xFF003D3D) else Color(0xFF2D1442)
    val borderColor = if (isMe) NeonCyan.copy(alpha = 0.6f) else NeonPurple.copy(alpha = 0.6f)
    val alignment = if (isMe) Alignment.End else Alignment.Start
    
    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(horizontal = 4.dp)
                .shadow(
                    elevation = 6.dp, 
                    shape = RoundedCornerShape(18.dp), 
                    ambientColor = borderColor, 
                    spotColor = borderColor
                )
        ) {
            Column(Modifier.padding(14.dp)) {
                val content = message.text ?: ""
                when {
                    content.startsWith("AUDIO:") -> AudioPlayerBubble(content)
                    content.startsWith("FILE:") -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilePresent, null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(content.removePrefix("FILE: "), color = Color.White, fontSize = 14.sp)
                        }
                    }
                    else -> {
                        Text(text = content, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
                    }
                }
                
                if (message.scheduledTime != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Outlined.Schedule, null, tint = NeonCyan, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Запланировано: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.scheduledTime!!))}",
                            fontSize = 10.sp, color = NeonCyan
                        )
                    }
                }

                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = Color.Gray.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AudioPlayerBubble(audioData: String) {
    var isPlaying by remember { mutableStateOf(false) }
    val duration = audioData.substringAfter("AUDIO: ").substringBefore(" s")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
    ) {
        IconButton(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier.size(36.dp).background(NeonCyan, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = if (isPlaying) 0.5f else 0f,
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = NeonCyan,
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(4.dp))
            Text("$duration сек", fontSize = 11.sp, color = NeonCyan.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun AnimatedAvatar(avatarUri: Uri?) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(42.dp).scale(scale).background(NeonCyan.copy(alpha = 0.1f), CircleShape))
        
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = SurfaceGray,
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
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
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}

@SuppressLint("Range")
@Composable
fun rememberContactName(id: String): State<String> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(id.take(10)) }
    LaunchedEffect(id) {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(id))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) state.value = it.getString(0)
            }
        } catch (e: Exception) {}
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
                if (it.moveToFirst()) state.value = it.getString(0)?.let(Uri::parse)
            }
        } catch (e: Exception) {}
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
    } catch (e: Exception) {}
    return name
}
