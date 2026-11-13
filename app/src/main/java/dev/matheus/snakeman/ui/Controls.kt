package dev.matheus.snakeman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.matheus.snakeman.game.Direction
import dev.matheus.snakeman.ui.theme.SnakeGreen
import dev.matheus.snakeman.ui.theme.SurfaceCard
import kotlin.math.abs

/** D-pad clássico em cruz, com quatro botões. */
@Composable
fun DirectionPad(
    onDirection: (Direction) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 54.dp
) {
    val total = buttonSize * 3 + 6.dp
    Box(modifier.size(total)) {
        PadButton("▲", Direction.UP, onDirection, buttonSize, Modifier.align(Alignment.TopCenter))
        PadButton("▼", Direction.DOWN, onDirection, buttonSize, Modifier.align(Alignment.BottomCenter))
        PadButton("◀", Direction.LEFT, onDirection, buttonSize, Modifier.align(Alignment.CenterStart))
        PadButton("▶", Direction.RIGHT, onDirection, buttonSize, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun PadButton(
    label: String,
    direction: Direction,
    onDirection: (Direction) -> Unit,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(SurfaceCard)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable { onDirection(direction) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = SnakeGreen,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Converte arrastos na tela em direções. A cada [thresholdDp] percorridos,
 * a direção dominante do arrasto é emitida e o acumulador zera, permitindo
 * vários comandos em um único swipe.
 */
@Composable
fun Modifier.swipeToSteer(
    thresholdDp: Dp = 24.dp,
    onDirection: (Direction) -> Unit
): Modifier {
    val thresholdPx = with(LocalDensity.current) { thresholdDp.toPx() }
    return pointerInput(thresholdPx) {
        var accumulated = Offset.Zero
        detectDragGestures(
            onDragStart = { accumulated = Offset.Zero },
            onDragEnd = { accumulated = Offset.Zero },
            onDragCancel = { accumulated = Offset.Zero },
            onDrag = { change, dragAmount ->
                change.consume()
                accumulated += dragAmount
                val x = accumulated.x
                val y = accumulated.y
                if (abs(x) >= thresholdPx || abs(y) >= thresholdPx) {
                    val direction = if (abs(x) > abs(y)) {
                        if (x > 0) Direction.RIGHT else Direction.LEFT
                    } else {
                        if (y > 0) Direction.DOWN else Direction.UP
                    }
                    onDirection(direction)
                    accumulated = Offset.Zero
                }
            }
        )
    }
}
