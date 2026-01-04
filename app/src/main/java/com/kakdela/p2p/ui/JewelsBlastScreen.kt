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

// ==================== КОНСТАНТЫ И ЦВЕТА ====================
private const val GRID_SIZE = 8
private const val JEWEL_TYPES = 6
private const val MAX_LEVELS = 200

private val gemColors = listOf(
    Color(0xFFFF0055), // Красный неон
    Color(0xFF00FFFF), // Циан
    Color(0xFF00FF00), // Зелёный неон
    Color(0xFFFFD700), // Золотой
    Color(0xFFBF00FF), // Пурпурный неон
    Color(0xFFFFFFFF)  // Белый/бриллиант
)

// ==================== МОДЕЛИ ДАННЫХ ====================
data class LevelData(
    val levelNumber: Int,
    val targetScore: Int,
    val moves: Int
)

data class Position(val row: Int, val col: Int)

enum class GameState { PLAYING, WON, LOST, SWAPPING, PROCESSING }

// Спецэффекты
enum class Effects { NONE, H_RAY, V_RAY, AREA, KIND }

// Камень
data class Gem(
    var kind: Int = -1,  // -1 = пусто
    var effect: Effects = Effects.NONE
)

// Match (для поиска цепочек)
private data class Match(
    var startRow: Int = 0,
    var startCol: Int = 0,
    var length: Int = 0,
    var isRow: Boolean = true  // true = горизонталь, false = вертикаль
)

// ==================== ГЕНЕРАТОР УРОВНЕЙ ====================
private fun generateLevel(level: Int): LevelData {
    val baseScore = 1000
    val scoreMultiplier = 250
    val movesBase = 30
    val moves = (movesBase - (level / 10)).coerceAtLeast(15)

    return LevelData(
        levelNumber = level,
        targetScore = baseScore + level * scoreMultiplier,
        moves = moves
    )
}

// ==================== ЛОГИКА ПОЛЯ (портировано из Mystery of Orient Express) ====================
class Field(val size: Int = GRID_SIZE, val kindsCount: Int = JEWEL_TYPES) {
    val cells = Array(size) { Array(size) { Gem(Random.nextInt(kindsCount)) } }

    private val rowMatches = mutableListOf<Match>()
    private val colMatches = mutableListOf<Match>()
    private var bonusScore = 0

    fun findMatchedGems(): Pair<Set<Position>, Int> {
        bonusScore = 0
        rowMatches.clear()
        colMatches.clear()

        findMatches(true)   // горизонтальные
        findMatches(false)  // вертикальные

        val matched = mutableSetOf<Position>()

        // Бонусы за длинные матчи
        for (match in rowMatches + colMatches) {
            if (match.length > 3) {
                bonusScore += match.length * (match.length - 1) * (match.length - 2) / 2
            }
        }

        // Создание спецкамней
        createSpecialGems(rowMatches, false)  // горизонталь → V_RAY (вертикальная молния)
        createSpecialGems(colMatches, true)   // вертикаль → H_RAY (горизонтальная молния)

        // Крест → AREA
        createAreaForCrosses()

        // Добавляем все матчи в набор удаляемых
        addMatchesToSet(rowMatches, matched)
        addMatchesToSet(colMatches, matched)

        return matched to bonusScore
    }

    private fun findMatches(isRowMatch: Boolean) {
        val matches = if (isRowMatch) rowMatches else colMatches

        for (fixed in 0 until size) {
            var length = 0
            var kind = -1

            for (variable in 0 until size) {
                val row = if (isRowMatch) fixed else variable
                val col = if (isRowMatch) variable else fixed
                val gem = cells[row][col]

                if (gem.kind >= 0 && gem.kind == kind) {
                    length++
                } else {
                    if (length >= 3) {
                        matches.add(Match(
                            startRow = if (isRowMatch) fixed else variable - length,
                            startCol = if (isRowMatch) variable - length else fixed,
                            length = length,
                            isRow = isRowMatch
                        ))
                    }
                    length = 1
                    kind = gem.kind
                }
            }

            if (length >= 3) {
                matches.add(Match(
                    startRow = if (isRowMatch) fixed else size - length,
                    startCol = if (isRowMatch) size - length else fixed,
                    length = length,
                    isRow = isRowMatch
                ))
            }
        }
    }

    private fun createSpecialGems(matches: List<Match>, horizontalRay: Boolean) {
        for (match in matches) {
            if (match.length > 3) {
                val freePositions = mutableListOf<Position>()
                for (i in 0 until match.length) {
                    val row = if (match.isRow) match.startRow else match.startRow + i
                    val col = if (match.isRow) match.startCol + i else match.startCol
                    if (cells[row][col].effect == Effects.NONE) {
                        freePositions.add(Position(row, col))
                    }
                }
                if (freePositions.isNotEmpty()) {
                    val pos = freePositions.random()
                    cells[pos.row][pos.col].effect = when (match.length) {
                        4 -> if (horizontalRay) Effects.H_RAY else Effects.V_RAY
                        else -> Effects.KIND.also { cells[pos.row][pos.col].kind = -1 }
                    }
                }
            }
        }
    }

