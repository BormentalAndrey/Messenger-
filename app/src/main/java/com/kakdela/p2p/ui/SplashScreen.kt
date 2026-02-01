package com.kakdela.p2p.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * SplashScreen — экран приветствия.
 * Исправлено: Добавлен systemBarsPadding для корректного отображения на Edge-to-Edge экранах.
 */
@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    var navigated by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (navigated) return@LaunchedEffect

        alpha.snapTo(0f)
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
        delay(1200)

        if (!navigated) {
            navigated = true
            onTimeout()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(), // Защита от наложения на системные бары
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Как дела?",
                color = Color.Cyan,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.alpha(alpha.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .width(120.dp)
                    .alpha(alpha.value * 0.5f),
                color = Color.Cyan,
                trackColor = Color.DarkGray
            )
        }
    }
}
