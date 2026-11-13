package dev.matheus.snakeman.data

import android.content.Context
import dev.matheus.snakeman.game.WallMode

/** Recorde, placar e preferências persistidas entre sessões. */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var highScore: Int
        get() = prefs.getInt(KEY_HIGH_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_HIGH_SCORE, value).apply()

    var wallMode: WallMode
        get() = if (prefs.getBoolean(KEY_WRAP, false)) WallMode.WRAP else WallMode.SOLID
        set(value) = prefs.edit().putBoolean(KEY_WRAP, value == WallMode.WRAP).apply()

    /** Música de fundo ligada por padrão; o jogador pode desligar no menu ou na pausa. */
    var musicEnabled: Boolean
        get() = prefs.getBoolean(KEY_MUSIC, true)
        set(value) = prefs.edit().putBoolean(KEY_MUSIC, value).apply()

    /** Top 5 partidas, da maior pontuação para a menor. */
    var scores: List<ScoreEntry>
        get() = ScoreBoard.decode(prefs.getString(KEY_SCORES, null))
        set(value) = prefs.edit().putString(KEY_SCORES, ScoreBoard.encode(value)).apply()

    /** Registra uma partida no placar e devolve a lista atualizada. */
    fun registerScore(score: Int, mode: WallMode, timestamp: Long): List<ScoreEntry> {
        val updated = ScoreBoard.withEntry(
            entries = scores,
            entry = ScoreEntry(score, mode, timestamp)
        )
        scores = updated
        return updated
    }

    private companion object {
        const val FILE_NAME = "snake_man_prefs"
        const val KEY_HIGH_SCORE = "high_score"
        const val KEY_WRAP = "wrap_mode"
        const val KEY_SCORES = "scores"
        const val KEY_MUSIC = "music_enabled"
    }
}
