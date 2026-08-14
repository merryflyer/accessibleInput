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

    private val appScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        setupGlobalExceptionHandler()
        // 注入 Application Context，供 :ocr_module 中经 DRouter 寻址到的服务使用
        OcrContextHolder.init(this)
        // 初始化 DRouter（必须在通过 DRouter.build().getService() 之前调用）
        DRouter.init(this)
        // 从缓存恢复高德 SDK Key，避免等待 WebSocket 下发 amap_config
        AMapLocationHelper.initFromCache(this)
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
        // 1. 开关没开，直接跳过（等用户自己去开）
        if (!isAccessibilityEnabledInSettings(context)) return true
        // 2. 实例已就绪，正常
        if (InputAccessibilityService.instance != null) return true
        // 3. 命中故障状态：尝试通过重写 Settings 触发系统重新绑定服务
        Log.w("App", "Accessibility ENABLED but instance=null → try heal by rewriting Secure settings")
        val serviceName = "${context.packageName}/${InputAccessibilityService::class.java.canonicalName}"
        try {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (current == null || !current.contains(serviceName, ignoreCase = true)) {
                Log.w("App", "Service not in ENABLED_ACCESSIBILITY_SERVICES, current=$current")
                return false
            }
            // 先移除再加回，强制系统重新解析并绑定
            val remaining = current.split(':')
                .filterNot { it.equals(serviceName, ignoreCase = true) }
                .joinToString(":")
            val rewritten = if (remaining.isEmpty()) serviceName else "$remaining:$serviceName"
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, rewritten)
            Log.d("App", "Rewrote ENABLED_ACCESSIBILITY_SERVICES, expect rebind shortly")
        } catch (e: SecurityException) {
            // 部分 ROM 不允许三方应用写 Settings.Secure（无 WRITE_SECURE_SETTINGS 权限）
            // 退化为：主动拉起保活服务，希望系统借此重启绑定流程
            Log.e("App", "Cannot write Secure settings, fallback to start KeepAliveService", e)
            try {
                context.startService(Intent(context, KeepAliveService::class.java))
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            Log.e("App", "Accessibility heal failed", e)
        }
        return false
    }

    private fun isAccessibilityEnabledInSettings(context: Context): Boolean {
        var accessibilityEnabled = 0
        val service = "${context.packageName}/${InputAccessibilityService::class.java.canonicalName}"
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (_: Settings.SettingNotFoundException) {
        }
        if (accessibilityEnabled == 1) {
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
        }
        return false
    }
}
