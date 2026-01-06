package com.kakdela.p2p.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey var id: String = "", // Firebase ID как основной ключ
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val durationSeconds: Int = 0,
    val isP2P: Boolean = false,
    var isMe: Boolean = false 
)

enum class MessageType {
    TEXT, IMAGE, FILE, AUDIO, CALL_OFFER, CALL_ANSWER, ICE_CANDIDATE
}

