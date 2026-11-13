package dev.matheus.snakeman.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.matheus.snakeman.game.Direction
import dev.matheus.snakeman.ui.theme.SnakeGreen
import dev.matheus.snakeman.ui.theme.SurfaceCard
import kotlin.math.abs

/**
 * D-pad grande em cruz, desenhado como uma peça única para o polegar.
 * Cada braço mede [armSize], então a peça inteira ocupa três braços.
 */
@Composable
fun DirectionPad(
    onDirection: (Direction) -> Unit,
    modifier: Modifier = Modifier,
    armSize: Dp = 54.dp
) {
    val total = armSize * 3
    val corner = armSize * 0.24f
    val arrowSize = with(LocalDensity.current) { (armSize * 0.34f).toSp() }
    val outline = MaterialTheme.colorScheme.outline

    Box(modifier.size(total)) {
        Canvas(Modifier.fillMaxSize()) {
            val arm = size.width / 3f
            val radius = arm * 0.24f
            val horizontal = Path().apply {
                addRoundRect(RoundRect(Rect(0f, arm, size.width, arm * 2f), CornerRadius(radius)))
            }
            val vertical = Path().apply {
                addRoundRect(RoundRect(Rect(arm, 0f, arm * 2f, size.height), CornerRadius(radius)))
            }
            val cross = Path().apply { op(horizontal, vertical, PathOperation.Union) }
            drawPath(cross, SurfaceCard)
            drawPath(cross, outline, style = Stroke(width = 1.dp.toPx()))
        }

        PadZone(
            label = "▲",
            direction = Direction.UP,
            onDirection = onDirection,
            size = armSize,
            fontSize = arrowSize,
            shape = RoundedCornerShape(topStart = corner, topEnd = corner),
            modifier = Modifier.align(Alignment.TopCenter)
        )
        PadZone(
            label = "▼",
            direction = Direction.DOWN,
            onDirection = onDirection,
            size = armSize,
            fontSize = arrowSize,
            shape = RoundedCornerShape(bottomStart = corner, bottomEnd = corner),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        PadZone(
            label = "◀",
            direction = Direction.LEFT,
            onDirection = onDirection,
            size = armSize,
            fontSize = arrowSize,
            shape = RoundedCornerShape(topStart = corner, bottomStart = corner),
            modifier = Modifier.align(Alignment.CenterStart)
        )
        PadZone(
            label = "▶",
            direction = Direction.RIGHT,
            onDirection = onDirection,
            size = armSize,
            fontSize = arrowSize,
            shape = RoundedCornerShape(topEnd = corner, bottomEnd = corner),
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun PadZone(
    label: String,
    direction: Direction,
    onDirection: (Direction) -> Unit,
    size: Dp,
    fontSize: TextUnit,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .clickable { onDirection(direction) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = SnakeGreen,
            fontSize = fontSize,
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
