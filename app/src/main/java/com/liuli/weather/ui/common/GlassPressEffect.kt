package com.liuli.weather.ui.common

import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.example.liquidglass.LiquidGlassView

/**
 * iOS 液态玻璃按压效果：按住时玻璃拉伸放大并通过 glassTint 淡入白色高光，松开回弹。
 * 通过 glassTint 实现高亮（不添加子 View），避免影响玻璃的 wrap_content 测量。
 */
object GlassPressEffect {

    /** 按住时的放大倍数（1.3x：明显但不突兀的凝胶反馈）。 */
    private const val PRESSED_SCALE = 1.3f

    /** 按住时的高光染色（ARGB，alpha 即强度）。 */
    private const val PRESSED_HIGHLIGHT = 0x5CFFFFFF

    private const val NO_HIGHLIGHT = 0x00000000

    /**
     * @param onPress 按压状态回调：true=按下 / false=松开。
     * 供宿主在按住期间临时开启该玻璃的逐帧动态采样——缩放动画期间 backdrop
     * 若用旧缓存，折射画面会在重采时跳变；逐帧重采可让折射实时跟随缩放。
     */
    fun attach(
        glassView: LiquidGlassView,
        onPress: ((Boolean) -> Unit)? = null,
        vararg touchSources: View
    ) {
        fun setPressed(down: Boolean) {
            onPress?.invoke(down)
            glassView.glassTint = if (down) PRESSED_HIGHLIGHT else NO_HIGHLIGHT
            glassView.animate()
                .scaleX(if (down) PRESSED_SCALE else 1f)
                .scaleY(if (down) PRESSED_SCALE else 1f)
                .setDuration(if (down) 140L else 380L)
                .setInterpolator(
                    // 按下用减速插值铺开，松开用 overshoot 回弹（大位移下张力略降防抖）
                    if (down) DecelerateInterpolator() else OvershootInterpolator(1.8f)
                )
                .start()
        }

        touchSources.forEach { source ->
            source.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> setPressed(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setPressed(false)
                }
                // 不消费事件，保证 onClick / onLongClick 正常触发
                false
            }
        }
    }
}
