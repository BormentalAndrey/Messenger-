package com.kakdela.p2p.ui.slots

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration

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
    val spinDuration = 1500L // миллисекунд анимации прокрутки

    var reelsState by remember { mutableStateOf(List(reelsCount) { gemImages.random() }) }
    var isSpinning by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NEON SLOT MACHINE",
                fontWeight = FontWeight.ExtraBold,
                color = Color.Cyan,
                fontSize = 32.sp,
                modifier = Modifier.padding(16.dp)
            )

            Spacer(Modifier.height(32.dp))

            // Сама слот-машина
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until reelsCount) {
                    Reel(
                        painter = painterMap[reelsState[i]]!!,
                        isSpinning = isSpinning
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Кнопка SPIN
            NeonButton(
                text = if (isSpinning) "SPINNING..." else "SPIN",
                enabled = !isSpinning
            ) {
                if (!isSpinning) {
                    isSpinning = true
                    resultMessage = ""
                    coroutineScope.launch {
                        val randomResults = List(reelsCount) { gemImages.random() }
                        val steps = 15 // сколько кадров прокрутки
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
                        } else {
                            "Try Again!"
                        }
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
            modifier = Modifier
                .size(80.dp)
                .offset(y = offsetY.dp),
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

/**
 * Загружает картинку из assets
 */
@Composable
fun loadPainterFromAssets(context: Context, assetName: String): Painter {
    return remember(assetName) {
        useResource("assets/$assetName") {
            androidx.compose.ui.res.loadImageBitmap(it)
        }.let { bitmap ->
            BitmapPainter(bitmap)
        }
    }
}
