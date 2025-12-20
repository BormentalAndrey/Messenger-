package com.kakdela.p2p.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

// Логика проверки победителя
fun checkWinnerForBoard(board: List<String>): String? {
    val winLines = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6)
    )
    for (line in winLines) {
        if (board[line[0]].isNotEmpty() && 
            board[line[0]] == board[line[1]] && 
            board[line[0]] == board[line[2]]) {
            return board[line[0]]
        }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeScreen() {
    var board by remember { mutableStateOf(List(9) { "" }) }
    var isPlayerTurn by remember { mutableStateOf(true) }
    var winner by remember { mutableStateOf<String?>(null) }
    var isDraw by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun resetGame() {
        board = List(9) { "" }
        isPlayerTurn = true
        winner = null
        isDraw = false
    }

    // Логика ИИ
    LaunchedEffect(isPlayerTurn) {
        if (!isPlayerTurn && winner == null && !isDraw) {
            delay(600)
            val emptyIndices = board.mapIndexedNotNull { index, s -> if (s.isEmpty()) index else null }
            if (emptyIndices.isNotEmpty()) {
                val move = emptyIndices[Random.nextInt(emptyIndices.size)]
                val newBoard = board.toMutableList()
                newBoard[move] = "O"
                board = newBoard
                
                val win = checkWinnerForBoard(board)
                if (win != null) {
                    winner = win
                } else if (board.none { it.isEmpty() }) {
                    isDraw = true
                } else {
                    isPlayerTurn = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("XO NEON", color = Color.Cyan, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black),
                actions = {
                    IconButton(onClick = { resetGame() }) {
                        Icon(Icons.Default.Refresh, null, tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Статус
            Text(
                text = when {
                    winner != null -> "ПОБЕДА: $winner"
                    isDraw -> "НИЧЬЯ"
                    isPlayerTurn -> "ТВОЙ ХОД"
                    else -> "ИИ ДУМАЕТ..."
                },
                color = if (winner != null) Color.Green else Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Поле
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                    .padding(8.dp)
            ) {
                Column {
                    for (i in 0 until 3) {
                        Row(modifier = Modifier.weight(1f)) {
                            for (j in 0 until 3) {
                                val index = i * 3 + j
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(4.dp)
                                        .background(Color.Black, RoundedCornerShape(8.dp))
                                        .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (isPlayerTurn && board[index].isEmpty() && winner == null) {
                                                val newBoard = board.toMutableList()
                                                newBoard[index] = "X"
                                                board = newBoard
                                                val win = checkWinnerForBoard(board)
                                                if (win != null) {
                                                    winner = win
                                                } else if (board.none { it.isEmpty() }) {
                                                    isDraw = true
                                                } else {
                                                    isPlayerTurn = false
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (board[index].isNotEmpty()) {
                                        Text(
                                            text = board[index],
                                            fontSize = 42.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (board[index] == "X") Color.Cyan else Color.Magenta
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Кнопка рестарта
            AnimatedVisibility(
                visible = winner != null || isDraw,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Button(
                    onClick = { resetGame() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ИГРАТЬ ЕЩЕ РАЗ", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

