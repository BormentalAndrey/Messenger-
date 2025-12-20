package com.kakdela.p2p.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

// Классический лабиринт Pac-Man (1 = стена, 0 = путь, 2 = точка, 3 = power pellet)
private val classicMaze = arrayOf(
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,2,1),
    intArrayOf(1,3,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,3,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,1,2,1,1,1,1,1,1,1,1,2,1,1,2,1,1,1,1,2,1),
    intArrayOf(1,2,2,2,2,2,2,1,1,2,2,2,2,1,1,2,2,2,2,2,1,1,2,2,2,2,2,1),
    intArrayOf(1,1,1,1,1,1,2,1,1,1,1,1,0,1,1,0,1,1,1,1,1,1,2,1,1,1,1,1),
    intArrayOf(0,0,0,0,0,1,2,1,1,1,1,1,0,1,1,0,1,1,1,1,1,1,2,1,0,0,0,0),
    intArrayOf(1,1,1,1,1,1,2,1,1,0,0,0,0,0,0,0,0,0,0,1,1,1,2,1,1,1,1,1),
    intArrayOf(1,1,1,1,1,1,2,1,1,0,1,1,1,1,1,1,1,1,0,1,1,1,2,1,1,1,1,1),
    intArrayOf(1,1,1,1,1,1,2,1,1,0,1,0,0,0,0,0,0,0,1,0,1,1,2,1,1,1,1,1),
    intArrayOf(0,0,0,0,0,0,2,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0),
    intArrayOf(1,1,1,1,1,1,2,1,1,0,1,0,0,0,0,0,0,0,1,0,1,1,2,1,1,1,1,1),
    intArrayOf(1,1,1,1,1,1,2,1,1,0,1,1,1,1,1,1,1,1,1,0,1,1,2,1,1,1,1,1),
    intArrayOf(1,1,1,1,1,1,2,1,1,0,0,0,0,0,0,0,0,0,0,0,1,1,2,1,1,1,1,1),
    intArrayOf(0,0,0,0,0,1,2,1,1,0,1,1,1,1,1,1,1,1,1,1,1,1,2,1,0,0,0,0),
    intArrayOf(1,1,1,1,1,1,2,1,1,0,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,2,1),
    intArrayOf(1,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,1),
    intArrayOf(1,1,2,1,1,2,1,1,2,1,1,1,1,1,1,1,1,1,2,1,1,2,1,1,2,1,1,1),
    intArrayOf(1,2,2,2,2,2,1,1,2,2,2,2,1,1,2,2,2,2,1,1,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,1,1,1,1,1,2,1,1,2,1,1,1,1,1,1,1,1,1,1,1,2,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1)
    // Добавьте недостающие строки до 31, если нужно (это упрощённая версия для примера)
)

enum class Direction { UP, DOWN, LEFT, RIGHT, NONE }

