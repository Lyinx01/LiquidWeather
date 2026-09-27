/**
 * 浮层玻璃的内容像素快照采样。
 *
 * 背景：玻璃的 backdropSource 指向包含其他玻璃的视图（content view / mainRoot）时，
 * 录制显示列表会引用其他玻璃的 RenderNode，彼此形成引用成环；交互触发重录后，
 * RenderThread 光栅化沿环无限递归（computeTransformImpl 512 帧），栈溢出闪退。
 *
 * 解法：用 setCustomBackdropCapture 把采样切换成像素位图——首次绘制时把浮层所在
 * 区域软件绘成位图（软件路径 isCapturingBackdrop 能拦住嵌套玻璃，且浮层自身在
 * 捕获期间临时 INVISIBLE），之后每帧从位图裁剪。位图没有节点引用，结构上不成环，
 * 同时保留对真实内容（卡片/文字）的折射。浮层自身因此回退 CPU 位图管线，
 * 小浮层短时显示，开销可忽略。
 */
package com.liuli.weather.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import com.example.liquidglass.LiquidGlassView

object GlassOverlayCapture {

    /** 给浮层玻璃挂内容像素快照采样；重复调用安全（重复设置仅覆盖回调） */
    fun attach(glass: LiquidGlassView, contentHost: View) {
        var snapshot: Bitmap? = null
        var capturing = false
        glass.setCustomBackdropCapture { bounds ->
            // 首帧（已布局）惰性截取浮层所在区域；捕获期间把浮层临时隐藏，
            // 既让快照里不含浮层自己，也切断「采样→自身绘制→再采样」的重入
            if (snapshot == null && !capturing && glass.isLaidOut && glass.width > 0) {
                capturing = true
                val wasVisible = glass.visibility == View.VISIBLE
                if (wasVisible) glass.visibility = View.INVISIBLE
                snapshot = try {
                    captureRegion(
                        contentHost,
                        glass.left.toFloat(), glass.top.toFloat(),
                        glass.width.toFloat(), glass.height.toFloat()
                    )
                } finally {
                    if (wasVisible) glass.visibility = View.VISIBLE
                    capturing = false
                }
            }
            val snap = snapshot ?: return@setCustomBackdropCapture null
            val x = bounds.left.toInt().coerceIn(0, snap.width - 2)
            val y = bounds.top.toInt().coerceIn(0, snap.height - 2)
            val w = minOf(bounds.width().toInt(), snap.width - x).coerceAtLeast(1)
            val h = minOf(bounds.height().toInt(), snap.height - y).coerceAtLeast(1)
            // 整幅裁剪时 createBitmap 可能原样返回源位图，而管线会回收返回值，
            // 必须复制，避免把快照本体交出去被回收
            if (w == snap.width && h == snap.height) {
                snap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                Bitmap.createBitmap(snap, x, y, w, h)
            }
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
