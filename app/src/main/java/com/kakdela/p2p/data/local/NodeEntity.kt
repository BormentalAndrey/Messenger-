package com.kakdela.p2p.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Локальная таблица узлов.
 * Используем @ColumnInfo для явного соответствия имен колонок,
 * чтобы phone_hash совпадал с серверным именованием.
 */
@Entity(
    tableName = "dht_nodes",
    indices = [
        Index(value = ["phone_hash"]),
        Index(value = ["userHash"], unique = true)
    ]
)
data class NodeEntity(
    @PrimaryKey
    val userHash: String,

    @ColumnInfo(name = "phone_hash")
    val phone_hash: String = "",

    val email: String? = null,

    // Хранится только локально для автовхода, на сервер не отправляется
    val passwordHash: String? = null,

    val phone: String? = null,

    val ip: String = "0.0.0.0",

    val port: Int = 8888,

    val publicKey: String = "",

    val lastSeen: Long = System.currentTimeMillis()
)
