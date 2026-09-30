/**
 * 浮层文字颜色自适应：按「浮层除文字外的实际渲染表面」的亮度选择白字/深字。
 *
 * 取色规则：**默认白字**，只有表面确实很亮时才用深字。
 * 玻璃是半透明的，压暗后的表面通常落在中灰区间（实测城市胶囊与 toast 表面约
 * 0.55 上下），这类表面配白字对比度更好、也符合玻璃控件的观感。因此阈值取
 * [LUMINANCE_THRESHOLD] = 0.62，而不是朴素的 0.5——0.5 会把中灰表面判成
 * 「亮表面」而给出黑字，正是「白字一闪变成黑字」的成因。
 *
 * 滞回：[LUMINANCE_HYSTERESIS] 内的亮度不再改变已有结论。表面亮度恰在阈值附近时
 * 两次测量会给出相反答案（实测 0.467 vs 0.527 跨越 0.5），没有滞回就会来回跳色。
 *
 * 取样对象是**浮层自身渲染出的表面**：把文字临时置为透明后把玻璃绘制到软件位图，
 * 统计中心区域不透明像素的平均亮度。
 * - toast：显示前先置透明，布局完成后延时测量再揭晓，保证首帧就是正确颜色；
 * - 常驻控件（如顶部城市胶囊）：没有入场时序，同帧测量并立即应用（见
 *   [adaptTextColorToSurface] 的重载），用户看不到中间态。
 */
package com.liuli.weather.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.TextView
import com.example.liquidglass.LiquidGlassToast
import com.example.liquidglass.LiquidGlassView

object GlassTextTone {

    private const val TAG = "GlassTextTone"

    /** 表面亮度判定阈值：高于此值视为很亮的表面（用深字），否则用白字 */
    private const val LUMINANCE_THRESHOLD = 0.62f

    /** 滞回带宽：亮度落在阈值 ± 该值内时维持既有结论，避免来回跳色 */
    private const val LUMINANCE_HYSTERESIS = 0.08f

    /** 揭晓延迟：大约在入场动画中段测量并显示文字 */
    private const val REVEAL_DELAY_MS = 160L

    /** 复测延迟：入场完成、自适应染色稳定后复核 */
    private const val SETTLE_DELAY_MS = 340L

    private const val DARK_TEXT = 0xE6000000.toInt()
    private const val LIGHT_TEXT = 0xF2FFFFFF.toInt()

    /** 文字临时置为完全透明用的颜色（测量期间使用，同帧内即被替换） */
    private const val TRANSPARENT = 0x00000000

    /**
     * 按亮度与「当前已选颜色」决定最终颜色（带滞回）。
     * @param current 当前颜色，null 表示首次判定
     */
    /**
     * 按亮度与「当前已选颜色」决定最终颜色（带滞回）。
     *
     * @param current 当前颜色。只有恰好等于本类产出的两种色值时才参与滞回；
     *   其它值（XML 里配的初始色、库自带配色）一律视为「首次判定」，
     *   否则首次决定会走 H+滞回 的更严阈值，与 toast 的首判不一致。
     */
    private fun pickTone(luminance: Float, current: Int?): Int = when (current) {
        DARK_TEXT ->
            if (luminance < LUMINANCE_THRESHOLD - LUMINANCE_HYSTERESIS) LIGHT_TEXT else DARK_TEXT
        LIGHT_TEXT ->
            if (luminance > LUMINANCE_THRESHOLD + LUMINANCE_HYSTERESIS) DARK_TEXT else LIGHT_TEXT
        else -> if (luminance > LUMINANCE_THRESHOLD) DARK_TEXT else LIGHT_TEXT
    }

    // ------------------------------------------------------------------ toast

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
                val lum = surfaceLuminance(glass)
                if (lum != null) {
                    val tone = pickTone(lum, lastTone)
                    if (!revealed) {
                        revealed = true
                        reveal(tone)
                    } else {
                        applyTone(tone) // 仅当越过滞回带时才改色
                    }
                } else if (!revealed) {
                    revealed = true
                    reveal(null) // 兜底：交回库的自动配色
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

    // ------------------------------------------------------------- 常驻控件

    /**
     * 给常驻玻璃控件上的文字做同样的自适应（顶部城市胶囊等）。
     *
     * 与 toast 不同，控件文字始终可见，所以不能靠切 visibility 来测量——那会闪。
     * 这里把文字颜色临时置为完全透明，测量后立即写回目标色：整段是同步完成的，
     * 期间不产生新的绘制帧，用户不会看到中间态。
     */
    fun adaptTextColorToSurface(glass: LiquidGlassView, textView: TextView) {
        if (glass.width <= 0 || glass.height <= 0) return
        val restore = textView.currentTextColor
        textView.setTextColor(TRANSPARENT)
        val lum = surfaceLuminance(glass)
        if (lum == null) {
            textView.setTextColor(restore)
            return
        }
        val tone = pickTone(lum, restore)
        textView.setTextColor(tone)
    }

    // ---------------------------------------------------------------- 测量

    /**
     * 渲染玻璃表面（软件画布）并返回平均亮度；未就绪或全部透明时返回 null。
     * 调用方需保证此刻玻璃上没有会被计入的文字/图标（toast 用 visibility，
     * 常驻控件用透明字色）。
     */
    private fun surfaceLuminance(glass: LiquidGlassView): Float? {
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
            if (n == 0) null else (lum / n).toFloat()
        } catch (e: Exception) {
            Log.w(TAG, "surfaceLuminance failed: ${e.message}")
            null
        }
    }
}
