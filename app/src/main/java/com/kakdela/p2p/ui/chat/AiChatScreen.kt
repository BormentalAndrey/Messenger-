package com.kakdela.p2p.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight

private val NeonGreen = Color(0xFF00FFB3)
private val NeonPink = Color(0xFFFF00FF)
private val DarkBg = Color(0xFF0A0A0A)
private val SurfaceGray = Color(0xFF1A1A1A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen() {
    val vm: AiChatViewModel = viewModel()
    var input by remember { mutableStateOf("") }

    // File picker
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { vm.sendFile(it) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AI-Помощник", color = NeonGreen, fontWeight = FontWeight.Bold) },
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
            }

            // Input + кнопки отправки
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg)
                    .padding(8.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Прикрепить файл", tint = NeonPink)
                }

                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    placeholder = { Text("Введите сообщение…", color = Color.Gray) },
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = SurfaceGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonGreen,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(25.dp),
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
    val bubbleColor = if (isUser) Color(0xFF003D2B) else SurfaceGray
    val baseNeon = if (isUser) NeonGreen else NeonPink
    val alignment = if (isUser) Alignment.End else Alignment.Start

    // Анимация свечения
    val infiniteTransition = rememberInfiniteTransition()
    val neonGlow by infiniteTransition.animateColor(
        initialValue = baseNeon.copy(alpha = 0.5f),
        targetValue = baseNeon.copy(alpha = 1f),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        // Размытое свечение
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(neonGlow.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .blur(radius = 16.dp)
        )

        // Основной пузырёк
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = neonGlow, spotColor = neonGlow)
                .border(width = 2.dp, color = neonGlow, shape = RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Text(
                text = msg.text,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}
