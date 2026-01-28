package com.kakdela.p2p.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakdela.p2p.model.ChatMessage
import com.kakdela.p2p.viewmodel.AiChatViewModel

private val NeonGreen = Color(0xFF00FFB3)
private val DarkBg = Color(0xFF0A0A0A)
private val SurfaceColor = Color(0xFF1A1A1A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(vm: AiChatViewModel = viewModel()) {
    var input by remember { mutableStateOf("") }
    
    // Получаем список только актуальных сообщений (Вопрос-Ответ)
    val messages = vm.displayMessages

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI Master", color = NeonGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        // Индикатор режима работы
                        Icon(
                            imageVector = if (vm.isOnline.value) Icons.Default.Cloud else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (vm.isOnline.value) NeonGreen else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            
            // Область чата (Single Response Mode)
            LazyColumn(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.Bottom // Прижимаем к низу
            ) {
                items(messages, key = { it.id }) { msg ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically()
                    ) {
                        AiChatBubble(msg)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (vm.isTyping.value) {
                    item { 
                        Text("Думаю...", color = NeonGreen, modifier = Modifier.padding(8.dp)) 
                    }
                }
            }

            // Зона загрузки модели (если нет интернета и модели нет)
            if (!vm.modelReady.value && !vm.isOnline.value) {
                ModelDownloadCard(
                    isDownloading = vm.isDownloading.value,
                    progress = vm.downloadProgress.intValue,
                    onDownload = { vm.downloadModel() }
                )
            } 
            // Поле ввода (активно всегда, если есть сеть ИЛИ модель)
            else if (vm.isOnline.value || vm.modelReady.value) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Спроси ИИ...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SurfaceColor,
                            unfocusedContainerColor = SurfaceColor,
                            focusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                vm.sendMessage(input)
                                input = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = NeonGreen)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun AiChatBubble(msg: ChatMessage) {
    val isMine = msg.isMine
    val align = if (isMine) Alignment.End else Alignment.Start
    val bg = if (isMine) NeonGreen.copy(alpha = 0.2f) else SurfaceColor
    
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(16.dp),
                color = Color.White
            )
        }
    }
}

@Composable
fun ModelDownloadCard(isDownloading: Boolean, progress: Int, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        border = BorderStroke(1.dp, NeonGreen),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Для работы офлайн нужна база знаний", color = Color.White)
            Spacer(Modifier.height(8.dp))
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = NeonGreen,
                )
                Text("$progress%", color = NeonGreen)
            } else {
                Button(onClick = onDownload, colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)) {
                    Icon(Icons.Default.Download, null, tint = Color.Black)
                    Text("Скачать Мозг (2.4 ГБ)", color = Color.Black)
                }
            }
        }
    }
}
