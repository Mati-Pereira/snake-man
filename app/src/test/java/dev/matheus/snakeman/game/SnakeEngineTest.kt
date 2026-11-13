package dev.matheus.snakeman.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class SnakeEngineTest {

    private lateinit var engine: SnakeEngine

    @Before
    fun setUp() {
        engine = SnakeEngine(cols = 20, rows = 20, random = Random(42))
    }

    @Test
    fun start_createsThreeSegments_andAppleOutsideSnake() {
        engine.start(WallMode.SOLID)

        assertEquals(GameStatus.PLAYING, engine.status)
        assertEquals(3, engine.snake.size)
        assertEquals(0, engine.score)
        assertNotEquals(null, engine.apple)
        assertTrue(engine.apple?.cell !in engine.snake)
        assertEquals(SnakeEngine.BASE_TICK_MILLIS, engine.tickMillis)
    }

    @Test
    fun tick_movesHeadOneCell_andKeepsLength() {
        engine.loadForTest(
            snake = listOf(Cell(2, 2), Cell(1, 2), Cell(0, 2)),
            apple = Cell(10, 10),
            direction = Direction.RIGHT
        )

        val outcome = engine.tick()

        assertEquals(TickOutcome.MOVED, outcome)
        assertEquals(Cell(3, 2), engine.snake.first())
        assertEquals(3, engine.snake.size)
    }

    @Test
    fun tick_eatingApple_grows_scores_andAccelerates() {
        engine.loadForTest(
            snake = listOf(Cell(2, 2), Cell(1, 2), Cell(0, 2)),
            apple = Cell(3, 2),
            direction = Direction.RIGHT
        )
        val speedBefore = engine.tickMillis

        val outcome = engine.tick()

        assertEquals(TickOutcome.ATE, outcome)
        assertEquals(4, engine.snake.size)
        assertEquals(SnakeEngine.APPLE_SCORE, engine.score)
        assertEquals(1, engine.apples)
        assertTrue("deve acelerar depois de comer", engine.tickMillis < speedBefore)
        assertTrue(engine.apple?.cell !in engine.snake)
    }

    @Test
    fun solidWall_killsSnake() {
        engine.loadForTest(
            snake = listOf(Cell(19, 5), Cell(18, 5), Cell(17, 5)),
            apple = Cell(10, 10),
            direction = Direction.RIGHT,
            mode = WallMode.SOLID
        )

        val outcome = engine.tick()

        assertEquals(TickOutcome.DIED, outcome)
        assertEquals(GameStatus.GAME_OVER, engine.status)
    }

    @Test
    fun wrapMode_traversesTheBorder() {
        engine.loadForTest(
            snake = listOf(Cell(19, 5), Cell(18, 5), Cell(17, 5)),
            apple = Cell(10, 10),
            direction = Direction.RIGHT,
            mode = WallMode.WRAP
        )

        val outcome = engine.tick()

        assertEquals(TickOutcome.MOVED, outcome)
        assertEquals(Cell(0, 5), engine.snake.first())
        assertEquals(GameStatus.PLAYING, engine.status)
    }

    @Test
    fun wrapMode_traversesOnTheYAxis() {
        engine.loadForTest(
            snake = listOf(Cell(5, 0), Cell(5, 1), Cell(5, 2)),
            apple = Cell(10, 10),
            direction = Direction.UP,
            mode = WallMode.WRAP
        )

        engine.tick()

        assertEquals(Cell(5, 19), engine.snake.first())
    }

    @Test
    fun selfCollision_killsSnake() {
        engine.loadForTest(
            snake = listOf(Cell(5, 5), Cell(5, 6), Cell(6, 6), Cell(6, 5), Cell(7, 5), Cell(7, 6)),
            apple = Cell(0, 0),
            direction = Direction.RIGHT
        )

        val outcome = engine.tick()

        assertEquals(TickOutcome.DIED, outcome)
        assertEquals(GameStatus.GAME_OVER, engine.status)
    }

    @Test
    fun movingIntoVacatedTail_isAllowed() {
        engine.loadForTest(
            snake = listOf(Cell(1, 1), Cell(1, 2), Cell(2, 2), Cell(2, 1)),
            apple = Cell(10, 10),
            direction = Direction.RIGHT
        )

        val outcome = engine.tick()

        assertEquals(TickOutcome.MOVED, outcome)
        assertEquals(Cell(2, 1), engine.snake.first())
        assertEquals(4, engine.snake.size)
    }

    @Test
    fun oppositeDirection_isIgnored() {
        engine.loadForTest(
            snake = listOf(Cell(4, 4), Cell(3, 4), Cell(2, 4)),
            apple = Cell(10, 10),
            direction = Direction.RIGHT
        )

        engine.queueDirection(Direction.LEFT)
        engine.tick()

        assertEquals(Cell(5, 4), engine.snake.first())
    }

    @Test
    fun queuedDirections_areAppliedInOrder() {
        engine.loadForTest(
            snake = listOf(Cell(4, 4), Cell(3, 4), Cell(2, 4)),
            apple = Cell(10, 10),
            direction = Direction.RIGHT
        )

        engine.queueDirection(Direction.UP)
        engine.queueDirection(Direction.LEFT)

        engine.tick()
        assertEquals(Cell(4, 3), engine.snake.first())

        engine.tick()
        assertEquals(Cell(3, 3), engine.snake.first())
    }

    @Test
    fun speedStopsAtMinimum() {
        engine.loadForTest(
            snake = listOf(Cell(5, 5), Cell(4, 5), Cell(3, 5)),
            apple = Cell(10, 10),
            applesEaten = 200
        )

        assertEquals(SnakeEngine.MIN_TICK_MILLIS, engine.tickMillis)
    }

    @Test
    fun pauseAndResume_changeStatusOnly() {
        engine.start(WallMode.SOLID)

        engine.pause()
        assertEquals(GameStatus.PAUSED, engine.status)
        assertEquals(TickOutcome.IDLE, engine.tick())

        engine.resume()
        assertEquals(GameStatus.PLAYING, engine.status)
    }

    @Test
    fun start_afterGameOver_resetsScoreAndSnake() {
        engine.loadForTest(
            snake = listOf(Cell(19, 5), Cell(18, 5), Cell(17, 5)),
            apple = Cell(10, 10),
            direction = Direction.RIGHT,
            mode = WallMode.SOLID
        )
        engine.tick()

        engine.start(WallMode.WRAP)

        assertEquals(GameStatus.PLAYING, engine.status)
        assertEquals(0, engine.score)
        assertEquals(3, engine.snake.size)
        assertEquals(WallMode.WRAP, engine.wallMode)
    }

    @Test
    fun goldenApple_spawnsEveryFifthApple() {
        engine.loadForTest(
            snake = listOf(Cell(2, 2), Cell(1, 2), Cell(0, 2)),
            apple = Cell(3, 2),
            applesEaten = 4
        )

        engine.tick()

        assertEquals(5, engine.apples)
        assertTrue("a quinta maçã é dourada", engine.apple?.isGolden == true)
        assertEquals(SnakeEngine.GOLDEN_LIFETIME_TICKS, engine.apple?.ticksLeft)
    }

    @Test
    fun goldenApple_isWorthThirtyPoints() {
        engine.loadForTest(
            snake = listOf(Cell(2, 2), Cell(1, 2), Cell(0, 2)),
            apple = Cell(3, 2),
            appleKind = AppleKind.GOLDEN
        )

        engine.tick()

        assertEquals(SnakeEngine.GOLDEN_SCORE, engine.score)
        assertEquals(4, engine.snake.size)
    }

    @Test
    fun goldenApple_expiresIntoNormalApple() {
        engine.loadForTest(
            snake = listOf(Cell(2, 2), Cell(1, 2), Cell(0, 2)),
            apple = Cell(10, 10),
            appleKind = AppleKind.GOLDEN,
            goldenTicksLeft = 2
        )

        engine.tick()
        assertEquals(1, engine.apple?.ticksLeft)
        assertTrue(engine.apple?.isGolden == true)

        engine.tick()
        assertTrue("dourada expirada vira maçã comum", engine.apple?.isGolden == false)
        assertEquals(null, engine.apple?.ticksLeft)
    }

    @Test
    fun normalApple_doesNotExpire() {
        engine.loadForTest(
            snake = listOf(Cell(2, 2), Cell(1, 2), Cell(0, 2)),
            apple = Cell(10, 10)
        )
        val original = engine.apple

        engine.tick()
        engine.tick()

        assertEquals(original, engine.apple)
    }
}
