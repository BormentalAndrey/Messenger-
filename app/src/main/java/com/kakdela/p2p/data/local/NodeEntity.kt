package com.kakdela.p2p.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сущность узла распределенной сети.
 * Хранит данные о 2500 активных пирах для P2P маршрутизации.
 */
@Entity(tableName = "dht_nodes")
data class NodeEntity(
    @PrimaryKey
    val userHash: String,      // Хеш публичного ключа
    val email: String? = null, 
    val passwordHash: String? = null, 
    val phone: String? = null, 
    val ip: String,            
    val port: Int = 8888,             
    val publicKey: String,     
    val lastSeen: Long = System.currentTimeMillis()
)
