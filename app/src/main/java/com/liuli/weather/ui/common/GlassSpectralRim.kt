package com.liuli.weather.ui.common

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import com.example.liquidglass.LiquidGlassView

/**
 * 装饰性光谱描边：玻璃色散效果的 iOS 式安全实现。
 *
 * 库内 dispersionStrength 的色散是"边缘加权"的通道分离（着色器里
 * offset * (1 ± dispersion * slope)，slope 在边缘为 1、内部为 0），红蓝通道
 * 的采样恰好全部错开在边缘上；玻璃贴着高对比背景（亮云/深色卡片交界）时，
 * 两通道从明暗不同的位置取色，边缘必然出现橙红色分光带——这是该设计固有的，
 * 与取值大小无关。
 *
 * 这里把"色散感"改为纯装饰：沿玻璃边缘画一道随角度渐变的微弱光谱描边，
 * 迎光位（左上）偏暖、背光位（右下）偏冷，模拟棱镜对镜面高光的分光；
 * 内容本身不做任何通道分离，任何背景下都不会出现彩色边纹。
 */
object GlassSpectralRim {

    /** 给一批玻璃视图挂上光谱描边（重复调用安全，已挂载的跳过）。 */
    fun attach(vararg views: LiquidGlassView) {
        views.forEach { view ->
            if (view.foreground == null) {
                view.foreground = RimDrawable(view.cornerRadius)
            }
        }
    }

    private class RimDrawable(cornerRadiusPx: Float) : Drawable() {

        private val cornerRadius = cornerRadiusPx
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        private val rect = RectF()

        // SweepGradient 0° 在 +x（右）方向、顺时针：
        // 45°=右下（背光位，冷色），225°=左上（迎光位，暖色），其余透明。
        // alpha 直接编码在颜色里（约 10-12%），叠加在库内的白色高光之上
        private val colors = intArrayOf(
            0x00000000,          // 0°    右
            0x1A96C4F2.toInt(),  // 45°   右下：背光位冷光
            0x00000000,          // 81°   渐出
            0x00000000,          // 135°  左下
            0x00000000,          // 180°  左
            0x00000000,          // 196°  渐入
            0x1DF0C9A2.toInt(),  // 225°  左上：迎光位暖光
            0x00000000,          // 270°  上
            0x00000000           // 360°
        )
        private val positions = floatArrayOf(
            0f, 0.125f, 0.225f, 0.375f, 0.5f, 0.545f, 0.625f, 0.75f, 1f
        )

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            if (!bounds.isEmpty) {
                paint.shader = SweepGradient(
                    bounds.exactCenterX(), bounds.exactCenterY(), colors, positions
                )
            }
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty || paint.shader == null) return
            val inset = 1.0f
            val half = minOf(b.width(), b.height()) / 2f
            val r = minOf(cornerRadius, half) - inset
            if (r <= 0f) return
            rect.set(inset, inset, b.width() - inset, b.height() - inset)
            canvas.drawRoundRect(rect, r, r, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun getAlpha(): Int = paint.alpha

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
