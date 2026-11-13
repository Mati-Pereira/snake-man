package dev.matheus.snakeman.web

import dev.matheus.snakeman.data.ScoreEntry
import dev.matheus.snakeman.game.Direction
import dev.matheus.snakeman.game.GameStatus
import dev.matheus.snakeman.game.SnakeEngine
import dev.matheus.snakeman.game.TickOutcome
import dev.matheus.snakeman.game.WallMode
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import kotlin.js.Date
import kotlin.math.abs

private const val SWIPE_PX = 24.0

private val engine = SnakeEngine()
private val prefs = WebPrefs()
private val audio = BrowserAudio()
private lateinit var board: BoardRenderer

private var highScore = prefs.highScore
private var scores = prefs.scores
private var menuMode = prefs.wallMode
private var musicEnabled = prefs.musicEnabled
private var newRecord = false
private var boardFull = false
private var showScores = false

private var overlaySignature = ""
private var lastStatus: GameStatus? = null
private var lastFrameTime = 0.0
private var accumulator = 0.0
private var lastCssSize = 0.0

fun main() {
    board = BoardRenderer(byId("board") as HTMLCanvasElement)
    wireChrome()
    wireKeyboard()
    wireSwipe()

    document.asDynamic().addEventListener("visibilitychange", {
        val hidden = (document.asDynamic().hidden as? Boolean) ?: false
        if (hidden && engine.status == GameStatus.PLAYING) {
            engine.pause()
            audio.pause()
            syncAll()
        }
    })

    resizeIfNeeded()
    syncAll()
    audio.prewarm()
    window.requestAnimationFrame { time -> frame(time) }
}

// ---------------------------------------------------------------------------
// Loop principal
// ---------------------------------------------------------------------------

private fun frame(time: Double) {
    val delta = if (lastFrameTime == 0.0) 0.0 else (time - lastFrameTime).coerceAtMost(200.0)
    lastFrameTime = time

    if (engine.status == GameStatus.PLAYING) {
        accumulator += delta
        var steps = 0
        while (engine.status == GameStatus.PLAYING && accumulator >= engine.tickMillis && steps < 8) {
            accumulator -= engine.tickMillis
            val scoreBefore = engine.score
            when (engine.tick()) {
                TickOutcome.ATE ->
                    if (engine.score - scoreBefore >= SnakeEngine.GOLDEN_SCORE) audio.eatGolden() else audio.eat()

                TickOutcome.DIED -> {
                    audio.death()
                    finishGame()
                }

                TickOutcome.WON -> {
                    boardFull = true
                    audio.win()
                    finishGame()
                }

                else -> Unit
            }
            steps++
        }
    } else {
        accumulator = 0.0
    }

    resizeIfNeeded()
    board.render(engine.snapshot(), engine.cols, engine.rows, time)
    updateHeader()
    syncAll()
    window.requestAnimationFrame { next -> frame(next) }
}

private fun resizeIfNeeded() {
    val size = byId("stage").clientWidth.toDouble()
    if (size > 0.0 && size != lastCssSize) {
        lastCssSize = size
        board.resize(size)
    }
}

// ---------------------------------------------------------------------------
// Ações
// ---------------------------------------------------------------------------

private fun startGame(mode: WallMode) {
    prefs.wallMode = mode
    menuMode = mode
    newRecord = false
    boardFull = false
    engine.start(mode)
    audio.click()
    syncAll()
}

private fun togglePause() {
    when (engine.status) {
        GameStatus.PLAYING -> engine.pause()
        GameStatus.PAUSED -> engine.resume()
        else -> return
    }
    syncAll()
}

private fun backToMenu() {
    engine.backToMenu()
    showScores = false
    syncAll()
}

private fun toggleMusic() {
    musicEnabled = !musicEnabled
    prefs.musicEnabled = musicEnabled
    when {
        !musicEnabled -> audio.stop()
        engine.status == GameStatus.PLAYING -> audio.playLoop()
    }
    audio.click()
    syncAll()
}

