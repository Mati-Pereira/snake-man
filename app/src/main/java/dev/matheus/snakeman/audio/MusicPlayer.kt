package dev.matheus.snakeman.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

/**
 * Toca a trilha do [ChiptuneSynth] em streaming.
 *
 * O loop é contínuo e sem silêncio entre voltas. A classe também cuida do foco
 * de áudio: se outro app começar a tocar, a música pausa ou abaixa; quando o
 * foco volta, ela retoma sozinha caso o jogo ainda esteja rolando.
 *
 * Todos os métodos de controle são chamados na main thread (efeitos do Compose).
 * A renderização e a escrita do PCM ficam em threads próprias.
 */
class MusicPlayer(context: Context) {

    private enum class Mode { NONE, LOOP, JINGLE }

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    @Volatile
    private var track: AudioTrack? = null
    private var mode = Mode.NONE
    private var generation = 0
    private var paused = false
    private var pausedByFocus = false
    private var focusGranted = false
    private var released = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusGranted = true
                runCatching { track?.setVolume(MUSIC_VOLUME) }
                if (pausedByFocus && !paused) {
                    runCatching { track?.play() }
                }
                pausedByFocus = false
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (track?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pausedByFocus = true
                    runCatching { track?.pause() }
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                runCatching { track?.setVolume(MUSIC_VOLUME * 0.22f) }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocus = false
                focusGranted = false
                stopInternal()
            }
        }
    }

    private val focusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusListener, handler)
                .setWillPauseWhenDucked(false)
                .build()
        } else {
            null
        }

    init {
        // Pré-renderiza o loop em segundo plano para o primeiro jogo começar rápido.
        thread(name = "snakeman-music-warmup", isDaemon = true) {
            runCatching { ChiptuneSynth.gameLoop() }
        }
    }

    /**
     * Começa (ou retoma) o loop da partida. Chamar várias vezes é seguro: se já
     * estiver tocando, apenas garante que está no estado "tocando".
     */
    fun playGameLoop() {
        if (released) return
        paused = false

        if (mode == Mode.LOOP) {
            if (track?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                runCatching { track?.play() }
            }
            return
        }

        stopInternal()
        mode = Mode.LOOP
        val token = ++generation

        thread(name = "snakeman-music-render", isDaemon = true) {
            val samples = runCatching { ChiptuneSynth.gameLoop() }.getOrNull()
                ?: return@thread
            handler.post {
                if (released || token != generation || mode != Mode.LOOP) return@post
                if (!requestFocus()) {
                    mode = Mode.NONE
                    return@post
                }
                startPlayback(samples, loop = true, respectPause = true)
            }
        }
    }

    /** Jingle de game over; com recorde, inclui a fanfarra comemorativa. */
    fun playGameOver(newRecord: Boolean) {
        playJingle { ChiptuneSynth.gameOver(newRecord) }
    }

    /** Jingle de vitória para quando o tabuleiro lota. */
    fun playVictory() {
        playJingle { ChiptuneSynth.victory() }
    }

    /** Pausa a música (partida pausada ou app em segundo plano). */
    fun pause() {
        if (released) return
        paused = true
        runCatching { track?.pause() }
    }

    /** Para tudo e libera a saída de áudio. */
    fun stop() {
        generation++
        stopInternal()
    }

    /** Libera definitivamente o player; o objeto não deve ser reutilizado. */
    fun release() {
        if (released) return
        released = true
        generation++
        stopInternal()
        handler.removeCallbacksAndMessages(null)
    }

    private fun playJingle(render: () -> ShortArray) {
        if (released) return
        stopInternal()
        mode = Mode.JINGLE
        paused = false
        val token = ++generation

        thread(name = "snakeman-music-render", isDaemon = true) {
            val samples = runCatching(render).getOrNull() ?: return@thread
            handler.post {
                if (released || token != generation || mode != Mode.JINGLE) return@post
                if (!requestFocus()) {
                    mode = Mode.NONE
                    return@post
                }
                startPlayback(samples, loop = false, respectPause = false)
            }
        }
    }

    private fun startPlayback(samples: ShortArray, loop: Boolean, respectPause: Boolean) {
        val newTrack = createTrack()
        if (newTrack == null) {
            mode = Mode.NONE
            abandonFocus()
            return
        }

        track = newTrack
        runCatching { newTrack.setVolume(MUSIC_VOLUME) }
        if (!respectPause || !paused) {
            runCatching { newTrack.play() }
        }

        thread(name = "snakeman-music-writer", isDaemon = true) {
            writerLoop(newTrack, samples, loop)
        }
    }

    private fun writerLoop(newTrack: AudioTrack, samples: ShortArray, loop: Boolean) {
        var offset = 0
        try {
            while (track === newTrack) {
                val remaining = samples.size - offset
                if (remaining <= 0) {
                    if (!loop) break
                    offset = 0
                    continue
                }

                val written = newTrack.write(
                    samples,
                    offset,
                    minOf(WRITE_CHUNK, remaining),
                    AudioTrack.WRITE_BLOCKING
                )
                if (written <= 0) break
                offset += written

                if (offset >= samples.size) {
                    if (!loop) break
                    offset = 0
                }
            }

            if (!loop && track === newTrack) {
                // Deixa o restante do buffer tocar antes de liberar a saída.
                Thread.sleep(JINGLE_TAIL_MS)
                handler.post { if (track === newTrack) stopInternal() }
            }
        } catch (_: InterruptedException) {
            // Thread encerrada durante stop/release.
        } catch (_: Throwable) {
            // A saída pode ser liberada enquanto a thread ainda escreve.
        }
    }

    private fun createTrack(): AudioTrack? = runCatching {
        val minBuffer = AudioTrack.getMinBufferSize(
            ChiptuneSynth.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return@runCatching null

        AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ChiptuneSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer * 2, MIN_BUFFER_BYTES))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }.getOrNull()

    private fun stopInternal() {
        val old = track
        track = null
        mode = Mode.NONE
        paused = false
        pausedByFocus = false

        if (old != null) {
            runCatching { old.pause() }
            runCatching { old.flush() }
            runCatching { old.release() }
        }
        abandonFocus()
    }

    private fun requestFocus(): Boolean {
        if (released) return false
        if (focusGranted) return true

        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest ?: return false
            audioManager.requestAudioFocus(request) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        focusGranted = granted
        return granted
    }

    private fun abandonFocus() {
        if (!focusGranted) return
        focusGranted = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private companion object {
        const val MUSIC_VOLUME = 0.55f
        const val WRITE_CHUNK = 4096
        const val MIN_BUFFER_BYTES = 8192
        const val JINGLE_TAIL_MS = 350L
    }
}
