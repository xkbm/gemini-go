package com.gemini.go.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gemini.go.data.model.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(m: MessageEntity)
    @Update suspend fun update(m: MessageEntity)
    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY timestamp ASC") fun observeByConversation(id: String): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY timestamp ASC") suspend fun getByConversation(id: String): List<MessageEntity>
    @Query("DELETE FROM messages WHERE conversationId = :id") suspend fun deleteByConversation(id: String)
    @Query("DELETE FROM messages WHERE id = :id") suspend fun deleteById(id: String)
    @Query("UPDATE messages SET isStreaming = 0 WHERE id = :id") suspend fun markStopped(id: String)
    @Query("SELECT * FROM messages WHERE id = :id") suspend fun getById(id: String): MessageEntity?
}
