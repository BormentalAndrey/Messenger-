package com.kakdela.p2p.data

data class Message(
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isDelivered: Boolean = false,
    val isRead: Boolean = false
)
