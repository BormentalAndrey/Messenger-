package com.kakdela.p2p.ui

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

// ===================== ENUMS =====================
enum class Direction { UP, DOWN, LEFT, RIGHT, NONE }
enum class PacmanGameState { READY, PLAYING, DYING, GAME_OVER }
enum class GhostMode { SCATTER, CHASE, FRIGHTENED }

// ===================== DATA =====================
data class GridPos(var x: Int, var y: Int)  // <-- Переименовано, чтобы не конфликтовать

data class Pacman(
    var pos: GridPos = GridPos(13, 23),
    var dir: Direction = Direction.LEFT,
    var nextDir: Direction = Direction.LEFT,
    var pixelX: Float = 13f,
    var pixelY: Float = 23f
)

data class Ghost(
    var pos: GridPos,
    var dir: Direction,
    val type: Int, // 0=Blinky, 1=Pinky, 2=Inky, 3=Clyde
    var pixelX: Float,
    var pixelY: Float
)

// ===================== ORIGINAL MAP =====================
private val PACMAN_MAP = arrayOf(
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,1,2,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,2,1),
    intArrayOf(1,3,1,1,1,1,1,2,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,3,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,1,2,1,2,1,1,1,1,1,1,1,1,2,1,2,1,1,1,1,1,2,1),
    intArrayOf(1,2,2,2,2,1,1,2,2,2,1,1,2,0,0,2,1,1,2,2,2,1,1,2,2,2,2,1),
    intArrayOf(1,1,1,1,2,1,1,2,0,0,0,0,2,0,0,2,0,0,0,0,2,1,1,2,1,1,1,1),
    intArrayOf(2,2,2,2,2,1,1,2,0,0,0,0,2,0,0,2,0,0,0,0,2,1,1,2,2,2,2,2),
    intArrayOf(1,1,1,1,2,1,1,2,0,1,1,1,4,4,4,4,1,1,1,0,2,1,1,2,1,1,1,1),
    intArrayOf(2,2,2,2,2,2,2,2,0,1,0,0,0,0,0,0,0,0,1,0,2,2,2,2,2,2,2,2),
    intArrayOf(1,1,1,1,2,1,1,2,0,1,1,0,0,0,0,0,0,0,1,0,2,1,1,2,1,1,1,1),
    intArrayOf(1,1,1,1,2,1,1,2,0,0,0,0,0,4,4,0,0,0,0,0,2,1,1,2,1,1,1,1),
    intArrayOf(1,1,1,1,2,1,1,2,0,1,1,0,0,0,0,0,0,0,1,0,2,1,1,2,1,1,1,1),
    intArrayOf(2,2,2,2,2,2,2,2,0,1,0,0,0,0,0,0,0,0,1,0,2,2,2,2,2,2,2,2),
    intArrayOf(1,1,1,1,2,1,1,2,0,1,1,1,4,4,4,4,1,1,1,0,2,1,1,2,1,1,1,1),
    intArrayOf(2,2,2,2,2,1,1,2,0,0,0,0,2,0,0,2,0,0,0,0,2,1,1,2,2,2,2,2),
    intArrayOf(1,1,1,1,1,1,1,2,1,1,1,1,2,0,0,2,1,1,1,1,1,2,1,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,1,4,4,1,2,1,1,2,1,4,4,1,1,2,1,1,1,1,2,1),
    intArrayOf(1,2,2,2,1,1,2,2,2,1,1,1,2,1,1,2,1,1,1,2,2,2,1,1,2,2,2,1),
    intArrayOf(1,1,1,2,1,1,2,1,2,1,1,1,2,1,1,2,1,1,1,2,1,1,2,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,1,2,2,2,2,2,0,0,2,2,2,2,2,1,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,1,1,1,2,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1)
)

