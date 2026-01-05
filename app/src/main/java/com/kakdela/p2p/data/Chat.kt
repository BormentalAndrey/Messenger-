package com.kakdela.p2p.data

/**
 * Модель чата (личного или группового)
 * Используется для списка чатов и навигации
 */
data class Chat(
    val id: String = "",

    // UI
    val title: String = "",
    val iconUrl: String? = null,

    // Тип чата
    val isGroup: Boolean = false,

    // Администраторы (только для групп)
    val adminIds: List<String> = emptyList(),

    // Участники чата
    val participantIds: List<String> = emptyList(),

    // Последнее сообщение (для списка чатов)
    val lastMessage: Message? = null,

    // Время последней активности (для сортировки)
    val timestamp: Long = System.currentTimeMillis()
)
