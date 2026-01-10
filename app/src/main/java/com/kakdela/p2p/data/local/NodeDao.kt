package com.kakdela.p2p.data.local

import androidx.room.*

@Dao
interface NodeDao {

    @Query("SELECT * FROM dht_nodes WHERE userHash = :hash LIMIT 1")
    suspend fun getNodeByHash(hash: String): NodeEntity?

    @Query("SELECT * FROM dht_nodes ORDER BY lastSeen DESC LIMIT 2500")
    suspend fun getAllNodes(): List<NodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeEntity>)

    /**
     * Обновление кэша: вставляем новые, удаляем старые (если больше 2500)
     */
    @Transaction
    suspend fun updateCache(nodes: List<NodeEntity>) {
        if (nodes.isEmpty()) return
        insertNodes(nodes)
        trimCache()
    }

    @Query("""
        DELETE FROM dht_nodes
        WHERE userHash NOT IN (
            SELECT userHash FROM dht_nodes ORDER BY lastSeen DESC LIMIT 2500
        )
    """)
    suspend fun trimCache()

    /**
     * Обновление сетевых данных при обнаружении пира
     */
    @Query("""
        UPDATE dht_nodes
        SET ip = :newIp, port = :newPort, publicKey = :pubKey, lastSeen = :timestamp
        WHERE userHash = :hash
    """)
    suspend fun updateNetworkInfo(
        hash: String,
        newIp: String,
        newPort: Int = 8888,
        pubKey: String,
        timestamp: Long
    )
}
