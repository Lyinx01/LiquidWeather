/**
 * 浮层玻璃的内容像素采样（稳定版）。
 *
 * 背景：浮层玻璃（错误条/toast）若采样包含其他玻璃的视图（content view / mainRoot），
 * 录制显示列表会引用其他玻璃的 RenderNode，彼此形成引用成环；交互触发重录后，
 * RenderThread 光栅化沿环无限递归（computeTransformImpl 512 帧），栈溢出闪退。
 * GPU 透镜管线在此架构下无法安全采样含玻璃的内容（hideOtherGlass 只在嵌套层生效），
 * 因此浮层统一走 **CPU 像素管线**：setCustomBackdropCapture 每次绘制时把浮层所在
 * 区域软件绘成位图（降采样 + 模糊边距外扩），管线对位图做模糊/染色/棱边。
 *
 * 稳定性要点：
 * - 捕获期间用 setTransitionVisibility 把浮层自身临时隐藏（不触发 invalidate，
 *   勿用 setVisibility，否则会造成「重截→invalide→再重截」的永久逐帧循环），
 *   同时切断「采样→自身绘制→再采样」的重入。
 * - 捕获区域按模糊半径外扩：高斯模糊在边缘向外取样，精确边界截取会让边缘
 *   像素被反复钳制形成脏边；外扩后模糊取到的是真实内容，结果随管线一起
 *   拉伸回浮层边界（轻微变焦在重模糊下不可见）。
 * - 滚动/内容位移时经窗口级 OnScrollChangedListener 触发玻璃重绘（节流），
 *   折射跟随当前内容，无残影。
 */
package com.liuli.weather.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.example.liquidglass.LiquidGlassView

object GlassOverlayCapture {

    /** 滚动重截的最小间隔（ms）：区域软件重绘有一定开销，节流避免滚动掉帧 */
    private const val RECAPTURE_INTERVAL_MS = 48L

    /**
     * 给浮层玻璃挂内容像素采样。
     *
     * @param glass 浮层玻璃视图
     * @param contentHost 内容容器（浮层的父容器；采样按浮层在其中的位置截取）
     * @param bandHeightDp 采样带高度（dp），需覆盖浮层及其模糊取样的范围
     * @param downsample 捕获降尺寸比例 (0-1]：软件模糊半径被钳制在 25px，
     *   缩小捕获再拉伸回去可获得更强的等效模糊（0.35 ≈ 3 倍）
     */
    fun attach(
        glass: LiquidGlassView,
        contentHost: ViewGroup,
        bandHeightDp: Int = 280,
        downsample: Float = 0.35f
    ) {
        var capturing = false
        var lastRecapture = 0L

        glass.setCustomBackdropCapture { bounds ->
            // 重入（自身绘制触发的采样）或未布局：返回空让管线走白色兜底，
            // 正常流程下不会发生（捕获期间浮层已临时隐藏）
            if (capturing || !glass.isLaidOut || glass.width <= 0 || contentHost.width <= 0) {
                return@setCustomBackdropCapture null
            }
            // 常驻复用的浮层隐藏期间不重截（GONE 状态下内容看不见）
            if (glass.visibility != View.VISIBLE) return@setCustomBackdropCapture null

            val blurRadius = 4f + glass.blurAmount * 32f
            val margin = blurRadius * 2f
            val regionLeft = glass.left - margin
            val regionTop = glass.top - margin
            val regionW = glass.width + margin * 2f
            val regionH = glass.height + margin * 2f

            val full = captureRegion(contentHost, regionLeft, regionTop, regionW, regionH, downsample)
                ?: return@setCustomBackdropCapture null
            // 整幅（含边距）返回：管线模糊时边缘取样到边距里的真实内容，
            // 结果拉伸回浮层边界——与库内 enableOptimizedCapture 的行为一致
            full
        }

        // 内容滚动/位移时重绘玻璃（节流）：折射跟随当前内容，无残影。
        // OnScrollChangedListener 挂在窗口级 ViewTreeObserver 上，任意视图滚动都会到达
        val scrollListener = ViewTreeObserver.OnScrollChangedListener {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRecapture >= RECAPTURE_INTERVAL_MS) {
                lastRecapture = now
                glass.invalidate()
            }
        }
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

    /** 软件绘制 host 的指定区域到独立位图（像素，无 RenderNode 引用） */
    private fun captureRegion(
        host: View,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        downsample: Float
    ): Bitmap? {
        val w = width.toInt()
        val h = height.toInt()
        if (w <= 0 || h <= 0) return null
        val s = downsample.coerceIn(0.05f, 1f)
        val bmp = Bitmap.createBitmap(
            (w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        if (s < 1f) canvas.scale(s, s)
        canvas.translate(-left, -top)
        host.draw(canvas)
        return bmp
    }
}
