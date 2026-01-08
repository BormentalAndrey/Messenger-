package com.kakdela.p2p.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakdela.p2p.model.ChatMessage
import com.kakdela.p2p.viewmodel.AiChatViewModel

private val NeonGreen = Color(0xFF00FFB3)
private val NeonPink = Color(0xFFFF00FF)
private val DarkBg = Color(0xFF0A0A0A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(vm: AiChatViewModel = viewModel()) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Авто-скролл вниз при новых сообщениях
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
                title = { Text("AI-NEON", color = NeonGreen, fontSize = 20.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                
                items(vm.messages, key = { it.id }) { msg ->
                    AiChatBubble(msg)
                }

                if (vm.isTyping.value) {
                    item {
                        TypingIndicator()
                    }
                }
                
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Поле ввода
            AiInputArea(
                input = input,
                onValueChange = { input = it },
                onFileClick = { filePickerLauncher.launch("*/*") },
                onSendClick = {
                    if (input.isNotBlank()) {
                        vm.sendMessage(input)
                        input = ""
                    }
                }
            )
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
    val infiniteTransition = rememberInfiniteTransition(label = "inputGlow")
    val glowColor by infiniteTransition.animateColor(
        initialValue = NeonGreen.copy(alpha = 0.4f),
        targetValue = NeonGreen,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "glow"
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onFileClick) {
            Icon(Icons.Default.AttachFile, contentDescription = null, tint = NeonPink)
        }

        TextField(
            value = input,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .border(1.dp, glowColor, RoundedCornerShape(24.dp)),
            placeholder = { Text("Запрос в нейросеть...", color = Color.Gray) },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(0.05f),
                unfocusedContainerColor = Color.White.copy(0.05f),
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
            Icon(Icons.Default.Send, contentDescription = null)
        }
    }
}

@Composable
fun AiChatBubble(msg: ChatMessage) {
    val isMine = msg.isMine
    val themeColor = if (isMine) NeonGreen else NeonPink
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(if (isMine) 18.dp else 18.dp))
                .border(1.dp, themeColor.copy(0.5f), RoundedCornerShape(18.dp))
                .background(themeColor.copy(alpha = 0.1f))
                .padding(12.dp)
        ) {
            Text(text = msg.text, color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
fun TypingIndicator() {
    Text(
        "ИИ анализирует данные...",
        color = NeonPink.copy(0.7f),
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 8.dp)
    )
}
