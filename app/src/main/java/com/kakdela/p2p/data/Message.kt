// Путь: app/src/main/java/com/kakdela/p2p/data/Message.kt

package com.kakdela.p2p.data

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

/**
 * Типы сообщений внутри чатов
 */
enum class MessageType {
    TEXT,
    IMAGE,
    FILE,
    AUDIO,

    // WebRTC signaling
    CALL_OFFER,
    CALL_ANSWER,
    ICE_CANDIDATE
}

/**
 * Универсальная модель сообщения.
 * Используется как для Firestore, так и для локальной БД Room.
 */
data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),

    val type: MessageType = MessageType.TEXT,

    // File / Media
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,

    // Audio
    val durationSeconds: Int = 0,

    // WebRTC signaling data (SDP / ICE)
    val callSdp: String? = null,

    // Read receipts
    val readBy: List<String> = emptyList(),

    // Флаг для DHT/P2P очереди
    val isP2P: Boolean = false
) {
    /**
     * Вычисляемое свойство для Compose UI.
     * Позволяет не передавать UID пользователя в каждый Bubble вручную.
     */
    val isMe: Boolean
        get() {
            val currentUid = Firebase.auth.currentUser?.uid
            return currentUid != null && senderId == currentUid
        }
}

