package com.kakdela.p2p.data

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
 * Универсальная модель сообщения
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
    val readBy: List<String> = emptyList()
)
