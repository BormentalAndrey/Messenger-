package com.kakdela.p2p.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

    // Авто-скролл
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Периодически обновляем статус (при входе)
    LaunchedEffect(Unit) {
        vm.refreshSystemStatus()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI Master", color = NeonGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        
                        // Иконки статуса
                        if (vm.isOnline.value) {
                             Icon(Icons.Default.Cloud, "Online", tint = NeonGreen, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        if (vm.isModelDownloaded.value) {
                             Icon(Icons.Default.Memory, "Local Ready", tint = Color.Yellow, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            
            // 1. СПИСОК СООБЩЕНИЙ
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(messages) { msg ->
                    AiChatBubble(msg)
                    Spacer(Modifier.height(8.dp))
                }

                if (vm.isTyping.value) {
                    item { 
                        Text("Печатает...", color = NeonGreen, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) 
                    }
                }
            }

            // 2. ЗОНА ЗАГРУЗКИ (Показываем, если идет загрузка ИЛИ если модели нет и интернета нет)
            // Если модели нет, но интернет есть - показываем кнопку "Скачать", но не блокируем чат
            
            val showDownloadCard = !vm.isModelDownloaded.value 
            
            if (showDownloadCard) {
                ModelDownloadCard(
                    isDownloading = vm.isDownloading.value,
                    progress = vm.downloadProgress.intValue,
                    hasInternet = vm.isOnline.value,
                    onDownload = { vm.downloadModel() }
                )
            }

            // 3. ПОЛЕ ВВОДА
            // Активно, если (Есть Интернет) ИЛИ (Есть Локальная Модель)
            val canChat = vm.isOnline.value || vm.isModelDownloaded.value

            if (canChat) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if(vm.isOnline.value) "Спроси Gemini..." else "Спроси Local AI...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SurfaceColor,
                            unfocusedContainerColor = SurfaceColor,
                            focusedTextColor = Color.White,
                            cursorColor = NeonGreen
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                vm.sendMessage(input)
                                input = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = NeonGreen,
                            disabledContainerColor = Color.Gray
                        ),
                        enabled = !vm.isTyping.value
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                    }
                }
            } else {
                // Если чатиться нельзя (нет сети и нет модели)
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Нет сети. Скачайте модель для офлайн работы.", color = Color.Gray)
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
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isMine) 16.dp else 2.dp, bottomEnd = if (isMine) 2.dp else 16.dp)
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
fun ModelDownloadCard(isDownloading: Boolean, progress: Int, hasInternet: Boolean, onDownload: () -> Unit) {
    // Не показываем кнопку, если интернета нет и загрузка не идет (скачать всё равно нельзя)
    if (!hasInternet && !isDownloading) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        border = BorderStroke(1.dp, if(isDownloading) NeonGreen else Color.Gray),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if(isDownloading) "Загрузка мозга..." else "Доступен Офлайн Режим", color = NeonGreen, fontWeight = FontWeight.Bold)
                    if (!isDownloading) {
                        Text("Скачайте модель (2.3 ГБ) для работы без интернета.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                
                if (!isDownloading) {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, "Download", tint = NeonGreen)
                    }
                }
            }
            
            if (isDownloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = NeonGreen,
                    trackColor = Color.DarkGray,
                )
                Text("$progress%", color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
