package com.kakdela.p2p.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

enum class EntertainmentType {
    WEB,
    INTERNAL_CHAT,
    GAME,
    MUSIC
}

data class EntertainmentItem(
    val id: String,
    val title: String,
    val description: String,
    val type: EntertainmentType,
    val route: String? = null,
    val url: String? = null
) {
    val iconVector: ImageVector
        get() = when (type) {
            EntertainmentType.GAME -> Icons.Filled.Gamepad
            EntertainmentType.INTERNAL_CHAT -> Icons.Filled.Chat
            EntertainmentType.WEB -> Icons.Filled.Public
            EntertainmentType.MUSIC -> Icons.Filled.MusicNote
        }
}

private val entertainmentItems = listOf(
    EntertainmentItem(
        id = "music",
        title = "Музыка",
        description = "MP3 проигрыватель",
        type = EntertainmentType.MUSIC,
        route = Routes.MUSIC
    ),
    EntertainmentItem(
        id = "global_chat",
        title = "ЧёКаВо?",
        description = "Общий чат",
        type = EntertainmentType.INTERNAL_CHAT,
        route = "chat/global"
    ),
    EntertainmentItem(
        id = "tictactoe",
        title = "Крестики-нолики",
        description = "Игра против ИИ",
        type = EntertainmentType.GAME,
        route = Routes.TIC_TAC_TOE
    ),
    EntertainmentItem(
        id = "pacman",
        title = "Pacman",
        description = "Классическая аркада",
        type = EntertainmentType.GAME,
        route = Routes.PACMAN
    ),
    EntertainmentItem(
        id = "jewels",
        title = "Jewels Blast",
        description = "Три в ряд",
        type = EntertainmentType.GAME,
        route = Routes.JEWELS
    ),
    EntertainmentItem(
        id = "sudoku",
        title = "Судоку",
        description = "Головоломка 9x9",
        type = EntertainmentType.GAME,
        route = Routes.SUDOKU
    ),
    EntertainmentItem(
        id = "tiktok",
        title = "TikTok",
        description = "Смотреть (ПК режим)",
        type = EntertainmentType.WEB,
        url = "https://www.tiktok.com"
    ),
    EntertainmentItem(
        id = "pikabu",
        title = "Пикабу",
        description = "Юмор",
        type = EntertainmentType.WEB,
        url = "https://pikabu.ru"
    ),
    EntertainmentItem(
        id = "crazygames",
        title = "CrazyGames",
        description = "Игры онлайн",
        type = EntertainmentType.WEB,
        url = "https://www.crazygames.com"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntertainmentScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Развлечения",
                        fontWeight = FontWeight.Black,
                        color = Color.Green,
                        letterSpacing = 1.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
fun EntertainmentNeonItem(
    item: EntertainmentItem,
    navController: NavHostController
) {
    val neonColor = when (item.type) {
        EntertainmentType.GAME -> Color.Green
        EntertainmentType.INTERNAL_CHAT -> Color.Cyan
        EntertainmentType.WEB -> Color.Magenta
        EntertainmentType.MUSIC -> Color.Yellow
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
                EntertainmentType.WEB -> {
                    item.url?.let { url ->
                        val encodedUrl = Uri.encode(url)
                        val encodedTitle = Uri.encode(item.title)
                        navController.navigate("webview/$encodedUrl/$encodedTitle")
                    }
                }
                else -> item.route?.let { navController.navigate(it) }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, neonColor.copy(alpha = 0.08f))
                    )
                )
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
