package com.gemini.go.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gemini.go.data.model.ConversationEntity
import com.gemini.go.data.model.MessageEntity

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 2, exportSchema = false)
abstract class GeminiDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    companion object {
        @Volatile private var INSTANCE: GeminiDatabase? = null
        fun getInstance(context: Context): GeminiDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, GeminiDatabase::class.java, "gemini_go.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
