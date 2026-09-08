package com.qyf.rememberenglish.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build

/**
 * 相册图片解码为位图（拍照/相册识别共用管线入口）。
 * API 28+ 用 ImageDecoder：自动处理 EXIF 旋转 + 降采样；
 * 统一输出 software bitmap（手写增强需要读像素）。
 */
object PhotoDecoder {

    fun decode(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val largest = maxOf(info.size.width, info.size.height)
                if (largest > maxDim) {
                    decoder.setTargetSampleSize((largest + maxDim - 1) / maxDim)
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            // API 26/27：两段解码降采样（不支持 EXIF 旋转，属边缘机型）
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val largest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (largest / (sample * 2) >= maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }
    }.getOrNull()
}