// ===================== COMPOSABLE =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacmanScreen() {
    val context = LocalContext.current

    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    val chompSound = remember { 0 } // Звук отключён (можно добавить raw-ресурс)

    var gameState by remember { mutableStateOf(PacmanGameState.READY) }
    var level by remember { mutableIntStateOf(1) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var dotsEaten by remember(level) { mutableIntStateOf(0) }

    var pacman by remember { mutableStateOf(Pacman()) }
    val ghosts = remember {
        mutableStateListOf(
            Ghost(GridPos(14, 11), Direction.LEFT, 0, 14f, 11f),
            Ghost(GridPos(14, 14), Direction.UP, 1, 14f, 14f),
            Ghost(GridPos(12, 14), Direction.UP, 2, 12f, 14f),
            Ghost(GridPos(16, 14), Direction.UP, 3, 16f, 14f)
        )
    }

    var frightenedTimer by remember { mutableIntStateOf(0) }
    var globalModeTimer by remember { mutableIntStateOf(0) }

    val maze = remember(level) { PACMAN_MAP.map { it.copyOf() }.toTypedArray() }
    val totalDots = remember { PACMAN_MAP.sumOf { row -> row.count { it == 2 || it == 3 } } }

    val mouthAnim by animateFloatAsState(
        targetValue = if (gameState == PacmanGameState.PLAYING) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(150), RepeatMode.Reverse)
    )

    LaunchedEffect(gameState) {
        if (gameState == PacmanGameState.READY) {
            delay(2000)
            gameState = PacmanGameState.PLAYING
        }
    }

    LaunchedEffect(gameState, level) {
        while (gameState == PacmanGameState.PLAYING) {
            globalModeTimer++

            val currentMode = when {
                globalModeTimer < 420 -> GhostMode.SCATTER
                globalModeTimer < 1200 -> GhostMode.CHASE
                globalModeTimer < 1260 -> GhostMode.SCATTER
                globalModeTimer < 2100 -> GhostMode.CHASE
                else -> { globalModeTimer = 0; GhostMode.SCATTER }
            }

            updatePacman(pacman, maze)

            val px = pacman.pos.x
            val py = pacman.pos.y
            when (maze[py][px]) {
                2 -> {
                    maze[py][px] = 0
                    score += 10
                    dotsEaten++
                    if (chompSound != 0) soundPool.play(chompSound, 0.5f, 0.5f, 1, 0, 1f)
                }
                3 -> {
                    maze[py][px] = 0
                    score += 50
                    frightenedTimer = 480
                    dotsEaten++
                }
            }

            val blinky = ghosts[0]
            ghosts.forEach { ghost ->
                updateGhost(ghost, pacman, blinky, maze, currentMode, frightenedTimer > 0)
            }

            ghosts.forEach { ghost ->
                if (abs(ghost.pixelX - pacman.pixelX) < 0.4f && abs(ghost.pixelY - pacman.pixelY) < 0.4f) {
                    if (frightenedTimer > 0) {
                        score += 200
                        ghost.pos = GridPos(14, 14)
                        ghost.pixelX = 14f
                        ghost.pixelY = 14f
                        frightenedTimer -= 60
                    } else {
                        lives--
                        if (lives <= 0) {
                            gameState = PacmanGameState.GAME_OVER
                        } else {
                            delay(1500)
                            resetLevel(pacman, ghosts, maze)
                        }
                    }
                }
            }

            frightenedTimer = maxOf(0, frightenedTimer - 1)

            if (dotsEaten >= totalDots) {
                level++
                dotsEaten = 0
                resetLevel(pacman, ghosts, maze)
            }

            delay(120)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PAC-MAN", color = Color.Yellow, fontSize = 24.sp) },
                actions = {
                    Text("Score: $score  ❤️x$lives  Lvl: $level", color = Color.White, fontSize = 16.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF000020))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellSize = minOf(size.width / 28f, (size.height - 120.dp.toPx()) / 31f)
                val offsetX = (size.width - 28 * cellSize) / 2
                val offsetY = (size.height - 31 * cellSize) / 2

                drawMaze(maze, cellSize, Offset(offsetX, offsetY))
                drawPacman(pacman, cellSize, Offset(offsetX, offsetY), mouthAnim)
                ghosts.forEachIndexed { i, g ->
                    drawGhost(g, i, cellSize, Offset(offsetX, offsetY), frightenedTimer > 0, frightenedTimer < 120)
                }
            }

            // Управление стрелками
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = { pacman.nextDir = Direction.LEFT }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Left", tint = Color.Yellow, modifier = Modifier.size(40.dp))
                }

                Column {
                    IconButton(onClick = { pacman.nextDir = Direction.UP }, modifier = Modifier.size(60.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Up", tint = Color.Yellow, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { pacman.nextDir = Direction.DOWN }, modifier = Modifier.size(60.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Down", tint = Color.Yellow, modifier = Modifier.size(40.dp))
                    }
                }

                IconButton(onClick = { pacman.nextDir = Direction.RIGHT }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Default.KeyboardArrowRight, "Right", tint = Color.Yellow, modifier = Modifier.size(40.dp))
                }
            }

            if (gameState == PacmanGameState.READY) {
                Text("READY!", color = Color.Yellow, fontSize = 64.sp, modifier = Modifier.align(Alignment.Center))
            }
            if (gameState == PacmanGameState.GAME_OVER) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GAME OVER", color = Color.Red, fontSize = 64.sp)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = {
                        gameState = PacmanGameState.READY
                        score = 0
                        lives = 3
                        level = 1
                    }) { Text("PLAY AGAIN") }
                }
            }
        }
    }
}

