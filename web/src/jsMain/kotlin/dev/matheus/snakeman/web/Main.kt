package dev.matheus.snakeman.web

import dev.matheus.snakeman.audio.ChiptuneSynth
import dev.matheus.snakeman.game.SnakeEngine

fun main() {
    val engine = SnakeEngine()
    println("engine ${engine.cols}x${engine.rows}")
    println("music samples ${ChiptuneSynth.gameLoop().size}")
}
