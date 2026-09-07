package com.qyf.rememberenglish.ui.add

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * 相机流式 OCR 分析器（CLAUDE.md 第五节）：
 * STRATEGY_KEEP_ONLY_LATEST 背压 + ~500ms 节流；ML Kit 离线拉丁模型。
 * 分析完成后必须 close(imageProxy)（ML Kit 持有 buffer）。
 */
@OptIn(markerClass = [ExperimentalGetImage::class])
class OcrTextAnalyzer(
    private val onText: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastAt = 0L

    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAt < THROTTLE_MS) {
            imageProxy.close()
            return
        }
        lastAt = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { onText(it.text) }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun stop() {
        recognizer.close()
    }

    companion object {
        private const val THROTTLE_MS = 500L
    }
}
