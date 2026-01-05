package com.kakdela.p2p.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakdela.p2p.game.JewelsBlastActivity
import coil.compose.rememberAsyncImagePainter

@Composable
fun JewelsBlastScreen() {
    val context = LocalContext.current

    // Создаем форму кристалла (ромб с небольшим удлинением)
    val crystalShape = GenericShape { size, _ ->
        moveTo(size.width / 2f, 0f)         // Верхняя точка
        lineTo(size.width, size.height * 0.4f) // Правый угол
        lineTo(size.width / 2f, size.height)   // Нижняя точка
        lineTo(0f, size.height * 0.4f)         // Левый угол
        close()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 1. Фоновое изображение во весь экран
        // Используем rememberAsyncImagePainter для загрузки из assets
        Image(
            painter = rememberAsyncImagePainter("file:///android_asset/video.png"),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop // Заполнение всего экрана без искажений
        )

        // 2. Кнопка "Играть" по центру в форме кристалла
        Button(
            onClick = {
                context.startActivity(
                    Intent(context, JewelsBlastActivity::class.java)
                )
            },
            modifier = Modifier.size(160.dp, 200.dp), // Пропорции кристалла
            shape = crystalShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6200EE).copy(alpha = 0.8f), // Полупрозрачный фиолетовый
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(8.dp)
        ) {
            Text(
                text = "ИГРАТЬ",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

