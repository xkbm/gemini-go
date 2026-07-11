package com.gemini.go.data.repo

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.gemini.go.data.model.GeminiModel

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_API_KEY, value) }
    var modelId: String
        get() = prefs.getString(KEY_MODEL, GeminiModel.DEFAULT.id) ?: GeminiModel.DEFAULT.id
        set(value) = prefs.edit { putString(KEY_MODEL, value) }
    val model: GeminiModel get() = GeminiModel.fromId(modelId)
    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.9f)
        set(value) = prefs.edit { putFloat(KEY_TEMPERATURE, value) }
    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.edit { putString(KEY_SYSTEM_PROMPT, value) }
    var lastConversationId: String?
        get() = prefs.getString(KEY_LAST_CONVERSATION, null)
        set(value) = prefs.edit { putString(KEY_LAST_CONVERSATION, value) }
    companion object {
        private const val PREFS_NAME = "gemini_go_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model_id"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_LAST_CONVERSATION = "last_conversation"
        const val DEFAULT_SYSTEM_PROMPT = "Eres Gemini, un asistente útil, amable y conciso. Respondes en el idioma del usuario."
    }
}
