/**
 * 渐进模糊覆盖层（对应 Apple 的 Scroll Edge Effect）。
 *
 * 放置在滚动内容的顶部/底部边缘：内容滚入该区域时，从清晰"平滑过渡到"模糊，
 * 而不是一条硬边界。iOS 26 的导航栏/工具栏下方就是这种效果。
 *
 * 实现（API 31+，全程 GPU）：
 * 1. 把父视图内容录制到 contentNode（带垂直外扩，避免模糊边缘吸黑）；
 * 2. [LAYER_COUNT] 个代理 RenderNode 分别挂**固定**的分级模糊 RenderEffect
 *    （半径从 max/K 到 max，只建一次，滚动中零重建）；
 * 3. [progress]（0~1，随滚动距离）驱动各级的叠加透明度：
 *    a_i = clamp(progress*K - i, 0, 1) —— 合成效果等价于「模糊半径 = progress * max」
 *    的动态模糊，全部是 GPU 合成（saveLayerAlpha + drawRenderNode），无 CPU 位图；
 * 4. 每级再用线性渐变 DST_IN 遮罩做空间上的渐进：最弱级铺满整条带，
 *    最强级集中在 [fadeExtentPx] 以内的贴边区域，中间级线性分布——
 *    同一时刻画面上"越靠边越模糊、往下逐渐清晰"，且强度随滚动继续增强。
 *
 * [fadeExtentPx] 是最强级渐变收尾的像素高度：设为城市胶囊底缘即可让整条
 * 渐变模糊覆盖到胶囊底部（MainActivity 接线处计算）。
 *
 * API < 31 回退为半透明渐变遮罩（scrim）。
 *
 * 用法：
 * ```kotlin
 * val edgeBlur = ScrollEdgeBlurView(context).apply {
 *     edge = ScrollEdgeBlurView.Edge.TOP
 *     maxBlurRadius = 40f
 * }
 * root.addView(edgeBlur, FrameLayout.LayoutParams(MATCH_PARENT, dp(120), Gravity.TOP))
 * edgeBlur.bindScrollView(scrollView)   // 滚动时自动重绘
 * edgeBlur.progress = ...               // 随滚动距离驱动强度（GPU 动态模糊）
 * ```
 */
package com.example.liquidglass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi

class ScrollEdgeBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 模糊所依附的边缘 */
    enum class Edge { TOP, BOTTOM }

    var edge = Edge.TOP
        set(value) {
            if (field != value) {
                field = value
                gradientsDirty = true
                invalidate()
            }
        }

    /** 最大模糊半径（px，贴边处的模糊强度） */
    var maxBlurRadius = 40f
        set(value) {
            val clamped = value.coerceIn(0f, 100f)
            if (field != clamped) {
                field = clamped
                effectsDirty = true
                invalidate()
            }
        }

    /**
     * 动态进度 0~1：0 = 无模糊，1 = 完整渐进模糊。
     * 中间值在 GPU 上按层叠加近似「模糊半径 = progress * maxBlurRadius」，
     * 不重建任何 RenderEffect。
     */
    var progress = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /**
     * 最强级渐变收尾的像素高度（相对本视图顶部）。
     * 设为「城市胶囊底缘」即可让模糊带覆盖到胶囊底部；<=0 时取高度的 55%。
     */
    var fadeExtentPx = -1f
        set(value) {
            if (field != value) {
                field = value
                gradientsDirty = true
                invalidate()
            }
        }

    private companion object {
        /** 分级模糊的层数：层数越多半径过渡越平滑，每层只是一次 GPU 合成 */
        const val LAYER_COUNT = 4
    }

    // GPU 节点（API 31+）
    private var contentNode: RenderNode? = null
    private var proxyNodes: Array<RenderNode?> = arrayOfNulls(LAYER_COUNT)
    private var effectsDirty = true
    private var gradientsDirty = true
    private var lastEffectRadius = -1f

    private val maskPaints = Array(LAYER_COUNT) {
        Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    }
    private val scrimPaint = Paint()

    private val location = IntArray(2)
    private val parentLocation = IntArray(2)
    private var isCapturing = false

    private var boundScrollView: View? = null
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { invalidate() }

    init {
        // 本视图不接收触摸：只是视觉覆盖层
        isClickable = false
        isFocusable = false
    }

    /** 绑定滚动视图：滚动时自动重绘本覆盖层 */
    fun bindScrollView(scrollView: View) {
        unbindScrollView()
        boundScrollView = scrollView
        if (isAttachedToWindow) {
            scrollView.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        }
    }

    fun unbindScrollView() {
        boundScrollView?.viewTreeObserver?.takeIf { it.isAlive }
            ?.removeOnScrollChangedListener(scrollListener)
        boundScrollView = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        boundScrollView?.viewTreeObserver?.addOnScrollChangedListener(scrollListener)
    }

    override fun onDetachedFromWindow() {
        boundScrollView?.viewTreeObserver?.takeIf { it.isAlive }
            ?.removeOnScrollChangedListener(scrollListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            contentNode?.discardDisplayList()
            proxyNodes.forEach { it?.discardDisplayList() }
        }
        contentNode = null
        for (i in proxyNodes.indices) proxyNodes[i] = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradientsDirty = true
        effectsDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (isCapturing) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            canvas.isHardwareAccelerated &&
            maxBlurRadius > 0.5f &&
            drawProgressiveBlur(canvas)
        ) {
            return
        }

        // 回退：半透明渐变遮罩（API < 31 或录制失败）
        drawScrimFallback(canvas)
    }

    // ==================== API 31+ 渐进模糊 ====================

    @RequiresApi(Build.VERSION_CODES.S)
    private fun drawProgressiveBlur(canvas: Canvas): Boolean {
        val parent = parent as? View ?: return false
        val w = width
        val h = height
        val margin = (maxBlurRadius * 2f).toInt().coerceAtLeast(8)

        val content = contentNode ?: RenderNode("ScrollEdgeContent").also { contentNode = it }

        // 1. 录制父视图内容（垂直外扩 margin）
        getLocationInWindow(location)
        parent.getLocationInWindow(parentLocation)
        val offsetX = (location[0] - parentLocation[0]).toFloat()
        val offsetY = (location[1] - parentLocation[1]).toFloat()

        content.setPosition(0, 0, w, h + 2 * margin)
        val rc = content.beginRecording(w, h + 2 * margin)
        try {
            rc.translate(-offsetX, margin - offsetY)
            isCapturing = true
            setTransitionVisibility(INVISIBLE)
            // 同时藏掉父容器直接子级里的玻璃（顶栏胶囊/底部按钮）。
            // 否则本次录制会引用玻璃的镜头节点，而玻璃的采样快照又可能引用
            // V.contentNode（V 显示列表干净时按引用重放，isCapturing 守卫拦不住），
            // 形成 采样快照 → V.contentNode → 玻璃镜头节点 → 采样快照 的引用环，
            // RenderThread 光栅化无限递归 SIGSEGV。
            // 被藏的胶囊/按钮在最终帧里由真实视图画在模糊带上方，视觉无损。
            hideSiblingGlasses(parent)
            try {
                parent.draw(rc)
            } finally {
                restoreSiblingGlasses()
                setTransitionVisibility(VISIBLE)
                isCapturing = false
            }
        } catch (_: Exception) {
            restoreSiblingGlasses()
            content.endRecording()
            return false
        }
        content.endRecording()

        // 2. 分级代理挂固定模糊效果（半径变化时才重建）
        if (effectsDirty || lastEffectRadius != maxBlurRadius) {
            for (i in 0 until LAYER_COUNT) {
                val radius = maxBlurRadius * (i + 1) / LAYER_COUNT
                val proxy = proxyNodes[i]
                    ?: RenderNode("ScrollEdgeL$i").also { proxyNodes[i] = it }
                proxy.setRenderEffect(
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                )
            }
            lastEffectRadius = maxBlurRadius
            effectsDirty = false
        }
        for (i in 0 until LAYER_COUNT) {
            recordProxy(proxyNodes[i]!!, content, w, h, margin)
        }

        // 3. 各级渐变遮罩（尺寸/收尾位置变化时重建）
        if (gradientsDirty) {
            rebuildGradients(w.toFloat(), h.toFloat())
            gradientsDirty = false
        }

        // 4. GPU 合成：a_i = clamp(progress*K - i, 0, 1)，弱级在下、强级在上。
        //    progress 走过 1/K、2/K…… 时最强可见半径逐级抬升，等价于半径随滚动增大。
        val bounds = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
        for (i in 0 until LAYER_COUNT) {
            val a = (progress * LAYER_COUNT - i).coerceIn(0f, 1f)
            if (a <= 0f) continue
            val save = canvas.saveLayerAlpha(bounds, (a * 255f).toInt().coerceIn(0, 255))
            canvas.drawRenderNode(proxyNodes[i]!!)
            canvas.drawRect(bounds, maskPaints[i])
            canvas.restoreToCount(save)
        }
        return true
    }

    /** 代理节点：把 contentNode 画进自己（模糊 RenderEffect 挂在代理上生效） */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun recordProxy(proxy: RenderNode, content: RenderNode, w: Int, h: Int, margin: Int) {
        proxy.setPosition(0, -margin, w, h + margin)
        val c = proxy.beginRecording(w, h + 2 * margin)
        try {
            c.drawRenderNode(content)
        } finally {
            proxy.endRecording()
        }
    }

    // 录制期间被临时藏掉的兄弟玻璃；复用列表避免逐帧分配
    private val hiddenSiblings = ArrayList<View>()

    /** 藏掉父容器直接子级里的玻璃（录制期间排除，防止 RenderNode 引用成环） */
    private fun hideSiblingGlasses(parent: View) {
        if (parent !is ViewGroup) return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is LiquidGlassView && child.visibility == View.VISIBLE) {
                child.setTransitionVisibility(View.INVISIBLE)
                hiddenSiblings.add(child)
            }
        }
    }

    private fun restoreSiblingGlasses() {
        for (v in hiddenSiblings) v.setTransitionVisibility(View.VISIBLE)
        hiddenSiblings.clear()
    }

    /** 第 i 级遮罩的收尾高度：最弱级=整条带，最强级=fadeExtentPx，中间线性分布 */
    private fun layerExtentPx(i: Int, h: Float): Float {
        val strongest = if (fadeExtentPx in 1f..h) fadeExtentPx else h * 0.55f
        val t = if (LAYER_COUNT <= 1) 0f else i.toFloat() / (LAYER_COUNT - 1)
        return h + (strongest - h) * t
    }

    private fun rebuildGradients(w: Float, h: Float) {
        val top = edge == Edge.TOP
        for (i in 0 until LAYER_COUNT) {
            val endY = layerExtentPx(i, h)
            val (y0, y1) = if (top) 0f to endY else h to (h - endY)
            maskPaints[i].shader = LinearGradient(
                0f, y0, 0f, y1,
                intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.30f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val (sy0, sy1) = if (top) 0f to h else h to 0f
        scrimPaint.shader = LinearGradient(
            0f, sy0, 0f, sy1,
            intArrayOf(0x66000000, Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
    }

    // ==================== 回退路径 ====================

    private fun drawScrimFallback(canvas: Canvas) {
        if (gradientsDirty) {
            rebuildGradients(width.toFloat(), height.toFloat())
            gradientsDirty = false
        }
        scrimPaint.alpha = (progress * 0x66).toInt().coerceIn(0, 0x66)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
    }
}
