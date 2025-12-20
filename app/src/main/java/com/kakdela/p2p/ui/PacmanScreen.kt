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
import kotlin.math.abs
import kotlinx.coroutines.delay

// 0 — путь, 1 — стена, 2 — точка, 3 — power точка
private val maze = arrayOf(
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
)

enum class Dir { UP, DOWN, LEFT, RIGHT, NONE }
data class Entity(var x: Float, var y: Float, var dir: Dir = Dir.NONE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacmanScreen() {
    var level by remember { mutableStateOf(1) }
    var lives by remember { mutableStateOf(3) }
    var score by remember { mutableStateOf(0) }

    val pac = remember { mutableStateOf(Entity(14f, 23f, Dir.LEFT)) }
    val ghosts = remember {
        mutableStateListOf(
            Entity(14f, 11f), Entity(12f, 14f), Entity(14f, 14f), Entity(16f, 14f)
        )
    }

    val mazeWidth = maze[0].size
    val mazeHeight = maze.size
    val cellSize = 20f

    var dotsLeft by remember { mutableStateOf(0) }
    var powerMode by remember { mutableStateOf(false) }
    var powerTimer by remember { mutableStateOf(0) }

    val wallColor = Color(0xFF00FFAA)
    val pacColor = Color.Yellow
    val ghostColors = listOf(
        Color.Red,
        Color.Magenta,
        Color.Cyan,
        Color(0xFFFF9800) // вместо Color.Orange
    ).map { c -> c.copy(alpha = if (powerMode) 0.4f else 1f) }

    LaunchedEffect(level) {
        dotsLeft = 0
        maze.forEach { row ->
            row.forEach { cell ->
                if (cell == 2 || cell == 3) dotsLeft++
            }
        }
        pac.value = Entity(14f, 23f, Dir.LEFT)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(140L)

            val p = pac.value
            var nx = p.x
            var ny = p.y
            when (p.dir) {
                Dir.UP -> ny--
                Dir.DOWN -> ny++
                Dir.LEFT -> nx--
                Dir.RIGHT -> nx++
                else -> {}
            }
            if (nx < 0) nx = mazeWidth - 1f
            if (nx >= mazeWidth) nx = 0f

            if (maze[ny.toInt()][nx.toInt()] != 1) {
                p.x = nx; p.y = ny
            }

            val cell = maze[p.y.toInt()][p.x.toInt()]
            if (cell == 2) {
                score += 10; dotsLeft--
            } else if (cell == 3) {
                score += 50
                powerMode = true
                powerTimer = 80
            }

            if (powerMode) {
                powerTimer--
                if (powerTimer <= 0) powerMode = false
            }

            if (dotsLeft == 0) {
                level++
                score += 500
            }

            ghosts.forEachIndexed { i, g ->
                val d = listOf(Dir.UP, Dir.DOWN, Dir.LEFT, Dir.RIGHT).shuffled()
                for (dir in d) {
                    var gx = g.x
                    var gy = g.y
                    when (dir) {
                        Dir.UP -> gy--
                        Dir.DOWN -> gy++
                        Dir.LEFT -> gx--
                        Dir.RIGHT -> gx++
                        else -> {}
                    }
                    if (maze[gy.toInt()][gx.toInt()] != 1) {
                        g.x = gx; g.y = gy; g.dir = dir
                        break
                    }
                }
                if (abs(g.x - p.x) < 0.6f && abs(g.y - p.y) < 0.6f) {
                    if (powerMode) {
                        score += 200
                        g.x = 14f; g.y = 14f
                    } else {
                        lives--
                        if (lives <= 0) {
                            level = 1; lives = 3; score = 0
                        }
                        pac.value = Entity(14f, 23f, Dir.LEFT)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Pac-Man | Level $level | Score $score | Lives $lives",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(Color.Black)
            )
        }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(pad)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, drag ->
                        if (abs(drag) > 40) {
                            pac.value.dir = if (drag > 0) Dir.RIGHT else Dir.LEFT
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, drag ->
                        if (abs(drag) > 40) {
                            pac.value.dir = if (drag > 0) Dir.DOWN else Dir.UP
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val ox = (size.width - mazeWidth * cellSize) / 2
                val oy = (size.height - mazeHeight * cellSize) / 2

                maze.forEachIndexed { y, row ->
                    row.forEachIndexed { x, c ->
                        val pos = Offset(ox + x * cellSize, oy + y * cellSize)
                        when (c) {
                            1 -> drawRect(
                                color = wallColor,
                                topLeft = pos,
                                size = Size(cellSize, cellSize),
                                style = Stroke(width = 4f)
                            )
                            2 -> drawCircle(
                                Color.White, 4f,
                                pos + Offset(cellSize / 2, cellSize / 2)
                            )
                            3 -> drawCircle(
                                Color.Cyan, 7f,
                                pos + Offset(cellSize / 2, cellSize / 2)
                            )
                        }
                    }
                }

                val pacPos = Offset(
                    ox + pac.value.x * cellSize + cellSize / 2,
                    oy + pac.value.y * cellSize + cellSize / 2
                )
                drawCircle(pacColor, cellSize / 2 - 2, pacPos)

                ghosts.forEachIndexed { i, g ->
                    val pos = Offset(
                        ox + g.x * cellSize + cellSize / 2,
                        oy + g.y * cellSize + cellSize / 2
                    )
                    drawCircle(
                        ghostColors[i % ghostColors.size],
                        cellSize / 2 - 3,
                        pos
                    )
                }
            }
        }
    }
}
