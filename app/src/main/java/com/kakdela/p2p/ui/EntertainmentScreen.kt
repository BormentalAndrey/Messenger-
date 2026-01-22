package com.kakdela.p2p.ui

import android.content.Intent
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.kakdela.p2p.R
import com.kakdela.p2p.ui.navigation.Routes
import com.kakdela.p2p.ui.player.MusicManager
import com.kakdela.p2p.ui.player.VideoPlayerActivity

enum class EntertainmentType {
    WEB,
    INTERNAL_CHAT,
    GAME,
    MUSIC,
    VIDEO
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
            EntertainmentType.VIDEO -> Icons.Filled.Movie
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
        id = "video",
        title = "Видео",
        description = "Неоновый видео плеер",
        type = EntertainmentType.VIDEO
    ),
    EntertainmentItem(
        id = "ai_chat",
        title = "AI Чат",
        description = "Умный помощник",
        type = EntertainmentType.INTERNAL_CHAT,
        route = Routes.AI_CHAT
    ),
    EntertainmentItem(
        id = "slots_1",
        title = "Слоты",
        description = "Неоновый слот-автомат",
        type = EntertainmentType.GAME,
        route = Routes.SLOTS_1
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
        title = "Кристаллы",
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
                        text = "Развлечения",
                        fontWeight = FontWeight.Black,
                        color = Color.Green
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
            items(entertainmentItems, key = { it.id }) { item ->
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
    val context = LocalContext.current

    val neonColor = when (item.type) {
        EntertainmentType.GAME -> Color.Green
        EntertainmentType.INTERNAL_CHAT -> Color.Cyan
        EntertainmentType.WEB -> Color.Magenta
        EntertainmentType.MUSIC -> Color.Yellow
        EntertainmentType.VIDEO -> Color(0xFFBA00FF)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(85.dp)
            .shadow(8.dp, spotColor = neonColor),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, neonColor.copy(alpha = 0.8f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        onClick = {
            when (item.type) {
                EntertainmentType.WEB -> {
                    item.url?.let { url ->
                        navController.navigate(
                            "webview/${Uri.encode(url)}/${Uri.encode(item.title)}"
                        )
                    }
                }

                EntertainmentType.VIDEO -> {
                    context.startActivity(
                        Intent(context, VideoPlayerActivity::class.java)
                    )
                }

                else -> {
                    item.route?.let { navController.navigate(it) }
                }
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
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (item.type == EntertainmentType.MUSIC && MusicManager.currentTrack != null) {
                AsyncImage(
                    model = MusicManager.currentTrack!!.albumArt,
                    contentDescription = null,
                    error = painterResource(id = R.mipmap.ic_launcher),
                    modifier = Modifier
                        .size(45.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background(
                            neonColor.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.iconVector,
                        contentDescription = null,
                        tint = neonColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                if (item.type == EntertainmentType.MUSIC && MusicManager.currentTrack != null) {
                    Text(
                        text = MusicManager.currentTrack!!.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "Сейчас играет",
                        color = neonColor,
                        fontSize = 10.sp
                    )
                } else {
                    Text(
                        text = item.title.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.description,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            if (item.type == EntertainmentType.MUSIC) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { MusicManager.playPrevious(context) }) {
                        Icon(Icons.Filled.ChevronLeft, null, tint = neonColor)
                    }
                    IconButton(
                        onClick = {
                            if (MusicManager.currentIndex == -1)
                                MusicManager.playTrack(context, 0)
                            else
                                MusicManager.togglePlayPause()
                        }
                    ) {
                        Icon(
                            imageVector = if (MusicManager.isPlaying)
                                Icons.Filled.Pause
                            else
                                Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = neonColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    IconButton(onClick = { MusicManager.playNext(context) }) {
                        Icon(Icons.Filled.ChevronRight, null, tint = neonColor)
                    }
                }
            } else {
                Icon(Icons.Filled.PlayArrow, null, tint = neonColor)
            }
        }
    }
}
