package dev.matheus.snakeman.data

import dev.matheus.snakeman.game.WallMode

/** Uma partida encerrada que entrou para o placar. */
data class ScoreEntry(
    val score: Int,
    val wallMode: WallMode,
    val timestamp: Long
)

/**
 * Regras do placar de recordes (top [MAX_ENTRIES]), sem dependência de Android.
 * A persistência é feita por [Prefs]; aqui só mora a lógica pura.
 */
object ScoreBoard {

    const val MAX_ENTRIES = 5

    fun withEntry(
        entries: List<ScoreEntry>,
        entry: ScoreEntry,
        limit: Int = MAX_ENTRIES
    ): List<ScoreEntry> = (entries + entry)
        .sortedWith(compareByDescending<ScoreEntry> { it.score }.thenByDescending { it.timestamp })
        .take(limit)

    fun encode(entries: List<ScoreEntry>): String = entries.joinToString("\n") { entry ->
        "${entry.score}|${entry.wallMode.name}|${entry.timestamp}"
    }

    fun decode(raw: String?): List<ScoreEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size != 3) return@mapNotNull null
                val score = parts[0].toIntOrNull() ?: return@mapNotNull null
                val mode = runCatching { WallMode.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
                val timestamp = parts[2].toLongOrNull() ?: return@mapNotNull null
                ScoreEntry(score, mode, timestamp)
            }
            .sortedWith(compareByDescending<ScoreEntry> { it.score }.thenByDescending { it.timestamp })
            .take(MAX_ENTRIES)
            .toList()
    }

    fun best(entries: List<ScoreEntry>): Int = entries.maxOfOrNull { it.score } ?: 0
}
