package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SudokuScreen() {
    // 9x9 доска: 0 = пусто, иначе число от 1 до 9
    var board by remember { mutableStateOf(createSudokuPuzzle()) }
    var selectedRow by remember { mutableStateOf(-1) }
    var selectedCol by remember { mutableStateOf(-1) }
    var isVictory by remember { mutableStateOf(false) }

    fun newGame() {
        board = createSudokuPuzzle()
        selectedRow = -1
        selectedCol = -1
        isVictory = false
    }

    fun setNumber(number: Int) {
        if (selectedRow in 0..8 && selectedCol in 0..8) {
            if (board[selectedRow][selectedCol].isFixed) return // нельзя менять исходные

            val newBoard = board.map { it.toMutableList() }.toMutableList()
            newBoard[selectedRow][selectedCol].value = number
            board = newBoard

            // Проверка на победу
            if (isBoardComplete(board) && isValidSolution(board)) {
                isVictory = true
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Судоку", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF1976D2))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isVictory) "🎉 Победа! Отлично!" else "Заполните поле",
                color = if (isVictory) Color(0xFF4CAF50) else Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            // Игровое поле
            Box(
                modifier = Modifier
                    .size(360.dp)
                    .padding(8.dp)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
            ) {
                Column {
                    for (row in 0 until 9) {
                        Row {
                            for (col in 0 until 9) {
                                val cell = board[row][col]
                                val isSelected = selectedRow == row && selectedCol == col
                                val hasConflict = cell.value != 0 && !isValidPlacement(board, row, col, cell.value)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(1.dp)
                                        .background(
                                            color = when {
                                                isSelected -> Color(0xFF42A5F5)
                                                (row / 3 == selectedRow / 3 && col / 3 == selectedCol / 3 && selectedRow != -1) -> Color(0xFF2A2A2A)
                                                else -> Color(0xFF1E1E1E)
                                            }
                                        )
                                        .border(
                                            width = when {
                                                col % 3 == 0 -> 3.dp
                                                col % 3 == 2 -> 3.dp
                                                else -> 1.dp
                                            },
                                            color = if (col % 3 == 2 && col != 8) Color(0xFFBBBBBB) else Color.Transparent
                                        )
                                        .border(
                                            width = when {
                                                row % 3 == 0 -> 3.dp
                                                row % 3 == 2 -> 3.dp
                                                else -> 1.dp
                                            },
                                            color = if (row % 3 == 2 && row != 8) Color(0xFFBBBBBB) else Color.Transparent
                                        )
                                        .clickable { selectedRow = row; selectedCol = col },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (cell.value == 0) "" else cell.value.toString(),
                                        fontSize = 28.sp,
                                        fontWeight = if (cell.isFixed) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = when {
                                            cell.isFixed -> Color(0xFFFFFFFF)
                                            hasConflict -> Color.Red
                                            else -> Color(0xFF90CAF9)
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Кнопки чисел 1–9
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                for (num in 1..9) {
                    Button(
                        onClick = { setNumber(num) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(num.toString(), fontSize = 20.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { setNumber(0) }, // стереть
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
                ) {
                    Text("Стереть")
                }

                Button(
                    onClick = { newGame() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                ) {
                    Text("Новая игра")
                }

                Button(
                    onClick = {
                        isVictory = isBoardComplete(board) && isValidSolution(board)
                    }
                ) {
                    Text("Проверить")
                }
            }
        }
    }
}

// Модель ячейки
data class Cell(var value: Int, val isFixed: Boolean)

// Генерация полной доски + удаление чисел
fun createSudokuPuzzle(difficulty: Int = 40): List<MutableList<Cell>> {
    val board = MutableList(9) { MutableList(9) { Cell(0, false) } }
    fillBoard(board)
    removeNumbers(board, difficulty)
    return board
}

// Заполнение доски валидным решением
fun fillBoard(board: MutableList<MutableList<Cell>>): Boolean {
    for (row in 0 until 9) {
        for (col in 0 until 9) {
            if (board[row][col].value == 0) {
                val numbers = (1..9).shuffled()
                for (num in numbers) {
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

// Удаление чисел для создания головоломки
fun removeNumbers(board: MutableList<MutableList<Cell>>, count: Int) {
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
    // Оставшиеся — фиксированные
    for (r in board.indices) {
        for (c in board[r].indices) {
            if (board[r][c].value != 0) {
                board[r][c].isFixed = true
            }
        }
    }
}

// Проверка, можно ли поставить число
fun isValidPlacement(board: List<List<Cell>>, row: Int, col: Int, num: Int): Boolean {
    // Строка
    for (c in 0 until 9) if (board[row][c].value == num) return false
    // Столбец
    for (r in 0 until 9) if (board[r][col].value == num) return false
    // Блок 3x3
    val startRow = row / 3 * 3
    val startCol = col / 3 * 3
    for (r in startRow until startRow + 3) {
        for (c in startCol until startCol + 3) {
            if (board[r][c].value == num) return false
        }
    }
    return true
}

fun isBoardComplete(board: List<List<Cell>>): Boolean =
    board.all { row -> row.all { it.value != 0 } }

fun isValidSolution(board: List<List<Cell>>): Boolean =
    board.indices.all { row ->
        (0..8).all { col -> isValidPlacement(board, row, col, board[row][col].value) }
    }
