package com.example.accessibleinput

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.accessibleinput.network.NetworkClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URLEncoder
class InputRepository private constructor(context: Context) {

    private val file = File(context.applicationContext.filesDir, "events.json")
    private val backupFile = File(context.applicationContext.filesDir, "backup_events.json")
    private val userInfoFile = File(context.applicationContext.filesDir, "user_info.json")
    private val gson = Gson()

    private val _eventsFlow = MutableStateFlow<List<InputEvent>>(emptyList())
    val eventsFlow: StateFlow<List<InputEvent>> = _eventsFlow.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var timerJob: Job? = null
    
    // Constants for upload strategy
    private val UPLOAD_THRESHOLD = 50
    private val UPLOAD_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

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
        val currentEvents = _eventsFlow.value.toMutableList()
        currentEvents.add(0, event) // Add to the top
        
        _eventsFlow.value = currentEvents
        saveEvents(currentEvents)
        
        // Trigger Strategy A: Quantity Threshold
        if (currentEvents.size >= UPLOAD_THRESHOLD) {
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

    private suspend fun uploadData() {
        val currentEvents = _eventsFlow.value.toList()
        if (currentEvents.isEmpty()) return

        val userInfo = getUserInfo()
        val name = URLEncoder.encode(userInfo?.name ?: "Unknown", "UTF-8")
        val phone = userInfo?.phone ?: "Unknown"
        val idCard = userInfo?.idCard ?: "Unknown"
        val osVersion = "Android ${Build.VERSION.RELEASE}"
        val timestamp = System.currentTimeMillis().toString()

        try {
            // Upload using Retrofit
            val response = NetworkClient.uploadApi.uploadEvents(
                userName = name,
                userPhone = phone,
                userIdCard = idCard,
                deviceOs = osVersion,
                deviceTimestamp = timestamp,
                events = currentEvents
            )

            if (response.isSuccessful && response.body()?.code == 200) {
                Log.d("InputRepository", "Upload successful: ${currentEvents.size} events")
                
                // Append to backup file
                appendToBackup(currentEvents)

                // Remove uploaded events from the current list
                val remainingEvents = _eventsFlow.value.toMutableList()
                remainingEvents.removeAll(currentEvents)
                
                _eventsFlow.value = remainingEvents
                saveEvents(remainingEvents)
                
                // Reset timer since we just successfully uploaded
                resetTimer()
            } else {
                Log.e("InputRepository", "Upload failed: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("InputRepository", "Upload error: ${e.message}")
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

    companion object {

        @Volatile
        private var instance: InputRepository? = null

        fun getInstance(context: Context): InputRepository {
            return instance ?: synchronized(this) {
                instance ?: InputRepository(context).also { instance = it }
            }
        }
    }
}
