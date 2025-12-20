package com.kakdela.p2p.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    var scoreX by remember { mutableStateOf(0) }
    var scoreO by remember { mutableStateOf(0) }
    var draws by remember { mutableStateOf(0) }

    fun checkWinnerForBoard(b: List<String>): String? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (line in lines) {
            if (b[line[0]].isNotEmpty() &&
                b[line[0]] == b[line[1]] &&
                b[line[1]] == b[line[2]]
            ) {
                return b[line[0]]
            }
        }
        return if (b.all { it.isNotEmpty() }) "draw" else null
    }

    fun checkWinner(): String? = checkWinnerForBoard(board)

    fun aiMoveEasy(): Int? =
        board.indices.filter { board[it].isEmpty() }.randomOrNull()

    fun aiMoveMedium(): Int? {
        // победный ход
        for (i in 0 until 9) {
            if (board[i].isEmpty()) {
                val t = board.toMutableList()
                t[i] = "O"
                if (checkWinnerForBoard(t) == "O") return i
            }
        }
        // блокировка игрока
        for (i in 0 until 9) {
            if (board[i].isEmpty()) {
                val t = board.toMutableList()
                t[i] = "X"
                if (checkWinnerForBoard(t) == "X") return i
            }
        }
        return aiMoveEasy()
    }

    fun aiMoveHard(): Int? {
        fun minimax(b: List<String>, isMax: Boolean): Int {
            val result = checkWinnerForBoard(b)
            if (result == "O") return 10
            if (result == "X") return -10
            if (result == "draw") return 0

            val empty = b.indices.filter { b[it].isEmpty() }
            if (isMax) {
                var best = -1000
                for (cell in empty) {
                    val t = b.toMutableList()
                    t[cell] = "O"
                    best = maxOf(best, minimax(t, false))
                }
                return best
            } else {
                var best = 1000
                for (cell in empty) {
                    val t = b.toMutableList()
                    t[cell] = "X"
                    best = minOf(best, minimax(t, true))
                }
                return best
            }
        }

        val empty = board.indices.filter { board[it].isEmpty() }
        var bestMove: Int? = null
        var bestScore = -1000

        for (c in empty) {
            val t = board.toMutableList()
            t[c] = "O"
            val score = minimax(t, false)
            if (score > bestScore) {
                bestScore = score
                bestMove = c
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

        when (val w = checkWinner()) {
            "X" -> { scoreX++; winner = "X" }
            "O" -> { scoreO++; winner = "O" }
            "draw" -> { draws++; winner = "draw" }
        }
    }

    fun playerTurn(index: Int) {
        if (board[index].isNotEmpty() || winner.isNotEmpty()) return

        board = board.toMutableList().apply { this[index] = "X" }
        when (val w = checkWinner()) {
            "X" -> { scoreX++; winner = "X"; return }
            "O" -> { scoreO++; winner = "O"; return }
            "draw" -> { draws++; winner = "draw"; return }
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
                title = { Text("Крестики-нолики", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(pad),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))

            Text(
                "X: $scoreX  |  O: $scoreO  |  Ничья: $draws",
                fontSize = 18.sp,
                color = Color.White
            )

            Spacer(Modifier.height(10.dp))

            Row {
                Difficulty.values().forEach {
                    Button(
                        onClick = { difficulty = it },
                        colors = ButtonDefaults.buttonColors(
                            if (difficulty == it) MaterialTheme.colorScheme.primary
                            else Color.DarkGray
                        ),
                        modifier = Modifier.padding(4.dp)
                    ) { Text(it.name) }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (winner.isNotEmpty()) {
                Text(
                    when (winner) {
                        "X" -> "🎉 Ты выиграл!"
                        "O" -> "🤖 Робот победил!"
                        "draw" -> "😐 Ничья!"
                        else -> ""
                    },
                    color = Color.White,
                    fontSize = 28.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = { restart() }) {
                    Text("Играть снова")
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(Modifier.size(330.dp)) {
                Column(Modifier.fillMaxSize()) {
                    for (i in 0..2) {
                        Row(Modifier.weight(1f)) {
                            for (j in 0..2) {
                                val index = i * 3 + j
                                val value = board[index]

                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(Color(0xFF191919))
                                        .clickable { playerTurn(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedVisibility(
                                        visible = value.isNotEmpty(),
                                        enter = scaleIn(),
                                        exit = scaleOut()
                                    ) {
                                        Text(
                                            value,
                                            fontSize = 48.sp,
                                            color = if (value == "X")
                                                MaterialTheme.colorScheme.primary
                                            else Color.Red
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
