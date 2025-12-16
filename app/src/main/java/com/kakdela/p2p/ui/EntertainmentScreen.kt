package com.kakdela.p2p.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavHostController

// Модель элемента
data class EntertainmentItem(
    val id: String,
    val title: String,
    val description: String,
    val url: String? = null, // null — внутренний чат
    val iconLetter: String = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
)

// Список без RuStore
private val entertainmentItems = listOf(
    EntertainmentItem(
        id = "global_chat",
        title = "ЧёКаВо? (глобальный чат)",
        description = "Общий чат всех пользователей приложения",
        url = null
    ),
    EntertainmentItem(
        id = "pikabu",
        title = "Пикабу",
        description = "Юмор, истории, мемы и новости",
        url = "https://pikabu.ru"
    ),
    EntertainmentItem(
        id = "tiktok",
        title = "TikTok",
        description = "Короткие видео, тренды, танцы и креатив",
        url = "https://www.tiktok.com"
    ),
    EntertainmentItem(
        id = "crazygames",
        title = "CrazyGames",
        description = "Тысячи бесплатных браузерных игр",
        url = "https://www.crazygames.ru"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntertainmentScreen(navController: NavHostController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Развлечения",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
        ) {
            items(entertainmentItems) { item ->
                EntertainmentListItem(
                    item = item,
                    onClick = {
                        if (item.url == null) {
                            navController.navigate("chat/global")
                        } else {
                            val customTabsIntent = CustomTabsIntent.Builder()
                                .setToolbarColor(MaterialTheme.colorScheme.primary.toArgb())
                                .setShowTitle(true)
                                .build()
                            customTabsIntent.launchUrl(context, item.url.toUri())
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EntertainmentListItem(
    item: EntertainmentItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.iconLetter,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = item.description,
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1
            )
        }

        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }

    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
}
