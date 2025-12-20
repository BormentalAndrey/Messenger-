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

// --- КОНСТАНТЫ И ЦВЕТА ---
private const val GRID_SIZE = 8
private const val JEWEL_TYPES = 6
private const val MAX_LEVELS = 200

// Неоновые цвета кристаллов
val gemColors = listOf(
    Color(0xFFFF0055), // Red Neon
    Color(0xFF00FFFF), // Cyan Neon
    Color(0xFF00FF00), // Green Neon
    Color(0xFFFFD700), // Gold
    Color(0xFFBF00FF), // Purple Neon
    Color(0xFFFFFFFF)  // White/Diamond
)

// --- МОДЕЛИ ДАННЫХ ---
data class LevelData(
    val levelNumber: Int,
    val targetScore: Int,
    val moves: Int
)

data class Position(val row: Int, val col: Int)

enum class GameState { PLAYING, WON, LOST, SWAPPING, PROCESSING }

// --- ГЕНЕРАТОР УРОВНЕЙ ---
fun generateLevel(level: Int): LevelData {
    // Алгоритмическая сложность: с каждым уровнем нужно больше очков, ходы варьируются
    val baseScore = 1000
    val scoreMultiplier = 250
    val movesBase = 25
    // Каждые 10 уровней ходов становится чуть меньше (минимум 15)
    val moves = (movesBase - (level / 10)).coerceAtLeast(15)
    
    return LevelData(
        levelNumber = level,
        targetScore = baseScore + (level * scoreMultiplier),
        moves = moves
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JewelsBlastScreen() {
    // Состояние уровня
    var currentLevelIndex by remember { mutableIntStateOf(1) }
    var levelData by remember { mutableStateOf(generateLevel(currentLevelIndex)) }
    
    // Состояние игры
    var score by remember { mutableIntStateOf(0) }
    var movesLeft by remember { mutableIntStateOf(levelData.moves) }
    var gameState by remember { mutableStateOf(GameState.PLAYING) }
    
    // Игровое поле (двумерный список ID цветов)
    var board by remember { 
        mutableStateOf(List(GRID_SIZE) { List(GRID_SIZE) { Random.nextInt(JEWEL_TYPES) } }) 
    }
    
    // Логика выделения
    var selectedPos by remember { mutableStateOf<Position?>(null) }
    
    val scope = rememberCoroutineScope()

    // Функция сброса уровня
    fun resetLevel(level: Int) {
        currentLevelIndex = level
        levelData = generateLevel(level)
        score = 0
        movesLeft = levelData.moves
        board = List(GRID_SIZE) { List(GRID_SIZE) { Random.nextInt(JEWEL_TYPES) } }
        gameState = GameState.PLAYING
        selectedPos = null
        
        // Начальная проверка на готовые совпадения (чтобы поле было честным)
        // В упрощенной версии просто оставляем как есть, игрок сразу получит комбо
    }

    // --- ЛОГИКА MATCH-3 ---
    
    // Проверка совпадений
    fun findMatches(currentBoard: List<List<Int>>): Set<Position> {
        val matches = mutableSetOf<Position>()
        
        // Горизонталь
        for (r in 0 until GRID_SIZE) {
            var count = 1
            for (c in 0 until GRID_SIZE - 1) {
                if (currentBoard[r][c] == currentBoard[r][c + 1]) {
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
        
        // Вертикаль
        for (c in 0 until GRID_SIZE) {
            var count = 1
            for (r in 0 until GRID_SIZE - 1) {
                if (currentBoard[r][c] == currentBoard[r + 1][c]) {
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

    // Обработка падения камней
    suspend fun processBoard() {
        gameState = GameState.PROCESSING
        var stability = false
        
        while (!stability) {
            val matches = findMatches(board)
            if (matches.isEmpty()) {
                stability = true
            } else {
                // 1. Подсчет очков
                val points = matches.size * 10 + (matches.size - 3) * 5
                score += points
                
                // 2. Удаление (ставим -1)
                val mutableBoard = board.map { it.toMutableList() }.toMutableList()
                matches.forEach { pos -> mutableBoard[pos.row][pos.col] = -1 }
                
                // Небольшая задержка для визуализации взрыва
                board = mutableBoard
                delay(200)

                // 3. Гравитация
                for (c in 0 until GRID_SIZE) {
                    val newCol = mutableListOf<Int>()
                    // Собираем существующие камни
                    for (r in 0 until GRID_SIZE) {
                        if (mutableBoard[r][c] != -1) newCol.add(mutableBoard[r][c])
                    }
                    // Дополняем сверху случайными
                    val itemsNeeded = GRID_SIZE - newCol.size
                    for (k in 0 until itemsNeeded) {
                        newCol.add(0, Random.nextInt(JEWEL_TYPES))
                    }
                    // Записываем обратно в столбец
                    for (r in 0 until GRID_SIZE) {
                        mutableBoard[r][c] = newCol[r]
                    }
                }
                board = mutableBoard
                delay(200)
            }
        }
        
        // Проверка условий победы/поражения
        if (score >= levelData.targetScore) {
            gameState = GameState.WON
        } else if (movesLeft <= 0) {
            gameState = GameState.LOST
        } else {
            gameState = GameState.PLAYING
        }
    }

    // Обработка клика
    fun onGemClick(pos: Position) {
        if (gameState != GameState.PLAYING) return

        if (selectedPos == null) {
            selectedPos = pos
        } else {
            val start = selectedPos!!
            // Проверка на соседство (сверху, снизу, слева, справа)
            val isAdjacent = (abs(start.row - pos.row) == 1 && start.col == pos.col) ||
                             (abs(start.col - pos.col) == 1 && start.row == pos.row)

            if (isAdjacent) {
                // СВАП
                gameState = GameState.SWAPPING
                movesLeft--
                
                scope.launch {
                    // 1. Визуально меняем
                    val mutableBoard = board.map { it.toMutableList() }.toMutableList()
                    val temp = mutableBoard[start.row][start.col]
                    mutableBoard[start.row][start.col] = mutableBoard[pos.row][pos.col]
                    mutableBoard[pos.row][pos.col] = temp
                    board = mutableBoard
                    
                    delay(200)
                    
                    // 2. Проверяем валидность хода
                    val matches = findMatches(board)
                    if (matches.isEmpty()) {
                        // Ход невалиден - возвращаем обратно
                        val tempBack = mutableBoard[start.row][start.col]
                        mutableBoard[start.row][start.col] = mutableBoard[pos.row][pos.col]
                        mutableBoard[pos.row][pos.col] = tempBack
                        board = mutableBoard
                        movesLeft++ // Возвращаем ход
                        gameState = GameState.PLAYING
                    } else {
                        // Ход успешен - запускаем каскад
                        processBoard()
                    }
                    selectedPos = null
                }
            } else {
                // Если кликнули далеко - просто меняем выделение
                selectedPos = pos
            }
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.background(Color.Black)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "УРОВЕНЬ $currentLevelIndex",
                            color = Color.Magenta,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black),
                    actions = {
                        IconButton(onClick = { resetLevel(currentLevelIndex) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = Color.White)
                        }
                    }
                )
                // Инфо панель
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("ХОДЫ", color = Color.Gray, fontSize = 12.sp)
                        Text("$movesLeft", color = if(movesLeft < 5) Color.Red else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ЦЕЛЬ: ${levelData.targetScore}", color = Color.Gray, fontSize = 12.sp)
                        Text("$score", color = Color.Cyan, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Прогресс бар
                val progress = (score.toFloat() / levelData.targetScore).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color.Magenta,
                    trackColor = Color.DarkGray,
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
            // ИГРОВОЕ ПОЛЕ
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_SIZE),
                modifier = Modifier
                    .padding(8.dp)
                    .aspectRatio(1f) // Квадратное поле
                    .border(2.dp, Brush.linearGradient(listOf(Color.Magenta, Color.Cyan)), RoundedCornerShape(8.dp)),
                userScrollEnabled = false
            ) {
                items(GRID_SIZE * GRID_SIZE) { index ->
                    val row = index / GRID_SIZE
                    val col = index % GRID_SIZE
                    val colorIndex = board[row][col]
                    
                    // Если индекс -1, значит камень уничтожен (рисуем пустоту или эффект)
                    if (colorIndex != -1) {
                        val isSelected = selectedPos?.row == row && selectedPos?.col == col
                        JewelItem(
                            color = gemColors[colorIndex],
                            isSelected = isSelected,
                            onClick = { onGemClick(Position(row, col)) }
                        )
                    } else {
                        Spacer(modifier = Modifier.padding(2.dp))
                    }
                }
            }
            
            // ДИАЛОГИ ПОБЕДЫ / ПОРАЖЕНИЯ
            if (gameState == GameState.WON) {
                GameDialog(
                    title = "УРОВЕНЬ ПРОЙДЕН!",
                    message = "Счёт: $score",
                    buttonText = "СЛЕДУЮЩИЙ",
                    color = Color.Green,
                    onAction = {
                        if (currentLevelIndex < MAX_LEVELS) {
                            resetLevel(currentLevelIndex + 1)
                        }
                    }
                )
            }
            
            if (gameState == GameState.LOST) {
                GameDialog(
                    title = "ХОДЫ ЗАКОНЧИЛИСЬ",
                    message = "Не хватило очков...",
                    buttonText = "ПОВТОРИТЬ",
                    color = Color.Red,
                    onAction = { resetLevel(currentLevelIndex) }
                )
            }
        }
    }
}

@Composable
fun JewelItem(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow), label = "scale"
    )

    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .scale(scale)
            .clickable(enabled = true, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Подсветка выбора
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = 0.3f), CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
        }

        // Отрисовка кристалла
        Canvas(modifier = Modifier.fillMaxSize(0.8f)) {
            val path = Path().apply {
                // Рисуем форму "бриллианта"
                moveTo(size.width / 2, 0f) // Верх
                lineTo(size.width, size.height / 2) // Право
                lineTo(size.width / 2, size.height) // Низ
                lineTo(0f, size.height / 2) // Лево
                close()
            }
            
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, color, color.copy(alpha = 0.8f)),
                    center = Offset(size.width * 0.3f, size.height * 0.3f),
                    radius = size.width
                ),
                style = Fill
            )
            
            // Блик
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = size.width * 0.1f,
                center = Offset(size.width * 0.3f, size.height * 0.3f)
            )
        }
    }
}

@Composable
fun GameDialog(
    title: String,
    message: String,
    buttonText: String,
    color: Color,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = BorderStroke(2.dp, color),
        modifier = Modifier.padding(32.dp).shadow(16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Star, null, tint = color, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) {
                Text(buttonText, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

