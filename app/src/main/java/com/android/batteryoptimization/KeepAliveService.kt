package com.android.batteryoptimization

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * 保活服务 — 提高进程优先级，防止被系统回收。
 * WebSocket 连接已移至 MainActivity，每次 APP 启动即开启。
 */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KeepAliveService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "KeepAliveService started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "KeepAliveService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "KeepAlive"
    }
}
