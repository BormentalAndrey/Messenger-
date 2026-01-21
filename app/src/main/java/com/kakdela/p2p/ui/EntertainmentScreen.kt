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
WEB, INTERNAL_CHAT, GAME, MUSIC, VIDEO
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
EntertainmentItem("music", "Музыка", "MP3 проигрыватель", EntertainmentType.MUSIC, Routes.MUSIC),
EntertainmentItem("video", "Видео", "Неоновый видео плеер", EntertainmentType.VIDEO),

// ✅ ИСПРАВЛЕНО: добавлена запятая  
EntertainmentItem("ai_chat", "AI Чат", "Умный помощник", EntertainmentType.INTERNAL_CHAT, Routes.AI_CHAT),  
EntertainmentItem("tictactoe", "Крестики-нолики", "Игра против ИИ", EntertainmentType.GAME, Routes.TIC_TAC_TOE),  
EntertainmentItem("pacman", "Pacman", "Классическая аркада", EntertainmentType.GAME, Routes.PACMAN),  
EntertainmentItem("jewels", "Кристаллы", "Три в ряд", EntertainmentType.GAME, Routes.JEWELS),  
EntertainmentItem("sudoku", "Судоку", "Головоломка 9x9", EntertainmentType.GAME, Routes.SUDOKU),  
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
                    item.iconVector,  
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
                    MusicManager.currentTrack!!.title,  
                    color = Color.White,  
                    fontWeight = FontWeight.Bold,  
                    maxLines = 1  
                )  
                Text(  
                    "Сейчас играет",  
                    color = neonColor,  
                    fontSize = 10.sp  
                )  
            } else {  
                Text(  
                    item.title.uppercase(),  
                    color = Color.White,  
                    fontWeight = FontWeight.Bold  
                )  
                Text(  
                    item.description,  
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
                        if (MusicManager.isPlaying)  
                            Icons.Filled.Pause  
                        else  
                            Icons.Filled.PlayArrow,  
                        null,  
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
