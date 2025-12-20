package com.kakdela.p2p.ui

import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

enum class Difficulty {
    EASY, MEDIUM, HARD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeScreen() {

    var board by remember { mutableStateOf(List(9) { "" }) }
    var winner by remember { mutableStateOf("") }
    var playerTurn by remember { mutableStateOf(true) }

    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    var scoreX by remember { mutableStateOf(0) }
    var scoreO by remember { mutableStateOf(0) }
    var draws by remember { mutableStateOf(0) }

    fun checkWinner(): String? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (line in lines) {
            if (board[line[0]].isNotEmpty() &&
                board[line[0]] == board[line[1]] &&
                board[line[1]] == board[line[2]]
            ) {
                return board[line[0]]
            }
        }
        return if (board.all { it.isNotEmpty() }) "draw" else null
    }

    fun aiMoveEasy(): Int? =
        board.indices.filter { board[it].isEmpty() }.randomOrNull()

    fun aiMoveMedium(): Int? {
        // попытка найти победный ход
        for (i in 0 until 9) {
            if (board[i].isEmpty()) {
                val testBoard = board.toMutableList()
                testBoard[i] = "O"
                if (checkWinnerForBoard(testBoard) == "O") return i
            }
        }
        // блокировка игрока
        for (i in 0 until 9) {
            if (board[i].isEmpty()) {
                val testBoard = board.toMutableList()
                testBoard[i] = "X"
                if (checkWinnerForBoard(testBoard) == "X") return i
            }
        }
        return aiMoveEasy()
    }

    fun checkWinnerForBoard(b: List<String>): String? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (line in lines) {
            if (b[line[0]].isNotEmpty() && b[line[0]] == b[line[1]] && b[line[1]] == b[line[2]]) {
                return b[line[0]]
            }
        }
        return if (b.all { it.isNotEmpty() }) "draw" else null
    }

    fun aiMoveHard(): Int? {
        fun minimax(b: List<String>, isMaximizing: Boolean): Int {
            val result = checkWinnerForBoard(b)
            if (result == "O") return 10
            if (result == "X") return -10
            if (result == "draw") return 0

            val empty = b.indices.filter { b[it].isEmpty() }
            if (isMaximizing) {
                var best = -1000
                for (cell in empty) {
                    val boardCopy = b.toMutableList()
                    boardCopy[cell] = "O"
                    val score = minimax(boardCopy, false)
                    best = maxOf(best, score)
                }
                return best
            } else {
                var best = 1000
                for (cell in empty) {
                    val boardCopy = b.toMutableList()
                    boardCopy[cell] = "X"
                    val score = minimax(boardCopy, true)
                    best = minOf(best, score)
                }
                return best
            }
        }

        val empty = board.indices.filter { board[it].isEmpty() }
        var bestMove: Int? = null
        var bestScore = -1000

        for (cell in empty) {
            val boardCopy = board.toMutableList()
            boardCopy[cell] = "O"
            val score = minimax(boardCopy, false)
            if (score > bestScore) {
                bestScore = score
                bestMove = cell
            }
        }
        return bestMove ?: aiMoveEasy()
    }

    fun aiTurn() {
        val move = when (difficulty) {
            Difficulty.EASY -> aiMoveEasy()
            Difficulty.MEDIUM -> aiMoveMedium()
            Difficulty.HARD -> aiMoveHard()
        }

        move?.let {
            board = board.toMutableList().apply { this[it] = "O" }
        }

        val w = checkWinner()
        winner = w ?: ""
        if (winner == "X") scoreX++
        if (winner == "O") scoreO++
        if (winner == "draw") draws++
    }

    fun playerMove(index: Int) {
        if (board[index].isNotEmpty() || winner.isNotEmpty()) return
        board = board.toMutableList().apply { this[index] = "X" }

        val w = checkWinner()
        if (w != null) {
            winner = w
            if (winner == "X") scoreX++
            if (winner == "O") scoreO++
            if (winner == "draw") draws++
            return
        }
        aiTurn()
    }

    fun restart() {
        board = List(9) { "" }
        winner = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Крестики-нолики", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = MaterialTheme.colorScheme.primary
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

            Spacer(Modifier.height(10.dp))

            // --- Score ---
            Text(
                "X: $scoreX   |   O: $scoreO   |   Ничья: $draws",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(8.dp)
            )

            // --- Difficulty Buttons ---
            Row {
                Difficulty.values().forEach {
                    Button(
                        onClick = { difficulty = it },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (difficulty == it)
                                MaterialTheme.colorScheme.primary else Color.DarkGray
                        ),
                        modifier = Modifier.padding(4.dp)
                    ) { Text(it.name) }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (winner.isNotEmpty()) {
                Text(
                    text = when (winner) {
                        "X" -> "🎉 Ты выиграл!"
                        "O" -> "🤖 Робот победил!"
                        "draw" -> "😐 Ничья!"
                        else -> ""
                    },
                    color = Color.White,
                    fontSize = 28.sp,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = { restart() }) {
                    Text("Играть снова")
                }
            }

            Spacer(Modifier.height(30.dp))

            // --- Game Grid ---
            Box(modifier = Modifier.size(330.dp)) {
                Column(Modifier.fillMaxSize()) {
                    for (i in 0..2) {
                        Row(Modifier.weight(1f)) {
                            for (j in 0..2) {
                                val index = i * 3 + j
                                val value = board[index]

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(Color(0xFF191919))
                                        .clickable { playerMove(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedVisibility(
                                        visible = value.isNotEmpty(),
                                        enter = scaleIn(),
                                        exit = scaleOut()
                                    ) {
                                        Text(
                                            text = value,
                                            fontSize = 48.sp,
                                            color = if (value == "X")
                                                MaterialTheme.colorScheme.primary else Color.Red
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
