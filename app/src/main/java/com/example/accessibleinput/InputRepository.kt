package com.example.accessibleinput

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
class InputRepository private constructor(context: Context) {

    private val file = File(context.applicationContext.filesDir, "events.json")
    private val userInfoFile = File(context.applicationContext.filesDir, "user_info.json")
    private val gson = Gson()

    private val _eventsFlow = MutableStateFlow<List<InputEvent>>(emptyList())
    val eventsFlow: StateFlow<List<InputEvent>> = _eventsFlow.asStateFlow()

    init {
        loadEvents()
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
        // Limit to latest 100 events for performance
        val trimmedEvents = if (currentEvents.size > 100) currentEvents.take(100) else currentEvents
        
        _eventsFlow.value = trimmedEvents
        saveEvents(trimmedEvents)
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
