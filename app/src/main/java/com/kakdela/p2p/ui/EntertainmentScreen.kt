package com.kakdela.p2p.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.Dp

// ================= Entertainment Types =================
enum class EntertainmentType {
    WEB, INTERNAL_CHAT, GAME, MUSIC, VIDEO, SLOT
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
            EntertainmentType.SLOT -> Icons.Filled.Casino
        }
}

// ================= Entertainment List =================
private val entertainmentItems = listOf(
    EntertainmentItem("music", "Музыка", "MP3 проигрыватель", EntertainmentType.MUSIC, Routes.MUSIC),
    EntertainmentItem("video", "Видео", "Неоновый видео плеер", EntertainmentType.VIDEO),
    EntertainmentItem("ai_chat", "AI Чат", "Умный помощник", EntertainmentType.INTERNAL_CHAT, Routes.AI_CHAT),
    EntertainmentItem("tictactoe", "Крестики-нолики", "Игра против ИИ", EntertainmentType.GAME, Routes.TIC_TAC_TOE),
    EntertainmentItem("pacman", "Pacman", "Классическая аркада", EntertainmentType.GAME, Routes.PACMAN),
    EntertainmentItem("jewels", "Кристаллы", "Три в ряд", EntertainmentType.GAME, Routes.JEWELS),
    EntertainmentItem("sudoku", "Судоку", "Головоломка 9x9", EntertainmentType.GAME, Routes.SUDOKU),
    EntertainmentItem("slots", "Слот-машина", "Неоновый слот", EntertainmentType.SLOT, Routes.SLOTS),
    EntertainmentItem("tiktok", "TikTok", "Смотреть (ПК режим)", EntertainmentType.WEB, url = "https://www.tiktok.com"),
    EntertainmentItem("pikabu", "Пикабу", "Юмор", EntertainmentType.WEB, url = "https://pikabu.ru"),
    EntertainmentItem("crazygames", "CrazyGames", "Игры онлайн", EntertainmentType.WEB, url = "https://www.crazygames.com")
)

// ================= Main Screen =================
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

// ================= Entertainment Item =================
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
        EntertainmentType.SLOT -> Color(0xFF00FFFF)
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
                EntertainmentType.WEB -> item.url?.let { url ->
                    navController.navigate("webview/${Uri.encode(url)}/${Uri.encode(item.title)}")
                }
                EntertainmentType.VIDEO -> context.startActivity(Intent(context, VideoPlayerActivity::class.java))
                EntertainmentType.SLOT -> item.route?.let { navController.navigate(it) } // Переход на слот
                else -> item.route?.let { navController.navigate(it) }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, neonColor.copy(alpha = 0.08f))))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(45.dp)
                    .background(neonColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.iconVector, contentDescription = null, tint = neonColor, modifier = Modifier.size(28.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(item.title.uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                Text(item.description, color = Color.Gray, fontSize = 11.sp)
            }

            Icon(Icons.Filled.PlayArrow, null, tint = neonColor)
        }
    }
}

// ================= Slot Machine Screen =================
@Composable
fun SlotMachineScreen() {
    val context = LocalContext.current
    val gemImages = listOf(
        "gem_blue.png",
        "gem_green.png",
        "gem_red.png",
        "gem_white.png",
        "gem_yellow.png"
    )

    val painterMap = remember { gemImages.associateWith { loadPainterFromAssets(context, it) } }

    val reelsCount = 3
    val spinDuration = 1500L

    var reelsState by remember { mutableStateOf(List(reelsCount) { gemImages.random() }) }
    var isSpinning by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NEON SLOT MACHINE",
                fontWeight = FontWeight.ExtraBold,
                color = Color.Cyan,
                fontSize = 32.sp,
                modifier = Modifier.padding(16.dp)
            )

            Spacer(Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until reelsCount) {
                    Reel(painter = painterMap[reelsState[i]]!!, isSpinning = isSpinning)
                }
            }

            Spacer(Modifier.height(32.dp))

            NeonButton(
                text = if (isSpinning) "SPINNING..." else "SPIN",
                enabled = !isSpinning
            ) {
                if (!isSpinning) {
                    isSpinning = true
                    resultMessage = ""
                    coroutineScope.launch {
                        val randomResults = List(reelsCount) { gemImages.random() }
                        val steps = 15
                        for (step in 0 until steps) {
                            reelsState = List(reelsCount) { gemImages.random() }
                            delay(50L + step * 10L)
                        }
                        reelsState = randomResults
                        isSpinning = false
                        resultMessage = if (reelsState.distinct().size == 1) {
                            "🎉 JACKPOT! 🎉"
                        } else if (reelsState.toSet().size == 2) {
                            "✨ Small Win! ✨"
                        } else "Try Again!"
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (resultMessage.isNotEmpty()) {
                Text(
                    text = resultMessage,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = if (resultMessage.contains("JACKPOT")) Color.Yellow else Color.Cyan,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.shadow(4.dp)
                )
            }
        }
    }
}

@Composable
fun Reel(painter: Painter, isSpinning: Boolean) {
    val transition = rememberInfiniteTransition()
    val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (isSpinning) 20f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .size(100.dp)
            .border(2.dp, Color.Cyan, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111111))
            .drawBehind {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color.Cyan.copy(alpha = 0.3f), Color.Transparent)),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(80.dp).offset(y = offsetY.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun NeonButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(2.dp, Color.Cyan, RoundedCornerShape(16.dp))
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clickable(enabled) { onClick() }
            .padding(horizontal = 32.dp, vertical = 16.dp)
    ) {
        Text(
            text = text,
            color = Color.Cyan,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp
        )
    }
}

@Composable
fun loadPainterFromAssets(context: Context, assetName: String): Painter {
    return remember(assetName) {
        context.assets.open(assetName).use { stream ->
            androidx.compose.ui.res.loadImageBitmap(stream)
        }.let { bitmap ->
            androidx.compose.ui.graphics.painter.BitmapPainter(bitmap.asImageBitmap())
        }
    }
}
