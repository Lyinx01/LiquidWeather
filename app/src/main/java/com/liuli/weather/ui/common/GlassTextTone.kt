/**
 * 浮层文字颜色自适应：按「toast 除文字外的实际渲染表面」的亮度选择深字/白字。
 *
 * 文字坐在玻璃表面上，玻璃经自适应染色后浅背景也会被压暗——按原始背景亮度选色
 * 会偏亮（用户实测：表面偏暗，应配亮色文字）。因此取样对象是 **toast 自身渲染
 * 出的表面**：隐藏文字/图标后，把玻璃绘制到软件位图，统计中心区域不透明像素的
 * 平均亮度。
 *
 * 时序设计（兼顾「无闪变」与「文字不迟到」）：
 * 1. 显示前先隐藏文字/图标（避免默认白色一闪而过）；
 * 2. 布局完成后 160ms 测量并揭晓文字——此时入场动画（260ms）仍在进行，
 *    视觉上文字与横幅一同出现；
 * 3. 340ms（动画结束、自适应染色稳定）复测一次，仅当明暗结论翻转时改色，
 *    避免首测过早（染色未稳定）导致的偶发偏差。
 *
 * 图标可见性原样保留：无图标（imageView GONE）的 toast 不会被误显示。
 */
package com.liuli.weather.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.View
import com.example.liquidglass.LiquidGlassToast
import com.example.liquidglass.LiquidGlassView

object GlassTextTone {

    private const val TAG = "GlassTextTone"

    /** 表面亮度判定阈值：高于此值视为亮表面（用深字），否则暗表面（用白字） */
    private const val LUMINANCE_THRESHOLD = 0.5f

    /** 揭晓延迟：大约在入场动画中段测量并显示文字 */
    private const val REVEAL_DELAY_MS = 160L

    /** 复测延迟：入场完成、自适应染色稳定后复核 */
    private const val SETTLE_DELAY_MS = 340L

    private const val DARK_TEXT = 0xE6000000.toInt()
    private const val LIGHT_TEXT = 0xF2FFFFFF.toInt()

    /**
     * 给 toast 挂上「按自身表面亮度自适应文字颜色」。
     * 在 [LiquidGlassToast.show] 之前调用；文字会在测量完成后自动显示。
     */
    fun adaptTextColorToSurface(toast: LiquidGlassToast) {
        val glass = toast.glass
        val tv = toast.textView
        val iv = toast.imageView
        val hadIcon = iv.visibility == View.VISIBLE

        // 先隐藏内容（玻璃表面本身不受影响），避免默认色先闪一帧
        tv.visibility = View.INVISIBLE
        if (hadIcon) iv.visibility = View.INVISIBLE

        var lastTone: Int? = null
        var revealed = false

        fun applyTone(tone: Int) {
            if (tone == lastTone) return
            lastTone = tone
            toast.setTextColor(tone)
        }

        fun reveal(tone: Int?) {
            tone?.let(::applyTone)
            tv.visibility = View.VISIBLE
            if (hadIcon) iv.visibility = View.VISIBLE
        }

        fun poll(tries: Int) {
            if (glass.width > 0 && glass.height > 0) {
                val tone = measureTone(glass)
                if (!revealed) {
                    revealed = true
                    reveal(tone)
                } else {
                    tone?.let(::applyTone) // 仅在结论翻转时改色
                }
            } else if (tries < 20) {
                glass.postDelayed({ poll(tries + 1) }, 16L)
            } else if (!revealed) {
                // 兜底：交回库的自动配色（避免文字永久隐藏）
                revealed = true
                reveal(null)
            }
        }

        glass.postDelayed({ poll(0) }, REVEAL_DELAY_MS)  // 揭晓
        glass.postDelayed({ poll(0) }, SETTLE_DELAY_MS)  // 复核
    }

    /** 渲染玻璃表面（软件画布，文字已隐藏）并返回建议文字颜色；未就绪返回 null */
    private fun measureTone(glass: LiquidGlassView): Int? {
        return try {
            val w = glass.width
            val h = glass.height
            if (w <= 0 || h <= 0) return null
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            glass.draw(Canvas(bmp))

            // 只统计中心区域的不透明像素：避开圆角外的透明区与边缘高光
            var lum = 0.0
            var n = 0
            val x0 = (w * 0.2).toInt()
            val x1 = (w * 0.8).toInt()
            val y0 = (h * 0.25).toInt()
            val y1 = (h * 0.75).toInt()
            var y = y0
            while (y < y1) {
                var x = x0
                while (x < x1) {
                    val p = bmp.getPixel(x, y)
                    if ((p ushr 24) and 0xFF >= 200) {
                        lum += (0.2126 * Color.red(p) + 0.7152 * Color.green(p) + 0.0722 * Color.blue(p)) / 255.0
                        n++
                    }
                    x += 4
                }
                y += 3
            }
            bmp.recycle()
            if (n == 0) return null
            if (lum / n > LUMINANCE_THRESHOLD) DARK_TEXT else LIGHT_TEXT
        } catch (e: Exception) {
            Log.w(TAG, "measureTone failed: ${e.message}")
            null
        }
    }
}
