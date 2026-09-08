package com.qyf.rememberenglish.ui.add

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 实时取景 + OCR 分析 + 拍照绑定（"实时显示单词意思"主交互）。
 * [onText] 在主线程回调识别文本；[imageCapture] 供"拍照识别"使用；
 * [enhance] 手写增强开关实时同步到分析线程；组件离开组合时解绑相机并释放分析器。
 */
@Composable
fun OcrCameraPreview(
    onText: (String) -> Unit,
    imageCapture: ImageCapture,
    enhance: Boolean,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    // 用 holder 传引用，便于离开组合时 stop recognizer / 更新增强开关
    val analyzerHolder = remember { arrayOfNulls<OcrTextAnalyzer>(1) }

    LaunchedEffect(enhance) {
        analyzerHolder[0]?.enhance = enhance
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzerHolder[0]?.stop()
            mainExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val analyzer = OcrTextAnalyzer(onText)
            analyzerHolder[0] = analyzer
            imageAnalysis.setAnalyzer(mainExecutor, analyzer)

            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener(
                {
                    try {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                            imageCapture,
                        )
                    } catch (_: Exception) {
                        // 相机不可用（被占用等）：界面提示由权限/空态承担
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
    )
}
