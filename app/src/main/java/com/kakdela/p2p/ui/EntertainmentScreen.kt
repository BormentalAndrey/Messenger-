package com.kakdela.p2p.ui

import android.content.Context
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
    EntertainmentItem("global_chat", "ЧёКаВо? (глобальный чат)", "Общий чат", EntertainmentType.INTERNAL_CHAT, "chat/global"),
    EntertainmentItem("tictactoe", "Крестики-нолики", "Игра против ИИ", EntertainmentType.GAME, Routes.TIC_TAC_TOE),
    EntertainmentItem("chess", "Шахматы", "Игра (в разработке)", EntertainmentType.GAME, Routes.CHESS),
    EntertainmentItem("pacman", "Пакман", "Аркада", EntertainmentType.GAME, Routes.PACMAN),
    EntertainmentItem("jewels", "Jewels Blast", "3 в ряд", EntertainmentType.GAME, Routes.JEWELS),
    EntertainmentItem("pikabu", "Пикабу", "Юмор, истории, мемы и новости", EntertainmentType.WEB, "https://pikabu.ru"),
    EntertainmentItem("tiktok", "TikTok", "Короткие видео, тренды, танцы и креатив", EntertainmentType.WEB, "https://www.tiktok.com"),
    EntertainmentItem("crazygames", "CrazyGames", "Тысячи бесплатных браузерных игр", EntertainmentType.WEB, "https://www.crazygames.ru")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntertainmentScreen(navController: NavHostController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Развлечения", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
        ) {
            items(items = entertainmentItems) { item ->
                EntertainmentListItem(item = item, navController = navController, context = context)
            }
        }
    }
}

@Composable
fun EntertainmentListItem(
    item: EntertainmentItem,
    navController: NavHostController,
    context: Context
) {
    // ИСПРАВЛЕНИЕ: Получаем цвет здесь, в контексте Composable, а не внутри clickable
    val toolbarColor = MaterialTheme.colorScheme.primary.toArgb()

    Column {
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
                                    .setToolbarColor(toolbarColor) // Используем заранее сохраненный цвет
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
}

