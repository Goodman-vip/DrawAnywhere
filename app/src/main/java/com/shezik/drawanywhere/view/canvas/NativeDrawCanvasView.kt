/*
DrawAnywhere: An Android application that lets you draw on top of other apps.
Copyright (C) 2025 shezik

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.shezik.drawanywhere.view.canvas

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.shezik.drawanywhere.DrawController
import com.shezik.drawanywhere.DrawViewModel
import com.shezik.drawanywhere.PathWrapper
import com.shezik.drawanywhere.StrokeModifier
import androidx.compose.ui.graphics.toArgb

class NativeDrawCanvasView(
    context: Context,
    private val drawController: DrawController,
    private val viewModel: DrawViewModel,
) : View(context) {

    var isPassthrough: Boolean = false

    /** Called by external code when paths change outside of touch events
     *  (e.g., undo/redo/clear from toolbar). */
    var onPathsChanged: (() -> Unit)? = null

    private var activePointerId: Int = -1
    private var strokeInProgress: Boolean = false

    // Reusable paint object for path rendering
    private val pathPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // No background fill — overlay window is already TRANSLUCENT.

        for (pw in drawController.pathList) {
            if (pw.points.isEmpty()) continue

            val androidPath = buildAndroidPath(pw.points)

            pathPaint.strokeWidth = pw.width
            // Combine color's intrinsic alpha with PathWrapper-level opacity.
            // Paint.setAlpha() replaces (not multiplies) the color's alpha channel,
            // so we pre-combine them into the ARGB color.
            val colorArgb = pw.color.toArgb()
            val colorAlpha = pw.color.alpha  // 0.0f..1.0f
            val combinedAlpha = (colorAlpha * pw.alpha * 255).toInt().coerceIn(0, 255)
            pathPaint.color = (colorArgb and 0x00FFFFFF) or (combinedAlpha shl 24)

            canvas.drawPath(androidPath, pathPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isPassthrough) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)

                val modifier = detectStrokeModifier(event)
                viewModel.startStroke(
                    point = androidx.compose.ui.geometry.Offset(event.x, event.y),
                    modifier = modifier
                )
                strokeInProgress = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!strokeInProgress) return false

                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                // Process historical points first for smoother strokes
                for (i in 0 until event.historySize) {
                    viewModel.updateStroke(
                        androidx.compose.ui.geometry.Offset(
                            event.getHistoricalX(pointerIndex, i),
                            event.getHistoricalY(pointerIndex, i)
                        )
                    )
                }
                // Process current point
                viewModel.updateStroke(
                    androidx.compose.ui.geometry.Offset(
                        event.getX(pointerIndex),
                        event.getY(pointerIndex)
                    )
                )
                invalidate()
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                strokeInProgress = false
                activePointerId = -1
                viewModel.finishStroke()
                invalidate()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Ignore additional pointers for now.
                // Future: multi-touch zoom will be handled here.
            }
        }
        return true
    }

    private fun detectStrokeModifier(event: MotionEvent): StrokeModifier {
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return StrokeModifier.None
        }
        val buttons = event.buttonState
        val primary = (buttons and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        val secondary = (buttons and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
        return when {
            primary && secondary -> StrokeModifier.Both
            primary -> StrokeModifier.PrimaryButton
            secondary -> StrokeModifier.SecondaryButton
            else -> StrokeModifier.None
        }
    }

    private fun buildAndroidPath(points: List<androidx.compose.ui.geometry.Offset>): Path {
        val p = Path()
        if (points.isEmpty()) return p

        val first = points.first()
        p.moveTo(first.x, first.y)

        points.zipWithNext().forEachIndexed { index, (start, end) ->
            val midX = (start.x + end.x) / 2f
            val midY = (start.y + end.y) / 2f
            if (index == 0) {
                p.lineTo(midX, midY)
            } else {
                p.quadTo(start.x, start.y, midX, midY)
            }
        }
        val last = points.last()
        p.lineTo(last.x, last.y)

        return p
    }
}
