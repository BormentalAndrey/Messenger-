
package com.kakdela.p2p.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

// ===================== ENUMS =====================
enum class Direction { UP, DOWN, LEFT, RIGHT, NONE }
enum class PacmanGameState { READY, PLAYING, DYING, GAME_OVER }

// ===================== DATA =====================
data class GridPos(var x: Int, var y: Int)

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

// ===================== ORIGINAL PACMAN MAP =====================
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
            updatePacman(pacman, maze)

            val px = pacman.pos.x
            val py = pacman.pos.y
            when (maze[py][px]) {
                2 -> {
                    maze[py][px] = 0
                    score += 10
                    dotsEaten++
                }
                3 -> {
                    maze[py][px] = 0
                    score += 50
                    frightenedTimer = 480
                    dotsEaten++
                }
            }

            ghosts.forEach { ghost ->
                updateGhostSimple(ghost, pacman, maze, frightenedTimer > 0)
            }

            ghosts.forEach { ghost ->
                if (abs(ghost.pixelX - pacman.pixelX) < 0.5f && abs(ghost.pixelY - pacman.pixelY) < 0.5f) {
                    if (frightenedTimer > 0) {
                        score += 200
                        ghost.pos = GridPos(14, 14)
                        ghost.pixelX = 14f
                        ghost.pixelY = 14f
                    } else {
                        lives--
                        if (lives <= 0) {
                            gameState = PacmanGameState.GAME_OVER
                        } else {
                            delay(1500)
                            resetLevel(pacman, ghosts)
                        }
                    }
                }
            }

            frightenedTimer = maxOf(0, frightenedTimer - 1)

            if (dotsEaten >= totalDots) {
                level++
                dotsEaten = 0
                resetLevel(pacman, ghosts)
            }

            delay(16)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PAC-MAN", color = Color.Yellow, fontSize = 24.sp) },
                actions = {
                    Text("Score: $score  ❤️x$lives  Level: $level", color = Color.White, fontSize = 16.sp)
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

                drawMaze(this, maze, cellSize, Offset(offsetX, offsetY))
                drawPacman(this, pacman, cellSize, Offset(offsetX, offsetY), mouthAnim)
                ghosts.forEachIndexed { i, g ->
                    drawGhost(this, g, i, cellSize, Offset(offsetX, offsetY), frightenedTimer > 0, frightenedTimer < 120)
                }
            }

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

// ===================== PACMAN UPDATE =====================
private fun updatePacman(pacman: Pacman, maze: Array<IntArray>) {
    val isCenteredX = abs(pacman.pixelX - pacman.pos.x) < 0.15f
    val isCenteredY = abs(pacman.pixelY - pacman.pos.y) < 0.15f
    val isCentered = isCenteredX && isCenteredY

    if (isCentered && pacman.nextDir != Direction.NONE) {
        val intendedPos = GridPos(pacman.pos.x, pacman.pos.y).apply {
            when (pacman.nextDir) {
                Direction.UP -> y--
                Direction.DOWN -> y++
                Direction.LEFT -> x--
                Direction.RIGHT -> x++
                else -> {}
            }
        }
        if (isValidMove(intendedPos, maze)) {
            pacman.dir = pacman.nextDir
        }
    }

    val movePos = GridPos(pacman.pos.x, pacman.pos.y).apply {
        when (pacman.dir) {
            Direction.UP -> y--
            Direction.DOWN -> y++
            Direction.LEFT -> x--
            Direction.RIGHT -> x++
            else -> {}
        }
    }

    if (isValidMove(movePos, maze)) {
        pacman.pos = movePos
    }

    if (pacman.pos.x < 0) pacman.pos.x = 27
    if (pacman.pos.x > 27) pacman.pos.x = 0

    val targetX = pacman.pos.x.toFloat()
    val targetY = pacman.pos.y.toFloat()

    pacman.pixelX += (targetX - pacman.pixelX) * 0.45f
    pacman.pixelY += (targetY - pacman.pixelY) * 0.45f
}

// ===================== GHOST UPDATE =====================
private fun updateGhostSimple(ghost: Ghost, pacman: Pacman, maze: Array<IntArray>, frightened: Boolean) {
    val dirs = Direction.entries.filter { it != ghost.dir.opposite() }
    val bestDir = if (frightened) {
        dirs.random()
    } else {
        dirs.minByOrNull {
            val nx = ghost.pos.x + it.dx()
            val ny = ghost.pos.y + it.dy()
            abs(nx - pacman.pos.x) + abs(ny - pacman.pos.y)
        } ?: ghost.dir
    }

    val newPos = GridPos(ghost.pos.x + bestDir.dx(), ghost.pos.y + bestDir.dy())
    if (isValidMove(newPos, maze)) {
        ghost.dir = bestDir
        ghost.pos = newPos
    }

    if (ghost.pos.x < 0) ghost.pos.x = 27
    if (ghost.pos.x > 27) ghost.pos.x = 0

    ghost.pixelX += (ghost.pos.x - ghost.pixelX) * 0.4f
    ghost.pixelY += (ghost.pos.y - ghost.pixelY) * 0.4f
}

