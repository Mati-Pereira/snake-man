package dev.matheus.snakeman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.matheus.snakeman.audio.Feedback
import dev.matheus.snakeman.audio.MusicPlayer
import dev.matheus.snakeman.data.Prefs
import dev.matheus.snakeman.data.ScoreEntry
import dev.matheus.snakeman.game.Direction
import dev.matheus.snakeman.game.GameStatus
import dev.matheus.snakeman.game.SnakeEngine
import dev.matheus.snakeman.game.TickOutcome
import dev.matheus.snakeman.game.WallMode
import dev.matheus.snakeman.ui.theme.AppleRed
import dev.matheus.snakeman.ui.theme.BoardBg
import dev.matheus.snakeman.ui.theme.Gold
import dev.matheus.snakeman.ui.theme.Night
import dev.matheus.snakeman.ui.theme.SnakeGreen
import dev.matheus.snakeman.ui.theme.SurfaceCard
import dev.matheus.snakeman.ui.theme.TextMuted
import dev.matheus.snakeman.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SnakeManApp() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val engine = remember { SnakeEngine() }
    val feedback = remember { Feedback(context) }
    val music = remember { MusicPlayer(context) }

    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var highScore by remember { mutableIntStateOf(prefs.highScore) }
    var scores by remember { mutableStateOf(prefs.scores) }
    var newRecord by remember { mutableStateOf(false) }
    var boardFull by remember { mutableStateOf(false) }
    var menuMode by remember { mutableStateOf(prefs.wallMode) }
    var showScores by remember { mutableStateOf(false) }
    var musicEnabled by remember { mutableStateOf(prefs.musicEnabled) }

    DisposableEffect(Unit) {
        onDispose {
            music.release()
            feedback.release()
        }
    }

    val startGame: (WallMode) -> Unit = { mode ->
        prefs.wallMode = mode
        menuMode = mode
        newRecord = false
        boardFull = false
        engine.start(mode)
        snapshot = engine.snapshot()
        feedback.click()
    }

    val pauseGame: () -> Unit = {
        engine.pause()
        snapshot = engine.snapshot()
    }

    val resumeGame: () -> Unit = {
        engine.resume()
        snapshot = engine.snapshot()
    }

    val backToMenu: () -> Unit = {
        engine.backToMenu()
        snapshot = engine.snapshot()
    }

    // Loop principal: cada "tick" move a cobra.
    LaunchedEffect(snapshot.status) {
        if (snapshot.status != GameStatus.PLAYING) return@LaunchedEffect
        while (true) {
            delay(engine.tickMillis)
            val scoreBefore = engine.score
            when (engine.tick()) {
                TickOutcome.ATE -> {
                    if (engine.score - scoreBefore >= SnakeEngine.GOLDEN_SCORE) {
                        feedback.eatGolden()
                    } else {
                        feedback.eat()
                    }
                    snapshot = engine.snapshot()
                }

                TickOutcome.DIED -> {
                    feedback.death()
                    snapshot = engine.snapshot()
                    break
                }

                TickOutcome.WON -> {
                    boardFull = true
                    feedback.win()
                    snapshot = engine.snapshot()
                    break
                }

                TickOutcome.MOVED -> snapshot = engine.snapshot()
                TickOutcome.IDLE -> break
            }
        }
    }

    // Música e placar acompanham o estado da partida.
    LaunchedEffect(snapshot.status) {
        when (snapshot.status) {
            GameStatus.PLAYING -> {
                if (musicEnabled) music.playGameLoop() else music.stop()
            }

            GameStatus.PAUSED -> music.pause()

            GameStatus.MENU -> music.stop()

            GameStatus.GAME_OVER -> {
                val newBest = snapshot.score > prefs.highScore
                if (newBest) {
                    prefs.highScore = snapshot.score
                    highScore = snapshot.score
                    newRecord = true
                }
                if (snapshot.score > 0) {
                    scores = prefs.registerScore(
                        score = snapshot.score,
                        mode = snapshot.wallMode,
                        timestamp = System.currentTimeMillis()
                    )
                }

                if (musicEnabled) {
                    if (boardFull) music.playVictory() else music.playGameOver(newBest)
                } else {
                    music.stop()
                }
            }
        }
    }

    // Pausa sozinho quando o app vai para segundo plano.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (engine.status == GameStatus.PLAYING) {
            engine.pause()
            snapshot = engine.snapshot()
        }
        music.pause()
    }

    // Mantém a tela ligada durante a partida.
    val view = LocalView.current
    DisposableEffect(snapshot.status) {
        view.keepScreenOn = snapshot.status == GameStatus.PLAYING
        onDispose { view.keepScreenOn = false }
    }

    val steer: (Direction) -> Unit = { engine.queueDirection(it) }

    val toggleMusic: () -> Unit = {
        musicEnabled = !musicEnabled
        prefs.musicEnabled = musicEnabled
        if (!musicEnabled) {
            music.stop()
        } else if (snapshot.status == GameStatus.PLAYING) {
            music.playGameLoop()
        }
        feedback.click()
    }

    // O placar tem tela própria, mas os efeitos acima continuam vivos para não
    // registrar a mesma partida duas vezes ao voltar.
    if (showScores) {
        ScoresScreen(entries = scores, onBack = { showScores = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Header(score = snapshot.score, highScore = highScore)

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val side = minOf(maxWidth, maxHeight)
            Box(
                modifier = Modifier
                    .size(side)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .swipeToSteer { steer(it) }
            ) {
                GameBoard(
                    snapshot = snapshot,
                    cols = engine.cols,
                    rows = engine.rows,
                    modifier = Modifier.fillMaxSize()
                )

                when (snapshot.status) {
                    GameStatus.MENU -> MenuOverlay(
                        mode = menuMode,
                        highScore = highScore,
                        musicEnabled = musicEnabled,
                        onToggleMusic = toggleMusic,
                        onModeChange = { menuMode = it },
                        onPlay = { startGame(menuMode) },
                        onScores = { showScores = true }
                    )

                    GameStatus.PAUSED -> PausedOverlay(
                        score = snapshot.score,
                        musicEnabled = musicEnabled,
                        onToggleMusic = toggleMusic,
                        onResume = resumeGame,
                        onRestart = { startGame(menuMode) },
                        onMenu = backToMenu
                    )

                    GameStatus.GAME_OVER -> GameOverOverlay(
                        score = snapshot.score,
                        highScore = highScore,
                        newRecord = newRecord,
                        boardFull = boardFull,
                        onRestart = { startGame(menuMode) },
                        onMenu = backToMenu,
                        onScores = { showScores = true }
                    )

                    GameStatus.PLAYING -> Unit
                }
            }
        }

        when (snapshot.status) {
            GameStatus.PLAYING, GameStatus.PAUSED -> ControlBar(
                paused = snapshot.status == GameStatus.PAUSED,
                onDirection = steer,
                onPauseToggle = { if (snapshot.status == GameStatus.PLAYING) pauseGame() else resumeGame() },
                onRestart = { startGame(menuMode) },
                onMenu = backToMenu
            )

            else -> Text(
                text = buildString {
                    if ((if (snapshot.status == GameStatus.MENU) menuMode else snapshot.wallMode) == WallMode.WRAP) {
                        append("MODO ATRAVESSA · bordas dão a volta")
                    } else {
                        append("MODO PAREDES · cuidado com as bordas")
                    }
                    append("  ·  dourada = 30")
                },
                color = Gold,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun Header(score: Int, highScore: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "SNAKE MAN",
            color = SnakeGreen,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 3.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat(label = "PONTOS", value = score)
            Stat(label = "RECORDE", value = highScore)
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp
        )
        Text(
            text = value.toString().padStart(3, '0'),
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ControlBar(
    paused: Boolean,
    onDirection: (Direction) -> Unit,
    onPauseToggle: () -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DirectionPad(onDirection = onDirection, buttonSize = 52.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArcadeButton(if (paused) "CONTINUAR" else "PAUSAR", onPauseToggle, width = 112.dp)
            ArcadeButton("NOVO JOGO", onRestart, width = 112.dp)
            ArcadeButton("MENU", onMenu, width = 112.dp)
        }
    }
}

@Composable
private fun MenuOverlay(
    mode: WallMode,
    highScore: Int,
    musicEnabled: Boolean,
    onToggleMusic: () -> Unit,
    onModeChange: (WallMode) -> Unit,
    onPlay: () -> Unit,
    onScores: () -> Unit
) {
    OverlayScrim {
        Text(
            text = "SNAKE MAN",
            color = SnakeGreen,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )
        Text(
            text = "swipe na tela ou use o D-pad",
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(4.dp))
        ModeSelector(mode = mode, onModeChange = onModeChange)
        Text(
            text = if (mode == WallMode.WRAP) {
                "a cobra atravessa de um lado ao outro"
            } else {
                "bater na parede encerra o jogo"
            },
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        MusicToggle(enabled = musicEnabled, onToggle = onToggleMusic)
        Spacer(Modifier.height(4.dp))
        ArcadeButton("JOGAR", onPlay, filled = true, width = 150.dp, height = 46.dp)
        ArcadeButton("RECORDES", onScores, width = 150.dp)
        Text(
            text = "RECORDE  ${highScore.toString().padStart(3, '0')}",
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun PausedOverlay(
    score: Int,
    musicEnabled: Boolean,
    onToggleMusic: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit
) {
    OverlayScrim {
        Text(
            text = "PAUSADO",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )
        Text(
            text = "PONTOS  ${score.toString().padStart(3, '0')}",
            color = TextMuted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        MusicToggle(enabled = musicEnabled, onToggle = onToggleMusic)
        Spacer(Modifier.height(4.dp))
        ArcadeButton("CONTINUAR", onResume, filled = true, width = 170.dp)
        ArcadeButton("NOVO JOGO", onRestart, width = 170.dp)
        ArcadeButton("MENU", onMenu, width = 170.dp)
    }
}

@Composable
private fun GameOverOverlay(
    score: Int,
    highScore: Int,
    newRecord: Boolean,
    boardFull: Boolean,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
    onScores: () -> Unit
) {
    OverlayScrim {
        Text(
            text = if (boardFull) "TABULEIRO CHEIO!" else "GAME OVER",
            color = if (boardFull) SnakeGreen else AppleRed,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 3.sp
        )
        if (newRecord) {
            Text(
                text = "★ NOVO RECORDE ★",
                color = SnakeGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
        }
        Text(
            text = "PONTOS  ${score.toString().padStart(3, '0')}",
            color = TextPrimary,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "RECORDE  ${highScore.toString().padStart(3, '0')}",
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(2.dp))
        ArcadeButton("JOGAR DE NOVO", onRestart, filled = true, width = 175.dp, height = 42.dp)
        ArcadeButton("MENU", onMenu, width = 175.dp, height = 40.dp)
        Text(
            text = "VER RECORDES",
            color = Gold,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onScores)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun OverlayScrim(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night.copy(alpha = 0.88f))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun ModeSelector(mode: WallMode, onModeChange: (WallMode) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(BoardBg)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeChip("PAREDES", selected = mode == WallMode.SOLID) { onModeChange(WallMode.SOLID) }
        ModeChip("ATRAVESSA", selected = mode == WallMode.WRAP) { onModeChange(WallMode.WRAP) }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) SnakeGreen else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Night else TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun MusicToggle(enabled: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(BoardBg)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "♪",
            color = if (enabled) SnakeGreen else TextMuted,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (enabled) "MÚSICA ON" else "MÚSICA OFF",
            color = if (enabled) SnakeGreen else TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ArcadeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    width: androidx.compose.ui.unit.Dp? = null,
    height: androidx.compose.ui.unit.Dp = 44.dp
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .heightIn(min = height)
            .clip(shape)
            .background(if (filled) SnakeGreen else SurfaceCard)
            .border(1.dp, if (filled) SnakeGreen else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (filled) Night else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

/** Tela própria com o top 5 de partidas. */
@Composable
private fun ScoresScreen(entries: List<ScoreEntry>, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = "RECORDES",
            color = SnakeGreen,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )
        Text(
            text = "TOP 5 PARTIDAS",
            color = TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(20.dp))

        if (entries.isEmpty()) {
            Text(
                text = "NENHUMA PARTIDA AINDA",
                color = TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 30.dp)
            )
            Text(
                text = "jogue para entrar no placar",
                color = TextMuted.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            entries.forEachIndexed { index, entry ->
                ScoreRow(index = index, entry = entry)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.weight(1f))
        ArcadeButton("VOLTAR", onBack, filled = true, width = 170.dp)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ScoreRow(index: Int, entry: ScoreEntry) {
    val shape = RoundedCornerShape(14.dp)
    val isTop = index == 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isTop) SurfaceCard else SurfaceCard.copy(alpha = 0.6f))
            .border(1.dp, if (isTop) Gold.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            color = if (isTop) Gold else TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = entry.score.toString().padStart(3, '0'),
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (entry.wallMode == WallMode.WRAP) "ATRAVESSA" else "PAREDES",
                color = if (entry.wallMode == WallMode.WRAP) SnakeGreen else TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(
                text = remember(entry.timestamp) { formatDate(entry.timestamp) },
                color = TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(timestamp))
