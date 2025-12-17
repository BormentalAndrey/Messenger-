package com.kakdela.p2p.data

data class Message(
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val fileUrl: String? = null,
    val isP2P: Boolean = false, // Флаг прямого канала
    val scheduledTime: Long = 0
)

