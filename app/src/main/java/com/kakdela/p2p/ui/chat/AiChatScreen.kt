package com.kakdela.p2p.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
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
    val messages = vm.displayMessages
    val listState = rememberLazyListState()

    // Авто-скролл вниз при новом сообщении
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Периодическая проверка статуса при открытии экрана
    LaunchedEffect(Unit) {
        vm.refreshSystemStatus()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Как дела? ИИ", color = NeonGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        
                        // Иконка статуса
                        if (vm.isOnline.value) {
                             Icon(Icons.Default.Cloud, "Online", tint = NeonGreen, modifier = Modifier.size(16.dp))
                        } else if (vm.isModelDownloaded.value) {
                             Icon(Icons.Default.Memory, "Offline Local", tint = Color.Yellow, modifier = Modifier.size(16.dp))
                        } else {
                             Icon(Icons.Default.CloudOff, "Offline No Brain", tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            
            // Список сообщений
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(16.dp),
            ) {
                items(messages) { msg ->
                    AiChatBubble(msg)
                    Spacer(Modifier.height(8.dp))
                }

                if (vm.isTyping.value) {
                    item { 
                        Text("Генерирую ответ...", color = NeonGreen, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) 
                    }
                }
            }

            // Условия отображения элементов управления
            val canChat = vm.isOnline.value || vm.isModelDownloaded.value
            
            if (!canChat) {
                // Если нет интернета И нет модели -> Предлагаем скачать (если бы интернет был, мы бы чатились)
                // Но скачать без интернета нельзя, поэтому показываем инфо.
                // А вот если интернета нет, но юзер нажал кнопку раньше и скачивание идет...
                
                if (vm.isDownloading.value) {
                     ModelDownloadCard(true, vm.downloadProgress.intValue) {}
                } else {
                    // Ситуация: Нет сети и нет модели. Тупик.
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                         Text("Нет интернета и базы знаний. Подключитесь к сети.", color = Color.Gray)
                    }
                }
            } else if (vm.isOnline.value && !vm.isModelDownloaded.value) {
                // Интернет есть, но модели нет. Можно чатиться, но предложим скачать на будущее.
                ModelDownloadCard(vm.isDownloading.value, vm.downloadProgress.intValue) {
                    vm.downloadModel()
                }
            }

            // Поле ввода (Если есть хоть какая-то возможность ответить)
            if (canChat) {
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
                            focusedTextColor = Color.White,
                            cursorColor = NeonGreen
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
                modifier = Modifier.padding(12.dp),
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ModelDownloadCard(isDownloading: Boolean, progress: Int, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Офлайн режим", color = NeonGreen, fontWeight = FontWeight.Bold)
                Text("Скачайте модель (2.3 ГБ), чтобы ИИ работал без интернета.", color = Color.Gray, fontSize = 12.sp)
            }
            
            if (isDownloading) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { progress / 100f }, color = NeonGreen, modifier = Modifier.size(40.dp))
                    Text("${progress}%", color = Color.White, fontSize = 10.sp)
                }
            } else {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, "Download", tint = NeonGreen)
                }
            }
        }
    }
}
