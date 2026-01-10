package com.kakdela.p2p.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID

/**
 * Основная модель сообщения.
 * Используется как для хранения в SQLite (Room), так и для отображения в UI.
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey 
    @ColumnInfo(name = "message_id")
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "text")
    val text: String = "",
    
    @ColumnInfo(name = "sender_id")
    val senderId: String = "", 
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "message_type")
    val type: MessageType = MessageType.TEXT,
    
    @ColumnInfo(name = "file_url")
    val fileUrl: String? = null,
    
    @ColumnInfo(name = "file_name")
    val fileName: String? = null,
    
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int = 0,
    
    @ColumnInfo(name = "is_p2p")
    val isP2P: Boolean = true,
    
    @ColumnInfo(name = "is_me")
    var isMe: Boolean = false,
    
    @ColumnInfo(name = "scheduled_time")
    val scheduledTime: Long? = null,

    /**
     * Статус сообщения (SENT, DELIVERED, READ, FAILED).
     * Добавление этого поля исправляет ошибку компиляции в NavGraph.kt
     */
    @ColumnInfo(name = "status")
    val status: String = "SENT"
)

/**
 * Типы контента, поддерживаемые P2P протоколом.
 */
enum class MessageType {
    TEXT, 
    IMAGE, 
    FILE, 
    AUDIO, 
    
    // Типы для сигнализации WebRTC (P2P видео/аудио звонки)
    CALL_OFFER, 
    CALL_ANSWER, 
    ICE_CANDIDATE
}
