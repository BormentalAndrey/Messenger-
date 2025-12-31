package com.kakdela.p2p.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs

// Карта (сокращенная версия для примера, используйте вашу полную карту)
private val maze = arrayOf(
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,2,1),
    intArrayOf(1,3,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,3,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1)
)

enum class Dir { UP, DOWN, LEFT, RIGHT, NONE }
data class Entity(var x: Float, var y: Float, var dir: Dir = Dir.NONE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacmanScreen() {
    var level by remember { mutableIntStateOf(1) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableIntStateOf(0) }
    
    // Используем State для реактивного обновления UI
    var pac by remember { mutableStateOf(Entity(14f, 2f, Dir.LEFT)) } // Start pos corrected for map
    val ghosts = remember { mutableStateListOf(Entity(14f, 2f), Entity(1f, 1f)) }
    
    val mazeWidth = maze[0].size
    val mazeHeight = maze.size
    val cellSize = 20f
    
    var dotsLeft by remember { mutableIntStateOf(0) }
    var powerMode by remember { mutableStateOf(false) }
    var powerTimer by remember { mutableIntStateOf(0) }
    
    // Цвета
    val wallColor = Color(0xFF00FFAA)
    val pacColor = Color.Yellow

    // Инициализация уровня
    LaunchedEffect(level) {
        dotsLeft = 0
        maze.forEach { row ->
            row.forEach { cell -> if (cell == 2 || cell == 3) dotsLeft++ }
        }
        pac = Entity(14f, 1f, Dir.LEFT)
    }

    // --- ОПТИМИЗИРОВАННЫЙ ИГРОВОЙ ЦИКЛ ---
    LaunchedEffect(Unit) {
        var lastUpdateTime = 0L
        val updateInterval = 140_000_000L // 140 миллисекунд в наносекундах

        while (true) {
            withFrameNanos { time ->
                if (lastUpdateTime == 0L) lastUpdateTime = time
                
                // Обновляемся только если прошло достаточно времени
                if (time - lastUpdateTime >= updateInterval) {
                    lastUpdateTime = time
                    
                    // 1. Движение Пакмана
                    val p = pac.copy() // Копия для изменения
                    var nx = p.x
                    var ny = p.y
                    
                    when (p.dir) {
                        Dir.UP -> ny--
                        Dir.DOWN -> ny++
                        Dir.LEFT -> nx--
                        Dir.RIGHT -> nx++
                        else -> {}
                    }

                    // Телепортация по краям
                    if (nx < 0) nx = mazeWidth - 1f
                    if (nx >= mazeWidth) nx = 0f

                    // Проверка столкновения со стенами
                    val cellY = ny.toInt().coerceIn(0, mazeHeight - 1)
                    val cellX = nx.toInt().coerceIn(0, mazeWidth - 1)
                    
                    if (maze[cellY][cellX] != 1) {
                        p.x = nx
                        p.y = ny
                    }
                    
                    // Обновляем состояние Пакмана
                    pac = p 

                    // 2. Логика поедания точек
                    val currentCell = maze[p.y.toInt().coerceIn(0, mazeHeight-1)][p.x.toInt().coerceIn(0, mazeWidth-1)]
                    
                    if (currentCell == 2) {
                        maze[p.y.toInt()][p.x.toInt()] = 0 // Очищаем карту
                        score += 10
                        dotsLeft--
                    } else if (currentCell == 3) {
                        maze[p.y.toInt()][p.x.toInt()] = 0
                        score += 50
                        powerMode = true
                        powerTimer = 80
                    }

                    if (powerMode) {
                        powerTimer--
                        if (powerTimer <= 0) powerMode = false
                    }

                    if (dotsLeft <= 0) {
                        level++
                        score += 500
                    }
                    
                    // 3. Движение призраков
                    ghosts.forEachIndexed { i, g ->
                         // Простая логика случайного движения
                         val d = listOf(Dir.UP, Dir.DOWN, Dir.LEFT, Dir.RIGHT).shuffled().first()
                         var gx = g.x
                         var gy = g.y
                         when (d) {
                            Dir.UP -> gy--
                            Dir.DOWN -> gy++
                            Dir.LEFT -> gx--
                            Dir.RIGHT -> gx++
                            else -> {}
                         }
                         if (maze.getOrNull(gy.toInt())?.getOrNull(gx.toInt()) != 1) {
                             g.x = gx
                             g.y = gy
                             g.dir = d
                             // Обновляем элемент списка для рекомпозиции
                             ghosts[i] = g
                         }
                         
                         // Столкновение
                         if (abs(g.x - p.x) < 0.6f && abs(g.y - p.y) < 0.6f) {
                             if (powerMode) {
                                 score += 200
                                 g.x = 14f; g.y = 1f
                                 ghosts[i] = g
                             } else {
                                 lives--
                                 if (lives <= 0) {
                                     level = 1; lives = 3; score = 0
                                 } else {
                                     pac = Entity(14f, 1f, Dir.LEFT)
                                 }
                             }
                         }
                    }
                }
            }
        }
    }

    // UI Render
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pac-Man | Lvl $level | Score $score", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(Color.Black)
            )
        }
    ) { pad ->
        Box(
            Modifier.fillMaxSize().background(Color.Black).padding(pad)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, drag ->
                        if (abs(drag) > 20) pac = pac.copy(dir = if (drag > 0) Dir.RIGHT else Dir.LEFT)
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, drag ->
                        if (abs(drag) > 20) pac = pac.copy(dir = if (drag > 0) Dir.DOWN else Dir.UP)
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val ox = (size.width - mazeWidth * cellSize) / 2
                val oy = (size.height - mazeHeight * cellSize) / 2
                
                // Рисуем карту
                maze.forEachIndexed { y, row ->
                    row.forEachIndexed { x, c ->
                        val pos = Offset(ox + x * cellSize, oy + y * cellSize)
                        when (c) {
                            1 -> drawRect(wallColor, topLeft = pos, size = Size(cellSize, cellSize), style = Stroke(width = 3f))
                            2 -> drawCircle(Color.White, 3f, pos + Offset(cellSize/2, cellSize/2))
                            3 -> drawCircle(Color.Cyan, 6f, pos + Offset(cellSize/2, cellSize/2))
                        }
                    }
                }
                
                // Рисуем Пакмана
                val pacPos = Offset(ox + pac.x * cellSize + cellSize/2, oy + pac.y * cellSize + cellSize/2)
                drawCircle(pacColor, cellSize/2 - 2, pacPos)
                
                // Рисуем призраков
                ghosts.forEach { g ->
                    val ghostPos = Offset(ox + g.x * cellSize + cellSize/2, oy + g.y * cellSize + cellSize/2)
                    drawCircle(if(powerMode) Color.Blue else Color.Red, cellSize/2 - 2, ghostPos)
                }
            }
        }
    }
}

