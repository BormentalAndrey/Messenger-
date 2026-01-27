package com.kakdela.p2p.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakdela.p2p.model.ChatMessage
import com.kakdela.p2p.viewmodel.AiChatViewModel

private val NeonGreen = Color(0xFF00FFB3)
private val NeonPink = Color(0xFFFF00FF)
private val DarkBg = Color(0xFF0A0A0A)
private val SurfaceColor = Color(0xFF1A1A1A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(vm: AiChatViewModel = viewModel()) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Логика автопрокрутки при новых сообщениях
    LaunchedEffect(vm.messages.size, vm.isTyping.value) {
        if (vm.messages.isNotEmpty()) {
            listState.animateScrollToItem(vm.messages.size)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { vm.sendMessage("Файл прикреплен: ${it.lastPathSegment}") }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("KAKDELA AI", color = NeonGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Список сообщений
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(vm.messages, key = { it.id }) { msg ->
                    AiChatBubble(msg)
                }

                if (vm.isTyping.value) {
                    item { TypingIndicator() }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Блок загрузки или поле ввода
            if (!vm.modelReady.value) {
                ModelDownloadCard(
                    isDownloading = vm.isDownloading.value,
                    progress = vm.downloadProgress.intValue,
                    onDownloadClick = { vm.downloadModel() }
                )
            } else {
                AiInputArea(
                    input = input,
                    onValueChange = { input = it },
                    onFileClick = { filePickerLauncher.launch("*/*") },
                    onSendClick = {
                        if (input.isNotBlank()) {
                            vm.sendMessage(input)
                            input = ""
                            keyboardController?.hide()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ModelDownloadCard(
    isDownloading: Boolean,
    progress: Int,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = RoundedCornerShape(16.dp),
        // ИСПРАВЛЕНО: Для Card используется BorderStroke, а не Modifier.border
        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isDownloading) {
                Text("Загрузка нейросети...", color = Color.White)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = NeonGreen,
                    trackColor = Color.Gray.copy(alpha = 0.3f),
                )
                Spacer(Modifier.height(8.dp))
                Text("$progress%", color = NeonGreen)
            } else {
                Text(
                    text = "Локальная модель не найдена",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDownloadClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Скачать Phi-3 (2.4 GB)", color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun AiInputArea(
    input: String,
    onValueChange: (String) -> Unit,
    onFileClick: () -> Unit,
    onSendClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowColor by infiniteTransition.animateColor(
        initialValue = NeonGreen.copy(alpha = 0.4f),
        targetValue = NeonGreen,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "glow_anim"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onFileClick) {
            Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = NeonPink)
        }

        TextField(
            value = input,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .border(1.dp, glowColor, RoundedCornerShape(24.dp))
                .heightIn(min = 50.dp, max = 120.dp),
            placeholder = { Text("Спроси ИИ...", color = Color.Gray) },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceColor,
                unfocusedContainerColor = SurfaceColor,
                cursorColor = NeonGreen,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(Modifier.width(8.dp))

        FloatingActionButton(
            onClick = onSendClick,
            containerColor = NeonGreen,
            contentColor = Color.Black,
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.Send, contentDescription = "Send")
        }
    }
}

@Composable
fun AiChatBubble(msg: ChatMessage) {
    val isMine = msg.isMine
    val themeColor = if (isMine) NeonGreen else NeonPink
    val align = if (isMine) Alignment.End else Alignment.Start
    val containerColor = if (isMine) themeColor.copy(alpha = 0.15f) else SurfaceColor
    
    // Формируем форму пузырька в зависимости от отправителя
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(containerColor)
                .border(1.dp, themeColor.copy(alpha = 0.3f), bubbleShape)
                .padding(12.dp)
        ) {
            Text(
                text = msg.text,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = NeonPink
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Анализ данных...",
            color = NeonPink.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}
