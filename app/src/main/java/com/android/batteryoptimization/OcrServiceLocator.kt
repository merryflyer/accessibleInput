package com.android.batteryoptimization

import com.android.batteryoptimization.ocr.api.IOcrService
import com.didi.drouter.api.DRouter

/**
 * OCR 服务寻址助手：通过 DRouter 按 path 获取 [IOcrService] 实现。
 *
 * 解耦关键：:app 完全不 import :ocr_module 的任何类，只依赖 :ocr_api 中的接口。
 * 当 :ocr_module 未集成（gradle.properties 中 useOcr=false）时，DRouter 找不到对应路由，
 * 返回 null —— 主 module 据此优雅降级（OCR 能力关闭），但其余功能照常运行。
 */
object OcrServiceLocator {
    fun get(): IOcrService? = try {
        // 若所用 DRouter 版本采用 ServiceLoader 形式，可改为：
        //   ServiceLoader.load(IOcrService::class.java).getInstance()
        DRouter.build(IOcrService.PATH).getService() as? IOcrService
    } catch (e: Throwable) {
        null
    }
}
