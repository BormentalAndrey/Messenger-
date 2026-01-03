package com.kakdela.p2p.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

/* ===================== ENUMS ===================== */

enum class Direction { UP, DOWN, LEFT, RIGHT, NONE }
enum class GameState { READY, PLAYING, DYING, GAME_OVER }
enum class GhostState { SCATTER, CHASE, FRIGHTENED, EATEN }

/* ===================== DATA ===================== */

data class GridPos(var x: Int, var y: Int) {
    operator fun plus(d: Direction) = GridPos(x + d.dx(), y + d.dy())
}

data class Pacman(
    var pos: GridPos,
    var pixelX: Float,
    var pixelY: Float,
    var dir: Direction,
    var nextDir: Direction
)

data class Ghost(
    var pos: GridPos,
    var pixelX: Float,
    var pixelY: Float,
    var dir: Direction,
    val type: Int,
    var state: GhostState = GhostState.SCATTER
)

/* ===================== CONSTANTS ===================== */

private const val PACMAN_SPEED = 0.045f
private const val GHOST_SPEED = 0.035f
private const val FRIGHT_SPEED = 0.02f
private const val EATEN_SPEED = 0.06f

/* ===================== MAP ===================== */

private val MAP = arrayOf(
    intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,1,2,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,2,1),
    intArrayOf(1,3,1,1,1,1,1,2,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,3,1),
    intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1),
    intArrayOf(1,2,1,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,2,1),
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

/* ===================== COMPOSABLE ===================== */

@Composable
fun PacmanScreen() {
    var state by remember { mutableStateOf(GameState.READY) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }

    val maze = remember { MAP.map { it.copyOf() }.toTypedArray() }

    val pacman = remember {
        Pacman(GridPos(13, 23), 13f, 23f, Direction.LEFT, Direction.LEFT)
    }

    val ghosts = remember {
        mutableStateListOf(
            Ghost(GridPos(14,11),14f,11f,Direction.LEFT,0),
            Ghost(GridPos(14,14),14f,14f,Direction.UP,1),
            Ghost(GridPos(12,14),12f,14f,Direction.UP,2),
            Ghost(GridPos(16,14),16f,14f,Direction.UP,3)
        )
    }

    var frightenedTimer by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(2000)
        state = GameState.PLAYING
    }

    LaunchedEffect(state) {
        while (state == GameState.PLAYING) {
            updatePacman(pacman, maze)

            ghosts.forEach {
                updateGhost(it, pacman, maze)
            }

            ghosts.forEach {
                if (collision(pacman, it)) {
                    if (it.state == GhostState.FRIGHTENED) {
                        it.state = GhostState.EATEN
                        score += 200
                    } else if (it.state != GhostState.EATEN) {
                        lives--
                        state = if (lives <= 0) GameState.GAME_OVER else GameState.READY
                    }
                }
            }

            if (frightenedTimer > 0) frightenedTimer--

            delay(16)
        }
    }

    /* ==== UI ==== */

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = minOf(size.width / 28f, size.height / 31f)
            drawMaze(this, maze, cell)
            drawPacman(this, pacman, cell)
            ghosts.forEach { drawGhost(this, it, cell) }
        }

        Controls(pacman)
    }
}

/* ===================== LOGIC ===================== */

private fun updatePacman(p: Pacman, maze: Array<IntArray>) {
    if (isCentered(p) && isValid(p.pos + p.nextDir, maze)) {
        p.dir = p.nextDir
    }
    if (isValid(p.pos + p.dir, maze)) {
        p.pos += p.dir
    }
    p.pixelX += (p.pos.x - p.pixelX) * PACMAN_SPEED
    p.pixelY += (p.pos.y - p.pixelY) * PACMAN_SPEED
}

private fun updateGhost(g: Ghost, p: Pacman, maze: Array<IntArray>) {
    val target = when (g.state) {
        GhostState.CHASE -> p.pos
        GhostState.SCATTER -> scatterCorner(g.type)
        GhostState.EATEN -> GridPos(14,14)
        GhostState.FRIGHTENED -> {
            randomDir(g, maze)?.let { g.dir = it }
            g.pos += g.dir
            return
        }
    }

    val dirs = validDirs(g, maze)
    g.dir = dirs.minByOrNull { dist(g.pos + it, target) } ?: g.dir
    g.pos += g.dir

    val speed = when (g.state) {
        GhostState.FRIGHTENED -> FRIGHT_SPEED
        GhostState.EATEN -> EATEN_SPEED
        else -> GHOST_SPEED
    }

    g.pixelX += (g.pos.x - g.pixelX) * speed
    g.pixelY += (g.pos.y - g.pixelY) * speed
}