data class Entity(var x: Float, var y: Float, var dir: Direction = Direction.NONE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacmanScreen() {
    var level by remember { mutableStateOf(1) }
    var score by remember { mutableStateOf(0) }
    var lives by remember { mutableStateOf(3) }
    var dotsLeft by remember { mutableStateOf(0) }
    var isPowerMode by remember { mutableStateOf(false) }
    var powerTimer by remember { mutableStateOf(0) }

    val cellSize = 20f
    val mazeWidth = 28
    val mazeHeight = 31

    val pacman = remember { mutableStateOf(Entity(14f, 23f, Direction.LEFT)) }
    val ghosts = remember { mutableStateListOf(
        Entity(14f, 11f), Entity(12f, 14f), Entity(14f, 14f), Entity(16f, 14f)
    ) }

    // Неоновые цвета (меняются по уровню)
    val neonHue = (level * 30) % 360f
    val wallColor = Color.hsl(neonHue, 0.8f, 0.6f)
    val pacmanColor = Color.Yellow
    val dotColor = Color.White
    val powerColor = Color.Cyan
    val ghostColors = listOf(Color.Red, Color.Magenta, Color.Cyan, Color.Orange).map { it.copy(alpha = if (isPowerMode) 0.5f else 1f) }

    // Инициализация точек
    LaunchedEffect(level) {
        dotsLeft = 0
        classicMaze.forEachIndexed { y, row ->
            row.forEachIndexed { x, cell ->
                if (cell == 2 || cell == 3) dotsLeft++
            }
        }
        pacman.value = Entity(14f, 23f, Direction.LEFT)
        ghosts.clear()
        ghosts.addAll(listOf(Entity(14f, 11f), Entity(12f, 14f), Entity(14f, 14f), Entity(16f, 14f)))
    }

    // Игровой цикл
    LaunchedEffect(Unit) {
        while (true) {
            delay(150L - level.coerceAtMost(50)) // Ускорение с уровнем

            // Движение Pacman
            val p = pacman.value
            var nx = p.x
            var ny = p.y
            when (p.dir) {
                Direction.UP -> ny -= 1
                Direction.DOWN -> ny += 1
                Direction.LEFT -> nx -= 1
                Direction.RIGHT -> nx += 1
                else -> {}
            }
            if (nx < 0) nx = mazeWidth - 1f
            if (nx >= mazeWidth) nx = 0f
            if (classicMaze[ny.toInt()][nx.toInt()] != 1) {
                p.x = nx
                p.y = ny
            }

            // Сбор точек
            val cell = classicMaze[p.y.toInt()][p.x.toInt()]
            if (cell == 2) {
                score += 10
                dotsLeft--
            } else if (cell == 3) {
                score += 50
                isPowerMode = true
                powerTimer = 100
            }

            if (dotsLeft == 0) {
                level = (level % 200) + 1 // 200 уровней с циклом
                score += 1000
            }

            // Power mode timer
            if (isPowerMode) {
                powerTimer--
                if (powerTimer <= 0) isPowerMode = false
            }

            // Движение призраков (простой ИИ)
            ghosts.forEachIndexed { i, g ->
                val dirs = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT).shuffled()
                for (d in dirs) {
                    var gx = g.x
                    var gy = g.y
                    when (d) {
                        Direction.UP -> gy -= 1
                        Direction.DOWN -> gy += 1
                        Direction.LEFT -> gx -= 1
                        Direction.RIGHT -> gx += 1
                        else -> {}
                    }
                    if (gx < 0) gx = mazeWidth - 1f
                    if (gx >= mazeWidth) gx = 0f
                    if (classicMaze[gy.toInt()][gx.toInt()] != 1) {
                        g.x = gx
                        g.y = gy
                        g.dir = d
                        break
                    }
                }

                // Коллизия с Pacman
                if (abs(g.x - p.x) < 0.5f && abs(g.y - p.y) < 0.5f) {
                    if (isPowerMode) {
                        score += 200
                        g.x = 14f; g.y = 14f // Возврат в центр
                    } else {
                        lives--
                        if (lives <= 0) {
                            // Game Over — сброс
                            level = 1
                            score = 0
                            lives = 3
                        }
                        pacman.value = Entity(14f, 23f, Direction.LEFT)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neon Pac-Man • Уровень $level • Счёт: $score • Жизни: $lives", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (abs(dragAmount) > 50) {
                            pacman.value.dir = if (dragAmount > 0) Direction.RIGHT else Direction.LEFT
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (abs(dragAmount) > 50) {
                            pacman.value.dir = if (dragAmount > 0) Direction.DOWN else Direction.UP
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val offsetX = (size.width - mazeWidth * cellSize) / 2
                val offsetY = (size.height - mazeHeight * cellSize) / 2

                // Стены с неоновым glow
                classicMaze.forEachIndexed { y, row ->
                    row.forEachIndexed { x, cell ->
                        val rect = Offset(offsetX + x * cellSize, offsetY + y * cellSize)
                        if (cell == 1) {
                            drawRect(
                                color = wallColor,
                                topLeft = rect,
                                size = Size(cellSize, cellSize),
                                style = Stroke(width = 4f)
                            )
                            drawRect(
                                color = wallColor.copy(alpha = 0.3f),
                                topLeft = rect - Offset(4f, 4f),
                                size = Size(cellSize + 8f, cellSize + 8f)
                            ) // Glow
                        } else if (cell == 2) {
                            drawCircle(color = dotColor, radius = 4f, center = rect + Offset(cellSize / 2, cellSize / 2))
                        } else if (cell == 3) {
                            drawCircle(color = powerColor, radius = 8f, center = rect + Offset(cellSize / 2, cellSize / 2))
                        }
                    }
                }

                // Pacman с анимацией рта
                val mouthAngle = if (pacman.value.dir != Direction.NONE) 0.2f + 0.2f * (System.currentTimeMillis() % 400 / 200f) else 0f
                val startAngle = when (pacman.value.dir) {
                    Direction.RIGHT -> 30f
                    Direction.LEFT -> 210f
                    Direction.UP -> 300f
                    Direction.DOWN -> 120f
                    else -> 30f
                }
                val pacPos = Offset(offsetX + pacman.value.x * cellSize + cellSize / 2, offsetY + pacman.value.y * cellSize + cellSize / 2)
                drawCircle(color = pacmanColor, radius = cellSize / 2 * 0.9f, center = pacPos)
                drawArc(
                    color = Color.Black,
                    startAngle = startAngle,
                    sweepAngle = 300f * mouthAngle,
                    useCenter = true,
                    topLeft = pacPos - Offset(cellSize / 2, cellSize / 2),
                    size = Size(cellSize, cellSize)
                )

                // Призраки
                ghosts.forEachIndexed { i, g ->
                    val ghostPos = Offset(offsetX + g.x * cellSize + cellSize / 2, offsetY + g.y * cellSize + cellSize / 2)
                    drawCircle(color = ghostColors[i % ghostColors.size], radius = cellSize / 2 * 0.8f, center = ghostPos)
                }
            }
        }
    }
}
