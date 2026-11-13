package dev.matheus.snakeman.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.matheus.snakeman.game.Apple
import dev.matheus.snakeman.game.Cell
import dev.matheus.snakeman.game.GameSnapshot
import dev.matheus.snakeman.game.SnakeEngine
import dev.matheus.snakeman.ui.theme.AppleRed
import dev.matheus.snakeman.ui.theme.BoardBg
import dev.matheus.snakeman.ui.theme.BoardGrid
import dev.matheus.snakeman.ui.theme.Gold
import dev.matheus.snakeman.ui.theme.GoldDeep
import dev.matheus.snakeman.ui.theme.SnakeGreen
import dev.matheus.snakeman.ui.theme.SnakeHeadGreen
import dev.matheus.snakeman.ui.theme.TailGreen
import kotlin.math.min

/** Desenha o tabuleiro, as maçãs e a cobra. */
@Composable
fun GameBoard(
    snapshot: GameSnapshot,
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier
) {
    val pulse by rememberInfiniteTransition(label = "apple").animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "applePulse"
    )

    Canvas(modifier = modifier.clip(RoundedCornerShape(20.dp))) {
        val cellW = size.width / cols
        val cellH = size.height / rows
        val cell = min(cellW, cellH)

        drawRect(color = BoardBg, size = size)
        drawGrid(cellW, cellH, cols, rows, cell)

        snapshot.apple?.let { apple ->
            drawApple(apple, cellW, cellH, cell, pulse)
        }

        // Cauda primeiro, cabeça por último, para a cabeça ficar sempre visível.
        snapshot.snake.asReversed().forEachIndexed { reversedIndex, cellPos ->
            val index = snapshot.snake.lastIndex - reversedIndex
            val progress = if (snapshot.snake.size > 1) {
                index / (snapshot.snake.size - 1).toFloat()
            } else {
                0f
            }
            val color = lerp(SnakeHeadGreen, TailGreen, progress)
            drawSegment(cellPos, color, cellW, cellH, cell, isHead = index == 0)
        }

        snapshot.snake.firstOrNull()?.let { head ->
            drawEyes(head, snapshot.direction.dx, snapshot.direction.dy, cellW, cellH, cell)
        }
    }
}

private fun DrawScope.drawGrid(
    cellW: Float,
    cellH: Float,
    cols: Int,
    rows: Int,
    cell: Float
) {
    for (x in 1 until cols) {
        drawLine(
            color = BoardGrid.copy(alpha = 0.55f),
            start = Offset(x * cellW, 0f),
            end = Offset(x * cellW, size.height),
            strokeWidth = 1f
        )
    }
    for (y in 1 until rows) {
        drawLine(
            color = BoardGrid.copy(alpha = 0.55f),
            start = Offset(0f, y * cellH),
            end = Offset(size.width, y * cellH),
            strokeWidth = 1f
        )
    }
    // Moldura interna sutil.
    val border = cell * 0.06f
    drawRect(
        color = BoardGrid,
        topLeft = Offset(border, border),
        size = Size(size.width - border * 2, size.height - border * 2),
        style = Stroke(width = 1.5f)
    )
}

private fun DrawScope.drawApple(
    apple: Apple,
    cellW: Float,
    cellH: Float,
    cell: Float,
    pulse: Float
) {
    val center = Offset((apple.cell.x + 0.5f) * cellW, (apple.cell.y + 0.5f) * cellH)
    if (apple.isGolden) {
        drawGoldenApple(apple, center, cell, pulse)
    } else {
        drawCircle(color = AppleRed.copy(alpha = 0.16f), radius = cell * 0.48f, center = center)
        drawCircle(color = AppleRed, radius = cell * 0.30f * pulse, center = center)
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = cell * 0.07f,
            center = center + Offset(-cell * 0.09f, -cell * 0.10f)
        )
        drawCircle(
            color = SnakeGreen,
            radius = cell * 0.09f,
            center = center + Offset(cell * 0.13f, -cell * 0.28f)
        )
    }
}

private fun DrawScope.drawGoldenApple(
    apple: Apple,
    center: Offset,
    cell: Float,
    pulse: Float
) {
    val remaining = apple.ticksLeft ?: SnakeEngine.GOLDEN_LIFETIME_TICKS
    val fraction = (remaining.toFloat() / SnakeEngine.GOLDEN_LIFETIME_TICKS).coerceIn(0f, 1f)

    // Brilho mais forte quando ainda há muito tempo.
    drawCircle(color = Gold.copy(alpha = 0.10f + 0.12f * fraction), radius = cell * 0.55f, center = center)
    drawCircle(color = GoldDeep, radius = cell * 0.32f * pulse, center = center)
    drawCircle(color = Gold, radius = cell * 0.26f * pulse, center = center)
    drawCircle(
        color = Color.White.copy(alpha = 0.75f),
        radius = cell * 0.07f,
        center = center + Offset(-cell * 0.09f, -cell * 0.10f)
    )

    // Anel mostrando o tempo restante.
    val ringRadius = cell * 0.40f
    drawArc(
        color = Gold.copy(alpha = 0.28f),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = center - Offset(ringRadius, ringRadius),
        size = Size(ringRadius * 2, ringRadius * 2),
        style = Stroke(width = cell * 0.07f)
    )
    drawArc(
        color = Gold,
        startAngle = -90f,
        sweepAngle = 360f * fraction,
        useCenter = false,
        topLeft = center - Offset(ringRadius, ringRadius),
        size = Size(ringRadius * 2, ringRadius * 2),
        style = Stroke(width = cell * 0.07f)
    )
}

private fun DrawScope.drawSegment(
    position: Cell,
    color: Color,
    cellW: Float,
    cellH: Float,
    cell: Float,
    isHead: Boolean
) {
    val gap = cell * 0.07f
    val topLeft = Offset(position.x * cellW + gap, position.y * cellH + gap)
    val segmentSize = Size(cellW - gap * 2, cellH - gap * 2)
    val corner = CornerRadius(cell * 0.30f, cell * 0.30f)

    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = segmentSize,
        cornerRadius = corner
    )

    if (isHead) {
        // Brilho no topo da cabeça.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = topLeft + Offset(segmentSize.width * 0.14f, segmentSize.height * 0.10f),
            size = Size(segmentSize.width * 0.72f, segmentSize.height * 0.22f),
            cornerRadius = corner
        )
    }
}

private fun DrawScope.drawEyes(
    head: Cell,
    dx: Int,
    dy: Int,
    cellW: Float,
    cellH: Float,
    cell: Float
) {
    val center = Offset((head.x + 0.5f) * cellW, (head.y + 0.5f) * cellH)
    val forward = Offset(dx.toFloat(), dy.toFloat())
    val perpendicular = Offset(-dy.toFloat(), dx.toFloat())
    val eyeRadius = cell * 0.085f
    val forwardOffset = cell * 0.16f
    val sideOffset = cell * 0.19f

    for (sign in listOf(-1f, 1f)) {
        val eyeCenter = center + forward * forwardOffset + perpendicular * (sideOffset * sign)
        drawCircle(color = Color(0xFF07131F), radius = eyeRadius, center = eyeCenter)
    }
}
