@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacmanScreen() {
    var level by remember { mutableIntStateOf(1) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableIntStateOf(0) }
    
    // Полная карта из оригинального pacman.js
    val maze = remember(level) {
        arrayOf(
            intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1),
            intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2,2,1),
            intArrayOf(1,2,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,2,1),
            intArrayOf(1,3,1,1,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,1,1,2,1,1,1,1,3,1),
            intArrayOf(1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1),
            intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1)
            // Добавь полную карту из pacman.js MAP константы
        )
    }
    
    // Игровые состояния
    var pacman by remember { mutableStateOf(Entity(14f, 5f, Dir.LEFT)) }
    val ghosts = remember { 
        mutableStateListOf(
            Entity(14f, 2f, Dir.UP), 
            Entity(13f, 2f, Dir.DOWN),
            Entity(15f, 2f, Dir.LEFT),
            Entity(14f, 1f, Dir.RIGHT)
        ) 
    }
    
    var dotsLeft by remember(level) { mutableIntStateOf(0) }
    var powerMode by remember { mutableStateOf(false) }
    var powerTimer by remember { mutableIntStateOf(0) }
    
    val cellSize = 18f
    val mazeWidth = maze[0].size
    val mazeHeight = maze.size
    
    // Подсчет точек
    LaunchedEffect(level) {
        dotsLeft = 0
        maze.forEach { row ->
            row.forEach { if (it == 2 || it == 3) dotsLeft++ }
        }
        pacman = Entity(14f, 5f, Dir.LEFT)
        ghosts.clear()
        ghosts.addAll(listOf(
            Entity(14f, 2f, Dir.UP), Entity(13f, 2f, Dir.DOWN),
            Entity(15f, 2f, Dir.LEFT), Entity(14f, 1f, Dir.RIGHT)
        ))
    }
    
    // 🎮 ГЛАВНЫЙ ИГРОВОЙ ЦИКЛ (60 FPS)
    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (true) {
            withFrameNanos { time ->
                if (time - lastTime > 16_666_666L) { // 60 FPS
                    lastTime = time
                    
                    // Движение Пакмана
                    val nextX = when (pacman.dir) {
                        Dir.LEFT -> pacman.x - 0.15f
                        Dir.RIGHT -> pacman.x + 0.15f
                        else -> pacman.x
                    }
                    val nextY = when (pacman.dir) {
                        Dir.UP -> pacman.y - 0.15f
                        Dir.DOWN -> pacman.y + 0.15f
                        else -> pacman.y
                    }
                    
                    // Телепорт
                    var finalX = nextX
                    if (finalX < 0) finalX = mazeWidth.toFloat()
                    if (finalX >= mazeWidth) finalX = 0f
                    
                    val cellX = finalX.toInt().coerceIn(0, mazeWidth - 1)
                    val cellY = nextY.toInt().coerceIn(0, mazeHeight - 1)
                    
                    if (maze[cellY][cellX] != 1) {
                        pacman = pacman.copy(x = finalX, y = nextY)
                        
                        // Поедание точек
                        when (maze[cellY][cellX]) {
                            2 -> {
                                maze[cellY][cellX] = 0
                                score += 10
                                dotsLeft--
                            }
                            3 -> {
                                maze[cellY][cellX] = 0
                                score += 50
                                powerMode = true
                                powerTimer = 420 // 7 сек
                            }
                        }
                    }
                    
                    // Power mode timer
                    if (powerMode) {
                        powerTimer--
                        if (powerTimer <= 0) powerMode = false
                    }
                    
                    // Победа уровня
                    if (dotsLeft <= 0) {
                        level++
                        score += 1000
                    }
                    
                    // Движение призраков (упрощенный ИИ)
                    ghosts.forEachIndexed { i, ghost ->
                        val dirs = listOf(Dir.UP, Dir.DOWN, Dir.LEFT, Dir.RIGHT)
                        val bestDir = dirs.minByOrNull { dir ->
                            val dx = when(dir) {
                                Dir.LEFT -> ghost.x - 0.5f; Dir.RIGHT -> ghost.x + 0.5f
                                else -> ghost.x
                            }
                            val dy = when(dir) {
                                Dir.UP -> ghost.y - 0.5f; Dir.DOWN -> ghost.y + 0.5f
                                else -> ghost.y
                            }
                            val dist = hypot(dx - pacman.x, dy - pacman.y)
                            if (maze[dy.toInt().coerceIn(0, mazeHeight-1)][dx.toInt().coerceIn(0, mazeWidth-1)] == 1) 999f else dist
                        } ?: ghost.dir
                        
                        val gx = when(bestDir) {
                            Dir.LEFT -> ghost.x - 0.12f; Dir.RIGHT -> ghost.x + 0.12f; else -> ghost.x
                        }
                        val gy = when(bestDir) {
                            Dir.UP -> ghost.y - 0.12f; Dir.DOWN -> ghost.y + 0.12f; else -> ghost.y
                        }
                        
                        val gCellX = gx.toInt().coerceIn(0, mazeWidth - 1)
                        val gCellY = gy.toInt().coerceIn(0, mazeHeight - 1)
                        
                        if (maze[gCellY][gCellX] != 1) {
                            ghosts[i] = ghost.copy(x = gx, y = gy, dir = bestDir)
                        }
                        
                        // Столкновение
                        if (hypot(ghost.x - pacman.x, ghost.y - pacman.y) < 0.4f) {
                            if (powerMode) {
                                score += 200
                                ghosts[i] = Entity(14f, 2f, Dir.UP)
                            } else {
                                lives--
                                if (lives > 0) {
                                    pacman = Entity(14f, 5f, Dir.LEFT)
                                } else {
                                    level = 1; lives = 3; score = 0
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎮 PAC-MAN | L:$level | S:$score | ❤️×$lives") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0f0f23))
                .padding(padding)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        when {
                            abs(dragAmount.x) > abs(dragAmount.y) && abs(dragAmount.x) > 30 -> {
                                pacman = pacman.copy(dir = if (dragAmount.x > 0) Dir.RIGHT else Dir.LEFT)
                            }
                            abs(dragAmount.y) > 30 -> {
                                pacman = pacman.copy(dir = if (dragAmount.y > 0) Dir.DOWN else Dir.UP)
                            }
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val offsetX = (size.width - mazeWidth * cellSize) / 2
                val offsetY = (size.height - mazeHeight * cellSize) / 2 + 60
                
                // Лабиринт
                maze.forEachIndexed { y, row ->
                    row.forEachIndexed { x, cell ->
                        val posX = offsetX + x * cellSize
                        val posY = offsetY + y * cellSize
                        
                        when (cell) {
                            1 -> { // Стены
                                drawRect(
                                    Color(0xFF00FFFF), 
                                    topLeft = Offset(posX, posY),
                                    size = Size(cellSize, cellSize),
                                    style = Stroke(width = 3f)
                                )
                            }
                            2 -> { // Точки
                                drawCircle(
                                    Color.White,
                                    2f,
                                    center = Offset(posX + cellSize/2, posY + cellSize/2)
                                )
                            }
                            3 -> { // Power pellets
                                drawCircle(
                                    Color.Cyan,
                                    5f,
                                    center = Offset(posX + cellSize/2, posY + cellSize/2)
                                )
                            }
                        }
                    }
                }
                
                // Пакман (анимация рта)
                val pacCenter = Offset(
                    offsetX + pacman.x * cellSize + cellSize/2,
                    offsetY + pacman.y * cellSize + cellSize/2
                )
                val mouthAngle = (sin(currentTime / 100f) * 0.3f + 0.7f).coerceIn(0.3f, 1f)
                
                drawArc(
                    Color.Yellow,
                    0f,
                    pacCenter,
                    cellSize * 0.45f,
                    (1f - mouthAngle) * PI,
                    mouthAngle * PI * 2,
                    useCenter = true
                )
                
                // Призраки
                ghosts.forEach { ghost ->
                    val ghostCenter = Offset(
                        offsetX + ghost.x * cellSize + cellSize/2,
                        offsetY + ghost.y * cellSize + cellSize/2
                    )
                    
                    // Тело призрака
                    drawRect(
                        if (powerMode) Color(0xFF4488FF) else Color(0xFFFF0040),
                        topLeft = ghostCenter + Offset(-cellSize/2 * 0.9f, -cellSize/2 * 0.9f),
                        size = Size(cellSize * 1.8f, cellSize * 1.8f),
                        style = Stroke(width = cellSize * 0.9f)
                    )
                    
                    // Волнистое дно
                    val path = Path().apply {
                        moveTo(ghostCenter.x - cellSize/2 * 0.9f, ghostCenter.y + cellSize/2 * 0.4f)
                        for (i in 0..4) {
                            val wave = if (i % 2 == 0) 3f else -3f
                            quadraticBezierTo(
                                ghostCenter.x - cellSize/2 * 0.9f + (i + 1) * cellSize * 0.18f,
                                ghostCenter.y + cellSize/2 * 0.4f + wave,
                                ghostCenter.x - cellSize/2 * 0.9f + (i + 2) * cellSize * 0.18f,
                                ghostCenter.y + cellSize/2 * 0.4f
                            )
                        }
                        close()
                    }
                    drawPath(path, if (powerMode) Color(0xFF4488FF) else Color(0xFFFF0040))
                }
            }
        }
    }
}

private fun hypot(x: Float, y: Float) = sqrt(x*x + y*y)