// ===================== HELPERS =====================
private fun isValidMove(pos: GridPos, maze: Array<IntArray>): Boolean {
    return pos.x in 0..27 && pos.y in 0..30 && maze[pos.y][pos.x] != 1
}

private fun Direction.dx() = when (this) { Direction.LEFT -> -1; Direction.RIGHT -> 1; else -> 0 }
private fun Direction.dy() = when (this) { Direction.UP -> -1; Direction.DOWN -> 1; else -> 0 }
private fun Direction.opposite() = when (this) {
    Direction.UP -> Direction.DOWN
    Direction.DOWN -> Direction.UP
    Direction.LEFT -> Direction.RIGHT
    Direction.RIGHT -> Direction.LEFT
    else -> Direction.NONE
}

private fun resetLevel(pacman: Pacman, ghosts: List<Ghost>) {
    pacman.pos = GridPos(13, 23)
    pacman.pixelX = 13f
    pacman.pixelY = 23f
    pacman.dir = Direction.LEFT
    pacman.nextDir = Direction.LEFT

    ghosts[0].pos = GridPos(14, 11); ghosts[0].pixelX = 14f; ghosts[0].pixelY = 11f
    ghosts[1].pos = GridPos(14, 14); ghosts[1].pixelX = 14f; ghosts[1].pixelY = 14f
    ghosts[2].pos = GridPos(12, 14); ghosts[2].pixelX = 12f; ghosts[2].pixelY = 14f
    ghosts[3].pos = GridPos(16, 14); ghosts[3].pixelX = 16f; ghosts[3].pixelY = 14f
}

// ===================== DRAW =====================
private fun drawMaze(scope: DrawScope, maze: Array<IntArray>, cell: Float, offset: Offset) = with(scope) {
    maze.forEachIndexed { y, row ->
        row.forEachIndexed { x, v ->
            val pos = offset + Offset(x * cell, y * cell)
            when (v) {
                1 -> drawRect(Color(0xFF4488FF), pos, Size(cell, cell))
                2 -> drawCircle(Color.White, cell * 0.12f, pos + Offset(cell / 2, cell / 2))
                3 -> drawCircle(Color.Cyan, cell * 0.35f, pos + Offset(cell / 2, cell / 2))
            }
        }
    }
}

private fun drawPacman(scope: DrawScope, p: Pacman, cell: Float, offset: Offset, mouth: Float) = with(scope) {
    val center = offset + Offset(p.pixelX * cell + cell / 2, p.pixelY * cell + cell / 2)
    val mouthAngle = 0.2f + mouth * 0.6f
    val startAngle = when (p.dir) {
        Direction.RIGHT -> 0f
        Direction.DOWN -> 90f
        Direction.LEFT -> 180f
        Direction.UP -> 270f
        else -> 0f
    }
    drawArc(
        color = Color.Yellow,
        startAngle = startAngle - mouthAngle * 45f,
        sweepAngle = 360f - mouthAngle * 90f,
        useCenter = true,
        topLeft = center - Offset(cell * 0.45f, cell * 0.45f),
        size = Size(cell * 0.9f, cell * 0.9f)
    )
}

private fun drawGhost(scope: DrawScope, g: Ghost, index: Int, cell: Float, offset: Offset, frightened: Boolean, flashing: Boolean) = with(scope) {
    val center = offset + Offset(g.pixelX * cell + cell / 2, g.pixelY * cell + cell / 2)
    val color = if (frightened) {
        if (flashing) Color.White else Color.Blue
    } else {
        when (index) {
            0 -> Color.Red
            1 -> Color.Magenta
            2 -> Color.Cyan
            else -> Color.Orange
        }
    }

    drawCircle(color, cell * 0.45f, center)

    val eyeX = cell * 0.15f
    val eyeY = cell * 0.1f
    drawCircle(Color.White, cell * 0.15f, center + Offset(-eyeX, -eyeY))
    drawCircle(Color.White, cell * 0.15f, center + Offset(eyeX, -eyeY))

    val pupilX = g.dir.dx() * cell * 0.1f
    val pupilY = g.dir.dy() * cell * 0.05f
    drawCircle(Color.Black, cell * 0.07f, center + Offset(-eyeX + pupilX, -eyeY + pupilY))
    drawCircle(Color.Black, cell * 0.07f, center + Offset(eyeX + pupilX, -eyeY + pupilY))
}
