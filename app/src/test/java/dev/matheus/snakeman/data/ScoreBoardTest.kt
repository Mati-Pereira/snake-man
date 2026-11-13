package dev.matheus.snakeman.data

import dev.matheus.snakeman.game.WallMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreBoardTest {

    private fun entry(score: Int, timestamp: Long, mode: WallMode = WallMode.SOLID) =
        ScoreEntry(score, mode, timestamp)

    @Test
    fun keepsOnlyTopFive() {
        var entries = emptyList<ScoreEntry>()
        listOf(10, 50, 30, 70, 20, 90, 40).forEachIndexed { index, score ->
            entries = ScoreBoard.withEntry(entries, entry(score, index.toLong()))
        }

        assertEquals(ScoreBoard.MAX_ENTRIES, entries.size)
        assertEquals(listOf(90, 70, 50, 40, 30), entries.map { it.score })
    }

    @Test
    fun tiesAreBrokenByMostRecent() {
        val entries = ScoreBoard.withEntry(
            listOf(entry(40, timestamp = 100L)),
            entry(40, timestamp = 200L)
        )

        assertEquals(200L, entries.first().timestamp)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val original = listOf(
            entry(120, 1_700_000_000_000L, WallMode.WRAP),
            entry(40, 1_600_000_000_000L, WallMode.SOLID)
        )

        val decoded = ScoreBoard.decode(ScoreBoard.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun decodeIgnoresGarbage() {
        val raw = "40|SOLID|1000\nlixo\n\n70|NAO_EXISTE|2000\n30|WRAP|abc\n10|WRAP|3000"

        val decoded = ScoreBoard.decode(raw)

        assertEquals(listOf(40, 10), decoded.map { it.score })
        assertTrue(decoded.all { it.wallMode == WallMode.SOLID || it.wallMode == WallMode.WRAP })
    }

    @Test
    fun decodeOfNullIsEmpty() {
        assertTrue(ScoreBoard.decode(null).isEmpty())
        assertTrue(ScoreBoard.decode("").isEmpty())
    }

    @Test
    fun bestReturnsHighestScore() {
        assertEquals(0, ScoreBoard.best(emptyList()))
        assertEquals(77, ScoreBoard.best(listOf(entry(12, 1), entry(77, 2), entry(30, 3))))
    }
}
