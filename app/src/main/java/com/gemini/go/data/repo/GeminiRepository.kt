package com.gemini.go.data.repo

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.gemini.go.data.api.GeminiClient
import com.gemini.go.data.api.StreamEvent
import com.gemini.go.data.db.GeminiDatabase
import com.gemini.go.data.model.Content
import com.gemini.go.data.model.ConversationEntity
import com.gemini.go.data.model.GenerationConfig
import com.gemini.go.data.model.GeminiModel
import com.gemini.go.data.model.MessageEntity
import com.gemini.go.data.model.Part
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class GeminiRepository(private val context: Context, private val db: GeminiDatabase, private val prefs: PreferencesManager) {
    private val client by lazy { GeminiClient(prefs.apiKey) }
    fun observeConversations(): Flow<List<ConversationEntity>> = db.conversationDao().observeAll()
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> = db.messageDao().observeByConversation(conversationId)
    suspend fun getConversation(id: String): ConversationEntity? = db.conversationDao().getById(id)
    suspend fun createConversation(model: GeminiModel): ConversationEntity {
        val now = System.currentTimeMillis()
        val conv = ConversationEntity(UUID.randomUUID().toString(), "Nuevo chat", now, now, model.id)
        db.conversationDao().upsert(conv)
        prefs.lastConversationId = conv.id
        return conv
    }
    suspend fun renameConversation(id: String, title: String) {
        db.conversationDao().getById(id)?.let { db.conversationDao().update(it.copy(title = title, updatedAt = System.currentTimeMillis())) }
    }
    suspend fun deleteConversation(id: String) {
        db.conversationDao().getById(id)?.let { db.conversationDao().delete(it); if (prefs.lastConversationId == id) prefs.lastConversationId = null }
    }
    suspend fun clearAllConversations() { db.conversationDao().deleteAll(); prefs.lastConversationId = null }
    suspend fun saveMessage(message: MessageEntity) {
        db.messageDao().insert(message)
        db.conversationDao().getById(message.conversationId)?.let { db.conversationDao().update(it.copy(updatedAt = System.currentTimeMillis())) }
    }
    suspend fun updateMessage(message: MessageEntity) = db.messageDao().update(message)
    suspend fun deleteMessage(id: String) = db.messageDao().deleteById(id)
    suspend fun markMessageStopped(id: String) = db.messageDao().markStopped(id)

    suspend fun getMessagesForApi(conversationId: String): List<Content> {
        val msgs = db.messageDao().getByConversation(conversationId)
        return msgs.filter { it.text.isNotBlank() || it.imagePaths.isNotBlank() }.map { msg ->
            val parts = mutableListOf<Part>()
            if (msg.imagePaths.isNotBlank()) {
                msg.imagePaths.split(",").filter { it.isNotBlank() }.forEach { path ->
                    try {
                        val (mime, base64) = readImageAsBase64(Uri.parse(path))
                        if (base64.isNotEmpty()) parts.add(Part.InlineDataPart(mime, base64))
                    } catch (_: Exception) {}
                }
            }
            if (msg.text.isNotBlank()) parts.add(Part.TextPart(msg.text))
            Content(msg.role, parts)
        }
    }

    fun sendMessage(conversationId: String, model: GeminiModel, systemPrompt: String, temperature: Float): Flow<StreamEvent> = flow {
        val contents = getMessagesForApi(conversationId)
        if (contents.isEmpty()) { emit(StreamEvent.Error("No hay mensajes para enviar")); return@flow }
        val responseModalities = if (model.supportsImageOutput) listOf("TEXT", "IMAGE") else null
        client.streamGenerate(model, contents, systemPrompt, GenerationConfig(temperature = temperature.toDouble(), responseModalities = responseModalities)).collect { emit(it) }
    }

    fun readImageAsBase64(uri: Uri): Pair<String, String> {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalArgumentException("No se pudo leer la imagen")
        return mime to Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun generateImage(prompt: String): String? {
        return try {
            client.generateImage(prompt)
        } catch (e: Exception) {
            // Return the error so the ViewModel can show it
            "ERROR:Excepción: ${e.javaClass.simpleName}: ${e.message ?: "(sin mensaje)"}"
        }
    }
}
