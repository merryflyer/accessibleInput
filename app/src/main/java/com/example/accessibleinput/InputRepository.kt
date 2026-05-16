package com.example.accessibleinput

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InputRepository private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _eventsFlow = MutableStateFlow<List<InputEvent>>(emptyList())
    val eventsFlow: StateFlow<List<InputEvent>> = _eventsFlow.asStateFlow()

    init {
        loadEvents()
    }

    private fun loadEvents() {
        val json = sharedPreferences.getString(KEY_EVENTS, null)
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
        val json = gson.toJson(events)
        sharedPreferences.edit().putString(KEY_EVENTS, json).apply()
    }

    companion object {
        private const val PREF_NAME = "accessible_input_prefs"
        private const val KEY_EVENTS = "key_input_events"

        @Volatile
        private var instance: InputRepository? = null

        fun getInstance(context: Context): InputRepository {
            return instance ?: synchronized(this) {
                instance ?: InputRepository(context).also { instance = it }
            }
        }
    }
}
