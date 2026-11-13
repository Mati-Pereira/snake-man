package dev.matheus.snakeman.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Sintetizador chiptune/synthwave escrito em Kotlin puro, sem assets.
 *
 * A trilha é uma composição original em Lá menor com cara de fliperama anos 80:
 * arpejos de 16 avos, baixo pulsante, bateria sintética e lead com eco. Tudo é
 * gerado em PCM na primeira chamada e fica em cache; o [MusicPlayer] só reproduz.
 *
 * Ficar sem imports de `android.*` mantém a síntese testável na JVM.
 */
object ChiptuneSynth {

    const val SAMPLE_RATE = 22_050

    private const val GAME_BPM = 120.0
    private const val GAME_BARS = 16
    private const val BEATS_PER_BAR = 4
    private const val TARGET_PEAK = 0.86

    private val gameLoopCache by lazy { composeGameLoop() }
    private val gameOverCache by lazy { composeGameOver(newRecord = false) }
    private val newRecordCache by lazy { composeGameOver(newRecord = true) }
    private val victoryCache by lazy { composeVictory() }

    /** Loop animado da partida, com cerca de 32 segundos. */
    fun gameLoop(): ShortArray = gameLoopCache

    /** Jingle de game over; a versão com recorde ganha uma fanfarra no final. */
    fun gameOver(newRecord: Boolean): ShortArray =
        if (newRecord) newRecordCache else gameOverCache

    /** Jingle alegre para quando o tabuleiro lota. */
    fun victory(): ShortArray = victoryCache

    // ---------------------------------------------------------------------
    // Composição da partida
    // ---------------------------------------------------------------------

    private fun composeGameLoop(): ShortArray {
        val secPerBeat = 60.0 / GAME_BPM
        val totalBeats = (GAME_BARS * BEATS_PER_BAR).toDouble()
        val size = (totalBeats * secPerBeat * SAMPLE_RATE).toInt()

        val lead = FloatArray(size)
        val arp = FloatArray(size)
        val bass = FloatArray(size)
        val drums = FloatArray(size)

        // Melodia: frase principal e resposta uma oitava acima.
        for (event in melodyA + melodyB) {
            addNote(
                out = lead,
                bpm = GAME_BPM,
                startBeat = event.beat,
                beats = event.beats,
                midiNote = event.midi,
                amplitude = 0.30,
                wave = Wave.LEAD,
                attackMs = 5.0,
                releaseMs = 55.0,
                slideTo = event.slideTo
            )
        }

        // Arpejo de 16 avos: o motor do clima synthwave.
        val arpPattern = intArrayOf(0, 2, 1, 3, 2, 1, 3, 2, 0, 2, 1, 3, 2, 3, 1, 2)
        for (bar in 0 until GAME_BARS) {
            val chord = chordForBar(bar)
            val octave = if (bar >= 8) 24 else 12
            for (step in 0 until 16) {
                val startBeat = bar * BEATS_PER_BAR + step * 0.25
                addNote(
                    out = arp,
                    bpm = GAME_BPM,
                    startBeat = startBeat,
                    beats = 0.22,
                    midiNote = chord[arpPattern[step]] + octave,
                    amplitude = if (bar >= 8) 0.085 else 0.105,
                    wave = Wave.SQUARE,
                    duty = 0.125,
                    attackMs = 2.0,
                    releaseMs = 12.0
                )
            }
        }

        // Baixo em oitavas, tocando a cada colcheia.
        val bassPattern = intArrayOf(0, 12, 0, 7, 0, 12, 0, 7)
        for (bar in 0 until GAME_BARS) {
            val root = chordForBar(bar)[0] - 12
            for (eighth in 0 until 8) {
                val startBeat = bar * BEATS_PER_BAR + eighth * 0.5
                addNote(
                    out = bass,
                    bpm = GAME_BPM,
                    startBeat = startBeat,
                    beats = 0.44,
                    midiNote = root + bassPattern[eighth],
                    amplitude = 0.50,
                    wave = Wave.TRIANGLE,
                    attackMs = 3.0,
                    releaseMs = 28.0
                )
            }
        }

        addGameDrums(drums)

        return mix(
            lead = lead,
            arp = arp,
            bass = bass,
            drums = drums,
            bpm = GAME_BPM,
            pump = true
        )
    }

