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
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onSendFile(it, getFileName(context, it)) }
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBackground
                ),
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
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "P2P E2EE Connection",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(
                            Intent(context, CallActivity::class.java)
                                .putExtra("chatId", chatPartnerId)
                        )
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
                    onScheduleMessage = {
                        onScheduleMessage(textState, it)
                        textState = ""
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp)
        ) {
            items(messages, key = { it.id }) {
                ChatBubble(it)
            }
        }
    }
}

/* -------------------- ЖИВОЙ АВАТАР -------------------- */

@Composable
fun AnimatedAvatar(avatarUri: Uri?) {
    val infinite = rememberInfiniteTransition(label = "avatar")
    val scale by infinite.animateFloat(
        1f, 1.08f,
        infiniteRepeatable(tween(1400, easing = EaseInOut), RepeatMode.Reverse),
        label = "scale"
    )
    val glow by infinite.animateFloat(
        0.2f, 0.6f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "glow"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(48.dp)
                .scale(scale)
                .background(NeonCyan.copy(alpha = glow), CircleShape)
        )
        AsyncImage(
            model = avatarUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(42.dp).clip(CircleShape)
        )
    }
}

/* -------------------- CHAT BUBBLES -------------------- */

@Composable
fun ChatBubble(message: Message) {
    val isMe = message.isMe
    val color = if (isMe) Color(0xFF003D3D) else Color(0xFF262626)

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = color,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column {
                if (message.text.isNotBlank()) {
                    Text(
                        message.text,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(6.dp)
                )
            }
        }
    }
}

/* -------------------- CONTACT DATA -------------------- */

@SuppressLint("Range")
@Composable
fun rememberContactName(id: String): State<String> {
    val context = LocalContext.current
    val state = remember { mutableStateOf("Unknown") }

    LaunchedEffect(id) {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(id)
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use {
            if (it.moveToFirst()) {
                state.value = it.getString(0)
            }
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
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(id)
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
            null, null, null
        )?.use {
            if (it.moveToFirst()) {
                state.value = it.getString(0)?.let(Uri::parse)
            }
        }
    }
    return state
}

/* -------------------- UTILS -------------------- */

fun getFileName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1) return it.getString(idx)
        }
    }
    return "file_${System.currentTimeMillis()}"
}
