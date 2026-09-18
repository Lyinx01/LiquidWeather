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

    /** 按住时的放大倍数（iOS 液态玻璃的"凝胶鼓起"效果）。 */
    private const val PRESSED_SCALE = 1.12f

    /** 按住时的高光染色（ARGB，alpha 即强度）。 */
    private const val PRESSED_HIGHLIGHT = 0x5CFFFFFF

    private const val NO_HIGHLIGHT = 0x00000000

    fun attach(glassView: LiquidGlassView, vararg touchSources: View) {
        fun setPressed(down: Boolean) {
            glassView.glassTint = if (down) PRESSED_HIGHLIGHT else NO_HIGHLIGHT
            glassView.animate()
                .scaleX(if (down) PRESSED_SCALE else 1f)
                .scaleY(if (down) PRESSED_SCALE else 1f)
                .setDuration(if (down) 110L else 300L)
                .setInterpolator(
                    // 按下略带回弹，松开用强 overshoot 模拟玻璃回弹
                    if (down) DecelerateInterpolator() else OvershootInterpolator(2.2f)
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
