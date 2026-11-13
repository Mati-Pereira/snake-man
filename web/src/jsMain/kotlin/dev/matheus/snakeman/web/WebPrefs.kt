package dev.matheus.snakeman.web

import dev.matheus.snakeman.data.ScoreBoard
import dev.matheus.snakeman.data.ScoreEntry
import dev.matheus.snakeman.game.WallMode
import kotlinx.browser.window

/** Equivalente web do `Prefs`: mesmas chaves, mas em localStorage. */
class WebPrefs {

    private val storage = window.localStorage

    var highScore: Int
        get() = storage.getItem(KEY_HIGH_SCORE)?.toIntOrNull() ?: 0
        set(value) {
            storage.setItem(KEY_HIGH_SCORE, value.toString())
        }

    var wallMode: WallMode
        get() = if (storage.getItem(KEY_WRAP) == "true") WallMode.WRAP else WallMode.SOLID
        set(value) {
            storage.setItem(KEY_WRAP, (value == WallMode.WRAP).toString())
        }

    var musicEnabled: Boolean
        get() = storage.getItem(KEY_MUSIC)?.toBooleanStrictOrNull() ?: true
        set(value) {
            storage.setItem(KEY_MUSIC, value.toString())
        }

    var scores: List<ScoreEntry>
        get() = ScoreBoard.decode(storage.getItem(KEY_SCORES))
        set(value) {
            storage.setItem(KEY_SCORES, ScoreBoard.encode(value))
        }

    fun registerScore(score: Int, mode: WallMode, timestamp: Long): List<ScoreEntry> {
        val updated = ScoreBoard.withEntry(
            entries = scores,
            entry = ScoreEntry(score, mode, timestamp)
        )
        scores = updated
        return updated
    }

    private companion object {
        const val KEY_HIGH_SCORE = "high_score"
        const val KEY_WRAP = "wrap_mode"
        const val KEY_SCORES = "scores"
        const val KEY_MUSIC = "music_enabled"
    }
}
