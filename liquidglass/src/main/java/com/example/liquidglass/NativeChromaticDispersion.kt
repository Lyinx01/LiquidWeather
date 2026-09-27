/**
 * 色散效果（纯 Kotlin 软件实现）
 *
 * 原 AAR 版本通过 JNI 调用 C++ 加速；本项目以源码形式引入且不带 NDK 构建，
 * 故此对象改为纯 Kotlin 实现（按法线贴图沿边缘做基础折射采样）。
 * 应用当前 dispersionStrength=0，此路径不会触发；保留实现以维持 API 兼容。
 */
package com.example.liquidglass

import android.graphics.Bitmap

object NativeChromaticDispersion {

    /**
     * 应用色散效果（原位处理）
     *
     * @param source 源图像
     * @param edgeDistance 边缘距离贴图（R：归一化边缘距离）
     * @param normalMap 法线贴图（可选；R：X 法线，G：Y 法线，128 为中心）
     * @param result 结果图像
     * @param refThickness 折射厚度
     * @param refFactor 折射系数
     * @param refDispersion 色散增益
     * @param dpr 设备像素比
     * @param useBilinear 是否双线性插值（软件实现使用最近邻）
     */
    fun chromaticDispersionInplace(
        source: Bitmap,
        edgeDistance: Bitmap,
        normalMap: Bitmap?,
        result: Bitmap,
        refThickness: Float,
        refFactor: Float,
        refDispersion: Float,
        dpr: Float,
        useBilinear: Boolean
    ) {
        val w = source.width
        val h = source.height
        val src = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        val edge = IntArray(w * h)
        edgeDistance.getPixels(edge, 0, w, 0, 0, w, h)
        val normal = if (normalMap != null) {
            IntArray(w * h).also { normalMap.getPixels(it, 0, w, 0, 0, w, h) }
        } else null
        val out = IntArray(w * h)

        val thickness = refThickness * dpr
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val e = (edge[idx] and 0xFF) / 255f
                // 越靠边缘折射位移越大（与透镜厚度剖面一致）
                val amount = (1f - e) * thickness * refFactor * refDispersion * 0.1f
                val nx: Float
                val ny: Float
                if (normal != null) {
                    nx = (((normal[idx] shr 16) and 0xFF) - 128) / 128f
                    ny = (((normal[idx] shr 8) and 0xFF) - 128) / 128f
                } else {
                    nx = 0f
                    ny = 0f
                }
                val sx = (x + nx * amount).toInt().coerceIn(0, w - 1)
                val sy = (y + ny * amount).toInt().coerceIn(0, h - 1)
                out[idx] = src[sy * w + sx]
            }
        }
        result.setPixels(out, 0, w, 0, 0, w, h)
    }

    /** 便捷方法：应用色散效果并返回新 Bitmap */
    fun apply(
        source: Bitmap,
        edgeDistance: Bitmap,
        normalMap: Bitmap? = null,
        refThickness: Float = 100f,
        refFactor: Float = 1.5f,
        refDispersion: Float = 7f,
        dpr: Float = 1.0f,
        useBilinear: Boolean = true
    ): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        chromaticDispersionInplace(source, edgeDistance, normalMap, result, refThickness, refFactor, refDispersion, dpr, useBilinear)
        return result
    }
}
