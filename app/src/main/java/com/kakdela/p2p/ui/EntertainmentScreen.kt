package com.kakdela.p2p.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    val iconVector: ImageVector get() = when (type) {
        EntertainmentType.GAME -> Icons.Filled.Gamepad
        EntertainmentType.INTERNAL_CHAT -> Icons.Filled.Chat
        else -> Icons.Filled.Public
    }
}

private val entertainmentItems = listOf(
    EntertainmentItem("global_chat", "ЧёКаВо?", "Общий чат", EntertainmentType.INTERNAL_CHAT, "chat/global"),
    EntertainmentItem("tictactoe", "Крестики-нолики", "Игра против ИИ", EntertainmentType.GAME, Routes.TIC_TAC_TOE),
    EntertainmentItem("pacman", "Pacman", "Классическая аркада", EntertainmentType.GAME, Routes.PACMAN),
    EntertainmentItem("jewels", "Jewels Blast", "Три в ряд (200 уровней)", EntertainmentType.GAME, Routes.JEWELS),

    // ←←← НОВАЯ ИГРА: СУДОКУ ←←←
    EntertainmentItem(
        id = "sudoku",
        title = "Судоку",
        description = "Классическая головоломка 9×9",
        type = EntertainmentType.GAME,
        route = Routes.SUDOKU
    ),

    // Веб-сервисы
    EntertainmentItem("tiktok", "TikTok", "Смотреть (ПК режим)", EntertainmentType.WEB, url = "https://www.tiktok.com"),
    EntertainmentItem("pikabu", "Пикабу", "Юмор", EntertainmentType.WEB, url = "https://pikabu.ru"),
    EntertainmentItem("crazygames", "CrazyGames", "Игры онлайн", EntertainmentType.WEB, url = "https://www.crazygames.com")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntertainmentScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Развлечения", fontWeight = FontWeight.Black, color = Color.Green, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(entertainmentItems) { item ->
                EntertainmentNeonItem(item, navController)
            }
        }
    }
}

@Composable
fun EntertainmentNeonItem(item: EntertainmentItem, navController: NavHostController) {
    val neonColor = when (item.type) {
        EntertainmentType.GAME -> Color.Green
        EntertainmentType.INTERNAL_CHAT -> Color.Cyan
        EntertainmentType.WEB -> Color.Magenta
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(8.dp, spotColor = neonColor),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, neonColor.copy(alpha = 0.8f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        onClick = {
            when (item.type) {
                EntertainmentType.WEB -> item.url?.let {
                    val encoded = URLEncoder.encode(it, StandardCharsets.UTF_8.toString())
                    navController.navigate("webview/\( encoded/ \){item.title}")
                }
                else -> item.route?.let { navController.navigate(it) }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, neonColor.copy(0.05f))))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(item.iconVector, contentDescription = null, tint = neonColor, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title.uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                Text(item.description, color = Color.Gray, fontSize = 12.sp)
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = neonColor)
        }
    }
}
