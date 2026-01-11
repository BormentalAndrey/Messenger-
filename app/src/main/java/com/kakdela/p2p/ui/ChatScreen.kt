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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
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

/* ---------- COLORS ---------- */

private val Bg = Color(0xFF0A0C10)
private val Glass = Color(0xFF1C212B).copy(alpha = 0.75f)
private val NeonCyan = Color(0xFF45F3FF)
private val NeonPurple = Color(0xFF9F7CFF)
private val NeonGreen = Color(0xFF35FFB0)

/* ---------- SCREEN ---------- */

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
    var text by remember { mutableStateOf("") }

    val contactName by rememberContactName(chatPartnerId)
    val avatar by rememberContactAvatar(chatPartnerId)

    val title = contactName.takeIf { it.isNotBlank() } ?: "ID ${chatPartnerId.take(8)}"

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { it?.let { uri -> onSendFile(uri, getFileName(context, uri)) } }

    Scaffold(
        containerColor = Bg,
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Glass
                ),
                modifier = Modifier.blur(12.dp),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = NeonCyan)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedAvatar(avatar)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, color = NeonCyan, fontWeight = FontWeight.SemiBold)
                            Text("Secure P2P • E2EE", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                },
                actions = {
                    IconButton { startCall(context, chatPartnerId, false) } {
                        Icon(Icons.Default.Call, null, tint = NeonCyan)
                    }
                    IconButton { startCall(context, chatPartnerId, true) } {
                        Icon(Icons.Outlined.Videocam, null, tint = NeonCyan)
                    }
                }
            )
        },
        bottomBar = {
            ChatInputArea(
                text = text,
                onTextChange = { text = it },
                onSend = { onSendMessage(text); text = "" },
                onAttachFile = { filePicker.launch("*/*") },
                onSendAudio = onSendAudio,
                onScheduleMessage = { time ->
                    onScheduleMessage(text, time)
                    text = ""
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.messageId }) { msg ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically { it / 3 }
                ) {
                    ChatBubble(msg)
                }
            }
        }
    }
}

/* ---------- INPUT ---------- */

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

    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableLongStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(recording) {
        if (recording) while (recording) { delay(1000); seconds++ }
    }

    fun schedule() {
        DatePickerDialog(context, { _, y, m, d ->
            calendar.set(y, m, d)
            TimePickerDialog(context, { _, h, min ->
                calendar.set(Calendar.HOUR_OF_DAY, h)
                calendar.set(Calendar.MINUTE, min)
                if (calendar.timeInMillis > System.currentTimeMillis())
                    onScheduleMessage(calendar.timeInMillis)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttachFile) {
            Icon(Icons.Default.Add, null, tint = NeonPurple)
        }

        Box(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Glass)
                .border(1.dp, NeonGreen.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp)
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Message…", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                trailingIcon = {
                    if (text.isNotBlank())
                        IconButton(onClick = ::schedule) {
                            Icon(Icons.Outlined.Schedule, null, tint = Color.Gray)
                        }
                }
            )
        }

        Spacer(Modifier.width(8.dp))

        FloatingActionButton(
            onClick = {
                when {
                    text.isNotBlank() -> onSend()
                    !recording -> {
                        val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
                        audioFile = file
                        recorder = MediaRecorder(context).apply {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setOutputFile(file.absolutePath)
                            prepare(); start()
                        }
                        recording = true
                    }
                    else -> {
                        recorder?.stop(); recorder?.release()
                        audioFile?.let { onSendAudio(Uri.fromFile(it), seconds.toInt()) }
                        recording = false; seconds = 0
                    }
                }
            },
            containerColor = if (recording) Color.Red else NeonGreen,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                if (text.isNotBlank()) Icons.Default.Send
                else if (recording) Icons.Default.Stop
                else Icons.Default.Mic,
                null,
                tint = Color.Black
            )
        }
    }
}

/* ---------- MESSAGE ---------- */

@Composable
fun ChatBubble(message: MessageEntity) {
    val isMe = message.isMe
    val bg = if (isMe) NeonCyan.copy(alpha = 0.12f) else NeonPurple.copy(alpha = 0.12f)
    val border = if (isMe) NeonCyan else NeonPurple

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bg,
            border = BorderStroke(1.dp, border.copy(alpha = 0.3f)),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(message.text ?: "", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    fontSize = 9.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

/* ---------- AVATAR ---------- */

@Composable
fun AnimatedAvatar(uri: Uri?) {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.95f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = EaseInOut),
            RepeatMode.Reverse
        )
    )

    Surface(
        shape = CircleShape,
        modifier = Modifier.size(36.dp).scale(pulse),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
        color = Glass
    ) {
        if (uri != null)
            AsyncImage(uri, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else
            Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.padding(6.dp))
    }
}

/* ---------- UTILS ---------- */

fun startCall(context: Context, id: String, isVideo: Boolean) {
    context.startActivity(
        Intent(context, CallActivity::class.java)
            .putExtra("chatId", id)
            .putExtra("isVideo", isVideo)
    )
}

@SuppressLint("Range")
@Composable
fun rememberContactName(id: String): State<String> {
    val ctx = LocalContext.current
    val s = remember { mutableStateOf("") }
    LaunchedEffect(id) {
        ctx.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(id)),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { if (it.moveToFirst()) s.value = it.getString(0) ?: "" }
    }
    return s
}

@SuppressLint("Range")
@Composable
fun rememberContactAvatar(id: String): State<Uri?> {
    val ctx = LocalContext.current
    val s = remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(id) {
        ctx.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(id)),
            arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
            null, null, null
        )?.use { if (it.moveToFirst()) s.value = it.getString(0)?.let(Uri::parse) }
    }
    return s
}

fun getFileName(context: Context, uri: Uri): String {
    var name = "file_${System.currentTimeMillis()}"
    context.contentResolver.query(uri, null, null, null, null)?.use {
        val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && i != -1) name = it.getString(i)
    }
    return name
}