private fun finishGame() {
    val isNewBest = engine.score > prefs.highScore
    if (isNewBest) {
        prefs.highScore = engine.score
        highScore = engine.score
        newRecord = true
    }
    if (engine.score > 0) {
        scores = prefs.registerScore(engine.score, engine.wallMode, Date.now().toLong())
    }
    if (musicEnabled) {
        if (boardFull) audio.playVictory() else audio.playGameOver(isNewBest)
    } else {
        audio.stop()
    }
    syncAll()
}

// ---------------------------------------------------------------------------
// Sincronização da tela
// ---------------------------------------------------------------------------

private fun syncAll() {
    val status = engine.status
    if (status != lastStatus) {
        lastStatus = status
        when (status) {
            GameStatus.PLAYING -> if (musicEnabled) audio.playLoop() else audio.stop()
            GameStatus.PAUSED -> audio.pause()
            GameStatus.MENU -> audio.stop()
            else -> Unit // GAME_OVER: o jingle já foi disparado em finishGame()
        }
    }
    syncChrome()
    renderOverlay()
}

private fun syncChrome() {
    val inGame = engine.status == GameStatus.PLAYING || engine.status == GameStatus.PAUSED
    byId("controls").show(inGame)
    byId("hint").show(!inGame)
    setText("pauseBtn", if (engine.status == GameStatus.PAUSED) "CONTINUAR" else "PAUSAR")

    val mode = if (engine.status == GameStatus.MENU) menuMode else engine.wallMode
    setText(
        "hint",
        buildString {
            if (mode == WallMode.WRAP) {
                append("MODO ATRAVESSA · bordas dão a volta")
            } else {
                append("MODO PAREDES · cuidado com as bordas")
            }
            append("  ·  dourada = 30")
        }
    )
}

private fun renderOverlay() {
    val signature = buildString {
        append(engine.status).append('|')
        append(menuMode).append('|')
        append(musicEnabled).append('|')
        append(engine.score).append('|')
        append(highScore).append('|')
        append(newRecord).append('|')
        append(boardFull).append('|')
        append(showScores)
    }
    if (signature == overlaySignature) return
    overlaySignature = signature

    val scoresScreen = byId("scoresScreen")
    if (showScores) {
        renderScoreList()
        scoresScreen.show(true)
        return
    }
    scoresScreen.show(false)

    byId("overlay").innerHTML = when (engine.status) {
        GameStatus.MENU -> menuHtml()
        GameStatus.PAUSED -> pausedHtml()
        GameStatus.GAME_OVER -> gameOverHtml()
        GameStatus.PLAYING -> ""
    }
    wireOverlay()
}

private fun menuHtml(): String = """
<div class="scrim">
    <div class="title snake">SNAKE MAN</div>
    <div class="subtitle">swipe na tela ou use o D-pad</div>
    <div class="mode-selector">
        <button class="chip ${if (menuMode == WallMode.SOLID) "selected" else ""}" id="menuSolid">PAREDES</button>
        <button class="chip ${if (menuMode == WallMode.WRAP) "selected" else ""}" id="menuWrap">ATRAVESSA</button>
    </div>
    <div class="subtitle">${
    if (menuMode == WallMode.WRAP) "a cobra atravessa de um lado ao outro" else "bater na parede encerra o jogo"
}</div>
    <button class="music-toggle ${if (musicEnabled) "on" else ""}" id="menuMusic">
        <span class="note">♪</span>${if (musicEnabled) "MÚSICA ON" else "MÚSICA OFF"}
    </button>
    <button class="arcade filled wide" id="menuPlay">JOGAR</button>
    <button class="arcade wide" id="menuScores">RECORDES</button>
    <div class="small muted">RECORDE&nbsp;&nbsp;${pad(highScore)}</div>
</div>
""".trimIndent()