    private fun addGameDrums(drums: FloatArray) {
        for (bar in 0 until GAME_BARS) {
            val barBeat = (bar * BEATS_PER_BAR).toDouble()

            // Bumbo em todos os tempos: four-on-the-floor.
            for (beat in 0 until BEATS_PER_BAR) {
                addKick(drums, GAME_BPM, barBeat + beat, if (beat == 0) 0.85 else 0.72)
            }

            // Caixa no 2 e no 4.
            addSnare(drums, GAME_BPM, barBeat + 1.0, 0.42)
            addSnare(drums, GAME_BPM, barBeat + 3.0, 0.46)

            // Chimbal em colcheias, com o aberto no contratempo.
            for (eighth in 0 until 8) {
                addHat(
                    out = drums,
                    bpm = GAME_BPM,
                    startBeat = barBeat + eighth * 0.5,
                    amplitude = if (eighth % 2 == 1) 0.085 else 0.050,
                    open = eighth % 2 == 1
                )
            }

            // Virada de caixa no fim de cada ciclo de 8 compassos.
            if (bar == 7 || bar == 15) {
                for (i in 0 until 4) {
                    addSnare(
                        out = drums,
                        bpm = GAME_BPM,
                        startBeat = barBeat + 3.0 + i * 0.25,
                        amplitude = 0.18 + i * 0.06
                    )
                }
            }
        }

        // Prato no início e na metade, marcando as duas seções.
        addCrash(drums, GAME_BPM, 0.0, 0.26)
        addCrash(drums, GAME_BPM, 32.0, 0.22)
    }

    // ---------------------------------------------------------------------
    // Jingles
    // ---------------------------------------------------------------------

    private fun composeGameOver(newRecord: Boolean): ShortArray {
        val base = composeGameOverBase()
        if (!newRecord) return base

        val tag = composeRecordTag()
        val gap = ShortArray((0.18 * SAMPLE_RATE).toInt())
        val merged = ShortArray(base.size + gap.size + tag.size)
        base.copyInto(merged, destinationOffset = 0)
        tag.copyInto(merged, destinationOffset = base.size + gap.size)
        return merged
    }

    private fun composeGameOverBase(): ShortArray {
        val bpm = 96.0
        val totalBeats = 8.0
        val tailSeconds = 0.9
        val size = ((totalBeats * 60.0 / bpm + tailSeconds) * SAMPLE_RATE).toInt()

        val lead = FloatArray(size)
        val arp = FloatArray(size)
        val bass = FloatArray(size)
        val drums = FloatArray(size)

        val melody = listOf(
            n(0.0, "E5", 0.75),
            n(0.75, "D5", 0.75),
            n(1.5, "C5", 0.75),
            n(2.25, "B4", 0.75),
            n(3.0, "A4", 2.2),
            n(5.6, "E4", 1.0),
            n(6.6, "A3", 1.4, slideTo = "A2")
        )
        for (event in melody) {
            addNote(
                out = lead,
                bpm = bpm,
                startBeat = event.beat,
                beats = event.beats,
                midiNote = event.midi,
                amplitude = 0.30,
                wave = Wave.LEAD,
                attackMs = 8.0,
                releaseMs = 90.0,
                slideTo = event.slideTo
            )
        }

        // Arpejo lento e descendente, como um fliperama apagando.
        val arpNotes = listOf(
            "A4", "E4", "C4", "E4", "A3", "E3", "C4", "A3",
            "F4", "C4", "A3", "C4", "E4", "B3", "G3", "E3"
        )
        for ((index, name) in arpNotes.withIndex()) {
            addNote(
                out = arp,
                bpm = bpm,
                startBeat = index * 0.5,
                beats = 0.45,
                midiNote = midi(name),
                amplitude = 0.11,
                wave = Wave.SQUARE,
                duty = 0.125,
                attackMs = 3.0,
                releaseMs = 40.0
            )
        }

        addNote(bass, bpm, 0.0, 2.8, midi("A2"), 0.50, Wave.TRIANGLE, releaseMs = 140.0)
        addNote(bass, bpm, 2.8, 1.6, midi("F2"), 0.45, Wave.TRIANGLE, releaseMs = 120.0)
        addNote(bass, bpm, 4.4, 1.6, midi("E2"), 0.45, Wave.TRIANGLE, releaseMs = 120.0)
        addNote(bass, bpm, 6.0, 2.6, midi("A2"), 0.55, Wave.TRIANGLE, releaseMs = 220.0)

        addCrash(drums, bpm, 0.0, 0.30)
        addKick(drums, bpm, 0.0, 0.90)
        addKick(drums, bpm, 2.8, 0.62)
        addKick(drums, bpm, 4.4, 0.70)
        addKick(drums, bpm, 6.0, 0.95)
        for ((index, startBeat) in listOf(3.5, 3.75, 4.0, 4.25).withIndex()) {
            addSnare(drums, bpm, startBeat, 0.16 + index * 0.07)
        }

        return mix(lead, arp, bass, drums, bpm, pump = false)
    }

