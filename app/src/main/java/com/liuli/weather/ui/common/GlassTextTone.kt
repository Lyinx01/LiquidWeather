/**
 * 浮层文字颜色自适应：按「文字正后方那一层」的实际亮度选择深字/白字。
 *
 * 库的 LiquidGlassToast 自适应采样的是整个背景画面（含浮层未覆盖的区域），
 * 且亮度计异步采样首帧前默认白字——造成定位偏差与「白→黑」闪变。
 * 这里改为同步测量 toast 所在位置那一小块区域的实际亮度，首帧即正确。
 */
package com.liuli.weather.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View

object GlassTextTone {

    /** 测量 host 指定区域的平均亮度（0-1） */
    fun measureRegionLuminance(host: View, left: Float, top: Float, width: Float, height: Float): Double {
        val w = width.toInt().coerceAtLeast(1)
        val h = height.toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(-left, -top)
        host.draw(canvas)
        val small = Bitmap.createScaledBitmap(bmp, 8, 8, true)
        var lum = 0.0
        var n = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val p = small.getPixel(x, y)
                if ((p ushr 24) and 0xFF < 8) continue
                lum += (0.2126 * Color.red(p) + 0.7152 * Color.green(p) + 0.0722 * Color.blue(p)) / 255.0
                n++
            }
        }
        bmp.recycle()
        small.recycle()
        return if (n == 0) 0.5 else (lum / n).coerceIn(0.0, 1.0)
    }

    /**
     * 按浮层后方内容的实际亮度返回建议文字颜色：
     * 亮底（天空等）→ 深色文字；暗底（深色面板等）→ 白色文字。
     */
    fun suggestTextColor(host: View, left: Float, top: Float, width: Float, height: Float): Int {
        return if (measureRegionLuminance(host, left, top, width, height) > 0.55) {
            0xE6000000.toInt()
        } else {
            0xF2FFFFFF.toInt()
        }
    }
}