private fun pausedHtml(): String = """
<div class="scrim">
    <div class="title">PAUSADO</div>
    <div class="score-line">PONTOS&nbsp;&nbsp;${pad(engine.score)}</div>
    <button class="music-toggle ${if (musicEnabled) "on" else ""}" id="pauseMusic">
        <span class="note">♪</span>${if (musicEnabled) "MÚSICA ON" else "MÚSICA OFF"}
    </button>
    <button class="arcade filled wide" id="pauseResume">CONTINUAR</button>
    <button class="arcade wide" id="pauseRestart">NOVO JOGO</button>
    <button class="arcade wide" id="pauseMenu">MENU</button>
</div>
""".trimIndent()

private fun gameOverHtml(): String = """
<div class="scrim">
    <div class="title ${if (boardFull) "snake" else "danger"}">${if (boardFull) "TABULEIRO CHEIO!" else "GAME OVER"}</div>
    ${if (newRecord) """<div class="record-badge">★ NOVO RECORDE ★</div>""" else ""}
    <div class="score-line">PONTOS&nbsp;&nbsp;${pad(engine.score)}</div>
    <div class="record-line">RECORDE&nbsp;&nbsp;${pad(highScore)}</div>
    <button class="arcade filled wide" id="overRestart">JOGAR DE NOVO</button>
    <button class="arcade wide" id="overMenu">MENU</button>
    <button class="link-button" id="overScores">VER RECORDES</button>
</div>
""".trimIndent()

private fun wireOverlay() {
    byIdOrNull("menuSolid")?.onClick {
        menuMode = WallMode.SOLID
        syncAll()
    }
    byIdOrNull("menuWrap")?.onClick {
        menuMode = WallMode.WRAP
        syncAll()
    }
    byIdOrNull("menuMusic")?.onClick { toggleMusic() }
    byIdOrNull("menuPlay")?.onClick { startGame(menuMode) }
    byIdOrNull("menuScores")?.onClick {
        showScores = true
        syncAll()
    }

    byIdOrNull("pauseMusic")?.onClick { toggleMusic() }
    byIdOrNull("pauseResume")?.onClick { togglePause() }
    byIdOrNull("pauseRestart")?.onClick { startGame(menuMode) }
    byIdOrNull("pauseMenu")?.onClick { backToMenu() }

    byIdOrNull("overRestart")?.onClick { startGame(menuMode) }
    byIdOrNull("overMenu")?.onClick { backToMenu() }
    byIdOrNull("overScores")?.onClick {
        showScores = true
        syncAll()
    }
}

private fun renderScoreList() {
    byId("scoreList").innerHTML = if (scores.isEmpty()) {
        """<div class="empty">NENHUMA PARTIDA AINDA<div class="small">jogue para entrar no placar</div></div>"""
    } else {
        scores.mapIndexed { index, entry -> scoreRowHtml(index, entry) }.joinToString("")
    }
}

private fun scoreRowHtml(index: Int, entry: ScoreEntry): String {
    val top = index == 0
    val wrap = entry.wallMode == WallMode.WRAP
    return """
<div class="score-row ${if (top) "top" else ""}">
    <div class="rank">${(index + 1).toString().padStart(2, '0')}</div>
    <div class="points">${pad(entry.score)}</div>
    <div class="meta">
        <div class="mode ${if (wrap) "wrap" else ""}">${if (wrap) "ATRAVESSA" else "PAREDES"}</div>
        <div class="date">${formatDate(entry.timestamp)}</div>
    </div>
</div>
""".trimIndent()
}

private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp.toDouble())
    fun two(value: Int) = value.toString().padStart(2, '0')
    return "${two(date.getDate())}/${two(date.getMonth() + 1)}/${two(date.getFullYear() % 100)} " +
        "${two(date.getHours())}:${two(date.getMinutes())}"
}

