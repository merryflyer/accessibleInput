package com.android.batteryoptimization.ocr.api

import android.content.Context

/**
 * 全局 Application Context 持有者。
 *
 * :ocr_module 中的服务实现需要 Context 来初始化 PaddleOCR 引擎，
 * 但 :ocr_module 不能直接依赖 :app。因此由 :app 在 [init] 中注入 Context，
 * :ocr_module 通过本对象读取——双方仅通过 :ocr_api 共享该能力，保持模块间解耦。
 */
object OcrContextHolder {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Context = appContext
}
