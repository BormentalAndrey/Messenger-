package com.kakdela.p2p.ui

import androidx.compose.foundation.BorderStroke  // ← ДОБАВЛЕН ИМПОРТ
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// ← ИСПРАВЛЕНО: isFixed теперь var
data class SudokuCell(var value: Int = 0, var isFixed: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SudokuScreen() {
    var board by remember { mutableStateOf(generateSudokuPuzzle()) }
    var selectedRow by remember { mutableStateOf(-1) }
    var selectedCol by remember { mutableStateOf(-1) }
    var isVictory by remember { mutableStateOf(false) }

    fun newGame() {
        board = generateSudokuPuzzle()
        selectedRow = -1
        selectedCol = -1
        isVictory = false
    }

    fun setNumber(num: Int) {
        if (selectedRow !in 0..8 || selectedCol !in 0..8) return
        if (board[selectedRow][selectedCol].isFixed) return

        val newBoard = board.map { it.toMutableList() }.toMutableList()
        newBoard[selectedRow][selectedCol].value = if (newBoard[selectedRow][selectedCol].value == num) 0 else num
        board = newBoard

        if (isBoardFull(board) && isValidSolution(board)) {
            isVictory = true
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "СУДОКУ",
                        color = Color.Green,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black),
                actions = {
                    IconButton(onClick = { newGame() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Новая игра", tint = Color.Green)
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isVictory) "🎉 ПОБЕДА! ГЕНИАЛЬНО!" else "Заполни поле правильно",
                color = if (isVictory) Color.Green else Color.Cyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            Card(
                modifier = Modifier
                    .size(360.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                border = BorderStroke(2.dp, Color.Green.copy(alpha = 0.6f))
            ) {
                Column {
                    for (row in 0 until 9) {
                        Row {
                            for (col in 0 until 9) {
                                val cell = board[row][col]
                                val isSelected = selectedRow == row && selectedCol == col
                                val hasError = cell.value != 0 && !isValidPlacement(board, row, col, cell.value)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(
                                            when {
                                                isSelected -> Color.Green.copy(alpha = 0.3f)
                                                (row / 3 == selectedRow / 3 && col / 3 == selectedCol / 3 && selectedRow != -1) -> Color(0xFF1A1A1A)
                                                else -> Color(0xFF0F0F0F)
                                            }
                                        )
                                        .border(
                                            width = if (col % 3 == 0) 3.dp else 1.dp,
                                            color = if (col % 3 == 0) Color.Green else Color.DarkGray
                                        )
                                        .border(
                                            width = if (row % 3 == 0) 3.dp else 1.dp,
                                            color = if (row % 3 == 0) Color.Green else Color.DarkGray
                                        )
                                        .clickable { selectedRow = row; selectedCol = col },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (cell.value == 0) "" else cell.value.toString(),
                                        fontSize = 32.sp,
                                        fontWeight = if (cell.isFixed) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = when {
                                            cell.isFixed -> Color.White
                                            hasError -> Color.Red
                                            else -> Color.Cyan
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                for (chunk in listOf(1..5, 6..9)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        for (num in chunk) {
                            Button(
                                onClick = { setNumber(num) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Green.copy(alpha = 0.8f)),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Text(num.toString(), fontSize = 20.sp, color = Color.Black)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { setNumber(0) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                    modifier = Modifier.width(160.dp)
                ) {
                    Text("Стереть", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun generateSudokuPuzzle(): List<MutableList<SudokuCell>> {
    val board = MutableList(9) { MutableList(9) { SudokuCell() } }
    fillBoard(board)
    removeCells(board, 45)
    return board
}

private fun fillBoard(board: MutableList<MutableList<SudokuCell>>): Boolean {
    for (row in 0..8) {
        for (col in 0..8) {
            if (board[row][col].value == 0) {
                val nums = (1..9).shuffled()
                for (num in nums) {
                    if (isValidPlacement(board, row, col, num)) {
                        board[row][col].value = num
                        if (fillBoard(board)) return true
                        board[row][col].value = 0
                    }
                }
                return false
            }
        }
    }
    return true
}

private fun removeCells(board: MutableList<MutableList<SudokuCell>>, count: Int) {
    var removed = 0
    while (removed < count) {
        val row = Random.nextInt(9)
        val col = Random.nextInt(9)
        if (board[row][col].value != 0) {
            board[row][col].apply {
                value = 0
                isFixed = false
            }
            removed++
        }
    }
    // ← ИСПРАВЛЕНО: теперь isFixed = var, можно присваивать
    board.forEach { rowList ->
        rowList.forEach { cell ->
            if (cell.value != 0) cell.isFixed = true
        }
    }
}

private fun isValidPlacement(board: List<List<SudokuCell>>, row: Int, col: Int, num: Int): Boolean {
    for (i in 0..8) {
        if (board[row][i].value == num || board[i][col].value == num) return false
    }
    val r = row / 3 * 3
    val c = col / 3 * 3
    for (i in r until r + 3) for (j in c until c + 3) if (board[i][j].value == num) return false
    return true
}

private fun isBoardFull(board: List<List<SudokuCell>>) = board.all { it.all { cell -> cell.value != 0 } }

// ← ИСПРАВЛЕНО: правильная реализация без flatten и ошибок типов
private fun isValidSolution(board: List<List<SudokuCell>>): Boolean {
    for (row in 0..8) {
        for (col in 0..8) {
            if (!isValidPlacement(board, row, col, board[row][col].value)) {
                return false
            }
        }
    }
    return true
}
