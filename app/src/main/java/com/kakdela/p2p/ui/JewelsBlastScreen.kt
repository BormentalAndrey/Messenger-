package com.kakdela.p2p.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

// ==================== КОНСТАНТЫ И ЦВЕТА ====================
private const val GRID_SIZE = 8              // 8×8 поле
private const val JEWEL_TYPES = 6            // 6 видов кристаллов
private const val MAX_LEVELS = 200            // Максимальное количество уровней

// Неоновые цвета кристаллов
private val gemColors = listOf(
    Color(0xFFFF0055), // Красный неон
    Color(0xFF00FFFF), // Циан
    Color(0xFF00FF00), // Зелёный неон
    Color(0xFFFFD700), // Золотой
    Color(0xFFBF00FF), // Пурпурный неон
    Color(0xFFFFFFFF)  // Белый/бриллиант
)

// ==================== МОДЕЛИ ДАННЫХ ====================
data class LevelData(
    val levelNumber: Int,
    val targetScore: Int,
    val moves: Int
)

data class Position(val row: Int, val col: Int)

enum class GameState { PLAYING, WON, LOST, SWAPPING, PROCESSING }

// ==================== ГЕНЕРАТОР УРОВНЕЙ ====================
private fun generateLevel(level: Int): LevelData {
    val baseScore = 1000
    val scoreMultiplier = 250
    val movesBase = 30
    val moves = (movesBase - (level / 10)).coerceAtLeast(15)

    return LevelData(
        levelNumber = level,
        targetScore = baseScore + level * scoreMultiplier,
        moves = moves
    )
}

