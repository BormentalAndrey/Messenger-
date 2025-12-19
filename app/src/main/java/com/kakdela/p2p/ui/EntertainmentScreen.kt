package com.kakdela.p2p.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.kakdela.p2p.ui.navigation.Routes
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class EntertainmentType { WEB, INTERNAL_CHAT, GAME }

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
    EntertainmentItem("global_chat", "ЧёКаВо?", "Общий чат", EntertainmentType.INTERNAL_CHAT, "chat/global"),
    EntertainmentItem("tictactoe", "Крестики-нолики", "Игра против ИИ", EntertainmentType.GAME, Routes.TIC_TAC_TOE),
    EntertainmentItem("pikabu", "Пикабу", "Юмор и новости", EntertainmentType.WEB, url = "https://pikabu.ru"),
    EntertainmentItem("tiktok", "TikTok", "Короткие видео", EntertainmentType.WEB, url = "https://www.tiktok.com"),
    EntertainmentItem("crazygames", "CrazyGames", "Браузерные игры", EntertainmentType.WEB, url = "https://www.crazygames.com")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntertainmentScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Развлечения", fontWeight = FontWeight.Bold, color = Color.White) },
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
                EntertainmentListItem(item, navController)
            }
        }
    }
}

@Composable
fun EntertainmentListItem(item: EntertainmentItem, navController: NavHostController) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    when (item.type) {
                        EntertainmentType.INTERNAL_CHAT -> {
                            item.route?.let { navController.navigate(it) }
                        }
                        EntertainmentType.GAME -> {
                            item.route?.let { navController.navigate(it) }
                        }
                        EntertainmentType.WEB -> {
                            item.url?.let {
                                // КРИТИЧНО: Кодируем URL, чтобы символы "/" не ломали навигацию Compose
                                val encodedUrl = URLEncoder.encode(it, StandardCharsets.UTF_8.toString())
                                navController.navigate("webview/$encodedUrl/${item.title}")
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
            Icon(Icons.Filled.PlayArrow, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
    }
}

