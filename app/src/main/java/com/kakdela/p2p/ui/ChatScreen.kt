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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Schedule
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
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.data.MessageType
import com.kakdela.p2p.ui.call.CallActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val NeonCyan = Color(0xFF00FFFF)
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
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var textState by remember { mutableStateOf("") }

    val contactName by rememberContactName(chatPartnerId)
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
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBackground),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = NeonCyan)
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedAvatar(avatarUri = contactAvatar)
                        Column {
                            Text(
                                text = contactName,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 16.sp
                            )
                            Text("P2P E2EE Connection", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(context, CallActivity::class.java).apply {
                            putExtra("chatId", chatPartnerId)
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Call, null, tint = NeonCyan)
                    }
                }
            )
        },
        bottomBar = {
            Box(Modifier.navigationBarsPadding()) {
                ChatInputArea(
                    text = textState,
                    onTextChange = { textState = it },
                    onSend = {
                        if (textState.isNotBlank()) {
                            onSendMessage(textState)
                            textState = ""
                        }
                    },
                    onAttachFile = { filePicker.launch("*/*") },
                    onSendAudio = onSendAudio,
                    onScheduleMessage = { time ->
                        onScheduleMessage(textState, time)
                        textState = ""
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(message)
            }
        }
    }
}

/* -------------------- КОМПОНЕНТЫ ВВОДА -------------------- */

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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        IconButton(onClick = onAttachFile) {
            Icon(Icons.Default.Add, null, tint = NeonCyan)
        }

        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Сообщение...", color = Color.Gray) },
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceGray,
                unfocusedContainerColor = SurfaceGray,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            trailingIcon = {
                IconButton(onClick = { onScheduleMessage(System.currentTimeMillis() + 3600000) }) {
                    Icon(Icons.Outlined.Schedule, null, tint = Color.Gray)
                }
            }
        )

        Spacer(Modifier.width(8.dp))

        FloatingActionButton(
            onClick = {
                if (text.isNotBlank()) onSend()
                else {
                    if (!recording) {
                        val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
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
                }
            },
            shape = CircleShape,
            containerColor = if (recording) Color.Red else NeonCyan,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                if (text.isNotBlank()) Icons.Default.Send else if (recording) Icons.Default.Stop else Icons.Default.Mic,
                null,
                tint = Color.Black
            )
        }
    }
}

/* -------------------- ВИЗУАЛИЗАЦИЯ СООБЩЕНИЙ -------------------- */

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

    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column {
                when (message.type) {
                    MessageType.IMAGE -> {
                        AsyncImage(
                            model = message.fileUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(200.dp).clip(shape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    MessageType.FILE -> {
                        FileView(message.fileName ?: "Document")
                    }
                    MessageType.AUDIO -> {
                        AudioView(message.fileUrl ?: "")
                    }
                    else -> {}
                }

                if (message.text.isNotBlank()) {
                    Text(
                        message.text,
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
fun FileView(name: String) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.FilePresent, null, tint = NeonCyan)
        Spacer(Modifier.width(8.dp))
        Text(name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AudioView(url: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            try {
                if (isPlaying) {
                    mediaPlayer.pause()
                    isPlaying = false
                } else {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(context, Uri.parse(url))
                    mediaPlayer.prepare()
                    mediaPlayer.start()
                    isPlaying = true
                    mediaPlayer.setOnCompletionListener { isPlaying = false }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = NeonCyan)
        }
        Text("Голосовое сообщение", color = Color.White, fontSize = 13.sp)
    }
}

/* -------------------- АВАТАР И КОНТАКТЫ -------------------- */

@Composable
fun AnimatedAvatar(avatarUri: Uri?) {
    val infinite = rememberInfiniteTransition(label = "avatar")
    val scale by infinite.animateFloat(
        1f, 1.05f,
        infiniteRepeatable(tween(1500, easing = EaseInOut), RepeatMode.Reverse),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp).scale(scale).background(NeonCyan.copy(alpha = 0.2f), CircleShape))
        AsyncImage(
            model = avatarUri ?: Icons.Default.Person, // Заглушка, если нет фото
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(38.dp).clip(CircleShape)
        )
    }
}

@SuppressLint("Range")
@Composable
fun rememberContactName(id: String): State<String> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(id.take(8)) }

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
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) return it.getString(idx)
            }
        }
    } catch (e: Exception) {}
    return "file_${System.currentTimeMillis()}"
}
