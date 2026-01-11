package com.kakdela.p2p.ui

data class ChatDisplay(
    val id: String,         // Hash-ID собеседника
    val title: String,      // Изначально номер телефона
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0
)
