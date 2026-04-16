package com.nomad.travel.data.chat

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "role") val role: String, // "user" | "assistant"
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "image_uri") val imageUri: String? = null,
    @ColumnInfo(name = "tool_tag") val toolTag: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
