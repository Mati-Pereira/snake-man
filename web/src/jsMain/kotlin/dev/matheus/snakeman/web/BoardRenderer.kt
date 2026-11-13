package dev.matheus.snakeman.web

import dev.matheus.snakeman.game.Apple
import dev.matheus.snakeman.game.Cell
import dev.matheus.snakeman.game.GameSnapshot
import dev.matheus.snakeman.game.SnakeEngine
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/**
 * Desenha o tabuleiro em Canvas 2D, reproduzindo o [Board] do Compose:
 * mesmas cores, mesma geometria e o mesmo pulso da maçã.
 */
class BoardRenderer(private val canvas: HTMLCanvasElement) {

    private val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    private var size = 0.0

    fun resize(cssSize: Double) {
        val dpr = window.devicePixelRatio.takeIf { it > 0.0 } ?: 1.0
        size = cssSize
        canvas.width = (cssSize * dpr).toInt()
        canvas.height = (cssSize * dpr).toInt()
        ctx.setTransform(dpr, 0.0, 0.0, dpr, 0.0, 0.0)
    }

    fun render(snapshot: GameSnapshot, cols: Int, rows: Int, timeMs: Double) {
        if (size <= 0.0) return

        val cellW = size / cols
        val cellH = size / rows
        val cell = min(cellW, cellH)
        val pulse = applePulse(timeMs)

        ctx.fillStyle = BOARD_BG
        ctx.fillRect(0.0, 0.0, size, size)
        drawGrid(cellW, cellH, cols, rows, cell)

        snapshot.apple?.let { drawApple(it, cellW, cellH, cell, pulse) }

        // Cauda primeiro, cabeça por último.
        snapshot.snake.asReversed().forEachIndexed { reversedIndex, position ->
            val index = snapshot.snake.lastIndex - reversedIndex
            val progress = if (snapshot.snake.size > 1) {
                index / (snapshot.snake.size - 1).toDouble()
            } else {
                0.0
            }
            drawSegment(position, snakeColor(progress), cellW, cellH, cell, isHead = index == 0)
        }

        snapshot.snake.firstOrNull()?.let { head ->
            drawEyes(head, snapshot.direction.dx, snapshot.direction.dy, cellW, cellH, cell)
        }
    }

    private fun drawGrid(cellW: Double, cellH: Double, cols: Int, rows: Int, cell: Double) {
        ctx.strokeStyle = "rgba(23, 40, 61, 0.55)"
        ctx.lineWidth = 1.0
        for (x in 1 until cols) {
            ctx.beginPath()
            ctx.moveTo(x * cellW, 0.0)
            ctx.lineTo(x * cellW, size)
            ctx.stroke()
        }
        for (y in 1 until rows) {
            ctx.beginPath()
            ctx.moveTo(0.0, y * cellH)
            ctx.lineTo(size, y * cellH)
            ctx.stroke()
        }
        val border = cell * 0.06
        ctx.strokeStyle = BOARD_GRID
        ctx.lineWidth = 1.5
        ctx.strokeRect(border, border, size - border * 2, size - border * 2)
    }

    private fun drawApple(apple: Apple, cellW: Double, cellH: Double, cell: Double, pulse: Double) {
        val centerX = (apple.cell.x + 0.5) * cellW
        val centerY = (apple.cell.y + 0.5) * cellH
        if (apple.isGolden) {
            drawGoldenApple(apple, centerX, centerY, cell, pulse)
        } else {
            circle(centerX, centerY, cell * 0.48, "rgba(255, 90, 95, 0.16)")
            circle(centerX, centerY, cell * 0.30 * pulse, APPLE_RED)
            circle(centerX - cell * 0.09, centerY - cell * 0.10, cell * 0.07, "rgba(255, 255, 255, 0.45)")
            circle(centerX + cell * 0.13, centerY - cell * 0.28, cell * 0.09, SNAKE_GREEN)
        }
    }

