package com.android.batteryoptimization.ocr.api

import android.graphics.Bitmap
import android.graphics.RectF
import com.didi.drouter.standard.Service

/**
 * OCR 服务接口（跨模块通信契约）。
 *
 * 主 module（:app）只依赖本接口与 [OcrResult]，不直接依赖具体实现；
 * 具体实现位于独立 module（:ocr_module），通过 DRouter 以 [PATH] 路由暴露。
 * 因此 :app 即便不依赖 :ocr_module 也能独立运行（未集成时 getService 返回 null，OCR 降级关闭）。
 */
@Service
interface IOcrService {

    companion object {
        /** DRouter 路由地址，:ocr_module 中的实现以相同 path 注册 */
        const val PATH = "/ocr/service"
    }

    /** 加载 OCR 推理模型。建议在后台线程调用（首次调用会惰性初始化引擎）。 */
    fun loadModels()

    /** 引擎是否已加载完成、可供识别 */
    val isReady: Boolean

    /**
     * 对位图执行 OCR 识别，返回文本区域列表（已过滤低置信度与键盘区域）。
     * 自动检测键盘是否可见：键盘可见时过滤掉底部约 40% 区域。
     */
    suspend fun recognize(bitmap: Bitmap): List<OcrResult>

    /** 释放 OCR 引擎资源 */
    fun destroy()
}
