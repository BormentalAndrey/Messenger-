package com.kakdela.p2p.ui

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.ui.navigation.Routes

/**
 * Вспомогательная функция для получения имени из телефонной книги.
 */
@SuppressLint("Range")
@Composable
fun rememberContactName(phoneNumber: String): String {
    val context = LocalContext.current
    // По умолчанию показываем номер, если имя не найдено
    var displayName by remember(phoneNumber) { mutableStateOf(phoneNumber) }

    LaunchedEffect(phoneNumber) {
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, 
                Uri.encode(phoneNumber)
            )
            val cursor = contentResolver.query(
                uri, 
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), 
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    displayName = it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        } catch (e: Exception) {
            // Если нет разрешений или контакт не найден, остается номер
        }
    }
    return displayName
}

@Composable
fun ChatListItem(chat: ChatDisplay, onClick: () -> Unit) {
    // ПОЛУЧАЕМ ИМЯ ИЗ ТЕЛЕФОННОЙ КНИГИ
    val contactName = rememberContactName(chat.title)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current 
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватарка собеседника
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF00FFFF).copy(alpha = 0.2f)) // Неоновый стиль
                .border(1.dp, Color(0xFF00FFFF).copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contactName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF00FFFF)
            )
        }

        Spacer(Modifier.width(16.dp))

        // Блок текста
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contactName, // Отображаем имя контакта
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = chat.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = chat.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    navController: NavHostController,
    identityRepository: IdentityRepository
) {
    val vm: ChatsListViewModel = viewModel()
    val chats by vm.chats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Как дела?",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color(0xFF00FFFF) // Неоновый голубой
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.CONTACTS) },
                containerColor = Color(0xFF00FFFF),
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Начать новый чат")
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (chats.isEmpty()) {
                EmptyChatsView(onFindContacts = { navController.navigate(Routes.CONTACTS) })
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = chats,
                        key = { it.id }
                    ) { chat ->
                        ChatListItem(chat) {
                            navController.navigate("chat/${chat.id}")
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 72.dp),
                            thickness = 0.5.dp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyChatsView(onFindContacts: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Пока нет активных чатов", color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onFindContacts,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Text("Найти контакты", color = Color(0xFF00FFFF))
            }
        }
    }
}
