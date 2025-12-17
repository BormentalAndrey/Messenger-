package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

val jewelColors = listOf(
    Color.Red,
    Color.Blue,
    Color.Green,
    Color.Yellow,
    Color.Magenta,
    Color.Cyan
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JewelsBlastScreen() {

    var board by remember {
        mutableStateOf(
            List(8) { List(8) { Random.nextInt(jewelColors.size) } }
        )
    }

    var score by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Jewels Blast",
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Счёт: $score",
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier.padding(16.dp)
            )

            Column {
                for (row in board.indices) {
                    Row {
                        for (col in board[row].indices) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(2.dp)
                                    .background(
                                        color = jewelColors[board[row][col]],
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        // Заглушка логики
                                        board = board.mapIndexed { r, list ->
                                            if (r == row) {
                                                list.mapIndexed { c, value ->
                                                    if (c == col)
                                                        (value + 1) % jewelColors.size
                                                    else value
                                                }
                                            } else list
                                        }
                                        score += 10
                                    }
                            )
                        }
                    }
                }
            }

            Text(
                text = "Логика совпадений в разработке\n(Скоро добавим удаление рядов и анимацию)",
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
