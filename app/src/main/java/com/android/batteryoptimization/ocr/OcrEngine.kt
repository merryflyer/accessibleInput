package com.android.batteryoptimization.ocr

import android.content.Context
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
                labelPath = "models/ocr/ppocr_keys_v1.txt",
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
     * Must be called after [loadModels] succeeds.
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

            // Map from PaddleOCR4Android results to our OcrResult format
            val appResults = rawResults.mapNotNull { raw ->
                val text = raw.label?.trim() ?: ""
                if (text.isBlank()) return@mapNotNull null

                val confidence = raw.confidence
                val normalizedBox = convertBoxToNormalized(raw, bitmap.width, bitmap.height)

                OcrResult(
                    text = text,
                    confidence = confidence,
                    box = normalizedBox
                )
            }

            // Sort top-to-bottom
            appResults.sortedBy { it.box?.top ?: 0f }.also {
                Log.d(TAG, "Recognition produced ${it.size} text lines")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR recognition failed", e)
            emptyList()
        }
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
