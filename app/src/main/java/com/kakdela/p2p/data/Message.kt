package com.kakdela.p2p.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey var id: String = "",
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val durationSeconds: Int = 0,
    val isP2P: Boolean = false,
    var isMe: Boolean = false // Теперь это изменяемое поле для маппинга
)

enum class MessageType {
    TEXT, IMAGE, FILE, AUDIO, CALL_OFFER, CALL_ANSWER, ICE_CANDIDATE
}

