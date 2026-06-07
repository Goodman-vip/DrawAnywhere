package com.shezik.drawanywhere.view

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.DecelerateInterpolator

class DismissTargetView(context: Context) : View(context) {

    companion object {
        const val SIZE_DP = 80f
        const val BOTTOM_OFFSET_DP = 48f
        private const val ACTIVE_DURATION_MS = 150L

        private const val CIRCLE_IDLE = (0x99 shl 24) or 0x333333
        private const val CIRCLE_ACTIVE = (0xDD shl 24) or 0xE53935
        private const val X_COLOR = 0xFF_FFFFFF.toInt()
        private const val SHADOW_COLOR = 0x30_000000
    }

    private val density = resources.displayMetrics.density
    private val colorEval = ArgbEvaluator()

    private val circlePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        setShadowLayer(3f * density, 0f, 2f * density, SHADOW_COLOR)
    }

    private val xPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = X_COLOR
        strokeWidth = 4f * density
        isAntiAlias = true
    }

    private var activeFraction = 0f
        set(value) { field = value; invalidate() }
    private var activeAnimator: ValueAnimator? = null

    var active: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            animateActive(if (value) 1f else 0f)
        }

    fun show() {
        animate().cancel()
        if (visibility != VISIBLE) {
            snapToCurrent()
            visibility = VISIBLE
            alpha = 0f
            scaleX = 0.5f
            scaleY = 0.5f
            animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(200L).start()
        }
    }

    fun hide() {
        animate().cancel()
        animate()
            .alpha(0f).setDuration(150L)
            .withEndAction { visibility = GONE }
            .start()
    }

    fun snapToCurrent() {
        activeAnimator?.cancel()
        activeAnimator = null
        activeFraction = if (active) 1f else 0f
    }

    private fun animateActive(target: Float) {
        activeAnimator?.cancel()
        activeAnimator = ValueAnimator.ofFloat(activeFraction, target).apply {
            duration = ACTIVE_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { activeFraction = animatedValue as Float }
            start()
        }
    }

    fun containsScreenPoint(screenX: Int, screenY: Int): Boolean {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return screenX in loc[0]..(loc[0] + width) && screenY in loc[1]..(loc[1] + height)
    }

    override fun onDraw(canvas: Canvas) {
        val f = activeFraction
        val r = (28f + 8f * f) * density
        val cx = width / 2f
        val cy = height / 2f

        circlePaint.color = colorEval.evaluate(f, CIRCLE_IDLE, CIRCLE_ACTIVE) as Int
        canvas.drawCircle(cx, cy, r, circlePaint)

        val xPad = r * 0.35f
        canvas.drawLine(cx - xPad, cy - xPad, cx + xPad, cy + xPad, xPaint)
        canvas.drawLine(cx + xPad, cy - xPad, cx - xPad, cy + xPad, xPaint)
    }
}
