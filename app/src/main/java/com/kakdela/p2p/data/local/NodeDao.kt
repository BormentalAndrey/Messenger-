package com.kakdela.p2p.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * DAO для DHT / Swarm / Discovery кэша.
 * Хранит последние известные сетевые данные узлов.
 */
@Dao
interface NodeDao {

    // ---------- QUERY ----------

    @Query(
        "SELECT * FROM dht_nodes " +
        "WHERE user_hash = :hash " +
        "LIMIT 1"
    )
    suspend fun getNodeByHash(hash: String): NodeEntity?

    @Query(
        "SELECT * FROM dht_nodes " +
        "WHERE email = :email " +
        "LIMIT 1"
    )
    suspend fun getUserByEmail(email: String): NodeEntity?

    /**
     * Узлы для Swarm поиска.
     * Лимит защищает от переполнения памяти.
     */
    @Query(
        "SELECT * FROM dht_nodes " +
        "ORDER BY last_seen DESC " +
        "LIMIT 2500"
    )
    suspend fun getAllNodes(): List<NodeEntity>

    // ---------- INSERT ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeEntity>)

    // ---------- CACHE MANAGEMENT ----------

    /**
     * Атомарное обновление DHT-кэша.
     */
    @Transaction
    suspend fun updateCache(nodes: List<NodeEntity>) {
        if (nodes.isEmpty()) return
        insertNodes(nodes)
        trimCache()
    }

    /**
     * Оставляет только 2500 самых свежих записей.
     */
    @Query(
        """
        DELETE FROM dht_nodes
        WHERE user_hash NOT IN (
            SELECT user_hash FROM (
                SELECT user_hash
                FROM dht_nodes
                ORDER BY last_seen DESC
                LIMIT 2500
            )
        )
        """
    )
    suspend fun trimCache()

    // ---------- NETWORK UPDATE ----------

    /**
     * Обновление сетевой информации узла.
     * Вызывается при:
     * - PEER_FOUND
     * - входящем UDP пакете
     */
    @Query(
        """
        UPDATE dht_nodes
        SET 
            ip = :newIp,
            port = :newPort,
            publicKey = :pubKey,
            lastSeen = :timestamp
        WHERE userHash = :hash
        """
    )
    suspend fun updateNetworkInfo(
        hash: String,
        newIp: String,
        newPort: Int = 8888,
        pubKey: String,
        timestamp: Long
    )

    // ---------- MAINTENANCE ----------

    @Query("DELETE FROM dht_nodes")
    suspend fun clearAll()
}
