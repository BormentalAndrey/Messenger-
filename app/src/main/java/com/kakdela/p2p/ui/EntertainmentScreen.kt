package com.kakdela.p2p.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import com.kakdela.p2p.ui.navigation.Routes

enum class EntertainmentType {
    WEB,
    INTERNAL_CHAT,
    GAME
}

data class EntertainmentItem(
    val id: String,
    val title: String,
    val description: String,
    val type: EntertainmentType,
    val route: String? = null,
    val url: String? = null
) {
    val iconLetter: String get() = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

private val entertainmentItems = listOf(
    EntertainmentItem(
        id = "global_chat",
        title = "ЧёКаВо? (глобальный чат)",
        description = "Общий чат всех пользователей приложения",
        type = EntertainmentType.INTERNAL_CHAT,
        route = "chat/global"
    ),
    EntertainmentItem(
        id = "tictactoe",
        title = "Крестики-нолики",
        description = "Классическая игра против ИИ",
        type = EntertainmentType.GAME,
        route = Routes.TIC_TAC_TOE
    ),
    EntertainmentItem(
        id = "chess",
        title = "Шахматы",
        description = "Стратегическая игра (в разработке)",
        type = EntertainmentType.GAME,
        route = Routes.CHESS
    ),
    EntertainmentItem(
        id = "pacman",
        title = "Пакман",
        description = "Собери точки, убегай от привидений",
        type = EntertainmentType.GAME,
        route = Routes.PACMAN
    ),
    EntertainmentItem(
        id = "jewels",
        title = "Jewels Blast",
        description = "Собирай камни три в ряд",
        type = EntertainmentType.GAME,
        route = Routes.JEWELS
    ),
    EntertainmentItem(
        id = "pikabu",
        title = "Пикабу",
        description = "Юмор, истории, мемы и новости",
        type = EntertainmentType.WEB,
        url = "https://pikabu.ru"
    ),
    EntertainmentItem(
        id = "tiktok",
        title = "TikTok",
        description = "Короткие видео, тренды, танцы и креатив",
        type = EntertainmentType.WEB,
        url = "https://www.tiktok.com"
    ),
    EntertainmentItem(
        id = "crazygames",
        title = "CrazyGames",
        description = "Тысячи бесплатных браузерных игр",
        type = EntertainmentType.WEB,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
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
                EntertainmentListItem(item = item, navController = navController, context = context)
            }
        }
    }
}

@Composable
fun EntertainmentListItem(
    item: EntertainmentItem,
    navController: NavHostController,
    context: android.content.Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when (item.type) {
                    EntertainmentType.INTERNAL_CHAT -> navController.navigate(item.route!!)
                    EntertainmentType.GAME -> navController.navigate(item.route!!)
                    EntertainmentType.WEB -> {
                        item.url?.let { url ->
                            CustomTabsIntent.Builder()
                                .setToolbarColor(MaterialTheme.colorScheme.primary.toArgb())
                                .setShowTitle(true)
                                .build()
                                .launchUrl(context, url.toUri())
                        }
                    }
                }
            }
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
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Открыть",
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }

    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
}
