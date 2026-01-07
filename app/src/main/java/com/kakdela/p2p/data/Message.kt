package com.kakdela.p2p.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore
import java.util.UUID

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey 
    var id: String = UUID.randomUUID().toString(), // Локальная генерация ID
    val text: String = "",
    val senderId: String = "", // Хеш публичного ключа (Fingerprint)
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val durationSeconds: Int = 0,
    val isP2P: Boolean = true, // По умолчанию true для децентрализованной сети
    
    // Поле для логики отображения (справа/слева)
    var isMe: Boolean = false,
    
    // Поле для запланированных сообщений (устраняет ошибку в ChatScreen)
    val scheduledTime: Long? = null 
)

enum class MessageType {
    TEXT, 
    IMAGE, 
    FILE, 
    AUDIO, 
    
    // Типы для сигнализации WebRTC (P2P звонки)
    CALL_OFFER, 
    CALL_ANSWER, 
    ICE_CANDIDATE
}

