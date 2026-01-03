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
    val iconVector: ImageVector
        get() = when (type) {
            EntertainmentType.GAME -> Icons.Filled.Gamepad
            EntertainmentType.INTERNAL_CHAT -> Icons.Filled.Chat
            EntertainmentType.WEB -> Icons.Filled.Public
        }
}

private val entertainmentItems = listOf(
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
        description = "Три в ряд (200 уровней)",
        type = EntertainmentType.GAME,
        route = Routes.JEWELS
    ),
    EntertainmentItem(
        id = "sudoku",
        title = "Судоку",
        description = "Классическая головоломка 9x9",
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
                        text = "Развлечения",
                        fontWeight = FontWeight.Black,
                        color = Color.Green,
                        letterSpacing = 2.sp
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
                EntertainmentNeonItem(
                    item = item,
                    navController = navController
                )
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
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(
                elevation = 8.dp,
                spotColor = neonColor
            ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = neonColor.copy(alpha = 0.8f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121212)
        ),
        onClick = {
            when (item.type) {
                EntertainmentType.WEB -> {
                    item.url?.let { url ->
                        val encodedUrl = URLEncoder.encode(
                            url,
                            StandardCharsets.UTF_8.toString()
                        )
                        val encodedTitle = URLEncoder.encode(
                            item.title,
                            StandardCharsets.UTF_8.toString()
                        )

                        navController.navigate(
                            "webview/$encodedUrl/$encodedTitle"
                        )
                    }
                }

                else -> {
                    item.route?.let { route ->
                        navController.navigate(route)
                    }
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            neonColor.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.iconVector,
                contentDescription = null,
                tint = neonColor,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.description,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = neonColor
            )
        }
    }
}
