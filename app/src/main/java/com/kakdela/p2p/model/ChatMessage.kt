package com.kakdela.p2p.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean = true // true если отправил пользователь, false если ИИ
) {
    // Вспомогательное поле для читаемости кода ИИ
    val isUser: Boolean get() = isMine
}
