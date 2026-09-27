/**
 * 浮层玻璃的内容实时采样。
 *
 * 背景：玻璃的 backdropSource 指向包含其他玻璃的视图（content view / mainRoot）时，
 * 录制显示列表会引用其他玻璃的 RenderNode，彼此形成引用成环；交互触发重录后，
 * RenderThread 光栅化沿环无限递归（computeTransformImpl 512 帧），栈溢出闪退。
 *
 * 解法：用 setCustomBackdropCapture 把采样切换成软件位图——每次绘制时把浮层所在
 * 区域现场绘成位图（软件路径 isCapturingBackdrop 能拦住嵌套玻璃；浮层自身在捕获
 * 期间临时 setTransitionVisibility(INVISIBLE)，既不入镜也不触发 invalidate）。
 * 位图没有节点引用，结构上不成环，同时折射始终跟随当前滚动/内容状态，无残影。
 * 浮层自身因此回退 CPU 位图管线，小浮层短时显示，开销可忽略。
 */
package com.liuli.weather.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewTreeObserver
import com.example.liquidglass.LiquidGlassView

object GlassOverlayCapture {

    /**
     * 给浮层玻璃挂内容实时采样；重复调用安全（重复设置仅覆盖回调）。
     *
     * @param downsample 采样降尺寸比例 (0-1]：软件模糊的半径被钳制在 25px，
     *   小尺寸捕获再拉伸回去可以获得更强的等效模糊（0.35 ≈ 3 倍等效模糊半径）
     */
    fun attach(glass: LiquidGlassView, contentHost: View, downsample: Float = 0.35f) {
        var capturing = false

        glass.setCustomBackdropCapture { bounds ->
            // 绘制中或未布局时无法采样，退回管线自带的白色兜底（仅出现在首帧前）
            if (capturing || !glass.isLaidOut || glass.width <= 0) {
                return@setCustomBackdropCapture null
            }
            // 现场重截：折射必须反映当前滚动/内容状态，否则出现旧内容残影
            capturing = true
            val wasVisible = glass.visibility == View.VISIBLE
            if (wasVisible) glass.setTransitionVisibility(View.INVISIBLE)
            // 捕获区域按模糊半径外扩（与库内 enableOptimizedCapture 同思路）：
            // 高斯模糊在边缘会向外取样，若按精确边界截取，边缘像素会被反复钳制
            // 形成脏边；外扩后模糊取到的是真实内容，结果拉伸回浮层边界即可
            val blurRadius = 4f + glass.blurAmount * 32f
            val margin = blurRadius * 2f
            val bmp = try {
                captureRegion(
                    contentHost,
                    glass.left - margin, glass.top - margin,
                    glass.width + 2f * margin, glass.height + 2f * margin,
                    downsample
                )
            } finally {
                if (wasVisible) glass.setTransitionVisibility(View.VISIBLE)
                capturing = false
            }
            bmp
        }

        // 内容滚动时重绘玻璃：像素采样必须跟随内容位移，否则出现残影。
        // OnScrollChangedListener 挂在窗口级 ViewTreeObserver 上，任意视图滚动都会到达；
        // 随浮层 attach/detach 注册与注销
        val scrollListener = ViewTreeObserver.OnScrollChangedListener { glass.invalidate() }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.viewTreeObserver.addOnScrollChangedListener(scrollListener)
            }

            override fun onViewDetachedFromWindow(v: View) {
                v.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
            }
        }
        glass.addOnAttachStateChangeListener(attachListener)
        if (glass.isAttachedToWindow) {
            glass.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        }
    }

    /** 软件绘制 host 的指定区域到独立位图（像素，无节点引用）；scale<1 时在缩小的画布上绘制 */
    private fun captureRegion(
        host: View,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float = 1f
    ): Bitmap? {
        val w = width.toInt()
        val h = height.toInt()
        if (w <= 0 || h <= 0) return null
        val s = scale.coerceIn(0.05f, 1f)
        val bmp = Bitmap.createBitmap((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (s < 1f) canvas.scale(s, s)
        canvas.translate(-left, -top)
        host.draw(canvas)
        return bmp
    }
}