    private fun drawGoldenApple(apple: Apple, centerX: Double, centerY: Double, cell: Double, pulse: Double) {
        val remaining = apple.ticksLeft ?: SnakeEngine.GOLDEN_LIFETIME_TICKS
        val fraction = (remaining.toDouble() / SnakeEngine.GOLDEN_LIFETIME_TICKS).coerceIn(0.0, 1.0)

        circle(centerX, centerY, cell * 0.55, "rgba(255, 201, 60, ${0.10 + 0.12 * fraction})")
        circle(centerX, centerY, cell * 0.32 * pulse, GOLD_DEEP)
        circle(centerX, centerY, cell * 0.26 * pulse, GOLD)
        circle(centerX - cell * 0.09, centerY - cell * 0.10, cell * 0.07, "rgba(255, 255, 255, 0.75)")

        val ringRadius = cell * 0.40
        ctx.strokeStyle = "rgba(255, 201, 60, 0.28)"
        ctx.lineWidth = cell * 0.07
        ctx.beginPath()
        ctx.arc(centerX, centerY, ringRadius, 0.0, PI * 2)
        ctx.stroke()

        ctx.strokeStyle = GOLD
        ctx.beginPath()
        ctx.arc(centerX, centerY, ringRadius, -PI / 2, -PI / 2 + PI * 2 * fraction)
        ctx.stroke()
    }

    private fun drawSegment(
        position: Cell,
        color: String,
        cellW: Double,
        cellH: Double,
        cell: Double,
        isHead: Boolean
    ) {
        val gap = cell * 0.07
        val x = position.x * cellW + gap
        val y = position.y * cellH + gap
        val width = cellW - gap * 2
        val height = cellH - gap * 2
        val radius = cell * 0.30

        roundRect(x, y, width, height, radius, color)

        if (isHead) {
            roundRect(
                x = x + width * 0.14,
                y = y + height * 0.10,
                width = width * 0.72,
                height = height * 0.22,
                radius = radius,
                color = "rgba(255, 255, 255, 0.18)"
            )
        }
    }

    private fun drawEyes(head: Cell, dx: Int, dy: Int, cellW: Double, cellH: Double, cell: Double) {
        val centerX = (head.x + 0.5) * cellW
        val centerY = (head.y + 0.5) * cellH
        val forwardX = dx.toDouble()
        val forwardY = dy.toDouble()
        val perpendicularX = -forwardY
        val perpendicularY = forwardX
        val eyeRadius = cell * 0.085
        val forwardOffset = cell * 0.16
        val sideOffset = cell * 0.19

        for (sign in listOf(-1.0, 1.0)) {
            val eyeX = centerX + forwardX * forwardOffset + perpendicularX * (sideOffset * sign)
            val eyeY = centerY + forwardY * forwardOffset + perpendicularY * (sideOffset * sign)
            circle(eyeX, eyeY, eyeRadius, EYE)
        }
    }

    private fun circle(centerX: Double, centerY: Double, radius: Double, color: String) {
        ctx.fillStyle = color
        ctx.beginPath()
        ctx.arc(centerX, centerY, radius, 0.0, PI * 2)
        ctx.fill()
    }

    private fun roundRect(x: Double, y: Double, width: Double, height: Double, radius: Double, color: String) {
        val r = min(radius, min(width, height) / 2)
        ctx.fillStyle = color
        ctx.beginPath()
        ctx.moveTo(x + r, y)
        ctx.lineTo(x + width - r, y)
        ctx.quadraticCurveTo(x + width, y, x + width, y + r)
        ctx.lineTo(x + width, y + height - r)
        ctx.quadraticCurveTo(x + width, y + height, x + width - r, y + height)
        ctx.lineTo(x + r, y + height)
        ctx.quadraticCurveTo(x, y + height, x, y + height - r)
        ctx.lineTo(x, y + r)
        ctx.quadraticCurveTo(x, y, x + r, y)
        ctx.closePath()
        ctx.fill()
    }

    /** Mesmo vai-e-vem do Compose: 0,86 ↔ 1,0 em 620 ms por trecho. */
    private fun applePulse(timeMs: Double): Double {
        val cyc = (timeMs % 1240.0) / 1240.0
        return 0.86 + 0.14 * (0.5 - 0.5 * cos(cyc * PI * 2))
    }

    private fun snakeColor(progress: Double): String {
        fun channel(from: Int, to: Int): Int = (from + (to - from) * progress).toInt()
        return "rgb(${channel(0x7C, 0x2E)}, ${channel(0xE3, 0x9E)}, ${channel(0x8B, 0x5B)})"
    }

    private companion object {
        const val BOARD_BG = "#0F1B2A"
        const val BOARD_GRID = "#17283D"
        const val APPLE_RED = "#FF5A5F"
        const val SNAKE_GREEN = "#4CD964"
        const val GOLD = "#FFC93C"
        const val GOLD_DEEP = "#E09A12"
        const val EYE = "#07131F"
    }
}
