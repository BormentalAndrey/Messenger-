package com.kakdela.p2p.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Локальная таблица узлов (DHT / P2P).
 * Используем @ColumnInfo для точного соответствия серверному API,
 * чтобы phone_hash и userHash совпадали с серверной схемой.
 */
@Entity(
    tableName = "dht_nodes",
    indices = [
        Index(value = ["phone_hash"]),
        Index(value = ["userHash"], unique = true)
    ]
)
data class NodeEntity(
    /** Уникальный идентификатор узла (SHA-256 identity hash). */
    @PrimaryKey
    val userHash: String,

    /** Хэш телефона для P2P discovery. */
    @ColumnInfo(name = "phone_hash")
    val phone_hash: String = "",

    /** Email узла (локальное хранение). */
    val email: String? = null,

    /** Локальный хэш пароля для автовхода, не отправляется на сервер. */
    val passwordHash: String? = null,

    /** Телефон узла (локальное хранение, для SMS fallback). */
    val phone: String? = null,

    /** Последний известный IP узла. */
    val ip: String = "0.0.0.0",

    /** Последний известный порт узла. */
    val port: Int = 8888,

    /** Публичный ключ узла для шифрования P2P-сообщений. */
    val publicKey: String = "",

    /** Метка времени последнего контакта с узлом. */
    val lastSeen: Long = System.currentTimeMillis()
)
