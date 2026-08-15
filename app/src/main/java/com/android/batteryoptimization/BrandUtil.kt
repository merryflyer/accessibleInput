package com.android.batteryoptimization

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * 品牌识别 & 各品牌专属设置页跳转。
 * 参考社区经验的常用包名/Activity，若跳转失败会 fallback 到应用详情页。
 */
object BrandUtil {

    private const val TAG = "BrandUtil"

    enum class Brand {
        VIVO,     // 含 iQOO
        XIAOMI,   // 含 Redmi / POCO
        HUAWEI,   // 含 Honor（老）
        OPPO,     // 含 Realme / OnePlus
        SAMSUNG,
        OTHER
    }

    fun currentBrand(): Brand {
        val brand = (Build.BRAND ?: "").lowercase()
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val product = (Build.PRODUCT ?: "").lowercase()
        val finger = (Build.FINGERPRINT ?: "").lowercase()
        return when {
            brand.contains("vivo") || manufacturer.contains("vivo")
                || product.contains("iqoo") || finger.contains("iqoo") -> Brand.VIVO
            brand.contains("xiaomi") || manufacturer.contains("xiaomi")
                || brand.contains("redmi") || brand.contains("poco") -> Brand.XIAOMI
            brand.contains("huawei") || manufacturer.contains("huawei")
                || brand.contains("honor") -> Brand.HUAWEI
            brand.contains("oppo") || manufacturer.contains("oppo")
                || brand.contains("oneplus") || brand.contains("realme") -> Brand.OPPO
            brand.contains("samsung") || manufacturer.contains("samsung") -> Brand.SAMSUNG
            else -> Brand.OTHER
        }
    }

    // ─────────── 跳转：应用详情（兜底） ───────────
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }

    // ─────────── 跳转：系统电池优化白名单 ───────────
    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    // ─────────── 跳转：自启动管理（各品牌原生页面） ───────────
    fun autoStartIntent(context: Context): Intent {
        return when (currentBrand()) {
            Brand.VIVO -> {
                val candidates = listOf(
                    // vivo 自启动
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    ),
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.PurviewTabActivity"
                    ),
                    // iQOO (旧版)
                    ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    ),
                    ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                    )
                )
                candidates.firstOrNull { resolve(context, Intent().setComponent(it)) }
                    ?.let { Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?: appDetailsIntent(context)
            }
            Brand.XIAOMI -> {
                val candidates = listOf(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
                candidates.firstOrNull { resolve(context, Intent().setComponent(it)) }
                    ?.let { Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?: appDetailsIntent(context)
            }
            Brand.HUAWEI -> {
                val candidates = listOf(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    ),
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                )
                candidates.firstOrNull { resolve(context, Intent().setComponent(it)) }
                    ?.let { Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?: appDetailsIntent(context)
            }
            Brand.OPPO -> {
                val candidates = listOf(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    ),
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                )
                candidates.firstOrNull { resolve(context, Intent().setComponent(it)) }
                    ?.let { Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?: appDetailsIntent(context)
            }
            Brand.SAMSUNG, Brand.OTHER -> appDetailsIntent(context)
        }
    }

    // ─────────── 跳转：后台耗电/高耗电白名单 ───────────
    fun backgroundPowerIntent(context: Context): Intent {
        return when (currentBrand()) {
            Brand.VIVO -> {
                val candidates = listOf(
                    ComponentName(
                        "com.vivo.batterysaving",
                        "com.vivo.batterysaving.BatterySavingActivity"
                    ),
                    ComponentName(
                        "com.iqoo.powersaving",
                        "com.iqoo.powersaving.PowerSavingMainActivity"
                    )
                )
                candidates.firstOrNull { resolve(context, Intent().setComponent(it)) }
                    ?.let { Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?: appDetailsIntent(context)
            }
            else -> batteryOptimizationIntent()
        }
    }

    // ─────────── 跳转：权限总览 / 单项权限（用于开启「后台弹出界面」等） ───────────
    fun permissionManagerIntent(context: Context): Intent {
        return when (currentBrand()) {
            Brand.VIVO -> {
                val candidates = listOf(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"
                    )
                )
                val intent = candidates.firstOrNull { resolve(context, Intent().setComponent(it)) }
                    ?.let { Intent().setComponent(it) }
                    ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            else -> appDetailsIntent(context)
        }
    }

    private fun resolve(context: Context, intent: Intent): Boolean {
        return try {
            context.packageManager.resolveActivity(intent, 0) != null
        } catch (t: Throwable) {
            Log.w(TAG, "resolve failed for ${intent.component}", t)
            false
        }
    }
}
