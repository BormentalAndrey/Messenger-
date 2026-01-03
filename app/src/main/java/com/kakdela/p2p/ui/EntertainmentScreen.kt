package com.kakdela.p2p.ui

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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
        id = "pikabu",
        title = "Пикабу",
        description = "Юмор",
        type = EntertainmentType.WEB,
        url = "https://pikabu.ru"
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
                        color = Color.Green
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black
                )
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
        border = BorderStroke(1.dp, neonColor),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        onClick = {
            when (item.type) {
                EntertainmentType.WEB -> {
                    item.url?.let {
                        navController.navigate(
                            "webview/${
                                URLEncoder.encode(it, StandardCharsets.UTF_8.toString())
                            }/${
                                URLEncoder.encode(item.title, StandardCharsets.UTF_8.toString())
                            }"
                        )
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
                        listOf(Color.Transparent, neonColor.copy(0.08f))
                    )
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(item.iconVector, null, tint = neonColor, modifier = Modifier.size(32.dp))

            Spacer(Modifier.width(20.dp))

            Column(Modifier.weight(1f)) {
                Text(item.title.uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                Text(item.description, color = Color.Gray, fontSize = 12.sp)
            }

            Icon(Icons.Filled.PlayArrow, null, tint = neonColor)
        }
    }
}
