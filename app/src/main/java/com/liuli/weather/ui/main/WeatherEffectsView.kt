/**
 * 程序化天气粒子层（动态背景）。
 *
 * 取代原来「静态雨丝图 + 整体平移」的伪动画：按天气类型生成并逐帧推进粒子——
 * - RAIN / STORM：三层景深雨滴（近层更粗更快更亮、雨痕长度与速度成正比形成拖尾感），
 *   统一风斜角，越界即从顶部重生；STORM 密度与速度更高。
 * - SNOW：三层雪花，尺寸/透明度分层，带横向飘摆。
 * - MIST：多层柔和雾带（垂直渐变的软边矩形）缓慢横移循环，用于雾/霾/浮尘。
 * - MOTES：晴天光尘，缓慢上浮 + 透明度呼吸。
 *
 * 由 Choreographer 驱动，暂停/不可见时自动停帧；效果层本身是玻璃的折射背景，
 * 动起来的粒子会让玻璃卡片的折射细节真正「活」起来。
 */
package com.liuli.weather.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class WeatherEffectsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { NONE, RAIN, STORM, SNOW, MIST, MOTES }

    var mode: Mode = Mode.NONE
        set(value) {
            if (field == value) return
            field = value
            onModeChanged()
        }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var paused = false
    private var running = false
    private var lastFrameNs = 0L
    private var timeSec = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- 雨 ----
    private class Drop {
        var x = 0f; var y = 0f; var len = 0f; var vy = 0f; var alpha = 0; var strokeW = 0f
    }
    private val drops = ArrayList<Drop>(320)
    /** 风斜角：每下落 1px 横移 -0.26px（与旧静态雨丝的角度一致） */
    private val slant = -0.26f

    // ---- 雪 / 光尘 ----
    private class Flake {
        var x = 0f; var y = 0f; var r = 0f
        var vy = 0f; var sway = 0f; var freq = 0f; var phase = 0f
        var alpha = 0; var alphaSpeed = 0f
    }
    private val flakes = ArrayList<Flake>(160)

    // ---- 雾 ----
    private class Band {
        var cx = 0f; var cy = 0f; var hw = 0f; var hh = 0f
        var vx = 0f; var shader: Shader? = null
    }
    private val bands = ArrayList<Band>(8)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNs == 0L) 0f
            else ((frameTimeNanos - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNs = frameTimeNanos
            timeSec += dt
            step(dt)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        // 每帧重绘的粒子层走硬件层，避免每帧软件重合成
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun pause() {
        paused = true
        stopLoop()
    }

    fun resume() {
        paused = false
        maybeStartLoop()
    }

    private fun onModeChanged() {
        rebuild()
        visibility = if (mode == Mode.NONE) GONE else VISIBLE
        maybeStartLoop()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        maybeStartLoop()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        maybeStartLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        super.onDetachedFromWindow()
    }

    private fun maybeStartLoop() {
        if (mode == Mode.NONE || paused || !isAttachedToWindow || visibility != VISIBLE) {
            stopLoop()
            return
        }
        if (running) return
        running = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopLoop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    // ------------------------------------------------------------- 粒子生成

    private fun rebuild() {
        drops.clear()
        flakes.clear()
        bands.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        when (mode) {
            Mode.RAIN -> buildRain(w, h, storm = false)
            Mode.STORM -> buildRain(w, h, storm = true)
            Mode.SNOW -> buildSnow(w, h)
            Mode.MOTES -> buildMotes(w, h)
            Mode.MIST -> buildMist(w, h)
            Mode.NONE -> Unit
        }
        invalidate()
    }

    private fun buildRain(w: Float, h: Float, storm: Boolean) {
        // 三层景深：远层细慢暗、近层粗快亮，长度随速度（拖尾感）
        val mul = if (storm) 1.45f else 1f
        data class Layer(val count: Int, val lenMin: Float, val lenMax: Float, val spMin: Float, val spMax: Float, val aMin: Int, val aMax: Int, val lineWidth: Float)
        val layers = listOf(
            Layer((42 * mul).toInt(), dp(10f), dp(18f), dp(380f), dp(520f), 38, 66, dp(1.0f)),
            Layer((38 * mul).toInt(), dp(18f), dp(30f), dp(620f), dp(840f), 66, 108, dp(1.4f)),
            Layer((26 * mul).toInt(), dp(30f), dp(50f), dp(920f), dp(1280f), 102, 158, dp(2.0f))
        )
        layers.forEach { l ->
            repeat(l.count) {
                drops.add(Drop().apply {
                    x = Random.nextFloat() * w * 1.3f - w * 0.2f
                    y = Random.nextFloat() * h
                    vy = l.spMin + Random.nextFloat() * (l.spMax - l.spMin)
                    len = l.lenMin + Random.nextFloat() * (l.lenMax - l.lenMin)
                    alpha = l.aMin + Random.nextInt(l.aMax - l.aMin)
                    strokeW = l.lineWidth
                })
            }
        }
    }

    private fun buildSnow(w: Float, h: Float) {
        data class Layer(val count: Int, val rMin: Float, val rMax: Float, val spMin: Float, val spMax: Float, val aMin: Int, val aMax: Int, val swayMax: Float)
        val layers = listOf(
            Layer(46, dp(1.0f), dp(1.8f), dp(50f), dp(85f), 110, 165, dp(8f)),
            Layer(34, dp(1.6f), dp(2.6f), dp(75f), dp(120f), 140, 195, dp(14f)),
            Layer(20, dp(2.4f), dp(3.6f), dp(105f), dp(160f), 175, 225, dp(22f))
        )
        layers.forEach { l ->
            repeat(l.count) {
                flakes.add(Flake().apply {
                    x = Random.nextFloat() * w
                    y = Random.nextFloat() * h
                    r = l.rMin + Random.nextFloat() * (l.rMax - l.rMin)
                    vy = l.spMin + Random.nextFloat() * (l.spMax - l.spMin)
                    sway = Random.nextFloat() * l.swayMax
                    freq = 0.25f + Random.nextFloat() * 0.4f
                    phase = Random.nextFloat() * 2f * PI.toFloat()
                    alpha = l.aMin + Random.nextInt(l.aMax - l.aMin)
                    alphaSpeed = 0.6f + Random.nextFloat() * 1.2f
                })
            }
        }
    }

    private fun buildMotes(w: Float, h: Float) {
        repeat(44) {
            flakes.add(Flake().apply {
                x = Random.nextFloat() * w
                y = Random.nextFloat() * h
                r = dp(0.8f) + Random.nextFloat() * dp(1.4f)
                vy = dp(8f) + Random.nextFloat() * dp(16f)   // 向上飘
                sway = Random.nextFloat() * dp(10f)
                freq = 0.15f + Random.nextFloat() * 0.25f
                phase = Random.nextFloat() * 2f * PI.toFloat()
                alpha = 28 + Random.nextInt(46)
                alphaSpeed = 0.3f + Random.nextFloat() * 0.5f
            })
        }
    }

    private fun buildMist(w: Float, h: Float) {
        val bandColor = 0xE6F0FA
        repeat(7) { i ->
            val hh = dp(50f) + Random.nextFloat() * dp(85f)
            val cy = h * (0.08f + 0.13f * i) + Random.nextFloat() * dp(30f)
            val hw = w * (0.62f + Random.nextFloat() * 0.35f)
            val alpha = 7 + Random.nextInt(10)
            val color = (alpha shl 24) or bandColor
            bands.add(Band().apply {
                cx = Random.nextFloat() * w * 1.4f - w * 0.2f
                this.cy = cy
                this.hw = hw
                this.hh = hh
                vx = dp(5f) + Random.nextFloat() * dp(9f)
                shader = LinearGradient(
                    0f, cy - hh, 0f, cy + hh,
                    intArrayOf(0x00FFFFFF, color, 0x00FFFFFF),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            })
        }
    }

    // ------------------------------------------------------------- 逐帧推进

    private fun step(dt: Float) {
        if (dt <= 0f) return
        val w = width.toFloat()
        val h = height.toFloat()

        when (mode) {
            Mode.RAIN, Mode.STORM -> {
                for (d in drops) {
                    d.y += d.vy * dt
                    d.x += d.vy * slant * dt
                    // 越界（下方或左侧）→ 从顶部重生
                    if (d.y - d.len > h) {
                        d.y = -d.len - Random.nextFloat() * dp(30f)
                        d.x = Random.nextFloat() * w * 1.3f - w * 0.2f
                    }
                    if (d.x < -dp(40f)) {
                        d.x += w * 1.3f
                    }
                }
            }
            Mode.SNOW -> {
                for (f in flakes) {
                    f.y += f.vy * dt
                    f.x += (f.sway * sin(timeSec * f.freq * 2f * PI.toFloat() + f.phase)) * dt * 2.2f
                    if (f.y - f.r > h) {
                        f.y = -f.r - Random.nextFloat() * dp(20f)
                        f.x = Random.nextFloat() * w
                    }
                    if (f.x < -dp(10f)) f.x = w + dp(5f)
                    if (f.x > w + dp(10f)) f.x = -dp(5f)
                }
            }
            Mode.MOTES -> {
                for (f in flakes) {
                    f.y -= f.vy * dt
                    f.x += (f.sway * sin(timeSec * f.freq * 2f * PI.toFloat() + f.phase)) * dt * 1.6f
                    if (f.y + f.r < 0f) {
                        f.y = h + f.r
                        f.x = Random.nextFloat() * w
                    }
                }
            }
            Mode.MIST -> {
                for (b in bands) {
                    b.cx += b.vx * dt
                    if (b.cx - b.hw > w + dp(20f)) {
                        b.cx = -b.hw - dp(20f)
                    }
                }
            }
            Mode.NONE -> Unit
        }
    }

    // ------------------------------------------------------------- 绘制

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (mode) {
            Mode.RAIN, Mode.STORM -> drawRain(canvas)
            Mode.SNOW -> drawSnow(canvas)
            Mode.MOTES -> drawMotes(canvas)
            Mode.MIST -> drawMist(canvas)
            Mode.NONE -> Unit
        }
    }

    private fun drawRain(canvas: Canvas) {
        val stroke = paint
        stroke.style = Paint.Style.STROKE
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = 0xFFE9F3FF.toInt()
        for (d in drops) {
            stroke.alpha = d.alpha
            stroke.strokeWidth = d.strokeW
            val tailX = d.x - d.len * slant
            val tailY = d.y - d.len
            canvas.drawLine(d.x, d.y, tailX, tailY, stroke)
        }
    }

    private fun drawSnow(canvas: Canvas) {
        val p = paint
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        for (f in flakes) {
            p.alpha = f.alpha
            canvas.drawCircle(f.x, f.y, f.r, p)
        }
    }

    private fun drawMotes(canvas: Canvas) {
        val p = paint
        p.style = Paint.Style.FILL
        p.color = 0xFFFFF6D8.toInt()
        for (f in flakes) {
            // 透明度呼吸：缓慢明暗
            val pulse = 0.62f + 0.38f * sin(timeSec * f.alphaSpeed * 2f * PI.toFloat() + f.phase)
            p.alpha = (f.alpha * pulse).toInt().coerceIn(0, 255)
            canvas.drawCircle(f.x, f.y, f.r, p)
        }
    }

    private fun drawMist(canvas: Canvas) {
        for (b in bands) {
            val shader = b.shader ?: continue
            paint.style = Paint.Style.FILL
            paint.shader = shader
            paint.alpha = 255
            canvas.drawRect(b.cx - b.hw, b.cy - b.hh, b.cx + b.hw, b.cy + b.hh, paint)
        }
        paint.shader = null
    }
}