// ==================== ОСНОВНОЙ ЭКРАН ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JewelsBlastScreen() {
    var currentLevel by remember { mutableIntStateOf(1) }
    var levelData by remember { mutableStateOf(generateLevel(currentLevel)) }

    var score by remember { mutableIntStateOf(0) }
    var movesLeft by remember { mutableIntStateOf(levelData.moves) }
    var gameState by remember { mutableStateOf(GameState.PLAYING) }

    // Игровое поле: -1 = пусто (удалённый камень)
    var board by remember {
        mutableStateOf(List(GRID_SIZE) { List(GRID_SIZE) { Random.nextInt(JEWEL_TYPES) } })
    }

    var selectedPos by remember { mutableStateOf<Position?>(null) }
    val scope = rememberCoroutineScope()

    // Сброс уровня
    fun resetLevel(newLevel: Int) {
        currentLevel = newLevel
        levelData = generateLevel(newLevel)
        score = 0
        movesLeft = levelData.moves
        board = List(GRID_SIZE) { List(GRID_SIZE) { Random.nextInt(JEWEL_TYPES) } }
        gameState = GameState.PLAYING
        selectedPos = null
    }

    // Поиск совпадений (3+ в ряд/столбец)
    fun findMatches(currentBoard: List<List<Int>>): Set<Position> {
        val matches = mutableSetOf<Position>()

        // Горизонтальные
        for (r in 0 until GRID_SIZE) {
            var count = 1
            for (c in 0 until GRID_SIZE - 1) {
                if (currentBoard[r][c] == currentBoard[r][c + 1] && currentBoard[r][c] != -1) {
                    count++
                } else {
                    if (count >= 3) {
                        for (k in 0 until count) matches.add(Position(r, c - k))
                    }
                    count = 1
                }
            }
            if (count >= 3) {
                for (k in 0 until count) matches.add(Position(r, GRID_SIZE - 1 - k))
            }
        }

        // Вертикальные
        for (c in 0 until GRID_SIZE) {
            var count = 1
            for (r in 0 until GRID_SIZE - 1) {
                if (currentBoard[r][c] == currentBoard[r + 1][c] && currentBoard[r][c] != -1) {
                    count++
                } else {
                    if (count >= 3) {
                        for (k in 0 until count) matches.add(Position(r - k, c))
                    }
                    count = 1
                }
            }
            if (count >= 3) {
                for (k in 0 until count) matches.add(Position(GRID_SIZE - 1 - k, c))
            }
        }
        return matches
    }

    // Обработка удаления, падения и каскада
    suspend fun processBoard() {
        gameState = GameState.PROCESSING
        var hasMatches: Boolean

        do {
            val matches = findMatches(board)
            hasMatches = matches.isNotEmpty()

            if (hasMatches) {
                // Очки
                val points = matches.size * 10 + (matches.size - 3) * 20
                score += points

                // Удаляем (ставим -1)
                val mutableBoard = board.map { it.toMutableList() }.toMutableList()
                matches.forEach { mutableBoard[it.row][it.col] = -1 }
                board = mutableBoard
                delay(300) // Время на "взрыв"

                // Гравитация + новые камни сверху
                for (col in 0 until GRID_SIZE) {
                    val column = mutableListOf<Int>()
                    for (row in 0 until GRID_SIZE) {
                        if (mutableBoard[row][col] != -1) column.add(mutableBoard[row][col])
                    }
                    val missing = GRID_SIZE - column.size
                    repeat(missing) { column.add(0, Random.nextInt(JEWEL_TYPES)) }
                    for (row in 0 until GRID_SIZE) {
                        mutableBoard[row][col] = column[row]
                    }
                }
                board = mutableBoard
                delay(300) // Время на падение
            }
        } while (hasMatches)

        // Проверка конца уровня
        when {
            score >= levelData.targetScore -> gameState = GameState.WON
            movesLeft <= 0 -> gameState = GameState.LOST
            else -> gameState = GameState.PLAYING
        }
    }

    // Обработка клика по кристаллу
    fun onGemClick(pos: Position) {
        if (gameState != GameState.PLAYING) return

        if (selectedPos == null) {
            selectedPos = pos
            return
        }

        val first = selectedPos!!
        val adjacent = (abs(first.row - pos.row) + abs(first.col - pos.col)) == 1

        if (adjacent) {
            movesLeft--
            gameState = GameState.SWAPPING

            scope.launch {
                // Свап
                val mutableBoard = board.map { it.toMutableList() }.toMutableList()
                val temp = mutableBoard[first.row][first.col]
                mutableBoard[first.row][first.col] = mutableBoard[pos.row][pos.col]
                mutableBoard[pos.row][pos.col] = temp
                board = mutableBoard
                delay(200)

                // Проверяем, есть ли совпадения
                if (findMatches(board).isEmpty()) {
                    // Неверный ход — возвращаем
                    val tempBack = mutableBoard[first.row][first.col]
                    mutableBoard[first.row][first.col] = mutableBoard[pos.row][pos.col]
                    mutableBoard[pos.row][pos.col] = tempBack
                    board = mutableBoard
                    movesLeft++
                    gameState = GameState.PLAYING
                } else {
                    processBoard()
                }
                selectedPos = null
            }
        } else {
            selectedPos = pos // Просто новое выделение
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.background(Color.Black)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Jewels Blast • УРОВЕНЬ $currentLevel",
                            color = Color.Magenta,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black),
                    actions = {
                        IconButton(onClick = { resetLevel(currentLevel) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = Color.White)
                        }
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("ХОДЫ", color = Color.Gray, fontSize = 12.sp)
                        Text("$movesLeft", color = if (movesLeft < 6) Color.Red else Color.White,
                            fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ЦЕЛЬ: ${levelData.targetScore}", color = Color.Gray, fontSize = 12.sp)
                        Text("$score", color = Color.Cyan, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }

                val progress = (score.toFloat() / levelData.targetScore).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color.Magenta,
                    trackColor = Color.DarkGray
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_SIZE),
                modifier = Modifier
                    .padding(16.dp)
                    .aspectRatio(1f)
                    .border(4.dp, Brush.linearGradient(listOf(Color.Magenta, Color.Cyan)), RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0A0A)),
                userScrollEnabled = false
            ) {
                items(GRID_SIZE * GRID_SIZE) { index ->
                    val row = index / GRID_SIZE
                    val col = index % GRID_SIZE
                    val type = board[row][col]

                    if (type != -1) {
                        JewelItem(
                            color = gemColors[type],
                            isSelected = selectedPos?.row == row && selectedPos?.col == col,
                            onClick = { onGemClick(Position(row, col)) }
                        )
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }
            }

            // Диалог победы
            AnimatedVisibility(visible = gameState == GameState.WON) {
                GameDialog(
                    title = "УРОВЕНЬ ПРОЙДЕН!",
                    message = "Счёт: $score",
                    buttonText = if (currentLevel < MAX_LEVELS) "СЛЕДУЮЩИЙ" else "ПОБЕДА!",
                    color = Color(0xFF00FF00),
                    onAction = {
                        if (currentLevel < MAX_LEVELS) resetLevel(currentLevel + 1)
                    }
                )
            }

            // Диалог поражения
            AnimatedVisibility(visible = gameState == GameState.LOST) {
                GameDialog(
                    title = "ХОДЫ ЗАКОНЧИЛИСЬ",
                    message = "Набрано: $score из ${levelData.targetScore}",
                    buttonText = "ПОВТОРИТЬ",
                    color = Color.Red,
                    onAction = { resetLevel(currentLevel) }
                )
            }
        }
    }
}

// ==================== КОМПОНЕНТ КРИСТАЛЛА ====================
@Composable
fun JewelItem(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "jewel_scale"
    )

    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .scale(scale)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = 0.4f), CircleShape)
                    .border(3.dp, Color.White, CircleShape)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize(0.85f)) {
            val path = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width, size.height / 2)
                lineTo(size.width / 2, size.height)
                lineTo(0f, size.height / 2)
                close()
            }

            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, color, color.copy(alpha = 0.7f)),
                    center = Offset(size.width * 0.3f, size.height * 0.3f),
                    radius = size.width
                ),
                style = Fill
            )

            // Блик
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = size.width * 0.15f,
                center = Offset(size.width * 0.3f, size.height * 0.3f)
            )
        }
    }
}

// ==================== ДИАЛОГ ====================
@Composable
fun GameDialog(title: String, message: String, buttonText: String, color: Color, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        border = BorderStroke(3.dp, color),
        modifier = Modifier
            .padding(32.dp)
            .shadow(24.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = color, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text(title, color = color, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Text(message, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.height(56.dp).width(200.dp)
            ) {
                Text(buttonText, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
