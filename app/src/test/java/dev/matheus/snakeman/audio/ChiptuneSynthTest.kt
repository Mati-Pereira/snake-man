package dev.matheus.snakeman.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * A síntese é Kotlin puro, então estes testes rodam na JVM como os do motor.
 * Eles não julgam se a música é bonita (isso é com o jogador), mas garantem
 * duração, volume, emenda do loop e variantes dos jingles.
 */
class ChiptuneSynthTest {

    @Test
    fun `loop do jogo tem a duracao esperada`() {
        val seconds = ChiptuneSynth.gameLoop().size.toDouble() / ChiptuneSynth.SAMPLE_RATE
        assertEquals(32.0, seconds, 0.01)
    }

    @Test
    fun `loop tem volume audivel e sem clipping`() {
        val samples = ChiptuneSynth.gameLoop()
        var peak = 0
        var sum = 0.0
        var signedSum = 0.0

        for (sample in samples) {
            val value = abs(sample.toInt())
            if (value > peak) peak = value
            sum += value
            signedSum += sample
        }

        val average = sum / samples.size
        val dcOffset = signedSum / samples.size
        assertTrue("pico baixo demais: $peak", peak > 20_000)
        assertTrue("pico colado no limite: $peak", peak < Short.MAX_VALUE.toInt())
        assertTrue("media baixa demais: $average", average > 1_500)
        assertTrue("offset DC alto demais: $dcOffset", abs(dcOffset) < 100.0)
    }

    @Test
    fun `loop comeca e termina em silencio para nao estalar`() {
        val samples = ChiptuneSynth.gameLoop()
        assertEquals(0, samples.first().toInt())
        assertEquals(0, samples.last().toInt())
    }

    @Test
    fun `game over com recorde e maior que o normal`() {
        val normal = ChiptuneSynth.gameOver(newRecord = false)
        val record = ChiptuneSynth.gameOver(newRecord = true)

        assertTrue("jingle normal vazio", normal.isNotEmpty())
        assertTrue("jingle de recorde deveria incluir a fanfarra", record.size > normal.size)
    }

    @Test
    fun `vitoria tem jingle proprio com mais de um segundo`() {
        val victory = ChiptuneSynth.victory()
        val seconds = victory.size.toDouble() / ChiptuneSynth.SAMPLE_RATE
        assertTrue("jingle de vitoria curto demais: $seconds s", seconds > 1.0)
    }

    @Test
    fun `sintese e deterministica`() {
        assertTrue(ChiptuneSynth.gameLoop().contentEquals(ChiptuneSynth.gameLoop()))
        assertTrue(
            ChiptuneSynth.gameOver(newRecord = true)
                .contentEquals(ChiptuneSynth.gameOver(newRecord = true))
        )
    }
}
