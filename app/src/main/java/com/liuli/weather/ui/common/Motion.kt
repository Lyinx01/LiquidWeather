package com.liuli.weather.ui.common

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * iOS 应用启动动画的速率曲线（spring 的贝塞尔近似）：
 * 起步轻快、无过冲、长减速尾巴平滑收住。
 */
object Motion {
    val iosAppLaunch: Interpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)
}