// ===================== ЛОГИКА =====================

private fun updateGhost(ghost: Ghost, pacman: Pacman, blinky: Ghost, maze: Array<IntArray>, mode: GhostMode, frightened: Boolean) {
    val target = if (frightened) {
        GridPos(Random.nextInt(0, 28), Random.nextInt(0, 31))
    } else if (mode == GhostMode.SCATTER) {
        when (ghost.type) {
            0 -> GridPos(25, 0)
            1 -> GridPos(2, 0)
            2 -> GridPos(27, 30)
            else -> GridPos(0, 30)
        }
    } else {
        when (ghost.type) {
            0 -> pacman.pos
            1 -> GridPos(
                (pacman.pos.x + pacman.dir.dx() * 4).coerceIn(0, 27),
                (pacman.pos.y + pacman.dir.dy() * 4).coerceIn(0, 30)
            )
            2 -> {
                val front = GridPos(
                    (pacman.pos.x + pacman.dir.dx() * 2).coerceIn(0, 27),
                    (pacman.pos.y + pacman.dir.dy() * 2).coerceIn(0, 30)
                )
                GridPos(
                    (2 * front.x - blinky.pos.x).coerceIn(0, 27),
                    (2 * front.y - blinky.pos.y).coerceIn(0, 30)
                )
            }
            else -> if (manhattanDistance(ghost.pos, pacman.pos) > 8) pacman.pos else GridPos(0, 30)
        }
    }

    val possibleDirs = Direction.entries.filter {
        it != ghost.dir.opposite() && isValidMove(
            GridPos((ghost.pos.x + it.dx()).coerceIn(0, 27), (ghost.pos.y + it.dy()).coerceIn(0, 30)),
            maze
        )
    }

    val bestDir = if (frightened) possibleDirs.randomOrNull() ?: ghost.dir else {
        possibleDirs.minByOrNull {
            manhattanDistance(
                GridPos((ghost.pos.x + it.dx()).coerceIn(0, 27), (ghost.pos.y + it.dy()).coerceIn(0, 30)),
                target
            )
        } ?: ghost.dir
    }

    val newPos = GridPos(
        (ghost.pos.x + bestDir.dx()).coerceIn(0, 27),
        (ghost.pos.y + bestDir.dy()).coerceIn(0, 30)
    )

    if (isValidMove(newPos, maze)) {
        ghost.dir = bestDir
        ghost.pos = newPos
    }

    if (ghost.pos.x < 0) ghost.pos.x = 27
    if (ghost.pos.x > 27) ghost.pos.x = 0

    ghost.pixelX += (ghost.pos.x - ghost.pixelX) * 0.3f
    ghost.pixelY += (ghost.pos.y - ghost.pixelY) * 0.3f
}

private fun isValidMove(pos: GridPos, maze: Array<IntArray>): Boolean {
    return pos.x in 0..27 && pos.y in 0..30 && maze[pos.y][pos.x] != 1
}

private fun manhattanDistance(p1: GridPos, p2: GridPos): Int = abs(p1.x - p2.x) + abs(p1.y - p2.y)

private fun Direction.dx() = when (this) { Direction.LEFT -> -1; Direction.RIGHT -> 1; else -> 0 }
private fun Direction.dy() = when (this) { Direction.UP -> -1; Direction.DOWN -> 1; else -> 0 }
private fun Direction.opposite() = when (this) {
    Direction.UP -> Direction.DOWN; Direction.DOWN -> Direction.UP
    Direction.LEFT -> Direction.RIGHT; Direction.RIGHT -> Direction.LEFT
    else -> Direction.NONE
}