private fun updateHeader() {
    setText("score", pad(engine.score))
    setText("best", pad(highScore))
}

private fun setText(id: String, value: String) {
    val element = byId(id)
    if (element.textContent != value) element.textContent = value
}

private fun pad(value: Int): String = value.toString().padStart(3, '0')

// ---------------------------------------------------------------------------
// Entrada
// ---------------------------------------------------------------------------

private fun wireChrome() {
    byId("padUp").onClick { engine.queueDirection(Direction.UP) }
    byId("padDown").onClick { engine.queueDirection(Direction.DOWN) }
    byId("padLeft").onClick { engine.queueDirection(Direction.LEFT) }
    byId("padRight").onClick { engine.queueDirection(Direction.RIGHT) }
    byId("pauseBtn").onClick { togglePause() }
    byId("restartBtn").onClick { startGame(menuMode) }
    byId("menuBtn").onClick { backToMenu() }
    byId("backFromScores").onClick {
        showScores = false
        syncAll()
    }
}

private fun wireKeyboard() {
    document.asDynamic().addEventListener("keydown", { event: dynamic ->
        val key = (event.key as? String) ?: ""
        val repeat = (event.repeat as? Boolean) ?: false
        var handled = true
        when (key) {
            "ArrowUp", "w", "W" -> if (!repeat) engine.queueDirection(Direction.UP)
            "ArrowDown", "s", "S" -> if (!repeat) engine.queueDirection(Direction.DOWN)
            "ArrowLeft", "a", "A" -> if (!repeat) engine.queueDirection(Direction.LEFT)
            "ArrowRight", "d", "D" -> if (!repeat) engine.queueDirection(Direction.RIGHT)
            " ", "Spacebar" -> if (!repeat) togglePause()
            "m", "M" -> if (!repeat) toggleMusic()
            else -> handled = false
        }
        if (handled) event.preventDefault()
    })
}

private fun wireSwipe() {
    val stage = byId("stage").asDynamic()
    var tracking = false
    var lastX = 0.0
    var lastY = 0.0
    var accumulatedX = 0.0
    var accumulatedY = 0.0

    stage.addEventListener("pointerdown", { event: dynamic ->
        tracking = true
        lastX = event.clientX as Double
        lastY = event.clientY as Double
        accumulatedX = 0.0
        accumulatedY = 0.0
    })

    stage.addEventListener("pointermove", { event: dynamic ->
        if (tracking) {
            val x = event.clientX as Double
            val y = event.clientY as Double
            accumulatedX += x - lastX
            accumulatedY += y - lastY
            lastX = x
            lastY = y

            if (abs(accumulatedX) >= SWIPE_PX || abs(accumulatedY) >= SWIPE_PX) {
                val direction = if (abs(accumulatedX) > abs(accumulatedY)) {
                    if (accumulatedX > 0) Direction.RIGHT else Direction.LEFT
                } else {
                    if (accumulatedY > 0) Direction.DOWN else Direction.UP
                }
                engine.queueDirection(direction)
                accumulatedX = 0.0
                accumulatedY = 0.0
            }
        }
    })

    stage.addEventListener("pointerup", { tracking = false })
    stage.addEventListener("pointercancel", { tracking = false })
}

// ---------------------------------------------------------------------------
// Utilitários de DOM
// ---------------------------------------------------------------------------

private fun byId(id: String): HTMLElement = document.getElementById(id) as HTMLElement

private fun byIdOrNull(id: String): HTMLElement? = document.getElementById(id) as? HTMLElement

private fun HTMLElement.listen(type: String, handler: (dynamic) -> Unit) {
    asDynamic().addEventListener(type, { event: dynamic -> handler(event) })
}

private fun HTMLElement.onClick(handler: () -> Unit) = listen("click") { handler() }

private fun HTMLElement.show(visible: Boolean) {
    classList.toggle("hidden", !visible)
}
