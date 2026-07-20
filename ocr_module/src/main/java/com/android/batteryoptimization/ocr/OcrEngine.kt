package com.android.batteryoptimization.ocr

import android.content.Context
import com.android.batteryoptimization.ocr.api.OcrResult
import android.graphics.Bitmap
import android.util.Log
import com.equationl.paddleocr4android.CpuPowerMode
import com.equationl.paddleocr4android.OCR
import com.equationl.paddleocr4android.OcrConfig
import com.equationl.paddleocr4android.Util.paddle.OcrResultModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OCR engine using PaddleOCR4Android library.
 *
 * Models expected in assets/models/ocr/:
 *   ch_PP-OCRv3_det.nb   — text detection  (DB)
 *   ch_PP-OCRv3_rec.nb   — text recognition (CRNN)
 *   ppocr_keys_v1.txt    — character dictionary
 *
 * The PaddleOCR4Android library handles copying .nb files from assets
 * to filesDir/cache internally.
 */
class OcrEngine(private val context: Context) {

    companion object {
        private const val TAG = "OcrEngine"

        /** 最低置信度阈值，低于此值的结果将被过滤 */
        private const val CONFIDENCE_THRESHOLD = 0.8f

        /**
         * 键盘区域 Y 轴阈值（归一化坐标 0..1）。
         * top > 此值 且 内容匹配键盘特征 → 判定为键盘区域并过滤。
         */
        private const val KEYBOARD_Y_THRESHOLD = 0.6f

        /** 常见拼音音节（中文输入法键盘候选） */
        private val PINYIN_SET = setOf(
            "wo", "ni", "ta", "de", "le", "shi", "bu", "zai", "zhe", "na",
            "ma", "hao", "jiu", "dou", "yao", "hui", "mei", "zhi", "dao",
            "xian", "shen", "zen", "zenm", "zenme", "shenm", "shenme",
            "wei", "qu", "lai", "kan", "gei", "rang", "ba", "yi", "you",
            "ruo", "neng", "dang", "xia", "xie", "jie",
            "tui", "dui", "shuo", "hua", "tian", "nian", "jian", "dian",
            "da", "xiao", "duo", "shao", "gao", "di", "chang", "duan"
        )

        /** QWERTY 键盘行模式 */
        private val QWERTY_PATTERNS = listOf(
            Regex("^[qwertyuiop]+$", RegexOption.IGNORE_CASE),
            Regex("^[asdfghjkl]+$", RegexOption.IGNORE_CASE),
            Regex("^[zxcvbnm]+$", RegexOption.IGNORE_CASE)
        )
    }

    private val isLoaded = AtomicBoolean(false)
    private var ocr: OCR? = null

    /** 引擎是否已加载完成可供识别 */
    val isReady: Boolean get() = isLoaded.get()