    private fun composeRecordTag(): ShortArray {
        val bpm = 150.0
        val beats = 4.0
        val tail = 1.0
        val size = ((beats * 60.0 / bpm + tail) * SAMPLE_RATE).toInt()

        val lead = FloatArray(size)
        val arp = FloatArray(size)
        val bass = FloatArray(size)
        val drums = FloatArray(size)

        val melody = listOf(
            n(0.0, "A5", 0.20),
            n(0.20, "C#6", 0.20),
            n(0.40, "E6", 0.20),
            n(0.60, "A6", 0.80),
            n(1.50, "E6", 0.20),
            n(1.70, "A6", 1.60)
        )
        for (event in melody) {
            addNote(
                out = lead,
                bpm = bpm,
                startBeat = event.beat,
                beats = event.beats,
                midiNote = event.midi,
                amplitude = 0.32,
                wave = Wave.LEAD,
                attackMs = 4.0,
                releaseMs = 60.0
            )
        }

        val arpNotes = listOf("A5", "C#6", "E6", "A6", "E6", "C#6", "A5", "C#6")
        for ((index, name) in arpNotes.withIndex()) {
            addNote(
                out = arp,
                bpm = bpm,
                startBeat = index * 0.25,
                beats = 0.22,
                midiNote = midi(name),
                amplitude = 0.13,
                wave = Wave.SQUARE,
                duty = 0.125,
                releaseMs = 18.0
            )
        }

        addNote(bass, bpm, 0.0, 1.4, midi("A2"), 0.50, Wave.TRIANGLE, releaseMs = 80.0)
        addNote(bass, bpm, 1.4, 0.8, midi("E3"), 0.42, Wave.TRIANGLE, releaseMs = 60.0)
        addNote(bass, bpm, 2.2, 1.8, midi("A2"), 0.55, Wave.TRIANGLE, releaseMs = 160.0)

        addCrash(drums, bpm, 0.0, 0.28)
        addKick(drums, bpm, 0.0, 0.85)
        addKick(drums, bpm, 0.5, 0.60)
        addKick(drums, bpm, 1.0, 0.65)
        addKick(drums, bpm, 1.5, 0.70)
        addKick(drums, bpm, 2.0, 0.80)

        return mix(lead, arp, bass, drums, bpm, pump = false)
    }

