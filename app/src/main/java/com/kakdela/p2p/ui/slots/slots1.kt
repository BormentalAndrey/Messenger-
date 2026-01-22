package com.kakdela.p2p.ui.slots

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Slots1Screen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Имена файлов в папке assets
    val gemFiles = listOf(
        "gem_blue.png",
        "gem_green.png",
        "gem_red.png",
        "gem_white.png",
        "gem_yellow.png"
    )

    // Кэшируем загруженные картинки
    val painterMap = gemFiles.associateWith { loadPainterFromAssets(context, it) }

    var reelsState by remember { mutableStateOf(List(3) { gemFiles.random() }) }
    var isSpinning by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf(1000) }

    val neonCyan = Color(0xFF00FFFF)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("NEON SLOTS", color = neonCyan, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Панель баланса
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                border = BorderStroke(1.dp, neonCyan),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = "BALANCE: $balance ₽",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Барабаны
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                reelsState.forEach { fileName ->
                    ReelItem(
                        painter = painterMap[fileName],
                        isSpinning = isSpinning
                    )
                }
            }

            // Сообщение о результате
            Box(Modifier.height(40.dp)) {
                if (resultMessage.isNotEmpty()) {
                    Text(
                        text = resultMessage,
                        color = if (resultMessage.contains("JACKPOT")) Color.Yellow else neonCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Кнопка SPIN
            NeonButton(
                text = if (isSpinning) "SPINNING..." else "SPIN (100 ₽)",
                enabled = !isSpinning && balance >= 100,
                color = neonCyan
            ) {
                if (!isSpinning && balance >= 100) {
                    balance -= 100
                    isSpinning = true
                    resultMessage = ""
                    scope.launch {
                        // Эффект вращения (кадры)
                        repeat(18) { i ->
                            reelsState = List(3) { gemFiles.random() }
                            delay(60L + i * 5L)
                        }
                        
                        val finalResult = List(3) { gemFiles.random() }
                        reelsState = finalResult
                        isSpinning = false

                        // Проверка выигрыша
                        val unique = finalResult.distinct().size
                        when (unique) {
                            1 -> { balance += 1500; resultMessage = "🎉 JACKPOT! +1500 🎉" }
                            2 -> { balance += 250; resultMessage = "✨ BIG WIN! +250 ✨" }
                            else -> { resultMessage = "TRY AGAIN" }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReelItem(painter: Painter?, isSpinning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "reelAnim")
    val shakeOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isSpinning) 10f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake"
    )

    Box(
        modifier = Modifier
            .size(100.dp)
            .shadow(if (isSpinning) 20.dp else 0.dp, RoundedCornerShape(16.dp), spotColor = Color.Cyan)
            .border(2.dp, Color.Cyan, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0A0A))
            .drawBehind {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color.Cyan.copy(0.2f), Color.Transparent)),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .size(70.dp)
                    .offset(y = shakeOffset.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            // Если картинка не загрузилась, показываем заглушку
            Text("💎", fontSize = 40.sp, modifier = Modifier.offset(y = shakeOffset.dp))
        }
    }
}

@Composable
fun NeonButton(text: String, enabled: Boolean, color: Color, onClick: () -> Unit) {
    val activeColor = if (enabled) color else Color.DarkGray
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(2.dp, activeColor, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = activeColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )
    }
}

/**
 * Загрузка Painter из Android Assets
 */
@Composable
fun loadPainterFromAssets(context: Context, fileName: String): Painter? {
    return remember(fileName) {
        try {
            context.assets.open(fileName).use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                BitmapPainter(bitmap.asImageBitmap())
            }
        } catch (e: Exception) {
            null
        }
    }
}
