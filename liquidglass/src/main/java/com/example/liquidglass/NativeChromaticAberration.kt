/**
 * NativeChromaticAberration - 色差效果（纯 Kotlin 软件实现）
 *
 * 原 AAR 版本通过 JNI 调用 C++ 加速；本项目以源码形式引入且不带 NDK 构建，
 * 故此对象改为纯 Kotlin 实现（按位移贴图对 RGB 通道做位移采样）。
 * 应用当前 aberrationIntensity=0，此路径不会触发；保留实现以维持 API 兼容。
 */
package com.example.liquidglass

import android.graphics.Bitmap

object NativeChromaticAberration {

    /**
     * 应用色差效果（原位处理）
     *
     * @param source 源图像（ARGB_8888）
     * @param displacement 位移贴图（R：X 位移，G：Y 位移，128 为中心）
     * @param result 结果图像
     * @param intensity 色差强度
     * @param scale 位移缩放系数
     * @param redOffset 红色通道额外位移
     * @param greenOffset 绿色通道额外位移
     * @param blueOffset 蓝色通道额外位移
     * @param useBilinear 是否双线性插值（软件实现使用最近邻，速度优先）
     */
    fun chromaticAberrationInplace(
        source: Bitmap,
        displacement: Bitmap,
        result: Bitmap,
        intensity: Float = 2.0f,
        scale: Float = 70.0f,
        redOffset: Float = 0.0f,
        greenOffset: Float = -0.05f,
        blueOffset: Float = -0.1f,
        useBilinear: Boolean = true
    ) {
        val w = source.width
        val h = source.height
        val src = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        val disp = IntArray(w * h)
        displacement.getPixels(disp, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val dm = disp[y * w + x]
                val dx = (((dm shr 16) and 0xFF) - 128) / 128f * scale * intensity
                val dy = (((dm shr 8) and 0xFF) - 128) / 128f * scale * intensity

                val rX = (x + dx + redOffset * intensity).toInt().coerceIn(0, w - 1)
                val rY = (y + dy + redOffset * intensity).toInt().coerceIn(0, h - 1)
                val gX = (x + dx + greenOffset * intensity).toInt().coerceIn(0, w - 1)
                val gY = (y + dy + greenOffset * intensity).toInt().coerceIn(0, h - 1)
                val bX = (x + dx + blueOffset * intensity).toInt().coerceIn(0, w - 1)
                val bY = (y + dy + blueOffset * intensity).toInt().coerceIn(0, h - 1)

                val r = (src[rY * w + rX] shr 16) and 0xFF
                val g = (src[gY * w + gX] shr 8) and 0xFF
                val b = src[bY * w + bX] and 0xFF
                val a = (src[y * w + x] ushr 24) and 0xFF
                out[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        result.setPixels(out, 0, w, 0, 0, w, h)
    }

    /** 应用色差效果（便捷方法，创建新的结果 Bitmap） */
    fun apply(
        source: Bitmap,
        displacement: Bitmap,
        intensity: Float = 2.0f,
        scale: Float = 70.0f,
        redOffset: Float = 0.0f,
        greenOffset: Float = -0.05f,
        blueOffset: Float = -0.1f,
        useBilinear: Boolean = true
    ): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        chromaticAberrationInplace(source, displacement, result, intensity, scale, redOffset, greenOffset, blueOffset, useBilinear)
        return result
    }

    fun validateBitmap(bitmap: Bitmap): Boolean {
        return bitmap.config == Bitmap.Config.ARGB_8888 && bitmap.isMutable
    }

    fun validateDimensions(vararg bitmaps: Bitmap): Boolean {
        if (bitmaps.isEmpty()) return true
        val width = bitmaps[0].width
        val height = bitmaps[0].height
        return bitmaps.all { it.width == width && it.height == height }
    }
}
