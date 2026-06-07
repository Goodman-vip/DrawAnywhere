package com.shezik.drawanywhere

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class DismissTargetView(context: Context) : View(context) {

    companion object {
        const val SIZE_DP = 80f
    }

    private val density = resources.displayMetrics.density

    private val circlePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val xPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xCC_FFFFFF.toInt()
        strokeWidth = 4f * density
        isAntiAlias = true
    }

    var overlapping: Boolean = false
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    fun containsScreenPoint(screenX: Int, screenY: Int): Boolean {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return screenX in loc[0]..(loc[0] + width) && screenY in loc[1]..(loc[1] + height)
    }

    override fun onDraw(canvas: Canvas) {
        val r = (if (overlapping) 36f else 28f) * density
        val alpha = if (overlapping) 0x99 else 0x66
        val cx = width / 2f
        val cy = height / 2f

        circlePaint.color = (0xFF_E53935.toInt() and 0x00FFFFFF) or (alpha shl 24)
        canvas.drawCircle(cx, cy, r, circlePaint)

        val xPad = r * 0.4f
        canvas.drawLine(cx - xPad, cy - xPad, cx + xPad, cy + xPad, xPaint)
        canvas.drawLine(cx + xPad, cy - xPad, cx - xPad, cy + xPad, xPaint)
    }
}
