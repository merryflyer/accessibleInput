package com.android.batteryoptimization

import android.app.Application
import com.android.batteryoptimization.ocr.api.OcrContextHolder
import com.didi.drouter.api.DRouter

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 注入 Application Context，供 :ocr_module 中经 DRouter 寻址到的服务使用
        OcrContextHolder.init(this)
        // 初始化 DRouter（必须在通过 DRouter.build().getService() 之前调用）
        DRouter.init(this)
    }
}