    private fun composeVictory(): ShortArray {
        val bpm = 140.0
        val beats = 8.0
        val tail = 0.8
        val size = ((beats * 60.0 / bpm + tail) * SAMPLE_RATE).toInt()

        val lead = FloatArray(size)
        val arp = FloatArray(size)
        val bass = FloatArray(size)
        val drums = FloatArray(size)

        val melody = listOf(
            n(0.0, "C5", 0.25),
            n(0.25, "E5", 0.25),
            n(0.50, "G5", 0.25),
            n(0.75, "C6", 0.65),
            n(1.50, "E6", 0.25),
            n(1.75, "G6", 0.25),
            n(2.00, "E6", 0.25),
            n(2.25, "C6", 0.75),
            n(3.00, "G5", 0.25),
            n(3.25, "C6", 0.25),
            n(3.50, "E6", 0.25),
            n(3.75, "G6", 0.75),
            n(4.50, "A5", 0.25),
            n(4.75, "C6", 0.25),
            n(5.00, "E6", 0.25),
            n(5.25, "A6", 1.00),
            n(6.25, "G6", 0.25),
            n(6.50, "E6", 0.25),
            n(6.75, "C6", 1.20)
        )
        for (event in melody) {
            addNote(
                out = lead,
                bpm = bpm,
                startBeat = event.beat,
                beats = event.beats,
                midiNote = event.midi,
                amplitude = 0.32,
                wave = Wave.LEAD,
                attackMs = 4.0,
                releaseMs = 55.0
            )
        }

        val arpNotes = listOf(
            "C5", "E5", "G5", "C6", "G5", "E5", "C5", "E5",
            "F5", "A5", "C6", "F6", "C6", "A5", "G5", "B5",
            "D6", "G6", "D6", "B5", "C6", "E6", "G6", "C7",
            "G6", "E6", "C6", "E6", "G6", "C7", "G6", "E6"
        )
        for ((index, name) in arpNotes.withIndex()) {
            addNote(
                out = arp,
                bpm = bpm,
                startBeat = index * 0.25,
                beats = 0.22,
                midiNote = midi(name),
                amplitude = 0.11,
                wave = Wave.SQUARE,
                duty = 0.125,
                releaseMs = 16.0
            )
        }

        addNote(bass, bpm, 0.0, 1.8, midi("C3"), 0.50, Wave.TRIANGLE, releaseMs = 90.0)
        addNote(bass, bpm, 2.0, 1.8, midi("G2"), 0.45, Wave.TRIANGLE, releaseMs = 90.0)
        addNote(bass, bpm, 4.0, 1.8, midi("F2"), 0.45, Wave.TRIANGLE, releaseMs = 90.0)
        addNote(bass, bpm, 6.0, 2.2, midi("C3"), 0.55, Wave.TRIANGLE, releaseMs = 160.0)

        addCrash(drums, bpm, 0.0, 0.30)
        for (beat in 0 until 8) {
            addKick(drums, bpm, beat.toDouble(), if (beat == 0) 0.85 else 0.68)
        }
        for (beat in listOf(1.0, 3.0, 5.0, 7.0)) {
            addSnare(drums, bpm, beat, 0.40)
        }
        for (beat in 0 until 16) {
            addHat(drums, bpm, beat * 0.5, if (beat % 2 == 1) 0.080 else 0.045, open = beat % 2 == 1)
        }

        return mix(lead, arp, bass, drums, bpm, pump = false)
    }

    // ---------------------------------------------------------------------
    // Osciladores e envelopes
    // ---------------------------------------------------------------------

    private enum class Wave { SQUARE, TRIANGLE, SAW, LEAD }

    private class Noise(seed: Int = 0x7FFF) {
        private var lfsr = if (seed == 0) 0x7FFF else seed and 0x7FFF

        fun next(): Double {
            val bit = (lfsr xor (lfsr shr 1)) and 1
            lfsr = (lfsr shr 1) or (bit shl 14)
            return if (lfsr and 1 == 1) 1.0 else -1.0
        }
    }

    private fun addNote(
        out: FloatArray,
        bpm: Double,
        startBeat: Double,
        beats: Double,
        midiNote: Int,
        amplitude: Double,
        wave: Wave,
        duty: Double = 0.5,
        attackMs: Double = 4.0,
        releaseMs: Double = 40.0,
        slideTo: Int? = null
    ) {
        val secPerBeat = 60.0 / bpm
        val start = (startBeat * secPerBeat * SAMPLE_RATE).toInt()
        val total = max(1, (beats * secPerBeat * SAMPLE_RATE).toInt())
        val startFrequency = frequency(midiNote)
        val endFrequency = slideTo?.let(::frequency) ?: startFrequency
        val attack = max(1, (attackMs / 1000.0 * SAMPLE_RATE).toInt())
        val release = max(1, (releaseMs / 1000.0 * SAMPLE_RATE).toInt())

        var phase = 0.0
        for (i in 0 until total) {
            val index = start + i
            if (index < 0) continue
            if (index >= out.size) break

            val progress = i.toDouble() / total
            val currentFrequency =
                startFrequency + (endFrequency - startFrequency) * progress
            phase += currentFrequency / SAMPLE_RATE
            if (phase >= 1.0) phase -= 1.0

            val raw = when (wave) {
                Wave.SQUARE -> if (phase < duty) 1.0 else -1.0
                Wave.TRIANGLE -> 1.0 - 4.0 * abs(phase - 0.5)
                Wave.SAW -> 2.0 * phase - 1.0
                Wave.LEAD -> {
                    val square = if (phase < 0.25) 1.0 else -1.0
                    val saw = 2.0 * phase - 1.0
                    square * 0.72 + saw * 0.28
                }
            }

            val attackGain = if (i < attack) i.toDouble() / attack else 1.0
            val releaseStart = total - release
            val releaseGain =
                if (i > releaseStart) (total - i).toDouble() / release else 1.0

            out[index] += (raw * amplitude * attackGain * releaseGain).toFloat()
        }
    }

