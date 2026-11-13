package dev.matheus.snakeman.game

/** Direções possíveis de movimento da cobra. */
enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    fun isOppositeOf(other: Direction): Boolean = dx + other.dx == 0 && dy + other.dy == 0
}

/** Como a cobra se comporta ao encostar na borda do tabuleiro. */
enum class WallMode {
    /** Bate na parede e perde. */
    SOLID,

    /** Atravessa a borda e aparece do outro lado. */
    WRAP
}

enum class GameStatus { MENU, PLAYING, PAUSED, GAME_OVER }

/** Uma célula do grid. */
data class Cell(val x: Int, val y: Int)

/** Tipos de maçã: a normal vale menos, a dourada vale mais e expira. */
enum class AppleKind { NORMAL, GOLDEN }

/**
 * Maçã posicionada no tabuleiro.
 * [ticksLeft] só é usado na maçã dourada e conta quantos passos ainda restam
 * antes de ela desaparecer.
 */
data class Apple(
    val cell: Cell,
    val kind: AppleKind = AppleKind.NORMAL,
    val ticksLeft: Int? = null
) {
    val isGolden: Boolean get() = kind == AppleKind.GOLDEN
}

enum class TickOutcome { IDLE, MOVED, ATE, DIED, WON }

/** Foto imutável do estado do jogo, usada pela UI do Compose. */
data class GameSnapshot(
    val snake: List<Cell>,
    val apple: Apple?,
    val status: GameStatus,
    val score: Int,
    val direction: Direction,
    val tickMillis: Long,
    val wallMode: WallMode
)
