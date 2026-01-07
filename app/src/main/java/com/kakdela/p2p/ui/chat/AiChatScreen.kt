package com.kakdela.p2p.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakdela.p2p.viewmodel.AiChatViewModel

private val NeonGreen = Color(0xFF00FFB3)
private val NeonPink = Color(0xFFFF00FF)
private val DarkBg = Color(0xFF0A0A0A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen() {
    val vm: AiChatViewModel = viewModel()
    var input by remember { mutableStateOf("") }

    // Файловый селектор
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            vm.sendMessage("Отправлен файл: ${it.lastPathSegment ?: "файл"}")
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AI-Помощник", color = NeonGreen, fontSize = 20.sp) },
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

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.messages) { msg ->
                    AiChatBubble(msg)
                }

                // Показываем "ИИ печатает" если идет обработка
                if (vm.isTyping.value) {
                    item {
                        AiChatBubble(ChatMessage(text = "ИИ печатает...", isUser = false))
                    }
                }
            }

            // Панель ввода сообщений
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .imePadding()
                    .background(Color.Transparent),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Прикрепить файл", tint = NeonPink)
                }

                // Пульсирующий бордер для поля ввода
                val infiniteTransition = rememberInfiniteTransition()
                val inputGlow by infiniteTransition.animateColor(
                    initialValue = NeonGreen.copy(alpha = 0.6f),
                    targetValue = NeonGreen.copy(alpha = 1f),
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .border(2.dp, inputGlow, RoundedCornerShape(26.dp))
                        .shadow(8.dp, RoundedCornerShape(26.dp), ambientColor = inputGlow),
                    placeholder = { Text("Введите сообщение…", color = NeonGreen.copy(alpha = 0.6f), fontSize = 15.sp) },
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.Transparent,
                        cursorColor = NeonGreen,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        textColor = Color.White
                    ),
                    shape = RoundedCornerShape(26.dp),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            vm.sendMessage(input)
                            input = ""
                        }
                    }
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Отправить", tint = NeonGreen)
                }
            }
        }
    }
}

@Composable
private fun AiChatBubble(msg: ChatMessage) {
    val isUser = msg.isUser
    val alignment: Alignment = if (isUser) Alignment.End else Alignment.Start
    val baseNeon = if (isUser) NeonGreen else NeonPink

    val infiniteTransition = rememberInfiniteTransition()
    val neonGlow by infiniteTransition.animateColor(
        initialValue = baseNeon.copy(alpha = 0.4f),
        targetValue = baseNeon.copy(alpha = 1f),
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .align(alignment)
                .widthIn(max = 300.dp)
        ) {
            // Размытое свечение позади пузырька
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(neonGlow.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .blur(radius = 20.dp)
            )

            // Основной пузырёк — прозрачный с неоновой рамкой
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, neonGlow, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Transparent)
                    .padding(16.dp)
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
}
