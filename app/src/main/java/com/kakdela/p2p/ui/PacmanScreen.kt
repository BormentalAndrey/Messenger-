// ==========================
//  PACMAN — FULL VERSION
// ==========================

package com.kakdela.p2p.ui

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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

// ---------------- ENUMS ----------------

enum class Direction { UP, DOWN, LEFT, RIGHT, NONE }
enum class GameState { READY, PLAYING, GAME_OVER }
enum class GhostMode { SCATTER, CHASE, FRIGHTENED }
enum class GhostType { BLINKY, PINKY, INKY, CLYDE }

// ---------------- DATA ----------------

data class Entity(
    var x: Float,
    var y: Float,
    var dir: Direction = Direction.LEFT,
    var next: Direction = Direction.LEFT
)

data class Ghost(
    var x: Float,
    var y: Float,
    var dir: Direction,
    val type: GhostType,
    var mode: GhostMode = GhostMode.SCATTER
)

data class SpeedProfile(
    val pacman: Float,
    val ghost: Float,
    val frightened: Float
)

// ---------------- SPEED TABLE ----------------

fun speedForLevel(level: Int) = when {
    level <= 1 -> SpeedProfile(0.18f, 0.15f, 0.09f)
    level <= 4 -> SpeedProfile(0.20f, 0.17f, 0.11f)
    level <= 8 -> SpeedProfile(0.22f, 0.19f, 0.12f)
    else -> SpeedProfile(0.24f, 0.21f, 0.13f)
}

// ---------------- MAP ----------------
// 0 empty, 1 wall, 2 dot, 3 power

private val MAP = Array(31) { y ->
    IntArray(28) { x ->
        when {
            x == 0 || y == 0 || x == 27 || y == 30 -> 1
            (x + y) % 7 == 0 -> 2
            (x == 1 && y == 3) || (x == 26 && y == 3) -> 3
            else -> 0
        }
    }
}

// ===================== MAIN =====================

@Composable
fun PacmanScreen() {

    val context = LocalContext.current

    // --- sounds ---
    val soundPool = remember { SoundPool.Builder().setMaxStreams(4).build() }
    val sndChomp = remember { soundPool.load(context, android.R.raw.btn_default, 1) }

    var gameState by remember { mutableStateOf(GameState.READY) }
    var level by remember { mutableIntStateOf(1) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }

    val speed = remember(level) { speedForLevel(level) }

    var pacman by remember {
        mutableStateOf(Entity(14f, 23f))
    }

    val ghosts = remember {
        mutableStateListOf(
            Ghost(14f, 14f, Direction.LEFT, GhostType.BLINKY),
            Ghost(13f, 14f, Direction.UP, GhostType.PINKY),
            Ghost(15f, 14f, Direction.DOWN, GhostType.INKY),
            Ghost(14f, 15f, Direction.RIGHT, GhostType.CLYDE)
        )
    }

    // --- mode timer ---
    var modeTimer by remember { mutableIntStateOf(0) }

    // ================= GAME LOOP =================

    LaunchedEffect(Unit) {
        gameState = GameState.PLAYING
        while (true) {
            if (gameState == GameState.PLAYING) {

                // ---- Pacman ----
                pacman.dir = pacman.next
                pacman.x += pacman.dir.vector().x * speed.pacman
                pacman.y += pacman.dir.vector().y * speed.pacman

                pacman.x = pacman.x.coerceIn(1f, 26f)
                pacman.y = pacman.y.coerceIn(1f, 29f)

                // ---- Ghosts ----
                updateGhosts(ghosts, pacman, speed)

                // ---- Mode switch ----
                modeTimer++
                if (modeTimer % 600 == 0) {
                    ghosts.forEach {
                        it.mode =
                            if (it.mode == GhostMode.SCATTER) GhostMode.CHASE
                            else GhostMode.SCATTER
                    }
                }
            }
            delay(16)
        }
    }

    // ================= UI =================

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Canvas(Modifier.fillMaxSize()) {
            val cell = min(size.width / 28f, size.height / 31f)
            drawMap(cell)
            drawPacman(pacman, cell)
            ghosts.forEachIndexed { i, g -> drawGhost(g, i, cell) }
        }

        AnalogJoystick(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp)
        ) { pacman.next = it }

        Text(
            "Score: $score   Lives: $lives",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
        )
    }
}

