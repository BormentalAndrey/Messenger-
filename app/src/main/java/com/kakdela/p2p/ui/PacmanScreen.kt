package com.kakdela.p2p.ui

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random  // <-- Добавлен импорт!

// ===================== ENUMS =====================
enum class Direction { UP, DOWN, LEFT, RIGHT, NONE }
enum class PacmanGameState { READY, PLAYING, DYING, GAME_OVER }  // <-- Переименован, чтобы не конфликтовать
enum class GhostMode { SCATTER, CHASE, FRIGHTENED }

// ===================== DATA =====================
data class Pacman(
    var x: Float = 14f,
    var y: Float = 23f,
    var dir: Direction = Direction.LEFT,
    var nextDir: Direction = Direction.LEFT
)

data class Ghost(
    var x: Float,
    var y: Float,
    var dir: Direction,
    val type: Int // 0=Blinky, 1=Pinky, 2=Inky, 3=Clyde
)

// ===================== ORIGINAL MAP =====================
private val ORIGINAL_MAP = arrayOf(
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,2,1),
    intArrayOf(1,3,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,3,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,2,1,1,1,1,1,1,1,1,1,2,1,2,1,1,1,1,1,2,1),
    intArrayOf(1,2,2,2,2,2,2,1,2,1,1,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,1,1,1,1,1,2,1,2,1,1,0,0,0,0,0,0,1,1,2,1,1,1,1,1,1,1,1),
    intArrayOf(0,0,0,0,0,1,2,1,0,0,0,0,0,0,0,0,0,0,0,0,1,2,1,0,0,0,0,0),
    intArrayOf(0,0,0,0,0,1,2,1,0,1,1,1,1,1,1,1,1,1,1,0,1,2,1,0,0,0,0,0),
    intArrayOf(0,0,0,0,0,1,2,1,0,1,0,0,0,0,0,0,0,0,0,1,0,1,2,1,0,0,0,0),
    intArrayOf(1,1,1,1,1,1,2,1,0,1,0,0,0,0,0,0,0,0,0,1,0,1,2,1,1,1,1,1),
    intArrayOf(0,0,0,0,0,0,2,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,2,0,0,0,0,0),
    intArrayOf(1,1,1,1,1,1,2,1,0,1,0,0,0,0,0,0,0,0,0,1,0,1,2,1,1,1,1,1),
    intArrayOf(0,0,0,0,0,1,2,1,0,1,1,1,1,1,1,1,1,1,1,0,1,2,1,0,0,0,0,0),
    intArrayOf(0,0,0,0,0,1,2,1,0,0,0,0,0,0,0,0,0,0,0,0,1,2,1,0,0,0,0,0),
    intArrayOf(1,1,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,2,1),
    intArrayOf(1,2,2,2,2,2,2,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1,2,2,2,2,1),
    intArrayOf(1,1,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,2,1),
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
    // Заглушка: используем встроенный звук или 0, если не важен
    val chompSound = remember {
        // android.R.raw.notification может не существовать в твоём проекте
        // Замени на свой raw-ресурс или оставь 0 для тишины
        0
    }

    var gameState by remember { mutableStateOf(PacmanGameState.READY) }
    var level by remember { mutableIntStateOf(1) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var dotsEaten by remember(level) { mutableIntStateOf(0) }
    var turbo by remember { mutableStateOf(false) }

    var pacman by remember { mutableStateOf(Pacman()) }
    val ghosts = remember {
        mutableStateListOf(
            Ghost(14f, 11f, Direction.LEFT, 0),
            Ghost(14f, 14f, Direction.UP, 1),
            Ghost(12f, 14f, Direction.UP, 2),
            Ghost(16f, 14f, Direction.UP, 3)
        )
    }

    var frightenedTimer by remember { mutableIntStateOf(0) }
    var globalModeTimer by remember { mutableIntStateOf(0) }

    val maze = remember(level) { ORIGINAL_MAP.map { it.copyOf() }.toTypedArray() }
    val totalDots = remember { ORIGINAL_MAP.sumOf { row -> row.count { it == 2 || it == 3 } } }

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

            if (canMove(pacman, pacman.nextDir, maze)) pacman.dir = pacman.nextDir
            movePacman(pacman, maze, level, turbo)

            val px = pacman.x.roundToInt().coerceIn(0, 27)
            val py = pacman.y.roundToInt().coerceIn(0, 30)
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
                val target = getTargetTile(ghost, pacman, blinky, currentMode, frightenedTimer > 0)
                val possibleDirs = Direction.entries.filter {
                    it != ghost.dir.opposite() && canMoveGhost(ghost, it, maze)
                }

                val bestDir = if (frightenedTimer > 0) {
                    possibleDirs.randomOrNull() ?: ghost.dir
                } else {
                    possibleDirs.minByOrNull { dir ->
                        val nx = ghost.x + dir.dx()
                        val ny = ghost.y + dir.dy()
                        hypot(nx - target.x, ny - target.y)
                    } ?: ghost.dir
                }

                ghost.dir = bestDir
                val speed = ghostSpeed(frightenedTimer > 0, level)
                ghost.x += bestDir.dx() * speed
                ghost.y += bestDir.dy() * speed

                if (ghost.x < 0) ghost.x = 27.9f
                if (ghost.x > 28) ghost.x = 0.1f
            }

            ghosts.forEach { ghost ->
                if (hypot(ghost.x - pacman.x, ghost.y - pacman.y) < 0.8f) {
                    if (frightenedTimer > 0) {
                        score += 200
                        ghost.x = 14f
                        ghost.y = 14f
                        frightenedTimer -= 60
                    } else {
                        lives--
                        if (lives <= 0) {
                            gameState = PacmanGameState.GAME_OVER
                        } else {
                            gameState = PacmanGameState.DYING
                            delay(2000)
                            resetPositions(pacman, ghosts)
                            gameState = PacmanGameState.PLAYING
                        }
                    }
                }
            }

            frightenedTimer = maxOf(0, frightenedTimer - 1)

            if (dotsEaten >= totalDots) {
                level++
                dotsEaten = 0
                resetPositions(pacman, ghosts)
            }

            delay(16)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PAC-MAN", color = Color.Yellow, fontSize = 24.sp) },
                actions = {
                    Text("Score: $score  Lives: $lives  Level: $level", color = Color.White, fontSize = 16.sp)
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

            AnalogJoystick(
                modifier = Modifier.align(Alignment.BottomStart).padding(32.dp)
            ) { dir, isTurbo ->
                pacman.nextDir = dir
                turbo = isTurbo
            }

            if (gameState == PacmanGameState.READY) {
                Text("READY!", color = Color.Yellow, fontSize = 48.sp, modifier = Modifier.align(Alignment.Center))
            }
            if (gameState == PacmanGameState.GAME_OVER) {
                Text("GAME OVER", color = Color.Red, fontSize = 64.sp, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

// ===================== ОТРИСОВКА =====================
private fun drawMaze(scope: DrawScope, maze: Array<IntArray>, cell: Float, offset: Offset) = with(scope) {
    maze.forEachIndexed { y, row ->
        row.forEachIndexed { x, v ->
            val pos = offset + Offset(x * cell, y * cell)
            when (v) {
                1 -> drawRect(Color(0xFF00FFFF), pos, Size(cell, cell), style = Stroke(4f))
                2 -> drawCircle(Color.White, cell * 0.12f, pos + Offset(cell / 2, cell / 2))
                3 -> drawCircle(Color.White, cell * 0.35f, pos + Offset(cell / 2, cell / 2))
            }
        }
    }
}

private fun drawPacman(scope: DrawScope, p: Pacman, cell: Float, offset: Offset, mouth: Float) = with(scope) {
    val center = offset + Offset(p.x * cell + cell / 2, p.y * cell + cell / 2)
    val mouthAngle = 0.2f + mouth * 0.7f
    val startAngle = when (p.dir) {
        Direction.RIGHT -> 0f
        Direction.DOWN -> 90f
        Direction.LEFT -> 180f
        Direction.UP -> 270f
        else -> 0f
    } - mouthAngle * 45f
    drawArc(
        color = Color.Yellow,
        startAngle = startAngle,
        sweepAngle = 360f - mouthAngle * 90f,
        useCenter = true,
        topLeft = center - Offset(cell * 0.45f, cell * 0.45f),
        size = Size(cell * 0.9f, cell * 0.9f)
    )
}

private fun drawGhost(scope: DrawScope, g: Ghost, index: Int, cell: Float, offset: Offset, frightened: Boolean, flashing: Boolean) = with(scope) {
    val center = offset + Offset(g.x * cell + cell / 2, g.y * cell + cell / 2)
    val color = if (frightened) {
        if (flashing) Color.White else Color(0xFF0000AA)
    } else {
        listOf(Color.Red, Color(0xFFFFB8FF), Color.Cyan, Color(0xFFFF9800))[index]
    }

    drawCircle(color, cell * 0.45f, center)

    val eyeOffset = cell * 0.15f
    drawCircle(Color.White, cell * 0.15f, center + Offset(-eyeOffset, -cell * 0.1f))
    drawCircle(Color.White, cell * 0.15f, center + Offset(eyeOffset, -cell * 0.1f))

    val pupil = cell * 0.07f
    val pupilX = g.dir.dx() * cell * 0.1f
    val pupilY = g.dir.dy() * cell * 0.05f
    drawCircle(Color.Black, pupil, center + Offset(-eyeOffset + pupilX, -cell * 0.1f + pupilY))
    drawCircle(Color.Black, pupil, center + Offset(eyeOffset + pupilX, -cell * 0.1f + pupilY))
}

// ===================== Остальные функции (без изменений) =====================
private fun movePacman(p: Pacman, maze: Array<IntArray>, level: Int, turbo: Boolean) {
    val baseSpeed = 0.18f + level * 0.02f
    val speed = if (turbo) baseSpeed * 1.8f else baseSpeed
    p.x += p.dir.dx() * speed
    p.y += p.dir.dy() * speed

    if (p.x < 0) p.x = 27.9f
    if (p.x > 28) p.x = 0.1f
}

private fun canMove(p: Pacman, dir: Direction, maze: Array<IntArray>): Boolean {
    if (dir == Direction.NONE) return true
    val nx = (p.x + dir.dx()).coerceIn(0f, 27.9f)
    val ny = (p.y + dir.dy()).coerceIn(0f, 30f)
    val ix = nx.roundToInt()
    val iy = ny.roundToInt()
    return maze[iy][ix] != 1
}

private fun canMoveGhost(g: Ghost, dir: Direction, maze: Array<IntArray>): Boolean {
    val nx = g.x + dir.dx()
    val ny = g.y + dir.dy()
    val ix = nx.roundToInt().coerceIn(0, 27)
    val iy = ny.roundToInt().coerceIn(0, 30)
    return maze[iy][ix] != 1
}

private fun getTargetTile(g: Ghost, p: Pacman, blinky: Ghost, mode: GhostMode, frightened: Boolean): Offset {
    if (frightened) return Offset(Random.nextFloat() * 28f, Random.nextFloat() * 31f)
    if (mode == GhostMode.SCATTER) return when (g.type) {
        0 -> Offset(25f, 0f)
        1 -> Offset(2f, 0f)
        2 -> Offset(27f, 30f)
        else -> Offset(0f, 30f)
    }
    return when (g.type) {
        0 -> Offset(p.x, p.y)
        1 -> Offset(p.x + p.dir.dx() * 4, p.y + p.dir.dy() * 4)
        2 -> {
            val front = Offset(p.x + p.dir.dx() * 2, p.y + p.dir.dy() * 2)
            Offset(2 * front.x - blinky.x, 2 * front.y - blinky.y)
        }
        else -> if (hypot(g.x - p.x, g.y - p.y) > 8) Offset(p.x, p.y) else Offset(0f, 30f)
    }
}

private fun ghostSpeed(frightened: Boolean, level: Int): Float =
    if (frightened) 0.08f else 0.15f + level * 0.01f

private fun Direction.dx(): Float = when (this) { Direction.RIGHT -> 1f; Direction.LEFT -> -1f; else -> 0f }
private fun Direction.dy(): Float = when (this) { Direction.DOWN -> 1f; Direction.UP -> -1f; else -> 0f }
private fun Direction.opposite(): Direction = when (this) {
    Direction.UP -> Direction.DOWN
    Direction.DOWN -> Direction.UP
    Direction.LEFT -> Direction.RIGHT
    Direction.RIGHT -> Direction.LEFT
    else -> Direction.NONE
}

private fun resetPositions(p: Pacman, ghosts: List<Ghost>) {
    p.x = 14f; p.y = 23f; p.dir = Direction.LEFT; p.nextDir = Direction.LEFT
    ghosts[0].apply { x = 14f; y = 11f }
    ghosts[1].apply { x = 14f; y = 14f }
    ghosts[2].apply { x = 12f; y = 14f }
    ghosts[3].apply { x = 16f; y = 14f }
}

// ===================== JOYSTICK =====================
@Composable
fun AnalogJoystick(
    modifier: Modifier = Modifier,
    onDirection: (Direction, Boolean) -> Unit
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size(160.dp)
            .background(Color(0x60000000), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        dragOffset = Offset.Zero
                        onDirection(Direction.NONE, false)
                    },
                    onDrag = { _, dragAmount ->
                        dragOffset += dragAmount
                        val distance = dragOffset.getDistance()
                        val angle = atan2(dragOffset.y, dragOffset.x) * 180f / PI.toFloat()

                        val dir = when {
                            distance < 30 -> Direction.NONE
                            angle in -45f..45f -> Direction.RIGHT
                            angle in 45f..135f -> Direction.DOWN
                            angle in -135f..-45f -> Direction.UP
                            else -> Direction.LEFT
                        }
                        onDirection(dir, distance > 80)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        dragOffset.x.coerceIn(-50f, 50f).toInt(),
                        dragOffset.y.coerceIn(-50f, 50f).toInt()
                    )
                }
                .size(80.dp)
                .background(Color.White.copy(alpha = 0.4f), CircleShape)
        )
    }
}