/* ===================== HELPERS ===================== */

private fun isValid(p: GridPos, m: Array<IntArray>) =
    p.y in m.indices && p.x in m[0].indices && m[p.y][p.x] != 1

private fun isCentered(p: Pacman) =
    abs(p.pixelX - p.pos.x) < 0.05f && abs(p.pixelY - p.pos.y) < 0.05f

private fun validDirs(g: Ghost, m: Array<IntArray>) =
    Direction.entries.filter {
        it != Direction.NONE && it != g.dir.opposite() && isValid(g.pos + it, m)
    }

private fun randomDir(g: Ghost, m: Array<IntArray>) =
    validDirs(g, m).randomOrNull()

private fun scatterCorner(type: Int) = when (type) {
    0 -> GridPos(27,0)
    1 -> GridPos(0,0)
    2 -> GridPos(27,30)
    else -> GridPos(0,30)
}

private fun collision(p: Pacman, g: Ghost) =
    abs(p.pixelX - g.pixelX) < 0.5f && abs(p.pixelY - g.pixelY) < 0.5f

private fun dist(a: GridPos, b: GridPos) =
    sqrt(((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y)).toFloat())

private fun Direction.dx() = when(this){Direction.LEFT->-1;Direction.RIGHT->1;else->0}
private fun Direction.dy() = when(this){Direction.UP->-1;Direction.DOWN->1;else->0}
private fun Direction.opposite() = when(this){
    Direction.UP->Direction.DOWN
    Direction.DOWN->Direction.UP
    Direction.LEFT->Direction.RIGHT
    Direction.RIGHT->Direction.LEFT
    else->Direction.NONE
}

/* ===================== DRAW ===================== */

private fun drawMaze(s: androidx.compose.ui.graphics.drawscope.DrawScope, m: Array<IntArray>, c: Float) = with(s) {
    m.forEachIndexed { y,row ->
        row.forEachIndexed { x,v ->
            when(v){
                1 -> drawRect(Color.Blue, Offset(x*c,y*c), Size(c,c))
                2 -> drawCircle(Color.White, c*0.1f, Offset(x*c+c/2,y*c+c/2))
                3 -> drawCircle(Color.Cyan, c*0.3f, Offset(x*c+c/2,y*c+c/2))
            }
        }
    }
}

private fun drawPacman(s: androidx.compose.ui.graphics.drawscope.DrawScope, p: Pacman, c: Float) = with(s) {
    drawCircle(Color.Yellow, c*0.45f, Offset(p.pixelX*c+c/2,p.pixelY*c+c/2))
}

private fun drawGhost(s: androidx.compose.ui.graphics.drawscope.DrawScope, g: Ghost, c: Float) = with(s) {
    val color = when {
        g.state == GhostState.FRIGHTENED -> Color.Blue
        g.state == GhostState.EATEN -> Color.Gray
        else -> listOf(Color.Red,Color.Magenta,Color.Cyan,Color(0xFFFF9800))[g.type]
    }
    drawCircle(color, c*0.45f, Offset(g.pixelX*c+c/2,g.pixelY*c+c/2))
}

/* ===================== CONTROLS ===================== */

@Composable
private fun Controls(p: Pacman) {
    Row(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
        IconButton({ p.nextDir = Direction.LEFT }) {
            Icon(Icons.Default.KeyboardArrowLeft,null,tint=Color.Yellow)
        }
        Column {
            IconButton({ p.nextDir = Direction.UP }) {
                Icon(Icons.Default.KeyboardArrowUp,null,tint=Color.Yellow)
            }
            IconButton({ p.nextDir = Direction.DOWN }) {
                Icon(Icons.Default.KeyboardArrowDown,null,tint=Color.Yellow)
            }
        }
        IconButton({ p.nextDir = Direction.RIGHT }) {
            Icon(Icons.Default.KeyboardArrowRight,null,tint=Color.Yellow)
        }
    }
}