// ================= LOGIC =================

private fun updateGhosts(
    ghosts: List<Ghost>,
    pacman: Entity,
    speed: SpeedProfile
) {
    val blinky = ghosts.first { it.type == GhostType.BLINKY }

    ghosts.forEach { g ->
        val target = when (g.mode) {
            GhostMode.SCATTER -> scatterTarget(g.type)
            GhostMode.CHASE -> chaseTarget(g, pacman, blinky)
            GhostMode.FRIGHTENED ->
                Offset(Random.nextFloat() * 28, Random.nextFloat() * 31)
        }

        val dir = bestDir(g, target)
        val s = if (g.mode == GhostMode.FRIGHTENED) speed.frightened else speed.ghost

        g.x += dir.vector().x * s
        g.y += dir.vector().y * s
        g.dir = dir
    }
}

private fun scatterTarget(t: GhostType) = when (t) {
    GhostType.BLINKY -> Offset(27f, 0f)
    GhostType.PINKY -> Offset(0f, 0f)
    GhostType.INKY -> Offset(27f, 30f)
    GhostType.CLYDE -> Offset(0f, 30f)
}

private fun chaseTarget(g: Ghost, p: Entity, b: Ghost) = when (g.type) {
    GhostType.BLINKY -> Offset(p.x, p.y)
    GhostType.PINKY ->
        Offset(p.x + p.dir.vector().x * 4, p.y + p.dir.vector().y * 4)
    GhostType.INKY -> {
        val px = p.x + p.dir.vector().x * 2
        val py = p.y + p.dir.vector().y * 2
        Offset(px * 2 - b.x, py * 2 - b.y)
    }
    GhostType.CLYDE ->
        if (hypot(g.x - p.x, g.y - p.y) > 8)
            Offset(p.x, p.y)
        else Offset(0f, 30f)
}

private fun bestDir(g: Ghost, t: Offset): Direction =
    Direction.values()
        .filter { it != Direction.NONE && it != g.dir.opposite() }
        .minByOrNull {
            val v = it.vector()
            hypot(g.x + v.x - t.x, g.y + v.y - t.y)
        } ?: g.dir

// ================= DRAW =================

private fun DrawScope.drawMap(cell: Float) {
    MAP.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            if (c == 1) {
                drawRect(
                    Color.Blue,
                    Offset(x * cell, y * cell),
                    Size(cell, cell)
                )
            }
        }
    }
}

private fun DrawScope.drawPacman(p: Entity, c: Float) {
    drawCircle(
        Color.Yellow,
        c / 2,
        Offset(p.x * c, p.y * c)
    )
}

private fun DrawScope.drawGhost(g: Ghost, i: Int, c: Float) {
    val colors = listOf(Color.Red, Color.Magenta, Color.Cyan, Color(0xFFFF9800))
    drawRoundRect(
        if (g.mode == GhostMode.FRIGHTENED) Color.Blue else colors[i],
        Offset(g.x * c - c / 2, g.y * c - c / 2),
        Size(c, c),
        CornerRadius(c / 2)
    )
}

// ================= JOYSTICK =================

@Composable
fun AnalogJoystick(
    modifier: Modifier,
    onDirection: (Direction) -> Unit
) {
    Box(
        modifier
            .size(160.dp)
            .background(Color(0x66000000), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { _, drag ->
                    val angle = atan2(drag.y, drag.x) * 180 / PI
                    onDirection(
                        when {
                            angle in -45.0..45.0 -> Direction.RIGHT
                            angle in 45.0..135.0 -> Direction.DOWN
                            angle in -135.0..-45.0 -> Direction.UP
                            else -> Direction.LEFT
                        }
                    )
                }
            }
    )
}

// ================= HELPERS =================

private fun Direction.vector() = when (this) {
    Direction.UP -> Offset(0f, -1f)
    Direction.DOWN -> Offset(0f, 1f)
    Direction.LEFT -> Offset(-1f, 0f)
    Direction.RIGHT -> Offset(1f, 0f)
    else -> Offset.Zero
}

private fun Direction.opposite() = when (this) {
    Direction.UP -> Direction.DOWN
    Direction.DOWN -> Direction.UP
    Direction.LEFT -> Direction.RIGHT
    Direction.RIGHT -> Direction.LEFT
    else -> Direction.NONE
}
