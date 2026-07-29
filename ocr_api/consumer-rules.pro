# OCR 数据类 / 接口（被主 module 通过 IOcrService 消费，release 混淆时需保留）
-keep class com.android.batteryoptimization.ocr.api.OcrResult { *; }
-keep class com.android.batteryoptimization.ocr.api.IOcrService { *; }
-keep class com.android.batteryoptimization.ocr.api.OcrContextHolder { *; }
