package net.akehurst.kotlin.compose.components.table

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * A shape that draws a border on selected sides with rounded corners.
 *
 * @param cornerRadius The radius for all corners.
 * @param hasTop Whether to draw the top side.
 * @param hasBottom Whether to draw the bottom side.
 * @param hasLeft Whether to draw the start (left) side.
 * @param hasRight Whether to draw the end (right) side.
 */
class SelectiveRoundedCornerShape(
    private val cornerRadius: Dp,
    private val hasTop: Boolean = true,
    private val hasBottom: Boolean = true,
    private val hasLeft: Boolean = true,
    private val hasRight: Boolean = true,
    private val topLeftCurved: Boolean = true,
    private val topRightCurved: Boolean = true,
    private val bottomRightCurved: Boolean = true,
    private val bottomLeftCurved: Boolean = true,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // 1. The Safety Check: If size is zero, return an empty outline.
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect.Zero)
        }

        val radiusPx = with(density) { cornerRadius.toPx() }.coerceAtMost(size.minDimension / 2)

        val path = Path()
        // start top left,
        // the position depends on if we need an arc or not,
        // which depend s on whether we have top and left sides
        // if we have both sides draw the arc (curved corner)
        when {
            hasTop && hasLeft -> when {
                topLeftCurved -> path.moveTo( radiusPx, 0f)
                else -> path.moveTo(0f, 0f)
            }
            hasTop -> path.moveTo(0f, 0f)
            else -> Unit
        }

        // draw top and top-right corner if needed
        when {
            hasTop && hasRight -> when {
                topRightCurved -> path.arcTo(
                    rect = Rect(size.width - 2 * radiusPx, 0f, size.width, 2 * radiusPx),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )

                else -> path.lineTo(size.width, 0f)
            }

            hasTop -> path.lineTo(size.width, 0f)
            else -> path.moveTo(size.width, 0f)
        }

        //draw right and bot-right corner if needed
        when {
            hasRight && hasBottom -> when {
                bottomRightCurved -> path.arcTo(
                    rect = Rect(size.width - 2 * radiusPx, size.height - 2 * radiusPx, size.width, size.height),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )

                else -> path.lineTo(size.width, size.height)
            }

            hasRight -> path.lineTo(size.width, size.height)
            else -> path.moveTo(size.width, size.height)
        }

        //draw bottom and bot-left corner if needed
        when {
            hasBottom && hasLeft -> when {
                bottomLeftCurved -> path.arcTo(
                    rect = Rect(0f, size.height - 2 * radiusPx, 2 * radiusPx, size.height),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                else -> path.lineTo(0f, size.height)
            }

            hasBottom -> path.lineTo(0f, size.height)
            else -> path.moveTo(0f, size.height)
        }

        //draw left and top-left corner if needed
        when {
            hasLeft && hasTop -> when {
                topLeftCurved -> path.arcTo(
                    rect = Rect(0f, 0f, 2 * radiusPx, 2 * radiusPx),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                else -> path.lineTo(0f, 0f)
            }
            hasLeft -> path.lineTo(0f, 0f)
            else -> Unit
        }

        return Outline.Generic(path)
    }
}