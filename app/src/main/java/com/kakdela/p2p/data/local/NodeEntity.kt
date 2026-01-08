package com.kakdela.p2p.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(tableName = "dht_nodes")
data class NodeEntity(
    @PrimaryKey val userHash: String,
    val ip: String,
    val port: Int,
    val publicKey: String,
    val phone: String, // Номер телефона для сверки с книгой контактов
    val lastSeen: Long
)

@Dao
interface NodeDao {
    // Получить конкретный узел (для P2P соединения)
    @Query("SELECT * FROM dht_nodes WHERE userHash = :hash")
    suspend fun getNode(hash: String): NodeEntity?

    // Получить ВСЕХ (для фильтрации контактов)
    @Query("SELECT * FROM dht_nodes")
    suspend fun getAllNodes(): List<NodeEntity>

    // Вставка списка (синхронизация с сервером)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<NodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity)
}
