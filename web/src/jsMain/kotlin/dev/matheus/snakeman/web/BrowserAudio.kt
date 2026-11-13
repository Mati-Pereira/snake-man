package dev.matheus.snakeman.web

import dev.matheus.snakeman.audio.ChiptuneSynth
import kotlinx.browser.window

/**
 * Camada de áudio do navegador. Usa o mesmo PCM do [ChiptuneSynth] que o
 * `MusicPlayer` toca no Android, só que entregue a um AudioBuffer do Web Audio.
 * Os efeitos, que no app vêm do `ToneGenerator`, são osciladores curtos.
 */
class BrowserAudio {

    private val context: dynamic = js(
        "(function(){var C=window.AudioContext||window.webkitAudioContext;" +
            "return C?new C():null;})()"
    )

    private val loopBuffer: dynamic by lazy { toBuffer(ChiptuneSynth.gameLoop()) }
    private val gameOverBuffer: dynamic by lazy { toBuffer(ChiptuneSynth.gameOver(false)) }
    private val recordBuffer: dynamic by lazy { toBuffer(ChiptuneSynth.gameOver(true)) }
    private val victoryBuffer: dynamic by lazy { toBuffer(ChiptuneSynth.victory()) }

    private var loopNode: dynamic = null
    private var jingleNode: dynamic = null
    private var loopStartedAt = 0.0
    private var loopOffset = 0.0

    /** Pré-renderiza o loop em segundo plano para o primeiro jogo não engasgar. */
    fun prewarm() {
        if (context == null) return
        window.setTimeout({ loopBuffer }, 600)
    }

    /** Precisa ser chamado dentro de um gesto do usuário (política de autoplay). */
    fun unlock() {
        if (context == null) return
        runCatching { context.resume() }
    }

    fun playLoop() {
        if (context == null || loopNode != null) return
        stopJingle()
        unlock()
        val c = context
        val buffer = loopBuffer
        val source = c.createBufferSource()
        val gain = c.createGain()
        gain.gain.value = MUSIC_VOLUME
        source.buffer = buffer
        source.loop = true
        source.connect(gain)
        gain.connect(c.destination)

        val duration = buffer.duration as Double
        val offset = if (duration > 0.0) loopOffset % duration else 0.0
        source.start(0.0, offset)
        loopNode = source
        loopStartedAt = c.currentTime as Double
    }

    /** Pausa guardando a posição, como o AudioTrack do app. */
    fun pause() {
        val node = loopNode ?: return
        val duration = loopBuffer.duration as Double
        if (duration > 0.0) {
            loopOffset = (loopOffset + (context.currentTime as Double) - loopStartedAt) % duration
        }
        runCatching { node.stop() }
        loopNode = null
    }

    fun stop() {
        if (loopNode != null) runCatching { loopNode.stop() }
        loopNode = null
        loopOffset = 0.0
        stopJingle()
    }

    private fun stopJingle() {
        if (jingleNode != null) runCatching { jingleNode.stop() }
        jingleNode = null
    }

    fun playGameOver(newRecord: Boolean) {
        playOnce(if (newRecord) recordBuffer else gameOverBuffer)
    }

    fun playVictory() {
        playOnce(victoryBuffer)
    }

    // --- efeitos curtos (aproximação dos tons do ToneGenerator) ---

    fun eat() {
        tone(880.0, 75, 0.14)
    }

    fun eatGolden() {
        tone(880.0, 60, 0.14)
        window.setTimeout({ tone(1175.0, 60, 0.14) }, 70)
        window.setTimeout({ tone(1568.0, 90, 0.14) }, 140)
    }

    fun click() {
        tone(1320.0, 45, 0.07)
    }

    fun death() {
        js("navigator.vibrate ? navigator.vibrate(180) : false")
    }

    fun win() {
        js("navigator.vibrate ? navigator.vibrate([0,35,55,35,55,80]) : false")
    }

    private fun playOnce(buffer: dynamic) {
        if (context == null) return
        stop()
        unlock()
        val c = context
        val source = c.createBufferSource()
        val gain = c.createGain()
        gain.gain.value = MUSIC_VOLUME
        source.buffer = buffer
        source.connect(gain)
        gain.connect(c.destination)
        source.start(0.0)
        jingleNode = source
    }

    private fun tone(freq: Double, millis: Int, volume: Double) {
        if (context == null) return
        unlock()
        val c = context
        val now = c.currentTime as Double
        val end = now + millis / 1000.0
        val oscillator = c.createOscillator()
        val gain = c.createGain()
        oscillator.type = "square"
        oscillator.frequency.setValueAtTime(freq, now)
        gain.gain.setValueAtTime(volume, now)
        gain.gain.exponentialRampToValueAtTime(0.0001, end)
        oscillator.connect(gain)
        gain.connect(c.destination)
        oscillator.start(now)
        oscillator.stop(end)
    }

    private fun toBuffer(samples: ShortArray): dynamic {
        if (context == null) return null
        val buffer = context.createBuffer(1, samples.size, ChiptuneSynth.SAMPLE_RATE)
        val data = buffer.getChannelData(0)
        for (index in samples.indices) {
            data[index] = samples[index] / 32768.0
        }
        return buffer
    }

    private companion object {
        const val MUSIC_VOLUME = 0.55
    }
}
