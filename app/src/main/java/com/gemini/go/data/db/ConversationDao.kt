package com.gemini.go.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gemini.go.data.model.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(c: ConversationEntity)
    @Update suspend fun update(c: ConversationEntity)
    @Delete suspend fun delete(c: ConversationEntity)
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM conversations WHERE id = :id") suspend fun getById(id: String): ConversationEntity?
    @Query("DELETE FROM conversations") suspend fun deleteAll()
}
