package com.liuli.weather.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * 每日预报里的温度区间条：轨道为当日温度范围在整周范围中的位置。
 */
class TempRangeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2EFFFFFF
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val trackRect = RectF()
    private val fillRect = RectF()

    private var globalMin = 0.0
    private var globalMax = 40.0
    private var valueMin = 0.0
    private var valueMax = 0.0

    fun setRange(globalMin: Double, globalMax: Double, valueMin: Double, valueMax: Double) {
        this.globalMin = globalMin
        this.globalMax = max(globalMax, globalMin + 0.1)
        this.valueMin = valueMin
        this.valueMax = valueMax
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val w = width.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = h / 2f
        trackRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        // iOS 风格温度渐变：低温绿 -> 中温黄 -> 高温橙
        fillPaint.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(0xFF63D471.toInt(), 0xFFFFD54F.toInt(), 0xFFFF9F45.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        val span = (globalMax - globalMin).coerceAtLeast(0.1)
        var start = ((valueMin - globalMin) / span).coerceIn(0.0, 1.0).toFloat()
        var end = ((valueMax - globalMin) / span).coerceIn(0.0, 1.0).toFloat()
        if (end - start < 0.06f) {
            val mid = (start + end) / 2f
            start = (mid - 0.03f).coerceAtLeast(0f)
            end = (mid + 0.03f).coerceAtMost(1f)
        }
        fillRect.set(start * w, 0f, end * w, h)
        canvas.drawRoundRect(fillRect, radius, radius, fillPaint)
    }
}
