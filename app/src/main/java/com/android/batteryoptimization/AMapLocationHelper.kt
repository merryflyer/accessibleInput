package com.android.batteryoptimization

import android.content.Context
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object AMapLocationHelper {

    private const val TAG = "AMapLocationHelper"
    private const val LOCATION_TIMEOUT_MS = 30000L
    private const val PREFS_NAME = "amap_prefs"
    private const val KEY_CACHED_SDK_KEY = "cached_sdk_key"
    private const val KEY_ERROR_LOGS = "location_error_logs"
    private const val MAX_ERROR_LOGS = 200
    private const val KEY_GPS_UPLOAD_LOGS = "gps_upload_logs"
    private const val MAX_GPS_UPLOAD_LOGS = 200
    private const val KEY_PENDING_LOCATIONS = "pending_locations"
    private const val MAX_PENDING_LOCATIONS = 100

    var isInit = false

    /**
     * 高德隐私合规初始化。必须在任何高德 SDK 接口（含 setApiKey）之前调用。
     * App.onCreate 中最先调用一次，全局生效；getLocation 内部会再保底调用一次。
     */
    fun initPrivacy(context: Context) {
        try {
            AMapLocationClient.updatePrivacyShow(context.applicationContext, true, true)
            AMapLocationClient.updatePrivacyAgree(context.applicationContext, true)
        } catch (e: Throwable) {
            Log.e(TAG, "initPrivacy failed", e)
        }
    }

    /** 记录定位错误日志到本地 */
    private fun logError(context: Context, errorCode: Any, errorInfo: String) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_ERROR_LOGS, null) ?: "[]"
            val type = object : TypeToken<MutableList<LocationErrorEntry>>() {}.type
            val list: MutableList<LocationErrorEntry> = Gson().fromJson(json, type) ?: mutableListOf()
            list.add(0, LocationErrorEntry(
                time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                timestamp = System.currentTimeMillis(),
                errorCode = errorCode.toString(),
                errorInfo = errorInfo
            ))
            if (list.size > MAX_ERROR_LOGS) list.subList(MAX_ERROR_LOGS, list.size).clear()
            prefs.edit().putString(KEY_ERROR_LOGS, Gson().toJson(list)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save location error log", e)
        }
    }

    /** 获取所有定位错误日志（按时间倒序） */
    fun getErrorLogs(context: Context): List<LocationErrorEntry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ERROR_LOGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LocationErrorEntry>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 清空定位错误日志 */
    fun clearErrorLogs(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_ERROR_LOGS).apply()
    }

    data class LocationErrorEntry(
        val time: String,
        val timestamp: Long,
        val errorCode: String,
        val errorInfo: String
    )

    /** 记录 GPS 上传日志到本地（sendLocation 成功后调用） */
    fun logGpsUpload(context: Context, locationMap: Map<String, Any>) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_GPS_UPLOAD_LOGS, null) ?: "[]"
            val type = object : TypeToken<MutableList<GpsUploadEntry>>() {}.type
            val list: MutableList<GpsUploadEntry> = Gson().fromJson(json, type) ?: mutableListOf()
            list.add(0, GpsUploadEntry(
                uploadTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                uploadTimestamp = System.currentTimeMillis(),
                latitude = (locationMap["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (locationMap["longitude"] as? Number)?.toDouble() ?: 0.0,
                accuracy = (locationMap["accuracy"] as? Number)?.toFloat() ?: 0f,
                altitude = (locationMap["altitude"] as? Number)?.toDouble() ?: 0.0,
                speed = (locationMap["speed"] as? Number)?.toFloat() ?: 0f,
                bearing = (locationMap["bearing"] as? Number)?.toFloat() ?: 0f,
                address = locationMap["address"] as? String ?: "",
                province = locationMap["province"] as? String ?: "",
                city = locationMap["city"] as? String ?: "",
                district = locationMap["district"] as? String ?: "",
                street = locationMap["street"] as? String ?: "",
                description = locationMap["description"] as? String ?: "",
                locationType = (locationMap["locationType"] as? Number)?.toInt() ?: -1,
                errorCode = (locationMap["errorCode"] as? Number)?.toInt() ?: 0,
                errorInfo = locationMap["errorInfo"] as? String ?: "",
                source = locationMap["source"] as? String ?: "unknown"
            ))
            if (list.size > MAX_GPS_UPLOAD_LOGS) list.subList(MAX_GPS_UPLOAD_LOGS, list.size).clear()
            prefs.edit().putString(KEY_GPS_UPLOAD_LOGS, Gson().toJson(list)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save GPS upload log", e)
        }
    }

    // ─── 待传位置存储（上传失败时落盘，有网时补传） ────────────────

    /** 保存待传位置到本地（sendLocation 失败时调用） */
    fun savePendingLocation(context: Context, locationMap: Map<String, Any>) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_PENDING_LOCATIONS, null) ?: "[]"
            val type = object : TypeToken<MutableList<Map<String, Any>>>() {}.type
            val list: MutableList<Map<String, Any>> = Gson().fromJson(json, type) ?: mutableListOf()
            list.add(0, locationMap)
            if (list.size > MAX_PENDING_LOCATIONS) list.subList(MAX_PENDING_LOCATIONS, list.size).clear()
            prefs.edit().putString(KEY_PENDING_LOCATIONS, Gson().toJson(list)).apply()
            Log.d(TAG, "待传位置已保存，当前 ${list.size} 条")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save pending location", e)
        }
    }

    /** 获取所有待传位置 */
    fun getPendingLocations(context: Context): List<Map<String, Any>> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PENDING_LOCATIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            Gson().fromJson<List<Map<String, Any>>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read pending locations", e)
            emptyList()
        }
    }

    /** 上传成功后清空待传位置 */
    fun clearPendingLocations(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_PENDING_LOCATIONS).apply()
        Log.d(TAG, "待传位置已清空")
    }

    /** 获取所有 GPS 上传日志（按时间倒序） */
    fun getGpsUploadLogs(context: Context): List<GpsUploadEntry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_GPS_UPLOAD_LOGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<GpsUploadEntry>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 清空 GPS 上传日志 */
    fun clearGpsUploadLogs(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_GPS_UPLOAD_LOGS).apply()
    }

    data class GpsUploadEntry(
        val uploadTime: String,
        val uploadTimestamp: Long,
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val altitude: Double,
        val speed: Float,
        val bearing: Float,
        val address: String,
        val province: String,
        val city: String,
        val district: String,
        val street: String,
        val description: String,
        val locationType: Int,
        val errorCode: Int,
        val errorInfo: String,
        val source: String
    )

    /**
     * 从 WebSocket 下发的配置中提取高德 API Key。
     * 兼容两种字段名：amap_api_key（实际下发）与 amap_client_api_key（历史/文档写法）。
     * 若传入的本身就是纯 key（非 JSON），则原样返回。
     * 这样也能修复旧版本误缓存为整段 JSON 字符串的问题。
     */
    private fun extractKey(raw: String): String {
        return try {
            val json = JsonParser.parseString(raw).asJsonObject
            json.get("amap_api_key")?.asString
                ?: json.get("amap_client_api_key")?.asString
                ?: raw
        } catch (e: Exception) {
            raw
        }
    }

    /**
     * 用 WebSocket 下发的最新 Key 初始化高德 SDK，并缓存到本地。
     * 后续启动时可直接从缓存恢复，无需等待 WebSocket 连接。
     */
    fun initConfig(key: String, context: Context) {
        // 隐私合规保底（App.onCreate 里应已先调用过）
        initPrivacy(context)
        // key 格式: {"amap_api_key":"xxx","amap_security_code":"xxx"}
        val fetchMapKey = extractKey(key)
        Log.d(TAG, "initConfig: key=$key, length=${key.length}， fetchMapKey=$fetchMapKey")
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CACHED_SDK_KEY, fetchMapKey).apply()
        AMapLocationClient.setApiKey(fetchMapKey)
        isInit = true
    }

    /**
     * 从本地缓存恢复高德 SDK Key，程序启动时调用。
     * @return true 表示缓存存在并已初始化
     */
    fun initFromCache(context: Context): Boolean {
        val rawCached = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_SDK_KEY, null)
        if (rawCached.isNullOrBlank()) {
            Log.d(TAG, "initFromCache: no cached key found")
            return false
        }
        // 兼容旧版本误缓存的整段 JSON：再次 extractKey 取出纯 key
        val cachedKey = extractKey(rawCached)
        Log.d(TAG, "initFromCache: found cached key, cachedKey = $cachedKey, length=${cachedKey.length}")
        AMapLocationClient.setApiKey(cachedKey)
        isInit = true
        return true
    }

    /**
     * 使用高德网络定位获取位置信息（同步调用，带超时）
     * @return Map containing latitude, longitude, accuracy, altitude, speed, address, etc.
     */
    @Synchronized
    fun getLocation(context: Context): Map<String, Any> {
        val result = mutableMapOf<String, Any>(
            "latitude" to 0.0,
            "longitude" to 0.0,
            "accuracy" to 0f,
            "altitude" to 0.0,
            "speed" to 0f,
            "bearing" to 0f,
            "address" to "",
            "country" to "",
            "province" to "",
            "city" to "",
            "cityCode" to "",
            "district" to "",
            "adCode" to "",
            "street" to "",
            "streetNum" to "",
            "road" to "",
            "description" to "",
            "locationType" to -1,
            "coordType" to "",
            "locationTime" to 0L,
            "errorCode" to -1,
            "errorInfo" to ""
        )
        if (isInit.not()){
            logError(context, -1, "高德SDK未初始化")
            return result
        }

        try {
            // 检查系统定位服务是否开启
            val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null || !locationManager.isLocationEnabled) {
                result["errorCode"] = -4
                result["errorInfo"] = "系统定位服务未开启"
                Log.w(TAG, "系统定位服务未开启，跳过定位")
                logError(context, -4, "系统定位服务未开启")
                return result
            }

            // 高德隐私合规保底调用（应该在 App.onCreate 就调一次）
            initPrivacy(context)

            val locationClient = AMapLocationClient(context.applicationContext)
            val latch = CountDownLatch(1)

            locationClient.setLocationListener(object : AMapLocationListener {
                override fun onLocationChanged(location: AMapLocation?) {
                    if (location != null) {
                        if (location.errorCode == 0) {
                            result["latitude"] = location.latitude.toDouble()
                            result["longitude"] = location.longitude.toDouble()
                            result["accuracy"] = location.accuracy
                            result["altitude"] = location.altitude.toDouble()
                            result["speed"] = location.speed
                            result["bearing"] = location.bearing
                            result["address"] = location.address ?: ""
                            result["country"] = location.country ?: ""
                            result["province"] = location.province ?: ""
                            result["city"] = location.city ?: ""
                            result["cityCode"] = location.cityCode ?: ""
                            result["district"] = location.district ?: ""
                            result["adCode"] = location.adCode ?: ""
                            result["street"] = location.street ?: ""
                            result["streetNum"] = location.streetNum ?: ""
                            result["road"] = location.road ?: ""
                            result["description"] = location.description ?: ""
                            result["locationType"] = location.locationType
                            result["coordType"] = location.coordType ?: ""
                            result["locationTime"] = location.time
                            result["errorCode"] = 0
                            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                            val addrInfo = "province=${location.province}, city=${location.city}, district=${location.district}"
                            if (location.address.isNullOrBlank()) {
                                Log.w(TAG, "高德定位成功但地址为空: lat=${location.latitude}, lng=${location.longitude}, $addrInfo, time=$timeStr")
                            } else {
                                Log.d(TAG, "高德定位成功: lat=${location.latitude}, lng=${location.longitude}, $addrInfo, time=$timeStr")
                            }
                        } else {
                            result["errorCode"] = location.errorCode
                            result["errorInfo"] = location.errorInfo ?: "未知错误"
                            Log.e(TAG, "高德定位失败: code=${location.errorCode}, info=${location.errorInfo}")
                            logError(context, location.errorCode, location.errorInfo ?: "未知错误")
                        }
                    }
                    latch.countDown()
                }
            })

            // 配置为高精度模式（GPS+网络）：无SIM卡时基站解析会抛NumberFormatException，
            // Battery_Saving纯网络定位会因此中断不回调；Hight_Accuracy的GPS定位独立于基站，
            // 不受NPE影响，仍能正常回调onLocationChanged
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isOnceLocationLatest = false
                interval = 10000L
                httpTimeOut = LOCATION_TIMEOUT_MS
                isNeedAddress = true
                isLocationCacheEnable = false
            }
            locationClient.setLocationOption(option)
            locationClient.startLocation()
            Log.d(TAG, "定位已启动，等待回调(超时${LOCATION_TIMEOUT_MS}ms)...")

            // 等待定位结果或超时
            val completed = latch.await(LOCATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!completed) {
                result["errorCode"] = -2
                result["errorInfo"] = "定位超时"
                Log.e(TAG, "高德定位超时 (${LOCATION_TIMEOUT_MS}ms)")
                logError(context, -2, "定位超时 (${LOCATION_TIMEOUT_MS}ms)")
            }

            locationClient.stopLocation()
            locationClient.onDestroy()

        } catch (e: Exception) {
            result["errorCode"] = -3
            result["errorInfo"] = e.message ?: "定位异常"
            Log.e(TAG, "高德定位异常", e)
            logError(context, -3, e.message ?: "定位异常")
        }

        return result
    }
}
