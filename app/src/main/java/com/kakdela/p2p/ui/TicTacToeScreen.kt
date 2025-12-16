package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeScreen() {
    var board by remember { mutableStateOf(List(9) { "" }) }
    var isPlayerTurn by remember { mutableStateOf(true) }
    var winner by remember { mutableStateOf("") }

    fun checkWinner(): String? {
        val lines = listOf(
            listOf(0,1,2), listOf(3,4,5), listOf(6,7,8),
            listOf(0,3,6), listOf(1,4,7), listOf(2,5,8),
            listOf(0,4,8), listOf(2,4,6)
        )
        for (line in lines) {
            if (board[line[0]].isNotEmpty() && board[line[0]] == board[line[1]] && board[line[1]] == board[line[2]]) {
                return board[line[0]]
            }
        }
        return if (board.all { it.isNotEmpty() }) "Ничья" else null
    }

    fun aiMove() {
        val emptyIndices = board.indices.filter { board[it].isEmpty() }
        if (emptyIndices.isNotEmpty() && winner.isEmpty()) {
            val move = emptyIndices.random()
            board = board.toMutableList().apply { this[move] = "O" }
            winner = checkWinner() ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Крестики-нолики", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (winner.isNotEmpty()) {
                Text(
                    text = if (winner == "Ничья") "Ничья!" else "Победил $winner!",
                    color = Color.White,
                    fontSize = 32.sp,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = {
                    board = List(9) { "" }
                    isPlayerTurn = true
                    winner = ""
                }) {
                    Text("Играть снова")
                }
            } else {
                Text("Твой ход (X)", color = Color.Gray, fontSize = 20.sp, modifier = Modifier.padding(16.dp))
            }

            Column(modifier = Modifier.size(300.dp)) {
                for (i in 0..2) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (j in 0..2) {
                            val index = i * 3 + j
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(Color(0xFF1A1A1A))
                                    .clickable(enabled = board[index].isEmpty() && winner.isEmpty() && isPlayerTurn) {
                                        board = board.toMutableList().apply { this[index] = "X" }
                                        isPlayerTurn = false
                                        winner = checkWinner() ?: ""
                                        if (winner.isEmpty()) aiMove()
                                        isPlayerTurn = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = board[index],
                                    fontSize = 48.sp,
                                    color = if (board[index] == "X") MaterialTheme.colorScheme.primary else Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
