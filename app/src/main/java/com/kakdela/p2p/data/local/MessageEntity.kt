package com.kakdela.p2p.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val chatId: String,
    val text: String,
    val senderId: String,
    val timestamp: Long,
    val fileBytes: ByteArray? = null
)
