package com.android.batteryoptimization.ocr

data class OcrResult(
    val text: String,
    val confidence: Float,
    val box: RectF? = null  // text region in the screenshot (normalized 0..1)
)

data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
