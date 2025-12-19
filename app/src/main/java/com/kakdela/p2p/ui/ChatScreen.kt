package com.kakdela.p2p.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakdela.p2p.model.ChatMessage
import java.util.Calendar

// Константы неоновых цветов
private val NeonCyan = Color(0xFF00FFFF)
private val NeonMagenta = Color(0xFFFF00FF)
private val DarkBackground = Color(0xFF0A0A0A)
private val SurfaceGray = Color(0xFF1A1A1A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onScheduleMessage: (String, Long) -> Unit
) {
    var textState by remember { mutableStateOf("") }

    // --- Состояния для диалогов выбора даты и времени ---
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    
    // Вспомогательная функция для обработки полного времени
    fun scheduleWithSelectedTime() {
        val selectedDateMillis = datePickerState.selectedDateMillis
        if (selectedDateMillis != null && textState.isNotBlank()) {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selectedDateMillis
            calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
            calendar.set(Calendar.MINUTE, timePickerState.minute)
            
            // Если время уже прошло, добавляем 1 день (опционально, или просто отправляем как есть)
            // Здесь отправляем именно на выбранное время
            onScheduleMessage(textState, calendar.timeInMillis)
            textState = ""
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "ЧАТ: $chatId",
                        color = NeonCyan,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black
                ),
                modifier = Modifier.shadow(8.dp, spotColor = NeonCyan)
            )
        },
        bottomBar = {
            ChatInputField(
                text = textState,
                onTextChange = { textState = it },
                onSend = {
                    if (textState.isNotBlank()) {
                        onSendMessage(textState)
                        textState = ""
                    }
                },
                onAttachFile = { uri ->
                    // Отправляем URI файла как сообщение (или здесь можно добавить логику загрузки)
                    onSendMessage("Файл: $uri")
                },
                onScheduleClick = {
                    if (textState.isNotBlank()) {
                        showDatePicker = true
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        
        // --- Диалоги выбора даты и времени ---
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        showDatePicker = false
                        showTimePicker = true // После даты открываем время
                    }) {
                        Text("Далее", color = NeonCyan)
                    }
                },
                colors = DatePickerDefaults.colors(
                    containerColor = SurfaceGray,
                    titleContentColor = NeonCyan,
                    headlineContentColor = Color.White,
                    dayContentColor = Color.White,
                    selectedDayContainerColor = NeonCyan,
                    selectedDayContentColor = Color.Black
                )
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (showTimePicker) {
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                containerColor = SurfaceGray,
                confirmButton = {
                    TextButton(onClick = {
                        showTimePicker = false
                        scheduleWithSelectedTime()
                    }) {
                        Text("Запланировать", color = NeonCyan)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text("Отмена", color = Color.Gray)
                    }
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Выберите время", color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
                        TimeInput(
                            state = timePickerState,
                            colors = TimePickerDefaults.colors(
                                timeSelectorSelectedContainerColor = NeonMagenta.copy(alpha = 0.2f),
                                timeSelectorUnselectedContainerColor = Color.Black,
                                timeSelectorSelectedContentColor = NeonMagenta,
                                timeSelectorUnselectedContentColor = Color.White
                            )
                        )
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            reverseLayout = false
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isMe = message.isMine
    val neonColor = if (isMe) NeonCyan else NeonMagenta
    
    val boxAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val columnAlignment = if (isMe) Alignment.End else Alignment.Start

    val bubbleShape: Shape = if (isMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = boxAlignment
    ) {
        Column(horizontalAlignment = columnAlignment) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .border(1.dp, neonColor.copy(alpha = 0.5f), bubbleShape)
                    .shadow(elevation = 10.dp, shape = bubbleShape, spotColor = neonColor),
                color = if (isMe) Color.Black else SurfaceGray,
                shape = bubbleShape
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                    
                    Text(
                        text = "12:00", 
                        color = neonColor.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: (Uri) -> Unit,
    onScheduleClick: () -> Unit
) {
    // Лаунчер для выбора файлов из системы
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onAttachFile(it) }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        color = Color.Black,
        tonalElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка скрепки: открывает выбор файла
            IconButton(onClick = { launcher.launch("*/*") }) {
                Icon(Icons.Outlined.AttachFile, "Attach", tint = NeonCyan)
            }

            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                placeholder = { Text("Сообщение...", color = Color.DarkGray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceGray,
                    unfocusedContainerColor = SurfaceGray,
                    focusedIndicatorColor = NeonCyan,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            )

            // Кнопка отложенной отправки: вызывает колбэк для открытия календаря
            IconButton(onClick = onScheduleClick) {
                Icon(Icons.Outlined.Schedule, "Schedule", tint = NeonMagenta)
            }

            // Кнопка отправки
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(NeonCyan, CircleShape)
                    .shadow(8.dp, CircleShape, spotColor = NeonCyan),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onSend) {
                    Icon(Icons.Filled.Send, "Send", tint = Color.Black)
                }
            }
        }
    }
}