    private fun createAreaForCrosses() {
        for (rowMatch in rowMatches) {
            for (colMatch in colMatches) {
                val crossRow = colMatch.startRow
                val crossCol = rowMatch.startCol
                if (crossRow in rowMatch.startRow until rowMatch.startRow + rowMatch.length &&
                    crossCol in colMatch.startCol until colMatch.startCol + colMatch.length) {
                    bonusScore += rowMatch.length * (rowMatch.length - 2) * colMatch.length * (colMatch.length - 2)
                    if (cells[crossRow][crossCol].effect == Effects.NONE) {
                        cells[crossRow][crossCol].effect = Effects.AREA
                    }
                }
            }
        }
    }

    private fun addMatchesToSet(matches: List<Match>, set: MutableSet<Position>) {
        for (match in matches) {
            for (i in 0 until match.length) {
                val row = if (match.isRow) match.startRow else match.startRow + i
                val col = if (match.isRow) match.startCol + i else match.startCol
                set.add(Position(row, col))
            }
        }
    }

    fun removeGems(toRemove: Set<Position>): Set<Position> {
        val chained = mutableSetOf<Position>()
        for (pos in toRemove) {
            val gem = cells[pos.row][pos.col]
            cells[pos.row][pos.col].kind = -1

            when (gem.effect) {
                Effects.H_RAY -> for (r in 0 until size) chained.add(Position(r, pos.col))
                Effects.V_RAY -> for (c in 0 until size) chained.add(Position(pos.row, c))
                Effects.AREA -> for (dr in -1..1) for (dc in -1..1) {
                    val nr = pos.row + dr
                    val nc = pos.col + dc
                    if (nr in 0 until size && nc in 0 until size) chained.add(Position(nr, nc))
                }
                Effects.KIND -> {
                    val targetKind = Random.nextInt(kindsCount)
                    for (r in 0 until size) for (c in 0 until size) {
                        if (cells[r][c].kind == targetKind) chained.add(Position(r, c))
                    }
                }
                else -> {}
            }
        }
        return chained
    }

    fun dropGems() {
        for (col in 0 until size) {
            val column = mutableListOf<Gem>()
            for (row in 0 until size) {
                if (cells[row][col].kind >= 0) column.add(cells[row][col])
            }
            val missing = size - column.size
            repeat(missing) { column.add(0, Gem(Random.nextInt(kindsCount))) }
            for (row in 0 until size) {
                cells[row][col] = column[row]
            }
        }
    }

    fun testSwap(p1: Position, p2: Position): Boolean {
        swap(p1, p2)
        val (matched, _) = findMatchedGems()
        val valid = matched.isNotEmpty()
        if (!valid) swap(p1, p2)
        return valid
    }

    private fun swap(p1: Position, p2: Position) {
        val temp = cells[p1.row][p1.col]
        cells[p1.row][p1.col] = cells[p2.row][p2.col]
        cells[p2.row][p2.col] = temp
    }

    fun hasNoMoves(): Boolean {
        // Упрощённая проверка (можно улучшить)
        for (r in 0 until size) for (c in 0 until size) {
            if (cells[r][c].effect == Effects.KIND) return false
        }
        // Полная проверка всех возможных свапов слишком тяжёлая, поэтому просто возвращаем false если есть матчи после шаффла
        return false
    }
}

