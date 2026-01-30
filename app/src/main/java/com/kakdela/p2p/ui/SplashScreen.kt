package com.kakdela.p2p.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * SplashScreen — экран приветствия при запуске приложения.
 * Выполняет плавное появление текста и индикатора загрузки.
 * Гарантирует единоразовый вызов onTimeout.
 */
@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    val alpha = remember { Animatable(0f) }

    // Защита от повторной навигации при пересоздании composable
    var isNavigated by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {

        // Анимацию не повторяем при уже выполненной навигации
        if (!isNavigated) {

            alpha.snapTo(0f)

            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800)
            )

            delay(1200)

            if (!isNavigated) {
                isNavigated = true
                onTimeout()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
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
