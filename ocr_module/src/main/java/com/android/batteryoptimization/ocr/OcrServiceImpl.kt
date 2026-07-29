package com.android.batteryoptimization.ocr

import android.graphics.Bitmap
import com.android.batteryoptimization.ocr.api.IOcrService
import com.android.batteryoptimization.ocr.api.OcrContextHolder
import com.android.batteryoptimization.ocr.api.OcrResult
import com.didi.drouter.annotation.Service
import com.didi.drouter.api.Extend

/**
 * OCR 服务的具体实现，注册到 DRouter，由 :app 通过 [IOcrService.PATH] 寻址调用。
 *
 * - 由 DRouter 以单例形式创建（无参构造），引擎在首次 [loadModels]/[recognize] 时惰性创建；
 * - 需要 Application Context 时通过 [OcrContextHolder] 获取（:app 在 Application.onCreate 注入）。
 */
@Service(function = [IOcrService::class], cache = Extend.Cache.SINGLETON)
class OcrServiceImpl : IOcrService {

    @Volatile
    private var engine: OcrEngine? = null

    @Synchronized
    private fun ensureEngine(): OcrEngine {
        if (engine == null) {
            engine = OcrEngine(OcrContextHolder.get())
        }
        return engine!!
    }

    override fun loadModels() {
        ensureEngine().loadModels()
    }

    override val isReady: Boolean
        get() = engine?.isReady ?: false

    override suspend fun recognize(bitmap: Bitmap): List<OcrResult> {
        return ensureEngine().recognize(bitmap)
    }

    override fun destroy() {
        engine?.destroy()
        engine = null
    }
}
