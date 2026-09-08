package com.qyf.rememberenglish.data.online

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 百度 OCR 响应解析结果 */
sealed interface BaiduOcrResult {
    data class Ok(val lines: List<String>) : BaiduOcrResult

    /** access_token 无效/过期（应刷新后重试） */
    data object AuthError : BaiduOcrResult

    /** 其他业务错误（配额用尽、图片非法等） */
    data class Failed(val message: String) : BaiduOcrResult
}

/** 纯 Kotlin 解析（可单测）：百度手写 OCR 的 token 与识别响应 */
object BaiduOcrParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** token 响应 → (access_token, 有效期秒)，无效返回 null */
    fun parseToken(text: String): Pair<String, Int>? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val token = runCatching { root.getValue("access_token").jsonPrimitive.content }.getOrNull()
            ?: return null
        val expiresIn = runCatching { root.getValue("expires_in").jsonPrimitive.content.toInt() }
            .getOrDefault(DEFAULT_EXPIRES_IN)
        return token to expiresIn
    }

    /** 识别响应 → 文字行；error_code 110/111 = token 问题（刷新重试），其余为业务错误 */
    fun parseOcr(text: String): BaiduOcrResult {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return BaiduOcrResult.Failed("响应解析失败")
        val errorCode = runCatching { root.getValue("error_code").jsonPrimitive.content.toInt() }.getOrNull()
        if (errorCode != null) {
            val message = runCatching { root.getValue("error_msg").jsonPrimitive.content }
                .getOrDefault("错误码 $errorCode")
            return if (errorCode == 110 || errorCode == 111) BaiduOcrResult.AuthError
            else BaiduOcrResult.Failed(message)
        }
        val lines = runCatching {
            root.getValue("words_result").jsonArray.mapNotNull { element ->
                runCatching { element.jsonObject.getValue("words").jsonPrimitive.content.trim() }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.getOrDefault(emptyList())
        return BaiduOcrResult.Ok(lines)
    }

    private const val DEFAULT_EXPIRES_IN = 2_592_000
}

/**
 * 百度智能云 手写文字识别（用户 2026-09-08 要求提升手写识别率）：
 * 离线 ML Kit 对手写识别率不足，拍照/相册静态图优先走云端（识别更准），失败自动回退本地。
 * 密钥由用户在"我的"页填入（DataStore 本地存储，不入仓库）；
 * 用 HttpURLConnection（无新依赖）；token 内存缓存约 30 天，失效自动刷新重试一次。
 */
@Singleton
class BaiduHandwritingClient @Inject constructor() {

    private val tokenMutex = Mutex()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenExpireAtMs = 0L

    /**
     * 识别一张已转正的图片，返回识别文本（行以 \n 连接）；
     * 失败（无密钥/网络/配额）返回 null，由调用方回退本地识别。
     */
    suspend fun recognize(apiKey: String, secretKey: String, bitmap: Bitmap): String? {
        if (apiKey.isBlank() || secretKey.isBlank()) return null
        return withContext(Dispatchers.IO) {
            val base64 = encodeImage(bitmap) ?: return@withContext null
            val token = getToken(apiKey, secretKey) ?: return@withContext null
            val result = request(base64, token)
            val finalResult = if (result is BaiduOcrResult.AuthError) {
                // token 被服务端回收等 → 强制刷新重试一次
                cachedToken = null
                val refreshed = fetchToken(apiKey, secretKey) ?: return@withContext null
                request(base64, refreshed)
            } else {
                result
            }
            (finalResult as? BaiduOcrResult.Ok)
                ?.lines
                ?.joinToString("\n")
                ?.takeIf { it.isNotBlank() }
        }
    }

    private suspend fun getToken(apiKey: String, secretKey: String): String? = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        val cached = cachedToken
        if (cached != null && now < tokenExpireAtMs) return cached
        return fetchToken(apiKey, secretKey)
    }

    private suspend fun fetchToken(apiKey: String, secretKey: String): String? =
        runCatching {
            val url = URL(
                "https://aip.baidubce.com/oauth/2.0/token" +
                    "?grant_type=client_credentials" +
                    "&client_id=${urlEncode(apiKey)}" +
                    "&client_secret=${urlEncode(secretKey)}",
            )
            val text = (url.openConnection() as HttpURLConnection).let { conn ->
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                try {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            }
            BaiduOcrParser.parseToken(text)?.let { (token, expiresIn) ->
                cachedToken = token
                tokenExpireAtMs = System.currentTimeMillis() + (expiresIn - EXPIRE_MARGIN_S) * 1_000L
                token
            }
        }.onFailure { cachedToken = null }.getOrNull()

    /** 识别请求：表单 image=<base64>，detect_direction 容忍拍摄倾斜 */
    private fun request(base64Image: String, token: String): BaiduOcrResult =
        runCatching {
            val url = URL("https://aip.baidubce.com/rest/2.0/ocr/v1/handwriting?access_token=$token")
            val body = "image=${urlEncode(base64Image)}&detect_direction=true"
            val text = (url.openConnection() as HttpURLConnection).let { conn ->
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(body.toByteArray()) }
                try {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            }
            BaiduOcrParser.parseOcr(text)
        }.getOrElse { BaiduOcrResult.Failed("网络请求失败") }

    /** 压到长边 ≤1600 再 JPEG 编码，控制上传体积（手写词场景足够清晰） */
    private fun encodeImage(bitmap: Bitmap): String? = runCatching {
        val maxDim = 1600
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > maxDim) {
            val scale = maxDim.toFloat() / largest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val bytes = ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            stream.toByteArray()
        }
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        /** 提前一天刷新，避免临界过期 */
        const val EXPIRE_MARGIN_S = 86_400
    }
}
