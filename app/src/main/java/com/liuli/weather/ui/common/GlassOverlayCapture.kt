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

    /** 给浮层玻璃挂内容实时采样；重复调用安全（重复设置仅覆盖回调） */
    fun attach(glass: LiquidGlassView, contentHost: View) {
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
            val bmp = try {
                captureRegion(
                    contentHost,
                    glass.left.toFloat(), glass.top.toFloat(),
                    glass.width.toFloat(), glass.height.toFloat()
                )
            } finally {
                if (wasVisible) glass.setTransitionVisibility(View.VISIBLE)
                capturing = false
            }
            bmp ?: return@setCustomBackdropCapture null
            // bounds 为玻璃局部坐标（0,0,w,h），与截取区域一一对应，整幅返回
            val x = bounds.left.toInt().coerceIn(0, bmp.width - 2)
            val y = bounds.top.toInt().coerceIn(0, bmp.height - 2)
            val w = minOf(bounds.width().toInt(), bmp.width - x).coerceAtLeast(1)
            val h = minOf(bounds.height().toInt(), bmp.height - y).coerceAtLeast(1)
            if (x == 0 && y == 0 && w == bmp.width && h == bmp.height) {
                bmp
            } else {
                Bitmap.createBitmap(bmp, x, y, w, h)
            }
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

    /** 软件绘制 host 的指定区域到独立位图（像素，无节点引用） */
    private fun captureRegion(host: View, left: Float, top: Float, width: Float, height: Float): Bitmap? {
        val w = width.toInt()
        val h = height.toInt()
        if (w <= 0 || h <= 0) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(-left, -top)
        host.draw(canvas)
        return bmp
    }
}
