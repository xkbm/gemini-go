package com.gemini.go

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.gemini.go.data.db.GeminiDatabase
import com.gemini.go.data.repo.GeminiRepository
import com.gemini.go.data.repo.PreferencesManager

class GeminiApp : Application() {
    val prefs: PreferencesManager by lazy { PreferencesManager(this) }
    val database: GeminiDatabase by lazy { GeminiDatabase.getInstance(this) }
    val repository: GeminiRepository by lazy { GeminiRepository(this, database, prefs) }
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
    companion object {
        @Volatile private var instance: GeminiApp? = null
        fun get(): GeminiApp = instance!!
    }
}
