package com.kakdela.p2p.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущность узла распределенной сети (P2P Peer).
 * Хранит данные об активных пирах для маршрутизации и сопоставления контактов.
 * * Индекс по phone_hash критически важен для мгновенного сопоставления контактов
 * из телефонной книги со списком зарегистрированных пользователей.
 */
@Entity(
    tableName = "dht_nodes",
    indices = [
        Index(value = ["phone_hash"]),
        Index(value = ["user_hash"], unique = true)
    ]
)
data class NodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_hash")
    val userHash: String,      // Security ID (Primary Key)

    @ColumnInfo(name = "phone_hash")
    val phone_hash: String,    // Discovery ID (Хеш номера + Pepper)

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
