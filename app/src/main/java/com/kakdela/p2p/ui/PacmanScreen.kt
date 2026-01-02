package com.kakdela.p2p.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════════════════════════════════
// 🎮 ПОЛНАЯ КАРТА PACMAN (28x31) - точно как в оригинале
// ═══════════════════════════════════════════════════════════════════════════════════════════════════════
private val PACMAN_MAP = arrayOf(
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,2,1),
    intArrayOf(1,3,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,3,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,2,1,1,1,1,1,1,1,1,1,2,1,2,1,1,1,1,1,2,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,1,1,2,2,0,0,2,2,1,1,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,1,1,1,1,1,2,1,2,1,1,2,0,0,0,0,2,1,1,2,1,1,1,1,1,1,1,1),
    intArrayOf(0,0,0,0,0,1,2,1,0,0,0,0,0,0,0,0,0,0,0,0,1,2,1,0,0,0,0,0),
    intArrayOf(0,0,0,0,0,1,2,1,0,1,1,1,4,4,4,4,1,1,1,0,1,2,1,0,0,0,0,0),
    intArrayOf(0,0,0,0,0,1,2,1,0,1,0,0,0,0,0,0,0,0,0,1,0,1,2,1,0,0,0,0),
    intArrayOf(1,1,1,1,1,1,2,1,0,1,0,0,0,4,4,0,0,0,0,1,0,1,2,1,1,1,1,1),
    intArrayOf(0,0,0,0,0,0,2,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,2,0,0,0,0,0),
    intArrayOf(1,1,1,1,1,1,2,1,0,1,0,0,0,0,0,0,0,0,0,1,0,1,2,1,1,1,1,1),
    intArrayOf(0,0,0,0,0,1,2,1,0,1,1,1,4,4,4,4,1,1,1,0,1,2,1,0,0,0,0,0),
    intArrayOf(0,0,0,0,0,1,2,1,0,0,0,0,0,0,0,0,0,0,0,0,1,2,1,0,0,0,0,0),
    intArrayOf(1,1,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,1,4,4,1,2,1,1,2,1,4,4,1,1,2,1,1,1,1,2,1),
    intArrayOf(1,2,2,2,1,1,2,2,2,1,1,1,2,1,1,2,1,1,1,2,2,2,1,1,2,2,2,1),
    intArrayOf(1,1,1,2,1,1,2,1,2,1,1,1,2,1,1,2,1,1,1,2,1,1,2,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,1,2,2,2,2,2,0,0,2,2,2,2,2,1,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,1,1,1,2,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1)
)

// ═══════════════════════════════════════════════════════════════════════════════════════════════════════
// 🎮 НАСТРОЙКИ ИГРЫ
// ═══════════════════════════════════════════════════════════════════════════════════════════════════════
enum class Direction { UP, DOWN, LEFT, RIGHT, NONE }
enum class GameState { COUNTDOWN, PLAYING, PAUSED, GAME_OVER, LEVEL_COMPLETE }

