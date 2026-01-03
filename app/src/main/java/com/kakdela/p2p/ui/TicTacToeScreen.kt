package com.kakdela.p2p.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Проверка победителя
private fun checkWinner(board: List<String>): String? {
    val lines = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // ряды
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // столбцы
        listOf(0, 4, 8), listOf(2, 4, 6)                   // диагонали
    )
    for (line in lines) {
        val (a, b, c) = line
        if (board[a].isNotEmpty() && board[a] == board[b] && board[a] == board[c]) {
            return board[a]
        }
    }
    return null
}

// Проверка на ничью
private fun isBoardFull(board: List<String>): Boolean = board.all { it.isNotEmpty() }

// Minimax — непобедимый ИИ
private fun minimax(board: List<String>, isMaximizing: Boolean): Int {
    val winner = checkWinner(board)
    if (winner == "O") return 10
    if (winner == "X") return -10
    if (isBoardFull(board)) return 0

    if (isMaximizing) {
        var best = Int.MIN_VALUE
        for (i in board.indices) {
            if (board[i].isEmpty()) {
                val newBoard = board.toMutableList().apply { this[i] = "O" }
                best = maxOf(best, minimax(newBoard, false))
            }
        }
        return best
    } else {
        var best = Int.MAX_VALUE
        for (i in board.indices) {
            if (board[i].isEmpty()) {
                val newBoard = board.toMutableList().apply { this[i] = "X" }
                best = minOf(best, minimax(newBoard, true))
            }
        }
        return best
    }
}

// Поиск лучшего хода для ИИ ("O")
private fun getBestMove(board: List<String>): Int {
    var bestScore = Int.MIN_VALUE
    var bestMove = -1

    for (i in board.indices) {
        if (board[i].isEmpty()) {
            val newBoard = board.toMutableList().apply { this[i] = "O" }
            val score = minimax(newBoard, false)
            if (score > bestScore) {
                bestScore = score
                bestMove = i
            }
        }
    }
    return bestMove
}

// Подсветка выигрышной линии
private fun getWinningLine(board: List<String>, winner: String): List<Int> {
    val lines = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6)
    )
    for (line in lines) {
        if (board[line[0]] == winner && board[line[1]] == winner && board[line[2]] == winner) {
            return line
        }
    }
    return emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeScreen() {
    var board by remember { mutableStateOf(List(9) { "" }) }
    var isPlayerTurn by remember { mutableStateOf(true) }
    var winner by remember { mutableStateOf<String?>(null) }
    var isDraw by remember { mutableStateOf(false) }

    fun resetGame() {
        board = List(9) { "" }
        isPlayerTurn = true
        winner = null
        isDraw = false
    }

    // Ход ИИ
    LaunchedEffect(isPlayerTurn, winner, isDraw) {
        if (!isPlayerTurn && winner == null && !isDraw) {
            delay(800) // Имитация "размышлений"
            val move = getBestMove(board)
            if (move != -1) {
                val newBoard = board.toMutableList()
                newBoard[move] = "O"
                board = newBoard

                val newWinner = checkWinner(board)
                if (newWinner != null) {
                    winner = newWinner
                } else if (isBoardFull(board)) {
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
                title = {
                    Text(
                        "XO NEON",
                        color = Color.Cyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black
                ),
                actions = {
                    IconButton(onClick = { resetGame() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Новая игра", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Статус игры
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(bottom = 40.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Text(
                    text = when {
                        winner == "X" -> "🎉 ТЫ ПОБЕДИЛ! НЕВЕРОЯТНО!"
                        winner == "O" -> "🤖 ИИ ПОБЕДИЛ"
                        isDraw -> "🤝 НИЧЬЯ — ОТЛИЧНЫЙ РЕЗУЛЬТАТ!"
                        isPlayerTurn -> "⚡ ТВОЙ ХОД (X)"
                        else -> "🧠 ИИ ДУМАЕТ..."
                    },
                    color = when {
                        winner == "X" -> Color.Cyan
                        winner == "O" -> Color.Magenta
                        isDraw -> Color.Yellow
                        else -> Color.White
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }

            // Игровое поле
            Card(
                modifier = Modifier.size(340.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Column {
                        for (row in 0 until 3) {
                            Row(modifier = Modifier.weight(1f)) {
                                for (col in 0 until 3) {
                                    val index = row * 3 + col
                                    val symbol = board[index]
                                    val winningLine = winner?.let { getWinningLine(board, it) } ?: emptyList()
                                    val isWinningCell = winningLine.contains(index)

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(8.dp)
                                            .shadow(
                                                elevation = if (isWinningCell) 20.dp else 6.dp,
                                                shape = RoundedCornerShape(16.dp),
                                                ambientColor = if (isWinningCell) Color.Magenta else Color.Black
                                            )
                                            .background(
                                                color = if (isWinningCell) Color.Magenta.copy(alpha = 0.25f) else Color(0xFF1E1E1E),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .border(
                                                width = if (isWinningCell) 4.dp else 2.dp,
                                                color = if (isWinningCell) Color.Magenta else Color(0xFF404040),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable(
                                                enabled = isPlayerTurn && symbol.isEmpty() && winner == null && !isDraw
                                            ) {
                                                val newBoard = board.toMutableList()
                                                newBoard[index] = "X"
                                                board = newBoard

                                                val newWinner = checkWinner(board)
                                                if (newWinner != null) {
                                                    winner = newWinner
                                                } else if (isBoardFull(board)) {
                                                    isDraw = true
                                                } else {
                                                    isPlayerTurn = false
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(  // Явный импорт для избежания конфликтов
                                            visible = symbol.isNotEmpty(),
                                            enter = fadeIn(),
                                            exit = fadeOut()
                                        ) {
                                            Text(
                                                text = symbol,
                                                fontSize = 56.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (symbol == "X") Color.Cyan else Color.Magenta,
                                                modifier = Modifier
                                                    .shadow(
                                                        elevation = 12.dp,
                                                        shape = CircleShape,
                                                        spotColor = Color.White,
                                                        ambientColor = Color.White
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Кнопка "Новая игра"
            AnimatedVisibility(
                visible = winner != null || isDraw,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Button(
                    onClick = { resetGame() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (winner == "O") Color.Magenta else Color.Cyan
                    ),
                    modifier = Modifier
                        .width(240.dp)
                        .height(60.dp)
                        .shadow(16.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "НОВАЯ ИГРА",
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
