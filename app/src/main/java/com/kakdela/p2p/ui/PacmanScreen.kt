package com.kakdela.p2p.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacmanScreen() {

    var pacmanX by remember { mutableStateOf(200f) }
    var pacmanY by remember { mutableStateOf(300f) }
    var mouthAngle by remember { mutableStateOf(0.3f) }

    LaunchedEffect(Unit) {
        while (true) {
            mouthAngle = if (mouthAngle == 0.3f) 0.1f else 0.3f
            delay(200)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Пакман",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
        ) {

            Canvas(modifier = Modifier.fillMaxSize()) {

                // Пакман
                drawCircle(
                    color = Color.Yellow,
                    radius = 80f,
                    center = Offset(pacmanX, pacmanY)
                )

                // Рот
                drawArc(
                    color = Color.Black,
                    startAngle = 30f,
                    sweepAngle = 300f * mouthAngle,
                    useCenter = true,
                    topLeft = Offset(pacmanX - 80f, pacmanY - 80f),
                    size = Size(160f, 160f)
                )

                // Еда
                drawCircle(Color.White, 10f, Offset(100f, 100f))
                drawCircle(Color.White, 10f, Offset(300f, 100f))
                drawCircle(Color.White, 10f, Offset(100f, 500f))
                drawCircle(Color.White, 10f, Offset(300f, 500f))
            }

            Text(
                text = "Управление в разработке\n(Скоро добавим движение)",
                color = Color.Gray,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
