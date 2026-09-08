package com.qyf.rememberenglish.data.ocr

import android.graphics.Bitmap
import android.graphics.Color

/**
 * 手写增强预处理（用户 2026-09-08 要求提升手写识别率）：
 * 灰度 + 亮度线性拉伸（2%~98% 分位对比度拉伸），
 * 放大手写墨迹与纸面的灰度差，帮助 ML Kit 提取笔画。印刷体默认不开启。
 */
object ImageEnhancer {

    fun enhance(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val histogram = IntArray(256)
        for (pixel in pixels) histogram[luminance(pixel)]++
        val total = pixels.size
        val lowCut = (total * 0.02).toInt()
        val highCut = (total * 0.98).toInt()
        var lo = 0
        var hi = 255
        var acc = 0
        for (v in 0..255) {
            acc += histogram[v]
            if (acc >= lowCut) {
                lo = v
                break
            }
        }
        acc = 0
        for (v in 0..255) {
            acc += histogram[v]
            if (acc >= highCut) {
                hi = v
                break
            }
        }
        val scale = if (hi - lo < 24) 1.0 else 255.0 / (hi - lo)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = ((luminance(pixel) - lo) * scale).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(gray, gray, gray) or (pixel and 0xFF000000.toInt())
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
