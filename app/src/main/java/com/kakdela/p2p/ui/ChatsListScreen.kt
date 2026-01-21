package com.kakdela.p2p.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.kakdela.p2p.ui.navigation.Routes

@SuppressLint("Range")
@Composable
fun rememberContactName(identifier: String): String {
    val context = LocalContext.current
    var displayName by remember(identifier) { mutableStateOf(identifier) }

    // Если это длинный хеш без цифр, сразу форматируем как ID
    if (identifier.length > 20 && !identifier.any { it.isDigit() } && !identifier.startsWith("+")) {
        return "ID: ${identifier.take(6)}..."
    }

    LaunchedEffect(identifier) {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(identifier)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        } catch (_: Exception) {}
    }
    return displayName
}

@Composable
fun AnimatedAvatar(
    letter: String,
    isActive: Boolean,
    isSms: Boolean,
    size: Dp = 52.dp
) {
    val transition = rememberInfiniteTransition(label = "avatar")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isActive && !isSms) 0.7f else 0.25f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val mainColor = if (isSms) Color(0xFFFFA500) else Color(0xFF00FFFF)

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (!isSms) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(mainColor.copy(alpha = glowAlpha))
                    .border(1.2.dp, mainColor.copy(alpha = 0.6f), CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1A))
                    .border(1.dp, mainColor.copy(alpha = 0.5f), CircleShape)
            )
        }

        Text(
            text = letter,
            fontWeight = FontWeight.ExtraBold,
            color = if (isSms) mainColor else Color.Black,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun ChatListItem(
    chat: ChatDisplay,
    onClick: () -> Unit
) {
    val contactName = rememberContactName(chat.phoneNumber ?: chat.title)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedAvatar(
            letter = contactName.firstOrNull()?.uppercase() ?: "?",
            isActive = chat.lastMessage.isNotBlank(),
            isSms = chat.lastMessageIsSms
        )

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contactName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = chat.time,
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.lastMessageIsSms) {
                    Icon(
                        Icons.Default.Sms,
                        contentDescription = "SMS",
                        tint = Color(0xFFFFA500).copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }

                Text(
                    text = chat.lastMessage,
                    color = Color.LightGray.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val vm: ChatsListViewModel = viewModel()
    val chats by vm.chats.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) vm.refreshSms()
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        } else {
            vm.refreshSms()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "СООБЩЕНИЯ",
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        color = Color(0xFF00FFFF)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                containerColor = Color(0xFF00FFFF),
                contentColor = Color.Black,
                shape = CircleShape,
                onClick = { navController.navigate(Routes.CONTACTS) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новый чат")
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (chats.isEmpty()) {
                EmptyStateView()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(chats, key = { it.id + it.timestamp }) { chat ->
                        ChatListItem(chat) {
                            val routeId = chat.phoneNumber ?: chat.p2pHash ?: chat.id
                            navController.navigate("chat/$routeId")
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 84.dp, end = 16.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Список пуст",
            color = Color(0xFF00FFFF).copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Нет активных диалогов или SMS",
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
