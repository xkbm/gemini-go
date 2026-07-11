package com.gemini.go.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String, val title: String, val createdAt: Long, val updatedAt: Long, val modelId: String
)

@Entity(tableName = "messages",
    foreignKeys = [ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("conversationId")])
data class MessageEntity(
    @PrimaryKey val id: String, val conversationId: String, val role: String,
    var text: String, val imagePaths: String = "", val timestamp: Long, var isStreaming: Boolean = false,
    var generatedImageBase64: String = "", var generatedImageMime: String = ""
)
