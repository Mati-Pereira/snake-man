package dev.matheus.snakeman.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Sons sintetizados (sem assets) e vibração para dar feedback ao jogador.
 *
 * As sequências usam o [Handler] da main thread para tocar notas em intervalos
 * curtos, o que permite arpejos sem precisar de arquivos de áudio.
 */
class Feedback(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
    }.getOrNull()

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /** Maçã comum: bip curto e vibração leve. */
    fun eat() {
        tone(ToneGenerator.TONE_PROP_BEEP2, EAT_MS)
        vibrate(18)
    }

    /** Maçã dourada: arpejo ascendente de três notas. */
    fun eatGolden() {
        tone(ToneGenerator.TONE_PROP_BEEP2, 60)
        later(70) { tone(ToneGenerator.TONE_PROP_ACK, 60) }
        later(140) { tone(ToneGenerator.TONE_PROP_BEEP, 80) }
        vibratePattern(longArrayOf(0, 25, 30, 30))
    }

    /** Game over: a música conta a história; aqui fica só o impacto físico. */
    fun death() {
        vibrate(180)
    }

    /** Tabuleiro completo: comemoração curta em três pulsos. */
    fun win() {
        vibratePattern(longArrayOf(0, 35, 55, 35, 55, 80))
    }

    fun click() {
        tone(ToneGenerator.TONE_PROP_BEEP, CLICK_MS)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        runCatching { toneGenerator?.release() }
    }

    private fun tone(tone: Int, durationMs: Int) {
        runCatching { toneGenerator?.startTone(tone, durationMs) }
    }

    private fun later(delayMs: Long, block: () -> Unit) {
        handler.postDelayed(block, delayMs)
    }

    private fun vibrate(millis: Long) {
        vibratePattern(longArrayOf(0, millis))
    }

    private fun vibratePattern(pattern: LongArray) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                device.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(pattern, -1)
            }
        }
    }

    private companion object {
        const val VOLUME = 70
        const val EAT_MS = 75
        const val CLICK_MS = 45
    }
}
