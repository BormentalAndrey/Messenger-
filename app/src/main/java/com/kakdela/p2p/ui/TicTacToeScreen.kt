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
import kotlin.math.min
import kotlin.math.max

// Непобедимый Minimax ИИ
data class Move(val index: Int, val score: Int)

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

fun isBoardFull(board: List<String>): Boolean = board.none { it.isEmpty() }

fun getBestMove(board: List<String>, isMaximizing: Boolean): Move? {
    val winner = checkWinnerForBoard(board)
    if (winner != null) {
        return Move(-1, if (winner == "O") 10 else -10)
    }
    if (isBoardFull(board)) {
        return Move(-1, 0)
    }

    val emptyCells = board.mapIndexedNotNull { index, cell -> if (cell.isEmpty()) index else null }
    
    return if (isMaximizing) {
        // Компьютер (O) максимизирует
        var bestScore = Int.MIN_VALUE
        var bestMove = -1
        for (move in emptyCells) {
            val newBoard = board.toMutableList().apply { this[move] = "O" }
            val score = getBestMove(newBoard, false)?.score ?: 0
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }
        Move(bestMove, bestScore)
    } else {
        // Игрок (X) минимизирует
        var bestScore = Int.MAX_VALUE
        var bestMove = -1
        for (move in emptyCells) {
            val newBoard = board.toMutableList().apply { this[move] = "X" }
            val score = getBestMove(newBoard, true)?.score ?: 0
            if (score < bestScore) {
                bestScore = score
                bestMove = move
            }
        }
        Move(bestMove, bestScore)
    }
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

    // Умный ИИ с задержкой
    LaunchedEffect(isPlayerTurn) {
        if (!isPlayerTurn && winner == null && !isDraw) {
            delay(800) // Реалистичная задержка
            val bestMove = getBestMove(board, true)
            bestMove?.let { move ->
                if (move.index != -1) {
                    val newBoard = board.toMutableList()
                    newBoard[move.index] = "O"
                    board = newBoard
                    
                    val win = checkWinnerForBoard(board)
                    if (win != null) {
                        winner = win
                    } else if (isBoardFull(board)) {
                        isDraw = true
                    } else {
                        isPlayerTurn = true
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "XO NEON • НЕПОБЕДИМЫЙ ИИ", 
                        color = Color.Cyan, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ) 
                },
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
            // Улучшенный статус
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(bottom = 32.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text = when {
                        winner != null -> "🤖 ПОБЕДА ИИ: $winner"
                        isDraw -> "🤝 НИЧЬЯ (максимум возможное!)"
                        isPlayerTurn -> "⚡ ТВОЙ ХОД (X)"
                        else -> "🧠 ИИ РАССЧИТЫВАЕТ..."
                    },
                    color = when {
                        winner == "O" -> Color.Magenta
                        winner == "X" -> Color.Green
                        isDraw -> Color.Cyan
                        else -> Color.White
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // Улучшенное игровое поле
            Card(
                modifier = Modifier
                    .size(340.dp)
                    .shadow(16.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Column {
                        for (i in 0 until 3) {
                            Row(modifier = Modifier.weight(1f)) {
                                for (j in 0 until 3) {
                                    val index = i * 3 + j
                                    val isWinningCell = winner != null && getWinningCells(board, winner).contains(index)
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(6.dp)
                                            .background(
                                                if (isWinningCell) Color(0x40Magenta) 
                                                else Color.Black, 
                                                RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                if (isWinningCell) 3.dp else 2.dp,
                                                if (isWinningCell) Color.Magenta else Color(0xFF333333), 
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                if (isPlayerTurn && board[index].isEmpty() && winner == null) {
                                                    val newBoard = board.toMutableList()
                                                    newBoard[index] = "X"
                                                    board = newBoard
                                                    val win = checkWinnerForBoard(board)
                                                    if (win != null) {
                                                        winner = win
                                                    } else if (isBoardFull(board)) {
                                                        isDraw = true
                                                    } else {
                                                        isPlayerTurn = false
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedVisibility(
                                            visible = board[index].isNotEmpty(),
                                            enter = fadeIn(),
                                            exit = fadeOut()
                                        ) {
                                            Text(
                                                text = board[index],
                                                fontSize = 48.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (board[index] == "X") Color.Cyan else Color.Magenta,
                                                shadow = androidx.compose.ui.graphics.Shadow(
                                                    Color.White, 
                                                    offset = androidx.compose.ui.geometry.Offset(2f, 2f), 
                                                    blurRadius = 4f
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

            // Улучшенная кнопка рестарта
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.shadow(12.dp, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        "🎮 НОВАЯ ИГРА", 
                        color = Color.Black, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

// Вспомогательная функция для подсветки выигрышных клеток
fun getWinningCells(board: List<String>, winner: String?): List<Int> {
    if (winner == null) return emptyList()
    
    val winLines = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6)
    )
    
    for (line in winLines) {
        if (board[line[0]] == winner && board[line[1]] == winner && board[line[2]] == winner) {
            return line
        }
    }
    return emptyList()
}
