package com.kakdela.p2p.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущность узла распределенной сети (P2P Peer).
 * Хранит данные об активных пирах для маршрутизации, DHT/Swarm и сопоставления контактов.
 *
 * Имена полей — camelCase (Kotlin-style), Room будет использовать их как имена колонок напрямую.
 * Это полностью совместимо с последними версиями NodeDao (updateNetworkInfo, trimCache и т.д.).
 */
@Entity(
    tableName = "dht_nodes",
    indices = [
        Index(value = ["phoneHash"]),
        Index(value = ["userHash"], unique = true)
    ]
)
data class NodeEntity(
    @PrimaryKey
    val userHash: String,               // Security ID (Primary Key)

    val phoneHash: String = "",         // Discovery ID (хэш номера + Pepper)

    val email: String? = null,

    val passwordHash: String? = null,   // Только для своего узла (локальная авторизация)

    val phone: String? = null,

    val ip: String = "0.0.0.0",         // Последний известный IP (заглушка для оффлайн)

    val port: Int = 8888,

    val publicKey: String = "",         // Публичный ключ (обязателен для шифрования)

    val lastSeen: Long = System.currentTimeMillis()
)
