package com.kakdela.p2p.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Модель чата для списка чатов (как в WhatsApp)
 */
data class Chat(
    val id: String = "",                    // Уникальный ID чата (sorted uid1_uid2 или "global")
    val title: String = "Новый чат",        // Название (имя собеседника или "Глобальный чат")
    val lastMessage: String = "",           // Текст последнего сообщения
    val lastMessageSenderId: String = "",   // Кто отправил последнее
    @ServerTimestamp
    val timestamp: Date? = null,            // Время последнего сообщения
    val unreadCount: Int = 0,               // Количество непрочитанных (для будущего)
    val participantIds: List<String> = emptyList()  // UID участников (для личных чатов — 2 человека)
)