private fun resetLevel(pacman: Pacman, ghosts: List<Ghost>, maze: Array<IntArray>) {
    pacman.pos = GridPos(13, 23)
    pacman.pixelX = 13f
    pacman.pixelY = 23f
    pacman.dir = Direction.LEFT
    pacman.nextDir = Direction.LEFT

    ghosts[0].apply { pos = GridPos(14, 11); pixelX = 14f; pixelY = 11f }
    ghosts[1].apply { pos = GridPos(14, 14); pixelX = 14f; pixelY = 14f }
    ghosts[2].apply { pos = GridPos(12, 14); pixelX = 12f; pixelY = 14f }
    ghosts[3].apply { pos = GridPos(16, 14); pixelX = 16f; pixelY = 14f }
}

// ===================== ОТРИСОВКА =====================
private fun DrawScope.drawMaze(maze: Array<IntArray>, cell: Float, offset: Offset) {
    maze.forEachIndexed { y, row ->
        row.forEachIndexed { x, v ->
            val pos = offset + Offset(x * cell, y * cell)
            when (v) {
                1 -> drawRect(Color(0xFF4488FF), pos, Size(cell, cell))
                2 -> drawCircle(Color.White, cell * 0.1f, pos + Offset(cell / 2, cell / 2))
                3 -> drawCircle(Color.Cyan, cell * 0.3f, pos + Offset(cell / 2, cell / 2))
            }
        }
    }
}

private fun DrawScope.drawPacman(p: Pacman, cell: Float, offset: Offset, mouth: Float) {
    val center = offset + Offset(p.pixelX * cell + cell / 2, p.pixelY * cell + cell / 2)
    val mouthAngle = 0.15f + mouth * 0.65f
    val startAngle = when (p.dir) {
        Direction.RIGHT -> 0f
        Direction.DOWN -> 90f
        Direction.LEFT -> 180f
        Direction.UP -> 270f
        else -> 0f
    }
    drawArc(
        Color.Yellow,
        startAngle - mouthAngle * 90f,
        360f - mouthAngle * 180f,
        useCenter = true,
        topLeft = center - Offset(cell * 0.45f, cell * 0.45f),
        size = Size(cell * 0.9f, cell * 0.9f)
    )
}

private fun DrawScope.drawGhost(g: Ghost, index: Int, cell: Float, offset: Offset, frightened: Boolean, flashing: Boolean) {
    val center = offset + Offset(g.pixelX * cell + cell / 2, g.pixelY * cell + cell / 2)
    val color = if (frightened) {
        if (flashing) Color.White else Color(0xFF4488FF)
    } else {
        listOf(Color.Red, Color(0xFFFFB8FF), Color.Cyan, Color(0xFFFF9800))[index]
    }

    // Тело
    drawRect(color, center + Offset(-cell * 0.45f, -cell * 0.45f), Size(cell * 0.9f, cell * 0.9f))

    // Простое волнистое дно (без Path)
    val bottomY = center.y + cell * 0.35f
    for (i in 0..5) {
        val wave = if (i % 2 == 0) cell * 0.08f else -cell * 0.08f
        drawLine(
            color,
            Offset(center.x - cell * 0.45f + i * cell * 0.18f, bottomY + wave),
            Offset(center.x - cell * 0.45f + (i + 1) * cell * 0.18f, bottomY + wave),
            strokeWidth = cell * 0.1f
        )
    }

    // Глаза
    val eyeOffset = cell * 0.15f
    drawCircle(Color.White, cell * 0.14f, center + Offset(-eyeOffset, -cell * 0.1f))
    drawCircle(Color.White, cell * 0.14f, center + Offset(eyeOffset, -cell * 0.1f))

    val pupil = cell * 0.07f
    val pupilX = g.dir.dx() * cell * 0.08f
    drawCircle(Color.Black, pupil, center + Offset(-eyeOffset + pupilX, -cell * 0.1f))
    drawCircle(Color.Black, pupil, center + Offset(eyeOffset + pupilX, -cell * 0.1f))
}
