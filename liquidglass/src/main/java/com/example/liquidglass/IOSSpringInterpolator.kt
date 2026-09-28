/**
 * iOS 浮窗弹簧插值器（单位质量阻尼谐振子）
 *
 * 与 iOS 横幅 / 灵动岛使用的 CASpringAnimation(UISpringTimingParameters) 同构：
 * - response 控制周期（秒），越小越快；典型横幅 0.4~0.5s
 * - dampingRatio 控制过冲；0.82 对应约 1.3% 的极轻微过冲——起步轻快、
 *   收尾带一丝回弹后立刻稳住，是 iOS 浮窗的标准手感
 *
 * 公式：x(t) = 1 - e^(-ζω₀t) · [cos(ω_d t) + (ζω₀/ω_d)·sin(ω_d t)]
 * 其中 ω₀ = 2π/response，ω_d = ω₀√(1-ζ²)。插值器输入 [0,1] 映射到 [0,response]。
 */
package com.example.liquidglass

import android.view.animation.Interpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class IOSSpringInterpolator(
    private val response: Float = 0.44f,
    private val dampingRatio: Float = 0.82f
) : Interpolator {

    private val omega0 = 2f * PI.toFloat() / response
    private val omegaD = omega0 * sqrt((1f - dampingRatio * dampingRatio).coerceAtLeast(1e-4f))
    private val decay = dampingRatio * omega0

    override fun getInterpolation(input: Float): Float {
        val t = input.coerceIn(0f, 1f) * response
        val e = exp(-decay * t)
        val v = 1f - e * (cos(omegaD * t) + (decay / omegaD) * sin(omegaD * t))
        return v
    }
}
