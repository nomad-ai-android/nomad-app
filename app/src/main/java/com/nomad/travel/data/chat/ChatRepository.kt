package com.nomad.travel.data.chat

import android.content.Context
import android.net.Uri
import com.nomad.travel.data.ChatMessage
import com.nomad.travel.data.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
    private val context: Context
) {

    fun observeSessions(): Flow<List<ChatSessionEntity>> = sessionDao.observeAll()

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
        messageDao.observeForSession(sessionId).map { list -> list.map { it.toUi() } }

    suspend fun snapshotMessages(sessionId: Long): List<ChatMessage> =
        messageDao.forSession(sessionId).map { it.toUi() }

    suspend fun createSession(title: String): Long =
        sessionDao.insert(ChatSessionEntity(title = title))

    suspend fun mostRecentSessionId(): Long? = sessionDao.mostRecentId()

    suspend fun sessionExists(id: Long): Boolean = sessionDao.byId(id) != null

    suspend fun renameSession(id: Long, title: String) = sessionDao.rename(id, title)

    suspend fun touchSession(id: Long) = sessionDao.touch(id)

    suspend fun deleteSession(id: Long) {
        val orphanedImages = messageDao.forSession(id).mapNotNull { it.imageUri }
        sessionDao.delete(id)
        orphanedImages.forEach { ChatImageStore.delete(context, Uri.parse(it)) }
    }

    suspend fun deleteAllSessions() {
        sessionDao.deleteAll()
        ChatImageStore.clearAll(context)
    }

    suspend fun appendMessage(
        sessionId: Long,
        role: Role,
        text: String,
        imageUri: Uri? = null,
        toolTag: String? = null
    ): Long {
        // Picker / camera URIs aren't stable across restarts — copy into our
        // managed dir so the message keeps a usable reference forever.
        val storedUri = imageUri?.let { ChatImageStore.persist(context, it) ?: it }
        val id = messageDao.insert(
            ChatMessageEntity(
                sessionId = sessionId,
                role = role.name.lowercase(),
                text = text,
                imageUri = storedUri?.toString(),
                toolTag = toolTag
            )
        )
        sessionDao.touch(sessionId)
        return id
    }

    suspend fun updateMessage(id: Long, text: String, toolTag: String?) {
        messageDao.updateContent(id, text, toolTag)
    }

    suspend fun deleteMessage(id: Long) = messageDao.delete(id)

    private fun ChatMessageEntity.toUi(): ChatMessage = ChatMessage(
        id = id.toString(),
        role = when (role) {
            "user" -> Role.USER
            "assistant" -> Role.ASSISTANT
            else -> Role.SYSTEM
        },
        text = text,
        imageUri = imageUri?.let { Uri.parse(it) },
        pending = false,
        streaming = false,
        toolTag = toolTag,
        createdAt = createdAt
    )
}