// ==================== ОСНОВНОЙ ЭКРАН ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JewelsBlastScreen() {
    var currentLevel by remember { mutableIntStateOf(1) }
    var levelData by remember { mutableStateOf(generateLevel(currentLevel)) }

    var score by remember { mutableIntStateOf(0) }
    var movesLeft by remember { mutableIntStateOf(levelData.moves) }
    var gameState by remember { mutableStateOf(GameState.PLAYING) }

    var field by remember { mutableStateOf(Field()) }

    var selectedPos by remember { mutableStateOf<Position?>(null) }
    val scope = rememberCoroutineScope()

    fun resetLevel(newLevel: Int) {
        currentLevel = newLevel
        levelData = generateLevel(newLevel)
        score = 0
        movesLeft = levelData.moves
        field = Field()
        gameState = GameState.PLAYING
        selectedPos = null
    }

    suspend fun processBoard() {
        gameState = GameState.PROCESSING
        var hasActivity = true

        while (hasActivity) {
            val (matched, bonus) = field.findMatchedGems()

            if (matched.isEmpty()) {
                hasActivity = false
                if (field.hasNoMoves() && movesLeft > 0) {
                    // Простой шаффл
                    field = Field()
                }
            } else {
                score += bonus + matched.size * 10

                delay(400)

                val chained = field.removeGems(matched)
                (matched + chained).forEach {
                    field.cells[it.row][it.col].kind = -1
                }

                delay(400)

                field.dropGems()

                delay(400)
            }
        }

        when {
            score >= levelData.targetScore -> gameState = GameState.WON
            movesLeft <= 0 && score < levelData.targetScore -> gameState = GameState.LOST
            else -> gameState = GameState.PLAYING
        }
    }

    fun onGemClick(pos: Position) {
        if (gameState != GameState.PLAYING || field.cells[pos.row][pos.col].kind < 0) return

        if (selectedPos == null) {
            selectedPos = pos
            return
        }

        val first = selectedPos!!
        val adjacent = abs(first.row - pos.row) + abs(first.col - pos.col) == 1

        if (adjacent && field.testSwap(first, pos)) {
            movesLeft--
            scope.launch { processBoard() }
        }
        selectedPos = null
    }

    Scaffold(
        topBar = {
            Column(Modifier.background(Color.Black)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Jewels Blast • УРОВЕНЬ $currentLevel",
                            color = Color.Magenta,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black),
                    actions = {
                        IconButton(onClick = { resetLevel(currentLevel) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = Color.White)
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("ХОДЫ", color = Color.Gray, fontSize = 12.sp)
                        Text("$movesLeft", color = if (movesLeft < 6) Color.Red else Color.White,
                            fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ЦЕЛЬ: ${levelData.targetScore}", color = Color.Gray, fontSize = 12.sp)
                        Text("$score", color = Color.Cyan, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }

                val progress = (score.toFloat() / levelData.targetScore).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color.Magenta,
                    trackColor = Color.DarkGray
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(padding),
            contentAlignment = Alignment.Center
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_SIZE),
                modifier = Modifier
                    .padding(16.dp)
                    .aspectRatio(1f)
                    .border(4.dp, Brush.linearGradient(listOf(Color.Magenta, Color.Cyan)), RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0A0A)),
                userScrollEnabled = false
            ) {
                items(GRID_SIZE * GRID_SIZE) { index ->
                    val row = index / GRID_SIZE
                    val col = index % GRID_SIZE
                    val gem = field.cells[row][col]

                    if (gem.kind != -1) {
                        JewelItem(
                            gem = gem,
                            isSelected = selectedPos?.row == row && selectedPos?.col == col,
                            onClick = { onGemClick(Position(row, col)) }
                        )
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }
            }

            AnimatedVisibility(visible = gameState == GameState.WON) {
                GameDialog(
                    title = "УРОВЕНЬ ПРОЙДЕН!",
                    message = "Счёт: $score",
                    buttonText = if (currentLevel < MAX_LEVELS) "СЛЕДУЮЩИЙ" else "ПОБЕДА!",
                    color = Color(0xFF00FF00),
                    onAction = {
                        if (currentLevel < MAX_LEVELS) resetLevel(currentLevel + 1)
                    }
                )
            }

            AnimatedVisibility(visible = gameState == GameState.LOST) {
                GameDialog(
                    title = "ХОДЫ ЗАКОНЧИЛИСЬ",
                    message = "Набрано: $score из ${levelData.targetScore}",
                    buttonText = "ПОВТОРИТЬ",
                    color = Color.Red,
                    onAction = { resetLevel(currentLevel) }
                )
            }
        }
    }
}

// ==================== КОМПОНЕНТ КРИСТАЛЛА ====================
@Composable
fun JewelItem(gem: Gem, isSelected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .scale(scale)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.4f), CircleShape)
                    .border(3.dp, Color.White, CircleShape)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize(0.85f)) {
            val baseColor = gemColors[gem.kind.coerceIn(0, gemColors.size - 1)]

            val brush = when (gem.effect) {
                Effects.KIND -> Brush.radialGradient(
                    colors = gemColors.shuffled() + gemColors.first(),
                    center = center,
                    radius = size.width
                )
                else -> Brush.radialGradient(
                    colors = listOf(Color.White, baseColor, baseColor.copy(alpha = 0.7f)),
                    center = Offset(size.width * 0.3f, size.height * 0.3f),
                    radius = size.width
                )
            }

            val path = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width, size.height / 2)
                lineTo(size.width / 2, size.height)
                lineTo(0f, size.height / 2)
                close()
            }

            drawPath(path = path, brush = brush, style = Fill)

            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = size.width * 0.15f,
                center = Offset(size.width * 0.3f, size.height * 0.3f)
            )

            // Спецэффекты
            when (gem.effect) {
                Effects.H_RAY -> drawLine(
                    Color.White, Offset(0f, center.y), Offset(size.width, center.y),
                    strokeWidth = size.width * 0.2f
                )
                Effects.V_RAY -> drawLine(
                    Color.White, Offset(center.x, 0f), Offset(center.x, size.height),
                    strokeWidth = size.width * 0.2f
                )
                Effects.AREA -> drawCircle(
                    Color.White.copy(alpha = 0.3f), radius = size.width * 0.7f, center = center
                )
                else -> {}
            }
        }
    }
}

// ==================== ДИАЛОГ ====================
@Composable
fun GameDialog(title: String, message: String, buttonText: String, color: Color, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        border = BorderStroke(3.dp, color),
        modifier = Modifier.padding(32.dp).shadow(24.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = color, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text(title, color = color, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Text(message, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.height(56.dp).width(200.dp)
            ) {
                Text(buttonText, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