    private fun addKick(out: FloatArray, bpm: Double, startBeat: Double, amplitude: Double) {
        val start = beatToSample(startBeat, bpm)
        val total = (0.17 * SAMPLE_RATE).toInt()
        val noise = Noise(0x1D2B)
        var phase = 0.0
        var previousNoise = 0.0

        for (i in 0 until total) {
            val index = start + i
            if (index < 0) continue
            if (index >= out.size) break

            val progress = i.toDouble() / total
            val frequency = 150.0 * (45.0 / 150.0).pow(progress)
            phase += frequency / SAMPLE_RATE
            if (phase >= 1.0) phase -= 1.0

            val envelope = (1.0 - progress).pow(2.2)
            val body = sin(2.0 * PI * phase) * amplitude * envelope

            // Um clique curto ajuda o bumbo a aparecer em alto-falantes pequenos.
            val currentNoise = noise.next()
            val click = (currentNoise - previousNoise) * 0.5 *
                (if (progress < 0.03) 0.18 * (1.0 - progress / 0.03) else 0.0)
            previousNoise = currentNoise

            out[index] += (body + click).toFloat()
        }
    }

    private fun addSnare(out: FloatArray, bpm: Double, startBeat: Double, amplitude: Double) {
        val start = beatToSample(startBeat, bpm)
        val total = (0.16 * SAMPLE_RATE).toInt()
        val noise = Noise(0x3C6E)

        for (i in 0 until total) {
            val index = start + i
            if (index < 0) continue
            if (index >= out.size) break

            val progress = i.toDouble() / total
            val envelope = (1.0 - progress).pow(3.0)
            val tone = sin(2.0 * PI * 185.0 * i / SAMPLE_RATE) * 0.32
            val value = (noise.next() * 0.68 + tone) * amplitude * envelope
            out[index] += value.toFloat()
        }
    }

    private fun addHat(
        out: FloatArray,
        bpm: Double,
        startBeat: Double,
        amplitude: Double,
        open: Boolean
    ) {
        val start = beatToSample(startBeat, bpm)
        val duration = if (open) 0.14 else 0.045
        val total = (duration * SAMPLE_RATE).toInt()
        val noise = Noise(0x5A17 + start)
        var previous = 0.0

        for (i in 0 until total) {
            val index = start + i
            if (index < 0) continue
            if (index >= out.size) break

            val progress = i.toDouble() / total
            val envelope = (1.0 - progress).pow(if (open) 2.0 else 3.5)
            val current = noise.next()
            // Diferenciação simples: deixa o ruído mais agudo, timbre de chimbal.
            val highPassed = (current - previous) * 0.5
            previous = current
            out[index] += (highPassed * amplitude * envelope).toFloat()
        }
    }

    private fun addCrash(out: FloatArray, bpm: Double, startBeat: Double, amplitude: Double) {
        val start = beatToSample(startBeat, bpm)
        val total = (0.55 * SAMPLE_RATE).toInt()
        val noise = Noise(0x6F3D)
        var previous = 0.0

        for (i in 0 until total) {
            val index = start + i
            if (index < 0) continue
            if (index >= out.size) break

            val progress = i.toDouble() / total
            val envelope = (1.0 - progress).pow(1.6)
            val current = noise.next()
            val highPassed = (current - previous) * 0.5
            previous = current
            out[index] += (highPassed * amplitude * envelope).toFloat()
        }
    }

    // ---------------------------------------------------------------------
    // Mixagem e masterização
    // ---------------------------------------------------------------------

    /**
     * Soma as camadas, aplica o "pump" de sidechain (o baixo/arpejo abaixam a
     * cada bumbo, como no synthwave), satura de leve e normaliza o pico.
     */
    private fun mix(
        lead: FloatArray,
        arp: FloatArray,
        bass: FloatArray,
        drums: FloatArray,
        bpm: Double,
        pump: Boolean
    ): ShortArray {
        val size = lead.size
        val echo = FloatArray(size)
        addEcho(echo, lead, delayBeats = 0.75, bpm = bpm, mixLevel = 0.20)

        val pumpEnvelope = if (pump) buildPump(size, bpm) else null
        val mixed = FloatArray(size)
        for (i in 0 until size) {
            val gain = pumpEnvelope?.get(i) ?: 1f
            val melodic = (lead[i] + echo[i]) * 0.95f + arp[i] * 0.90f + bass[i]
            mixed[i] = melodic * gain + drums[i]
        }

        // Remove o offset DC das ondas quadradas; sem isso o sinal fica
        // assimétrico e perde headroom.
        var mean = 0.0
        for (value in mixed) mean += value
        mean /= mixed.size
        val dc = mean.toFloat()
        for (i in mixed.indices) mixed[i] -= dc

        // Saturação suave antes de normalizar: cola a bateria no resto da mixagem.
        for (i in mixed.indices) {
            mixed[i] = tanh(mixed[i].toDouble()).toFloat()
        }

        var peak = 0f
        for (value in mixed) peak = max(peak, abs(value))
        val gain = if (peak > 0f) TARGET_PEAK.toFloat() / peak else 1f
        for (i in mixed.indices) mixed[i] *= gain

        applySeamFade(mixed)
        return toPcm(mixed)
    }

