package com.nomad.travel.data.chat

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Insert
    suspend fun insert(session: ChatSessionEntity): Long

    @Update
    suspend fun update(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET updated_at = :ts WHERE id = :id")
    suspend fun touch(id: Long, ts: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET title = :title, updated_at = :ts WHERE id = :id")
    suspend fun rename(id: Long, title: String, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM chat_sessions ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun byId(id: Long): ChatSessionEntity?

    @Query("SELECT id FROM chat_sessions ORDER BY updated_at DESC LIMIT 1")
    suspend fun mostRecentId(): Long?

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAll()
}

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Update
    suspend fun update(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET text = :text, tool_tag = :toolTag WHERE id = :id")
    suspend fun updateContent(id: Long, text: String, toolTag: String?)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY id ASC")
    fun observeForSession(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY id ASC")
    suspend fun forSession(sessionId: Long): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun sessionDao(): ChatSessionDao
    abstract fun messageDao(): ChatMessageDao

    companion object {
        @Volatile private var instance: ChatDatabase? = null
        fun get(context: Context): ChatDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ChatDatabase::class.java,
                "nomad-chats.db"
            ).build().also { instance = it }
        }
    }
}