data class Entity(
    var x: Float,
    var y: Float,
    var dir: Direction = Direction.NONE,
    var nextDir: Direction = Direction.NONE,
    var targetX: Float = 0f,
    var targetY: Float = 0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacmanScreen(
    onBack: () -> Unit = {}
) {
    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════
    // 🎮 ИГРОВЫЕ СОСТОЯНИЯ
    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════
    var gameState by remember { mutableStateOf(GameState.COUNTDOWN) }
    var level by remember { mutableIntStateOf(1) }
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var dotsEaten by remember { mutableIntStateOf(0) }
    var totalDots by remember { mutableIntStateOf(0) }
    
    // Игровые объекты
    var pacman by remember { mutableStateOf(Entity(13.5f, 23f)) }
    val ghosts = remember {
        mutableStateListOf(
            Entity(13.5f, 11f, Direction.LEFT),  // Red (Blinky)
            Entity(12.5f, 14f, Direction.DOWN),   // Pink (Pinky)
            Entity(14.5f, 14f, Direction.UP),    // Blue (Inky)
            Entity(13.5f, 14f, Direction.RIGHT)  // Orange (Clyde)
        )
    }
    
    // Power mode
    var powerMode by remember { mutableStateOf(false) }
    var powerTimer by remember { mutableIntStateOf(0) }
    
    // Анимации
    val pacmanMouthAnim by animateFloatAsState(
        targetValue = if (gameState == GameState.PLAYING) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(200),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val ghostAnim by animateFloatAsState(
        targetValue = if (gameState == GameState.PLAYING) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(300),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val countdownAnim by animateFloatAsState(
        targetValue = if (gameState == GameState.COUNTDOWN) 1f else 0f,
        animationSpec = spring()
    )
    
    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════
    // 🗺️ РАБОТА С КАРТОЙ
    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════
    val maze = remember(level) {
        PACMAN_MAP.map { it.toMutableList().toIntArray() }.toTypedArray().also { map ->
            totalDots = 0
            map.forEach { row ->
                row.forEach { cell ->
                    if (cell == 2 || cell == 3) totalDots++
                }
            }
        }
    }
    
    // Подсчет оставшихся точек
    val dotsLeft by derivedStateOf {
        var count = 0
        maze.forEach { row ->
            row.forEach { if (it == 2 || it == 3) count++ }
        }
        count
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════
    // 🎮 ИНИЦИАЛИЗАЦИЯ УРОВНЯ
    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════
    LaunchedEffect(level) {
        pacman = Entity(13.5f, 23f, Direction.LEFT)
        ghosts.clear()
        ghosts.addAll(
            listOf(
                Entity(13.5f, 11f, Direction.LEFT),
                Entity(12.5f, 14f, Direction.DOWN),
                Entity(14.5f, 14f, Direction.UP),
                Entity(13.5f, 14f, Direction.RIGHT)
            )
        )
        dotsEaten = 0
        powerMode = false
        gameState = GameState.COUNTDOWN
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════
    // ⚡ ОСНОВНОЙ ИГРОВОЙ ЦИКЛ (60 FPS)
    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════
    LaunchedEffect(gameState) {
        if (gameState != GameState.PLAYING) return@LaunchedEffect
        
        var lastTime = 0L
        while (gameState == GameState.PLAYING) {
            withFrameNanos { time ->
                if (time - lastTime > 16_666_666L) { // ~60 FPS
                    lastTime = time
                    
                    updatePacman(maze, pacman)
                    updateGhosts(maze, ghosts, pacman, powerMode)
                    updateCollisions(maze, pacman, ghosts, powerMode, powerTimer) { 
                        score += it; checkLevelComplete(dotsLeft, ::levelUp) 
                    }
                    
                    // Power mode timer
                    if (powerMode && powerTimer > 0) {
                        powerTimer--
                        if (powerTimer <= 0) powerMode = false
                    }
                }
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════
    // ⏱️ COUNTDOWN (3-2-1-GO!)
    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════
    LaunchedEffect(gameState) {
        if (gameState == GameState.COUNTDOWN) {
            repeat(4) { i ->
                delay(1000)
                if (gameState != GameState.COUNTDOWN) return@repeat
            }
            gameState = GameState.PLAYING
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PAC-MAN",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Yellow
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Text(
                        text = "L:$level  $score",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F23))
                .padding(padding)
        ) {
            // 🎮 ГЛАВНЫЙ CANVAS
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellSize = minOf(
                    (size.width - 40.dp.toPx()) / 28,
                    (size.height - 200.dp.toPx()) / 31
                )
                val mazeOffsetX = (size.width - 28 * cellSize) / 2
                val mazeOffsetY = (size.height - 31 * cellSize) / 2 + 40.dp.toPx()
                
                drawGame(
                    maze = maze,
                    pacman = pacman,
                    ghosts = ghosts,
                    cellSize = cellSize,
                    mazeOffset = Offset(mazeOffsetX, mazeOffsetY),
                    pacmanMouthAnim = pacmanMouthAnim,
                    ghostAnim = ghostAnim,
                    powerMode = powerMode,
                    gameState = gameState,
                    countdownAnim = countdownAnim,
                    lives = lives,
                    score = score,
                    highScore = highScore,
                    level = level
                )
            }
            
            // 🎮 УПРАВЛЕНИЕ СВАЙПОМ
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            val sensitivity = 30f
                            when {
                                abs(dragAmount.x) > abs(dragAmount.y) && abs(dragAmount.x) > sensitivity -> {
                                    pacman.nextDir = if (dragAmount.x > 0) Direction.RIGHT else Direction.LEFT
                                }
                                abs(dragAmount.y) > sensitivity -> {
                                    pacman.nextDir = if (dragAmount.y > 0) Direction.DOWN else Direction.UP
                                }
                            }
                        }
                    }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════════════
// 🧠 ЛОГИКА ИГРЫ
// ═══════════════════════════════════════════════════════════════════════════════════════════════════════

private fun updatePacman(maze: Array<IntArray>, pacman: Entity) {
    // Проверка поворота
    if (canTurn(maze, pacman)) {
        pacman.dir = pacman.nextDir
        pacman.nextDir = pacman.dir
    }
    
    // Движение
    val speed = 0.18f
    val nextX = when (pacman.dir) {
        Direction.LEFT -> pacman.x - speed
        Direction.RIGHT -> pacman.x + speed
        else -> pacman.x
    }
    val nextY = when (pacman.dir) {
        Direction.UP -> pacman.y - speed
        Direction.DOWN -> pacman.y + speed
        else -> pacman.y
    }
    
    // Телепорт через стены
    var finalX = if (nextX < 0) 27f else if (nextX >= 28) 0f else nextX
    val cellX = finalX.toInt().coerceIn(0, 27)
    val cellY = nextY.toInt().coerceIn(0, 30)
    
    if (maze[cellY][cellX] != 1) {
        pacman.x = finalX
        pacman.y = nextY
    }
}

private fun canTurn(maze: Array<IntArray>, entity: Entity): Boolean {
    val cellX = entity.x.toInt()
    val cellY = entity.y.toInt()
    val nextCellX = when (entity.nextDir) {
        Direction.LEFT -> cellX - 1
        Direction.RIGHT -> cellX + 1
        else -> cellX
    }
    val nextCellY = when (entity.nextDir) {
        Direction.UP -> cellY - 1
        Direction.DOWN -> cellY + 1
        else -> cellY
    }
    return maze[nextCellY.coerceIn(0, 30)][nextCellX.coerceIn(0, 27)] != 1
}

private fun updateGhosts(
    maze: Array<IntArray>,
    ghosts: List<Entity>,
    pacman: Entity,
    powerMode: Boolean
) {
    ghosts.forEachIndexed { index, ghost ->
        val speed = if (powerMode) 0.12f else 0.15f
        val dirs = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT)
        
        // Простой ИИ: преследование + случайность
        val bestDir = dirs.minByOrNull { dir ->
            val testX = when(dir) {
                Direction.LEFT -> ghost.x - 0.5f
                Direction.RIGHT -> ghost.x + 0.5f
                else -> ghost.x
            }
            val testY = when(dir) {
                Direction.UP -> ghost.y - 0.5f
                Direction.DOWN -> ghost.y + 0.5f
                else -> ghost.y
            }
            val dist = hypot(testX - pacman.x, testY - pacman.y)
            val cellX = testX.toInt().coerceIn(0, 27)
            val cellY = testY.toInt().coerceIn(0, 30)
            if (maze[cellY][cellX] == 1) 999f else dist + Random.nextFloat() * 0.5f
        } ?: ghost.dir
        
        val nextX = when(bestDir) {
            Direction.LEFT -> ghost.x - speed
            Direction.RIGHT -> ghost.x + speed
            else -> ghost.x
        }
        val nextY = when(bestDir) {
            Direction.UP -> ghost.y - speed
            Direction.DOWN -> ghost.y + speed
            else -> ghost.y
        }
        
        val cellX = nextX.toInt().coerceIn(0, 27)
        val cellY = nextY.toInt().coerceIn(0, 30)
        
        if (maze[cellY][cellX] != 1) {
            ghosts[index] = ghost.copy(
                x = if (nextX < 0) 27f else if (nextX >= 28) 0f else nextX,
                y = nextY,
                dir = bestDir
            )
        }
    }
}

private fun updateCollisions(
    maze: Array<IntArray>,
    pacman: Entity,
    ghosts: List<Entity>,
    powerMode: Boolean,
    powerTimer: Int,
    onScore: (Int) -> Unit
): Int {
    var scoreGain = 0
    
    // Поедание точек
    val pacCellX = pacman.x.toInt().coerceIn(0, 27)
    val pacCellY = pacman.y.toInt().coerceIn(0, 30)
    val cell = maze[pacCellY][pacCellX]
    
    when (cell) {
        2 -> {
            maze[pacCellY][pacCellX] = 0
            scoreGain += 10
        }
        3 -> {
            maze[pacCellY][pacCellX] = 0
            scoreGain += 50
            powerMode = true
            powerTimer = 420 // 7 секунд
        }
    }
    
    // Столкновение с призраками
    ghosts.forEach { ghost ->
        if (hypot(ghost.x - pacman.x, ghost.y - pacman.y) < 0.6f) {
            if (powerMode) {
                scoreGain += 200
                // Возврат призрака в центр
                ghost.x = 13.5f
                ghost.y = 14f
            } else {
                // Смерть Pacman
                pacman.x = 13.5f
                pacman.y = 23f
                pacman.dir = Direction.LEFT
            }
        }
    }
    
    if (scoreGain > 0) onScore(scoreGain)
    return scoreGain
}

private fun checkLevelComplete(dotsLeft: Int, levelUp: () -> Unit) {
    if (dotsLeft <= 0) {
        levelUp()
    }
}

private fun levelUp() {
    // Переход на следующий уровень (реализуется через LaunchedEffect)
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════════════
// 🖌️ ОТРИСОВКА
// ═══════════════════════════════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawGame(
    maze: Array<IntArray>,
    pacman: Entity,
    ghosts: List<Entity>,
    cellSize: Float,
    mazeOffset: Offset,
    pacmanMouthAnim: Float,
    ghostAnim: Float,
    powerMode: Boolean,
    gameState: GameState,
    countdownAnim: Float,
    lives: Int,
    score: Int,
    highScore: Int,
    level: Int
) {
    // Фон
    drawRect(Color(0xFF0F0F23))
    
    // Отрисовка лабиринта
    drawMaze(maze, cellSize, mazeOffset)
    
    // Pacman
    drawPacman(pacman, cellSize, mazeOffset, pacmanMouthAnim)
    
    // Призраки
    ghosts.forEachIndexed { i, ghost ->
        drawGhost(ghost, i, cellSize, mazeOffset, ghostAnim, powerMode)
    }
    
    // UI элементы
    drawUI(lives, score, highScore, level, size, cellSize)
    
    // Состояния игры
    when (gameState) {
        GameState.COUNTDOWN -> drawCountdown(countdownAnim, size)
        GameState.GAME_OVER -> drawGameOver(size)
        else -> {}
    }
}

private fun DrawScope.drawMaze(maze: Array<IntArray>, cellSize: Float, offset: Offset) {
    maze.forEachIndexed { y, row ->
        row.forEachIndexed { x, cell ->
            val pos = offset + Offset(x * cellSize, y * cellSize)
            when (cell) {
                1 -> {
                    // Стены
                    drawRect(
                        Color(0xFF00FFFF),
                        topLeft = pos,
                        size = Size(cellSize, cellSize),
                        style = Stroke(width = 3f)
                    )
                    drawRect(
                        Color(0xFF0000AA),
                        topLeft = pos + Offset(2f, 2f),
                        size = Size(cellSize - 4f, cellSize - 4f)
                    )
                }
                2 -> {
                    // Маленькие точки
                    drawCircle(
                        Color.White,
                        2f,
                        center = pos + Offset(cellSize * 0.5f, cellSize * 0.5f)
                    )
                }
                3 -> {
                    // Большие точки (power pellets)
                    drawCircle(
                        Color.Cyan,
                        6f,
                        center = pos + Offset(cellSize * 0.5f, cellSize * 0.5f)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPacman(
    pacman: Entity,
    cellSize: Float,
    offset: Offset,
    mouthAnim: Float
) {
    val center = offset + Offset(
        pacman.x * cellSize + cellSize * 0.5f,
        pacman.y * cellSize + cellSize * 0.
