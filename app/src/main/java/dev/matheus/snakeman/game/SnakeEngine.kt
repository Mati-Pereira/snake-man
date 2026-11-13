package dev.matheus.snakeman.game

import kotlin.random.Random

/**
 * Motor do jogo da cobra, sem nenhuma dependência de Android.
 * Toda a lógica de movimento, colisão, crescimento, maçã dourada e velocidade
 * vive aqui, o que permite testar tudo em testes unitários puros.
 */
class SnakeEngine(
    val cols: Int = 20,
    val rows: Int = 20,
    private val random: Random = Random.Default
) {

    private val pendingDirections = ArrayDeque<Direction>()

    var snake: List<Cell> = emptyList()
        private set

    var apple: Apple? = null
        private set

    var status: GameStatus = GameStatus.MENU
        private set

    var score: Int = 0
        private set

    var direction: Direction = Direction.RIGHT
        private set

    var wallMode: WallMode = WallMode.SOLID
        private set

    private var applesEaten: Int = 0

    /** Intervalo entre ticks: fica menor (mais rápido) a cada maçã comida. */
    val tickMillis: Long
        get() = (BASE_TICK_MILLIS - applesEaten * SPEED_STEP_MILLIS).coerceAtLeast(MIN_TICK_MILLIS)

    /** Quantidade de maçãs comidas na partida. */
    val apples: Int get() = applesEaten

    fun snapshot(): GameSnapshot = GameSnapshot(
        snake = snake,
        apple = apple,
        status = status,
        score = score,
        direction = direction,
        tickMillis = tickMillis,
        wallMode = wallMode
    )

    /** Prepara uma nova partida, já em estado PLAYING. */
    fun start(mode: WallMode = wallMode) {
        wallMode = mode
        val startX = cols / 2
        val startY = rows / 2
        snake = listOf(
            Cell(startX, startY),
            Cell(startX - 1, startY),
            Cell(startX - 2, startY)
        )
        direction = Direction.RIGHT
        pendingDirections.clear()
        score = 0
        applesEaten = 0
        status = GameStatus.PLAYING
        spawnApple(golden = false)
    }

    fun pause() {
        if (status == GameStatus.PLAYING) status = GameStatus.PAUSED
    }

    fun resume() {
        if (status == GameStatus.PAUSED) status = GameStatus.PLAYING
    }

    fun backToMenu() {
        status = GameStatus.MENU
        score = 0
        applesEaten = 0
        snake = emptyList()
        apple = null
        pendingDirections.clear()
    }

    /**
     * Enfileira uma direção. Viradas de 180° são descartadas e a fila
     * tem no máximo 2 entradas, para que swipes rápidos não causem
     * auto-colisão acidental.
     */
    fun queueDirection(newDirection: Direction) {
        if (status != GameStatus.PLAYING && status != GameStatus.PAUSED) return
        val reference = pendingDirections.lastOrNull() ?: direction
        if (newDirection.isOppositeOf(reference) || newDirection == reference) return
        if (pendingDirections.size >= MAX_QUEUED_DIRECTIONS) return
        pendingDirections.addLast(newDirection)
    }

    /** Avança um passo da simulação. */
    fun tick(): TickOutcome {
        if (status != GameStatus.PLAYING) return TickOutcome.IDLE

        pendingDirections.removeFirstOrNull()?.let { direction = it }

        val head = snake.first()
        var nextX = head.x + direction.dx
        var nextY = head.y + direction.dy

        when (wallMode) {
            WallMode.WRAP -> {
                nextX = (nextX + cols) % cols
                nextY = (nextY + rows) % rows
            }

            WallMode.SOLID -> {
                if (nextX !in 0 until cols || nextY !in 0 until rows) {
                    status = GameStatus.GAME_OVER
                    return TickOutcome.DIED
                }
            }
        }

        val newHead = Cell(nextX, nextY)
        val target = apple
        val eating = target != null && target.cell == newHead
        val eatingGolden = eating && target?.isGolden == true

        // A cauda sai da célula neste mesmo tick, então ela não conta
        // como colisão quando a cobra não está crescendo.
        val bodyToCheck = if (eating) snake.size else snake.size - 1
        for (index in 0 until bodyToCheck) {
            if (snake[index] == newHead) {
                status = GameStatus.GAME_OVER
                return TickOutcome.DIED
            }
        }

        snake = if (eating) {
            listOf(newHead) + snake
        } else {
            listOf(newHead) + snake.dropLast(1)
        }

        if (!eating) {
            ageGoldenApple()
            return TickOutcome.MOVED
        }

        score += if (eatingGolden) GOLDEN_SCORE else APPLE_SCORE
        applesEaten++

        // Uma em cada [GOLDEN_EVERY] maçãs é dourada.
        val goldenNext = applesEaten % GOLDEN_EVERY == 0
        val spawned = spawnApple(goldenNext)
        return if (spawned) TickOutcome.ATE else {
            status = GameStatus.GAME_OVER
            TickOutcome.WON
        }
    }

    /** Envelhece a maçã dourada; quando o tempo acaba ela vira uma maçã comum. */
    private fun ageGoldenApple() {
        val current = apple ?: return
        if (!current.isGolden) return
        val remaining = (current.ticksLeft ?: 0) - 1
        if (remaining <= 0) {
            spawnApple(golden = false)
        } else {
            apple = current.copy(ticksLeft = remaining)
        }
    }

    /** Posiciona a maçã em uma célula livre. Retorna false se o tabuleiro lotou. */
    private fun spawnApple(golden: Boolean): Boolean {
        val occupied = snake.toHashSet()
        if (occupied.size >= cols * rows) {
            apple = null
            return false
        }
        while (true) {
            val candidate = Cell(random.nextInt(cols), random.nextInt(rows))
            if (candidate !in occupied) {
                apple = if (golden) {
                    Apple(candidate, AppleKind.GOLDEN, GOLDEN_LIFETIME_TICKS)
                } else {
                    Apple(candidate, AppleKind.NORMAL)
                }
                return true
            }
        }
    }

    /** Apenas para testes: força um estado específico. */
    internal fun loadForTest(
        snake: List<Cell>,
        apple: Cell?,
        direction: Direction = Direction.RIGHT,
        mode: WallMode = WallMode.SOLID,
        applesEaten: Int = 0,
        appleKind: AppleKind = AppleKind.NORMAL,
        goldenTicksLeft: Int? = null
    ) {
        this.snake = snake
        this.apple = apple?.let { cell ->
            if (appleKind == AppleKind.GOLDEN) {
                Apple(cell, AppleKind.GOLDEN, goldenTicksLeft ?: GOLDEN_LIFETIME_TICKS)
            } else {
                Apple(cell, AppleKind.NORMAL)
            }
        }
        this.direction = direction
        this.wallMode = mode
        this.applesEaten = applesEaten
        this.score = applesEaten * APPLE_SCORE
        pendingDirections.clear()
        status = GameStatus.PLAYING
    }

    companion object {
        const val BASE_TICK_MILLIS = 190L
        const val SPEED_STEP_MILLIS = 6L
        const val MIN_TICK_MILLIS = 70L
        const val APPLE_SCORE = 10
        const val GOLDEN_SCORE = 30
        const val GOLDEN_EVERY = 5
        const val GOLDEN_LIFETIME_TICKS = 45
        const val MAX_QUEUED_DIRECTIONS = 2
    }
}
