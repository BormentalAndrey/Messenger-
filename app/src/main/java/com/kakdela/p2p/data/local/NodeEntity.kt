package com.kakdela.p2p.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущность узла распределенной сети (P2P Peer).
 * Хранит данные об активных пирах для маршрутизации и сопоставления контактов.
 * * Индекс по phone_hash ускоряет поиск контактов при синхронизации телефонной книги.
 */
@Entity(
    tableName = "dht_nodes",
    indices = [Index(value = ["phone_hash"])]
)
data class NodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_hash")
    val userHash: String,      // Security ID (Хеш публичного ключа или связки данных)

    @ColumnInfo(name = "phone_hash")
    val phone_hash: String,    // Discovery ID (SHA-256 номера телефона с PEPPER)

    @ColumnInfo(name = "email")
    val email: String? = null, 

    @ColumnInfo(name = "password_hash")
    val passwordHash: String? = null, 

    @ColumnInfo(name = "phone")
    val phone: String? = null, 

    @ColumnInfo(name = "ip_address")
    val ip: String,            

    @ColumnInfo(name = "port")
    val port: Int = 8888,             

    @ColumnInfo(name = "public_key")
    val publicKey: String,     

    @ColumnInfo(name = "last_seen")
    val lastSeen: Long = System.currentTimeMillis()
)
