/**
 * 程序化天空背景（动态）。
 *
 * 取代原来「六张静态 bg_sky_* layer-list + 三个漂移 ImageView + 星空/光晕呼吸」的做法，
 * 整片天空由一个 View 逐帧绘制：
 * - 底色：四段垂直渐变（天顶 → 中层 → 地平线 → 地平线亮带），以极慢速度上下漂移，
 *   天空会缓慢「呼吸」而不是一张死图；
 * - 云：一朵云是一簇大小不一、上亮下暗的软块拼成的云团（预烘焙成位图，逐帧只做缩放与
 *   alpha），按三层景深以不同速度横移循环，形成视差；
 * - 太阳/月亮：按当天日出日落把时刻折算成弧线位置（清晨低、正午高、黄昏低），光晕呼吸；
 * - 金色时刻：日出/日落前后给地平线染暖，强度由当前时刻与日出日落的距离决定；
 * - 星空：夜间星点各自闪烁；雷阵雨会偶发双闪。
 *
 * 它同时是玻璃卡片的折射源（bg_container 的直接子视图），所以刻意保留了大尺度明暗结构
 * ——云团、日月、地平线亮带——玻璃才有细节可折射。
 */
package com.liuli.weather.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class SkyBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Sky { SUNNY, CLOUDY, RAIN, STORM, SNOW, FOG, NIGHT }

    var sky: Sky = Sky.CLOUDY
        set(value) {
            if (field == value) return
            field = value
            sceneDirty = true
            invalidate()
        }

    /** 天气配色：渐变三段 + 云的色调/浓度/速度/数量。 */
    private class Ramp(
        val zenith: Int,
        val middle: Int,
        val horizon: Int,
        val cloudTint: Int,
        val cloudAlpha: Float,
        val cloudSpeed: Float,
        val cloudCount: Int,
        val starCount: Int,
        val lightning: Boolean
    )

    private class Cloud {
        var x = 0f
        var y = 0f
        var drawW = 0f
        var alpha = 0f
        var speed = 0f
        var sprite = 0
    }

    private class Star {
        var x = 0f
        var y = 0f
        var r = 0f
        var base = 0f
        var phase = 0f
        var speed = 0f
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    // ---- 场景状态
    private var ramp: Ramp = rampFor(Sky.CLOUDY)
    private var horizonWarmth = 0f
    private var sunProgress = -1f
    private var moonProgress = -1f

    // ---- 逐帧状态
    private var running = false
    private var lastFrameNs = 0L
    private var timeSec = 0f
    private var sinceRedrawSec = 0f
    private var flashT = 0f
    private var nextFlashSec = 0f

    // ---- 绘制资源
    private val basePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val baseMatrix = android.graphics.Matrix()
    private val cloudPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sunBloomPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sunCorePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val moonGlowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val moonDiscPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonShadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flashPaint = Paint()
    private val horizonPaint = Paint()
    private val tmpRect = RectF()

    private val clouds = ArrayList<Cloud>(10)
    private val stars = ArrayList<Star>(140)
    private var cloudSprites: List<Bitmap> = emptyList()
    private var glowSprite: Bitmap? = null

    private var spritesDirty = true
    private var sceneDirty = true

    init {
        initPaints()
    }

    // ------------------------------------------------------------ 对外接口

    /**
     * 传入当前时刻与当天日出日落（"HH:mm"），驱动太阳/月亮位置与金色时刻染色。
     * 解析失败时退回 06:12 / 18:24 的经验值。
     */
    fun setTimeContext(nowMillis: Long, sunrise: String?, sunset: String?) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f

        val sr = parseHour(sunrise) ?: DEFAULT_SUNRISE
        val ss = parseHour(sunset) ?: DEFAULT_SUNSET
        val daySpan = (ss - sr).coerceAtLeast(1f)

        sunProgress = if (hour in sr..ss) (hour - sr) / daySpan else -1f

        val nightSpan = (24f - daySpan).coerceAtLeast(1f)
        val nightHour = if (hour >= ss) hour - ss else hour + 24f - ss
        moonProgress = if (hour < sr || hour > ss) (nightHour / nightSpan).coerceIn(0f, 1f) else -1f

        // 金色时刻：离日出/日落越近越暖
        horizonWarmth = max(gauss(hour, sr, 1.15f), gauss(hour, ss, 1.15f))
        sceneDirty = true
        invalidate()
    }

    fun pause() {
        running = false
        lastFrameNs = 0L
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    /**
     * 启动逐帧驱动。
     *
     * 刻意不在这里判断宽高：onAttachedToWindow 早于第一次 layout，此时 width 还是 0，
     * 若因此提前 return，之后就再没有时机把动画拉起来（表现为背景完全静止）。
     * 尺寸为 0 时 advance()/onDraw() 各自会直接返回，空转一帧无害。
     */
    fun resume() {
        if (running) return
        running = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
        invalidate()
    }

    // ------------------------------------------------------------ 逐帧驱动

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNs == 0L) 0f
            else ((frameTimeNanos - lastFrameNs) / 1_000_000_000.0).toFloat()
            lastFrameNs = frameTimeNanos
            if (dt > 0f) {
                val step = dt.coerceAtMost(0.05f)
                timeSec += step
                advance(step)
                // 天空自身的运动很慢（云 2-6 dp/s、渐变 26s 一个来回），逐帧重绘整屏
                // 是纯浪费。按固定间隔重绘即可，肉眼无差；只有闪电要全速响应。
                sinceRedrawSec += step
                if (sinceRedrawSec >= REDRAW_INTERVAL_SEC || flashT > 0f) {
                    sinceRedrawSec = 0f
                    invalidate()
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) resume()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!isAttachedToWindow) return
        if (visibility == VISIBLE) resume() else pause()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        spritesDirty = true
        sceneDirty = true
        // 首次布局完成后再兜一次：onAttachedToWindow 时尺寸还是 0
        if (visibility == VISIBLE) resume()
    }

    // ------------------------------------------------------------ 推帧

    private fun advance(dt: Float) {
        val w = width.toFloat()
        if (w <= 0f) return

        // 云团横移；越过右边界后从左边界外回绕，并换一个高度
        for (c in clouds) {
            c.x += c.speed * dt
            if (c.x - c.drawW * 0.5f > w + dp(16f)) {
                c.x = -c.drawW * 0.5f - dp(16f)
                c.y = cloudY()
            }
        }

        // 雷电：随机间隔触发一次双闪
        if (ramp.lightning) {
            nextFlashSec -= dt
            if (nextFlashSec <= 0f) {
                flashT = 1f
                nextFlashSec = 3.5f + Random.nextFloat() * 7f
            }
        }
        if (flashT > 0f) flashT = (flashT - dt / FLASH_DECAY_SEC).coerceAtLeast(0f)
    }

    // ------------------------------------------------------------ 绘制

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (spritesDirty) {
            buildSprites()
            spritesDirty = false
            sceneDirty = true
        }
        if (sceneDirty) {
            buildScene()
            sceneDirty = false
        }

        // 底色渐变：整段缓慢上下漂移，天空像在呼吸
        val drift = sin(timeSec / DRIFT_PERIOD_SEC * TWO_PI) * h * 0.030f
        baseMatrix.setTranslate(0f, drift)
        basePaint.shader?.setLocalMatrix(baseMatrix)
        canvas.drawRect(0f, 0f, w, h, basePaint)

        drawHorizon(canvas, w, h)
        drawSunMoon(canvas, w, h)
        drawStars(canvas)
        drawClouds(canvas)
        drawFlash(canvas, w, h)
    }

    /** 一次性的画刷初始化：Paint 的默认颜色是黑色，凡是用色的都要显式指定。 */
    private fun initPaints() {
        moonDiscPaint.color = 0xFFF2F5FF.toInt()
        moonShadePaint.color = 0xFF9AA6C4.toInt()
        starPaint.color = 0xFFFFFFFF.toInt()
        flashPaint.color = 0xFFCDE4FF.toInt()
    }

    /** 地平线亮带：画面下部一条横向柔光，制造大气透视的纵深。 */
    private fun drawHorizon(canvas: Canvas, w: Float, h: Float) {
        if (horizonShader == null) return
        horizonPaint.alpha = (horizonAlpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, h * HORIZON_TOP, w, h * HORIZON_BOTTOM, horizonPaint)
    }

    private var horizonShader: LinearGradient? = null
    private var horizonAlpha = 0f

    private fun drawSunMoon(canvas: Canvas, w: Float, h: Float) {
        val sprite = glowSprite ?: return
        val pulse = 0.9f + 0.1f * sin(timeSec / 3.4f * TWO_PI)
        val isNightSky = ramp.starCount > 0

        if (sunProgress >= 0f && !isNightSky) {
            val x = w * (0.14f + 0.72f * sunProgress)
            val y = h * (0.36f - 0.28f * sin(PI.toFloat() * sunProgress))
            // 三层同心柔光叠出光晕：外圈大气散射 → 中层辉光 → 明亮核心。
            // 不用实心圆画日面——平涂的圆在高亮天空下会像一张贴纸，边缘发死。
            drawGlow(canvas, sprite, sunBloomPaint, x, y, dp(210f) * pulse, 0.30f)
            drawGlow(canvas, sprite, sunBloomPaint, x, y, dp(78f) * pulse, 0.52f)
            drawGlow(canvas, sprite, sunCorePaint, x, y, dp(30f) * pulse, 0.94f)
        }
        if (moonProgress >= 0f && isNightSky) {
            val x = w * (0.18f + 0.64f * moonProgress)
            val y = h * (0.46f - 0.30f * sin(PI.toFloat() * moonProgress))
            drawGlow(canvas, sprite, moonGlowPaint, x, y, dp(120f) * pulse, 0.22f)
            // 月亮是实体，保留清晰圆面；再用一块偏暗的圆压出相位阴影，
            // 避免整轮满月看起来像贴纸
            moonDiscPaint.alpha = 255
            canvas.drawCircle(x, y, dp(15f), moonDiscPaint)
            moonShadePaint.alpha = 58
            canvas.drawCircle(x - dp(5f), y - dp(4f), dp(13f), moonShadePaint)
        }
    }

    private fun drawGlow(
        canvas: Canvas,
        sprite: Bitmap,
        paint: Paint,
        cx: Float,
        cy: Float,
        r: Float,
        alpha: Float
    ) {
        tmpRect.set(cx - r, cy - r, cx + r, cy + r)
        paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawBitmap(sprite, null, tmpRect, paint)
    }

    private fun drawStars(canvas: Canvas) {
        if (stars.isEmpty()) return
        for (s in stars) {
            val twinkle = 0.55f + 0.45f * sin(timeSec * s.speed + s.phase)
            val a = s.base * twinkle
            // 极淡的外晕 + 锐利的星点：星点必须有明确颜色，Paint 默认色是黑色，
            // 漏设就会画出一圈黑晕；外晕半径也不能大，否则星星会变成气泡
            starPaint.alpha = (a * 0.13f * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(s.x, s.y, s.r * 1.9f, starPaint)
            starPaint.alpha = (a * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(s.x, s.y, s.r, starPaint)
        }
    }

    private fun drawClouds(canvas: Canvas) {
        if (cloudSprites.isEmpty()) return
        for (c in clouds) {
            val sprite = cloudSprites[c.sprite % cloudSprites.size]
            val dw = c.drawW
            val dh = dw / CLOUD_ASPECT
            val left = c.x - dw * 0.5f
            if (left > width || left + dw < 0f) continue
            val top = c.y - dh * 0.5f
            tmpRect.set(left, top, left + dw, top + dh)
            cloudPaint.alpha = (c.alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(sprite, null, tmpRect, cloudPaint)
        }
    }

    /** 雷电双闪：一次触发里两段短促脉冲，第二段更弱。 */
    private fun drawFlash(canvas: Canvas, w: Float, h: Float) {
        if (flashT <= 0f) return
        val a = max(spike(flashT, 0.94f, 0.10f), 0.55f * spike(flashT, 0.60f, 0.09f))
        if (a < 0.01f) return
        flashPaint.alpha = (a * 255f).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, flashPaint)
    }

    // ------------------------------------------------------------ 场景构建

    /** 尺寸变化时重建位图精灵（云团、通用光晕）。 */
    private fun buildSprites() {
        if (width <= 0 || height <= 0) return
        cloudSprites = listOf(bakeCloudSprite(0), bakeCloudSprite(7))
        if (glowSprite == null) glowSprite = bakeGlowSprite()
    }

    /** 天气/时刻变化时重建配色、渐变、云与星星。 */
    private fun buildScene() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        ramp = rampFor(sky)
        val warm = horizonWarmth

        val middle = blend(ramp.middle, GOLDEN, warm * 0.16f)
        val horizon = blend(ramp.horizon, GOLDEN, warm * 0.80f)
        val horizonBright = blend(horizon, WHITE, 0.16f)

        // 渐变上下各留 12% 余量，漂移时不会露出 CLAMP 的边
        basePaint.shader = LinearGradient(
            0f, -h * 0.12f, 0f, h * 1.12f,
            intArrayOf(ramp.zenith, middle, horizon, horizonBright),
            floatArrayOf(0f, 0.44f, 0.80f, 1f),
            Shader.TileMode.CLAMP
        )

        // 地平线亮带（透明 → 亮 → 透明，颜色取地平线色调）
        val bandColor = blend(horizon, WHITE, 0.10f)
        horizonShader = LinearGradient(
            0f, h * HORIZON_TOP, 0f, h * HORIZON_BOTTOM,
            intArrayOf(bandColor and 0x00FFFFFF, bandColor, bandColor and 0x00FFFFFF),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        horizonPaint.shader = horizonShader
        horizonAlpha = (0.22f + 0.30f * warm) * if (ramp.starCount > 0) 0.75f else 1f

        cloudPaint.colorFilter =
            PorterDuffColorFilter(ramp.cloudTint, PorterDuff.Mode.MULTIPLY)

        buildClouds()
        buildStars()
        if (ramp.lightning && nextFlashSec <= 0f) nextFlashSec = 1.5f + Random.nextFloat() * 3f
    }

    private fun buildClouds() {
        clouds.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        repeat(ramp.cloudCount) { i ->
            val depth = i % 3
            val scale = when (depth) {
                0 -> 0.55f + Random.nextFloat() * 0.22f
                1 -> 0.85f + Random.nextFloat() * 0.28f
                else -> 1.18f + Random.nextFloat() * 0.36f
            }
            val c = Cloud()
            c.drawW = w * 0.62f * scale
            c.alpha = ramp.cloudAlpha * when (depth) {
                0 -> 0.55f
                1 -> 0.80f
                else -> 1f
            }
            c.speed = dp(BASE_CLOUD_SPEED_DP) * ramp.cloudSpeed * (0.55f + 0.45f * depth)
            c.sprite = if (Random.nextBoolean()) 0 else 1
            c.x = Random.nextFloat() * w
            c.y = cloudY()
            clouds.add(c)
        }
    }

    /** 云的高度：铺满整个画面（玻璃卡片要处处有可折射的细节），偏上留白少一些。 */
    private fun cloudY(): Float {
        val h = height.toFloat()
        val k = Random.nextFloat()
        return h * (0.04f + 0.80f * k * k)
    }

    private fun buildStars() {
        stars.clear()
        if (ramp.starCount == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        repeat(ramp.starCount) {
            val s = Star()
            s.x = Random.nextFloat() * w
            s.y = Random.nextFloat() * h * 0.80f
            // 绝大多数是细小暗星，少量亮星稍大——整片均匀等大的星星会显得很假
            val bright = Random.nextFloat() > 0.88f
            s.r = if (bright) dp(1.05f + Random.nextFloat() * 0.55f)
            else dp(0.40f + Random.nextFloat() * 0.32f)
            s.base = if (bright) 0.78f + Random.nextFloat() * 0.22f
            else 0.28f + Random.nextFloat() * 0.40f
            s.phase = Random.nextFloat() * TWO_PI
            s.speed = 0.6f + Random.nextFloat() * 1.8f
            stars.add(s)
        }
    }

    /**
     * 烘焙一朵云的位图：底部一排偏暗的灰蓝压出厚度，主体用大小交错的白色软块拼出
     * 不规则轮廓，顶部再叠几团隆起。软块是 0→1 半径的三段径向渐变，边缘很软。
     */
    private fun bakeCloudSprite(seed: Int): Bitmap {
        val bmp = Bitmap.createBitmap(SPRITE_W, SPRITE_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rnd = Random(seed)
        val w = SPRITE_W.toFloat()
        val h = SPRITE_H.toFloat()

        fun puff(cx: Float, cy: Float, r: Float, color: Int) {
            // 用多段渐变的近似高斯衰减，而不是「实心芯 + 硬边」两段式：
            // 两段式在大量软块重叠时，实心芯会沿同一高度连成可见的横向条带
            // （雾天这种低对比背景下尤其明显）。
            val base = color and 0x00FFFFFF
            val a = color ushr 24
            fun fade(f: Float) = base or ((a * f).toInt().coerceIn(0, 255) shl 24)
            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(fade(1f), fade(0.92f), fade(0.62f), fade(0.30f), fade(0.09f), fade(0f)),
                floatArrayOf(0f, 0.20f, 0.44f, 0.66f, 0.85f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, paint)
        }

        val shade = 0xFFA9B9C9.toInt()
        val body = 0xFFFFFFFF.toInt()

        // 底部背光面（带纵向抖动，避免各软块中心连成一条水平线）
        var x = w * 0.20f
        while (x <= w * 0.80f) {
            puff(
                x, h * (0.60f + rnd.nextFloat() * 0.10f),
                h * (0.20f + rnd.nextFloat() * 0.08f), shade
            )
            x += w * 0.11f
        }
        // 主体团块
        x = w * 0.14f
        while (x <= w * 0.87f) {
            puff(
                x, h * (0.44f + rnd.nextFloat() * 0.14f),
                h * (0.22f + rnd.nextFloat() * 0.12f), body
            )
            x += w * (0.08f + rnd.nextFloat() * 0.05f)
        }
        // 顶部隆起
        x = w * 0.28f
        while (x <= w * 0.72f) {
            puff(
                x, h * (0.28f + rnd.nextFloat() * 0.12f),
                h * (0.15f + rnd.nextFloat() * 0.09f), body
            )
            x += w * 0.10f
        }
        return bmp
    }

    /** 通用光晕精灵：白色径向渐变，靠 colorFilter 着色后给日月与星点复用。 */
    private fun bakeGlowSprite(): Bitmap {
        val size = GLOW_SPRITE_PX
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val half = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            half, half, half,
            intArrayOf(WHITE, WHITE, WHITE and 0x00FFFFFF),
            floatArrayOf(0f, 0.18f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(half, half, half, paint)
        return bmp
    }

    // ------------------------------------------------------------ 工具

    private fun parseHour(hhmm: String?): Float? {
        if (hhmm.isNullOrBlank()) return null
        val h = hhmm.substringBefore(":").trim().toIntOrNull() ?: return null
        val m = hhmm.substringAfter(":", "").trim().toIntOrNull() ?: 0
        return h + m / 60f
    }

    private fun gauss(x: Float, center: Float, width: Float): Float {
        val t = (x - center) / width
        return exp(-t * t)
    }

    /** 平滑双闪脉冲：在 [at] 附近的高斯峰，宽 [width]。 */
    private fun spike(t: Float, at: Float, width: Float): Float {
        val d = (t - at) / width
        return exp(-d * d)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        val ia = 1f - t
        val r = ((a shr 16 and 0xFF) * ia + (b shr 16 and 0xFF) * t).toInt()
        val g = ((a shr 8 and 0xFF) * ia + (b shr 8 and 0xFF) * t).toInt()
        val bl = ((a and 0xFF) * ia + (b and 0xFF) * t).toInt()
        val al = ((a ushr 24) * ia + (b ushr 24) * t).toInt()
        return (al shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun rampFor(sky: Sky): Ramp = when (sky) {
        Sky.SUNNY -> Ramp(
            0xFF1463D6.toInt(), 0xFF3E9BEA.toInt(), 0xFFA9DEF9.toInt(),
            cloudTint = 0xFFFFFFFF.toInt(), cloudAlpha = 0.62f, cloudSpeed = 1f,
            cloudCount = 5, starCount = 0, lightning = false
        )
        Sky.CLOUDY -> Ramp(
            0xFF3C5470.toInt(), 0xFF66809A.toInt(), 0xFFA6BAC9.toInt(),
            cloudTint = 0xFFDCE6EF.toInt(), cloudAlpha = 0.70f, cloudSpeed = 0.85f,
            cloudCount = 7, starCount = 0, lightning = false
        )
        Sky.RAIN -> Ramp(
            0xFF1E2B39.toInt(), 0xFF33475C.toInt(), 0xFF5D7689.toInt(),
            cloudTint = 0xFFB4C2CE.toInt(), cloudAlpha = 0.78f, cloudSpeed = 1.9f,
            cloudCount = 8, starCount = 0, lightning = false
        )
        Sky.STORM -> Ramp(
            0xFF111A24.toInt(), 0xFF1F2C3B.toInt(), 0xFF3C5165.toInt(),
            cloudTint = 0xFF8E9BAA.toInt(), cloudAlpha = 0.84f, cloudSpeed = 2.5f,
            cloudCount = 9, starCount = 0, lightning = true
        )
        Sky.SNOW -> Ramp(
            0xFF54697E.toInt(), 0xFF7E95AA.toInt(), 0xFFC6D6E4.toInt(),
            cloudTint = 0xFFEDF4FA.toInt(), cloudAlpha = 0.58f, cloudSpeed = 0.7f,
            cloudCount = 6, starCount = 0, lightning = false
        )
        Sky.FOG -> Ramp(
            0xFF6D7B87.toInt(), 0xFF8D9BA7.toInt(), 0xFFBCC6CE.toInt(),
            cloudTint = 0xFFE8EEF3.toInt(), cloudAlpha = 0.40f, cloudSpeed = 0.45f,
            cloudCount = 5, starCount = 0, lightning = false
        )
        Sky.NIGHT -> Ramp(
            0xFF05081A.toInt(), 0xFF141C38.toInt(), 0xFF2E3760.toInt(),
            cloudTint = 0xFF7E8AA6.toInt(), cloudAlpha = 0.40f, cloudSpeed = 0.6f,
            cloudCount = 5, starCount = 88, lightning = false
        )
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()

        /** 日出/日落染暖用的金色。 */
        const val GOLDEN = 0xFFFFB673.toInt()

        /** 云团精灵的宽高比（宽 : 高）。 */
        const val CLOUD_ASPECT = 2.0f
        const val SPRITE_W = 640
        const val SPRITE_H = 320
        const val GLOW_SPRITE_PX = 256

        /** 云层基础速度（dp/s），再乘天气系数与景深系数。 */
        const val BASE_CLOUD_SPEED_DP = 2.6f

        /** 底色渐变上下漂移一个来回的周期（秒）。 */
        const val DRIFT_PERIOD_SEC = 26f

        /** 重绘间隔（秒）。天空运动很慢，30fps 足够；闪电期间会临时全速。 */
        const val REDRAW_INTERVAL_SEC = 1f / 30f

        /** 闪电整段衰减时长（秒）。 */
        const val FLASH_DECAY_SEC = 0.62f

        /** 地平线亮带占据画面高度的区间。 */
        const val HORIZON_TOP = 0.40f
        const val HORIZON_BOTTOM = 0.86f

        const val TWO_PI = (2.0 * PI).toFloat()

        const val DEFAULT_SUNRISE = 6.2f
        const val DEFAULT_SUNSET = 18.4f
    }
}
