# OCR 引擎实现与 DRouter 服务实现需保留（DRouter 通过路由表实例化，release 混淆时需保留）
-keep class com.android.batteryoptimization.ocr.OcrEngine { *; }
-keep class com.android.batteryoptimization.ocr.OcrServiceImpl { *; }

# PaddleLite 推理库
-keep class com.baidu.paddle.lite.** { *; }
-dontwarn com.baidu.paddle.lite.**
