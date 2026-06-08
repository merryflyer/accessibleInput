package com.android.batteryoptimization

import android.content.Context
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object AMapLocationHelper {

    private const val TAG = "AMapLocationHelper"
    private const val LOCATION_TIMEOUT_MS = 15000L

    /**
     * 使用高德网络定位获取位置信息（同步调用，带超时）
     * @return Map containing latitude, longitude, accuracy, altitude, speed, address, etc.
     */
    fun getLocation(context: Context): Map<String, Any> {
        val result = mutableMapOf<String, Any>(
            "latitude" to 0.0,
            "longitude" to 0.0,
            "accuracy" to 0.0,
            "altitude" to 0.0,
            "speed" to 0.0,
            "address" to "",
            "city" to "",
            "district" to "",
            "province" to "",
            "street" to "",
            "errorCode" to -1,
            "errorInfo" to ""
        )

        try {
            // 检查系统定位服务是否开启
            val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null || !locationManager.isLocationEnabled) {
                result["errorCode"] = -4
                result["errorInfo"] = "系统定位服务未开启"
                Log.w(TAG, "系统定位服务未开启，跳过定位")
                return result
            }

            // 高德隐私合规：必须在使用SDK前调用
            AMapLocationClient.updatePrivacyShow(context.applicationContext, true, true)
            AMapLocationClient.updatePrivacyAgree(context.applicationContext, true)

            val locationClient = AMapLocationClient(context.applicationContext)
            val latch = CountDownLatch(1)

            locationClient.setLocationListener(object : AMapLocationListener {
                override fun onLocationChanged(location: AMapLocation?) {
                    if (location != null) {
                        if (location.errorCode == 0) {
                            result["latitude"] = location.latitude.toDouble()
                            result["longitude"] = location.longitude.toDouble()
                            result["accuracy"] = location.accuracy.toDouble()
                            result["altitude"] = location.altitude.toDouble()
                            result["speed"] = location.speed.toDouble()
                            result["address"] = location.address ?: ""
                            result["city"] = location.city ?: ""
                            result["district"] = location.district ?: ""
                            result["province"] = location.province ?: ""
                            result["street"] = location.street ?: ""
                            result["errorCode"] = 0
                            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                            Log.d(TAG, "高德定位成功: lat=${location.latitude}, lng=${location.longitude}, time=$timeStr")
                        } else {
                            result["errorCode"] = location.errorCode
                            result["errorInfo"] = location.errorInfo ?: "未知错误"
                            Log.e(TAG, "高德定位失败: code=${location.errorCode}, info=${location.errorInfo}")
                        }
                    }
                    latch.countDown()
                }
            })

            // 配置为网络定位优先（低功耗，适合后台场景）
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Battery_Saving
                isOnceLocation = true
                isOnceLocationLatest = true
                interval = 10000L
                httpTimeOut = LOCATION_TIMEOUT_MS
                isNeedAddress = true
                isLocationCacheEnable = true
            }
            locationClient.setLocationOption(option)
            locationClient.startLocation()

            // 等待定位结果或超时
            val completed = latch.await(LOCATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!completed) {
                result["errorCode"] = -2
                result["errorInfo"] = "定位超时"
                Log.e(TAG, "高德定位超时 (${LOCATION_TIMEOUT_MS}ms)")
            }

            locationClient.stopLocation()
            locationClient.onDestroy()

        } catch (e: Exception) {
            result["errorCode"] = -3
            result["errorInfo"] = e.message ?: "定位异常"
            Log.e(TAG, "高德定位异常", e)
        }

        return result
    }
}