    private fun addEcho(
        out: FloatArray,
        source: FloatArray,
        delayBeats: Double,
        bpm: Double,
        mixLevel: Double
    ) {
        val delay = (delayBeats * 60.0 / bpm * SAMPLE_RATE).toInt()
        if (delay <= 0 || delay >= source.size) return
        val size = source.size
        for (i in 0 until size) {
            val delayedIndex = (i - delay + size) % size
            out[i] += source[delayedIndex] * mixLevel.toFloat()
        }
    }

    private fun buildPump(size: Int, bpm: Double): FloatArray {
        val beatSamples = (60.0 / bpm * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val duckSamples = (beatSamples * 0.34).toInt().coerceAtLeast(1)
        val pump = FloatArray(size) { 1f }

        var start = 0
        while (start < size) {
            for (i in 0 until duckSamples) {
                val index = (start + i) % size
                val gain = 0.58f + 0.42f * (i.toFloat() / duckSamples)
                if (gain < pump[index]) pump[index] = gain
            }
            start += beatSamples
        }
        return pump
    }

    /** Fade de 4 ms nas pontas: a emenda do loop fica sem estalo. */
    private fun applySeamFade(buffer: FloatArray) {
        val fade = (0.004 * SAMPLE_RATE).toInt().coerceAtLeast(1)
        for (i in 0 until fade) {
            val gain = i.toFloat() / fade
            buffer[i] *= gain
            buffer[buffer.size - 1 - i] *= gain
        }
    }

    private fun toPcm(samples: FloatArray): ShortArray {
        val pcm = ShortArray(samples.size)
        for (i in samples.indices) {
            pcm[i] = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return pcm
    }

    // ---------------------------------------------------------------------
    // Notas e acordes
    // ---------------------------------------------------------------------

    private data class NoteEvent(
        val beat: Double,
        val midi: Int,
        val beats: Double,
        val slideTo: Int? = null
    )

    private fun n(
        beat: Double,
        name: String,
        beats: Double,
        slideTo: String? = null
    ): NoteEvent = NoteEvent(beat, midi(name), beats, slideTo?.let(::midi))

    private fun beatToSample(beat: Double, bpm: Double): Int =
        (beat * 60.0 / bpm * SAMPLE_RATE).toInt()

    private fun frequency(midiNote: Int): Double =
        440.0 * 2.0.pow((midiNote - 69) / 12.0)

    private fun midi(name: String): Int {
        val note = name.trim().uppercase()
        require(note.length >= 2) { "nota inválida: $name" }

        var semitone = when (note[0]) {
            'C' -> 0
            'D' -> 2
            'E' -> 4
            'F' -> 5
            'G' -> 7
            'A' -> 9
            'B' -> 11
            else -> error("nota inválida: $name")
        }

        var index = 1
        if (index < note.length && (note[index] == '#' || note[index] == 'B')) {
            semitone += if (note[index] == '#') 1 else -1
            index++
        }

        val octave = note.substring(index).toInt()
        return (octave + 1) * 12 + semitone
    }

    private fun chordForBar(bar: Int): IntArray = when ((bar % 8) / 2) {
        0 -> chordAm
        1 -> chordF
        2 -> chordC
        else -> chordG
    }

    private val chordAm = intArrayOf(57, 60, 64, 69)
    private val chordF = intArrayOf(53, 57, 60, 65)
    private val chordC = intArrayOf(60, 64, 67, 72)
    private val chordG = intArrayOf(55, 59, 62, 67)

    // Melodia principal (compassos 1 a 8), em Lá menor.
    private val melodyA = listOf(
        n(0.0, "A4", 0.5), n(0.5, "C5", 0.5), n(1.0, "E5", 0.5), n(1.5, "A5", 0.5),
        n(2.0, "G5", 0.5), n(2.5, "E5", 0.5), n(3.0, "C5", 0.5), n(3.5, "E5", 0.5),
        n(4.0, "A5", 1.0), n(5.0, "G5", 0.5), n(5.5, "E5", 0.5), n(6.0, "D5", 0.5),
        n(6.5, "E5", 0.5), n(7.0, "C5", 1.0),

        n(8.0, "F5", 0.5), n(8.5, "E5", 0.5), n(9.0, "C5", 0.5), n(9.5, "F5", 0.5),
        n(10.0, "A5", 1.0), n(11.0, "G5", 0.5), n(11.5, "F5", 0.5),
        n(12.0, "E5", 1.0), n(13.0, "D5", 0.5), n(13.5, "C5", 0.5), n(14.0, "D5", 0.5),
        n(14.5, "E5", 0.5), n(15.0, "F5", 1.0),

        n(16.0, "G5", 0.5), n(16.5, "E5", 0.5), n(17.0, "C5", 0.5), n(17.5, "E5", 0.5),
        n(18.0, "G5", 0.5), n(18.5, "A5", 0.5), n(19.0, "G5", 0.5), n(19.5, "E5", 0.5),
        n(20.0, "C6", 1.0), n(21.0, "B5", 0.5), n(21.5, "G5", 0.5), n(22.0, "E5", 0.5),
        n(22.5, "G5", 0.5), n(23.0, "C6", 1.0),

        n(24.0, "B5", 0.5), n(24.5, "G5", 0.5), n(25.0, "D5", 0.5), n(25.5, "G5", 0.5),
        n(26.0, "B5", 1.0), n(27.0, "A5", 0.5), n(27.5, "G5", 0.5),
        n(28.0, "D5", 1.0), n(29.0, "E5", 0.5), n(29.5, "F5", 0.5), n(30.0, "G5", 0.5),
        n(30.5, "A5", 0.5), n(31.0, "B5", 1.0)
    )

    // Resposta da melodia (compassos 9 a 16), mais aguda e energética.
    private val melodyB = listOf(
        n(32.0, "E6", 0.5), n(32.5, "C6", 0.5), n(33.0, "A5", 0.5), n(33.5, "E5", 0.5),
        n(34.0, "A5", 0.5), n(34.5, "C6", 0.5), n(35.0, "E6", 0.5), n(35.5, "D6", 0.5),
        n(36.0, "C6", 1.0), n(37.0, "B5", 0.5), n(37.5, "A5", 0.5), n(38.0, "G5", 0.5),
        n(38.5, "A5", 0.5), n(39.0, "E5", 1.0),

        n(40.0, "F5", 0.5), n(40.5, "A5", 0.5), n(41.0, "C6", 0.5), n(41.5, "F6", 0.5),
        n(42.0, "E6", 1.0), n(43.0, "C6", 0.5), n(43.5, "A5", 0.5),
        n(44.0, "G5", 1.0), n(45.0, "A5", 0.5), n(45.5, "C6", 0.5), n(46.0, "A5", 0.5),
        n(46.5, "G5", 0.5), n(47.0, "F5", 1.0),

        n(48.0, "E6", 0.5), n(48.5, "C6", 0.5), n(49.0, "G5", 0.5), n(49.5, "C6", 0.5),
        n(50.0, "E6", 0.5), n(50.5, "G6", 0.5), n(51.0, "E6", 0.5), n(51.5, "C6", 0.5),
        n(52.0, "D6", 1.0), n(53.0, "C6", 0.5), n(53.5, "G5", 0.5), n(54.0, "E5", 0.5),
        n(54.5, "G5", 0.5), n(55.0, "C6", 1.0),

        n(56.0, "B5", 0.5), n(56.5, "D6", 0.5), n(57.0, "G6", 0.5), n(57.5, "D6", 0.5),
        n(58.0, "B5", 1.0), n(59.0, "A5", 0.5), n(59.5, "G5", 0.5),
        n(60.0, "A5", 1.0), n(61.0, "B5", 0.5), n(61.5, "D6", 0.5), n(62.0, "E6", 0.5),
        n(62.5, "D6", 0.5), n(63.0, "B5", 1.0)
    )
}
