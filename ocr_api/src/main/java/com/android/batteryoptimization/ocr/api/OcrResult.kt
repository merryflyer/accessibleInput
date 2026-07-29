package com.android.batteryoptimization.ocr.api

import android.graphics.RectF

/**
 * OCR 识别结果。
 * @param box 文本区域在截图中的归一化坐标（0..1，left/top/right/bottom）
 */
data class OcrResult(
    val text: String,
    val confidence: Float,
    val box: RectF? = null
)
