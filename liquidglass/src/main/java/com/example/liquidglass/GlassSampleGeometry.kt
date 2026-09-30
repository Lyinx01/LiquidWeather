package com.example.liquidglass

import android.view.View

/**
 * 玻璃采样几何工具。
 *
 * 透镜与模糊管线都把「背景来源」按 **1:1 屏幕像素**录制进玻璃的局部坐标系，
 * 因此要求玻璃的局部坐标与屏幕像素一一对应——这只在玻璃**未被缩放**时成立。
 *
 * 玻璃被缩放时（例如按压放大 1.3 倍）：[View.getLocationOnScreen] 返回的是
 * **变换之后**的左上角。View 默认绕中心缩放，放大后左上角会向左上外扩
 * `pivot * (scale - 1)`；而录制区仍按未缩放的 width/height 取样。两者不一致，
 * 采样内容就会相对真实背景整体平移，缩放越大偏移越明显——表现就是按住控件时
 * 折射内容「错位」漂移（右下方向位移，因为锚点变成了左上角）。
 *
 * 这里把偏移还原成**未缩放**时的位置，让采样内容与视图缩放共用同一个锚点
 * （[View.getPivotX]/[View.getPivotY]，默认中心）。这样中心点在缩放前后看到的
 * 是同一块背景，四周向外放大，观感才是正常的「放大镜」，而不是「内容往下掉」。
 *
 * 注意：[View.getLocationOnScreen] 已经把祖先的变换也算进去了（滚动容器因此天然
 * 兼容），这里只撤销玻璃**自身**的缩放，祖先变换保持不动。
 */
internal object GlassSampleGeometry {

    // 仅在 UI 线程调用（视图绘制路径），复用数组避免逐帧分配
    private val glassLocation = IntArray(2)
    private val parentLocation = IntArray(2)

    /**
     * 计算玻璃相对 [parent] 的采样偏移，写入 [out]（out[0]=x，out[1]=y）。
     * 结果已是「未缩放」位置，可直接用于按 1:1 像素录制背景。
     */
    fun unscaledOffset(glass: View, parent: View, out: FloatArray) {
        glass.getLocationOnScreen(glassLocation)
        parent.getLocationOnScreen(parentLocation)
        val sx = glass.scaleX
        val sy = glass.scaleY
        out[0] = (glassLocation[0] - parentLocation[0]) +
            (if (sx != 1f) glass.pivotX * (sx - 1f) else 0f)
        out[1] = (glassLocation[1] - parentLocation[1]) +
            (if (sy != 1f) glass.pivotY * (sy - 1f) else 0f)
    }
}
