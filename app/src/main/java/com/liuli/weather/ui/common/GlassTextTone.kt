/**
 * 浮层文字颜色自适应：按「toast 除文字外的实际渲染表面」的亮度选择深字/白字。
 *
 * 文字坐在玻璃表面上，而玻璃表面 = 模糊 + 自适应染色之后的成品——浅色背景会被
 * 自适应染色压暗，所以按原始背景亮度选色是错的。正确做法：等浮层渲染完成后，
 * 隐藏文字/图标，实测玻璃表面的平均亮度，再设定文字颜色并显示。
 *
 * 时序：显示 → 文字先隐藏 → 入场动画完成（320ms）后采样玻璃表面 → 设色 → 文字出现。
 * 期间用 setTransitionVisibility（不触发 invalidate），避免逐帧重采样循环。
 */
package com.liuli.weather.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.View
import com.example.liquidglass.LiquidGlassToast

object GlassTextTone {

    /** 表面亮度判定阈值：高于此值视为亮底（用深字），否则暗底（用白字） */
    private const val LUMINANCE_THRESHOLD = 0.5f

    /** 文字隐藏的时长 = 入场动画完成后再采样 */
    private const val MEASURE_DELAY_MS = 320L

    /**
     * 按浮层自身渲染表面（除文字/图标外）的亮度设定文字颜色。
     * 深色表面 → 白字；亮色表面 → 深字。测量在入场动画完成后进行。
     */
    fun adaptTextColorToSurface(toast: LiquidGlassToast) {
        val glass = toast.glass
        val tv = toast.textView
        val iv = toast.imageView

        // 文字先隐藏：等待表面渲染完成，避免出现中间态颜色
        tv.visibility = View.INVISIBLE
        iv.visibility = View.INVISIBLE

        val measure = Runnable {
            try {
                val w = maxOf(glass.width, 1)
                val h = maxOf(glass.height, 1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                // 直接绘制玻璃自身：软件画布走 CPU 管线，输出即用户看到的表面
                glass.draw(Canvas(bmp))

                // 只统计中心区域的不透明像素（避开圆角外的透明区与边缘高光）
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

                if (n > 0) {
                    val surfaceLum = lum / n
                    toast.setTextColor(
                        if (surfaceLum > LUMINANCE_THRESHOLD) 0xE6000000.toInt() else 0xF2FFFFFF.toInt()
                    )
                }
            } catch (e: Exception) {
                Log.w("GlassTextTone", "surface adapt failed: ${e.message}")
            } finally {
                tv.visibility = View.VISIBLE
                iv.visibility = View.VISIBLE
            }
        }
        glass.postDelayed(measure, MEASURE_DELAY_MS)
    }
}