    /**
     * Initialize PaddleOCR4Android.
     * Call once on service start (from a background thread).
     */
    fun loadModels() {
        if (isLoaded.get()) return
        try {
            ocr = OCR(context)

            val config = OcrConfig(
                modelPath = "models/ch_PP-OCRv4",
                cpuThreadNum = 4,
                cpuPowerMode = CpuPowerMode.LITE_POWER_HIGH,
                scoreThreshold = 0.3f,
                detLongSize = 960,
                detModelFilename = "det.nb",
                recModelFilename = "rec.nb",
                clsModelFilename = "cls.nb",
                isRunDet = true,
                isRunCls = true,
                isRunRec = true,
                isUseOpencl = false,
                isDrwwTextPositionBox = false
            )

            val result = ocr!!.initModelSync(config)
            if (result.isSuccess && result.getOrNull() == true) {
                isLoaded.set(true)
                Log.d(TAG, "OCR models loaded successfully")
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "Failed to load OCR models: ${error?.message}", error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OCR models", e)
        }
    }

    /**
     * Run OCR on a bitmap and return recognized text regions.
     *
     * 自动检测键盘是否可见：
     *  - 先扫一遍底部区域的识别结果
     *  - 如果发现键盘特征（单字母、QWERTY行、九宫格、拼音等）→ 判定键盘可见
     *  - 键盘可见时，底部 40% 区域的结果全部过滤掉
     *  - 无键盘特征时，保留所有结果
     */
    suspend fun recognize(bitmap: Bitmap): List<OcrResult> = withContext(Dispatchers.Default) {
        val engine = ocr ?: return@withContext emptyList()
        if (!isLoaded.get()) {
            Log.w(TAG, "Models not loaded yet")
            return@withContext emptyList()
        }

        try {
            val result = engine.runSync(bitmap)
            if (result.isFailure) {
                Log.e(TAG, "OCR recognition failed", result.exceptionOrNull())
                return@withContext emptyList()
            }

            val ocrResult = result.getOrNull() ?: return@withContext emptyList()
            val rawResults = ocrResult.outputRawResult

            if (rawResults.isEmpty()) {
                Log.d(TAG, "No text detected")
                return@withContext emptyList()
            }

            // 第一遍：过滤空白和低置信度，计算坐标
            data class Candidate(val text: String, val confidence: Float, val box: RectF?)
            val candidates = rawResults.mapNotNull { raw ->
                val text = raw.label?.trim() ?: ""
                if (text.isBlank()) return@mapNotNull null
                val confidence = raw.confidence
                if (confidence < CONFIDENCE_THRESHOLD) return@mapNotNull null
                val box = convertBoxToNormalized(raw, bitmap.width, bitmap.height)
                Candidate(text, confidence, box)
            }

            if (candidates.isEmpty()) {
                Log.d(TAG, "No text detected")
                return@withContext emptyList()
            }

            // 检测底部区域是否有键盘特征 → 判断键盘是否可见
            val hasKeyboard = candidates.any { c ->
                c.box != null && c.box.top > KEYBOARD_Y_THRESHOLD && isKeyboardContent(c.text)
            }

            // 第二遍：根据是否检测到键盘，决定是否过滤底部区域
            val appResults = candidates.filter { c ->
                if (hasKeyboard && c.box != null && c.box.top > KEYBOARD_Y_THRESHOLD) {
                    Log.v(TAG, "Filtered by keyboard: '$c.text' at top=${"%.2f".format(c.box.top)}")
                    false
                } else true
            }

            // Sort top-to-bottom and convert to OcrResult
            appResults.sortedBy { it.box?.top ?: 0f }.map {
                OcrResult(it.text, it.confidence, it.box)
            }.also {
                val filtered = candidates.size - it.size
                Log.d(TAG, "Recognition produced ${it.size} text lines" +
                        if (hasKeyboard) " (keyboard detected, filtered $filtered bottom lines)" else "")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR recognition failed", e)
            emptyList()
        }
    }

    /**
     * 判断文本是否为键盘内容特征（单字母、QWERTY行、九宫格、拼音等）。
     * 用于自动检测键盘是否可见。
     */
    private fun isKeyboardContent(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false

        // 单字母/数字（键盘按键）
        if (t.length == 1) {
            val c = t[0]
            return !(c in '一'..'鿿' || c in '㐀'..'䶿')  // 非中文 → 键盘按键
        }

        // 纯英文且长度 ≤ 4 → 常见拼音音节
        if (t.all { it in 'a'..'z' || it in 'A'..'Z' } && t.length <= 4) {
            if (t.lowercase() in PINYIN_SET) return true
        }

        // QWERTY 行模式
        if (QWERTY_PATTERNS.any { it.matches(t) }) return true

        // 纯数字（键盘数字行）
        if (t.all { it.isDigit() }) return true

        return false
    }

    /**
     * Convert OcrResultModel's absolute pixel coordinates to normalized 0..1 rect.
     */
    private fun convertBoxToNormalized(
        model: OcrResultModel,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): RectF? {
        val points = model.points ?: return null
        if (points.isEmpty()) return null

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        for (point in points) {
            if (point.x < minX) minX = point.x
            if (point.x > maxX) maxX = point.x
            if (point.y < minY) minY = point.y
            if (point.y > maxY) maxY = point.y
        }

        return RectF(
            left = minX.toFloat() / bitmapWidth,
            top = minY.toFloat() / bitmapHeight,
            right = maxX.toFloat() / bitmapWidth,
            bottom = maxY.toFloat() / bitmapHeight
        )
    }

    /**
     * Release all resources.
     */
    fun destroy() {
        isLoaded.set(false)
        ocr?.releaseModel()
        ocr = null
    }
}
