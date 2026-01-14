package com.kakdela.p2p.data.local

import androidx.room.*

@Dao
interface NodeDao {

    /**
     * Получение узла по хэшу.
     */
    @Query("SELECT * FROM dht_nodes WHERE userHash = :hash LIMIT 1")
    suspend fun getNodeByHash(hash: String): NodeEntity?

    /**
     * Получение всех узлов, упорядоченных по времени последнего визита.
     * Лимит 2500 для оптимизации.
     */
    @Query("SELECT * FROM dht_nodes ORDER BY lastSeen DESC LIMIT 2500")
    suspend fun getAllNodes(): List<NodeEntity>

    /**
     * Вставка или обновление одного узла.
     * Используется для upsert операций.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: NodeEntity)

    /**
     * Пакетная вставка или обновление узлов.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(nodes: List<NodeEntity>)

    /**
     * Комплексное обновление кэша узлов.
     * Сохраняет новые узлы и удаляет старые, оставляя только 2500 самых свежих.
     */
    @Transaction
    suspend fun updateCache(nodes: List<NodeEntity>) {
        if (nodes.isEmpty()) return

        upsertAll(nodes)
        trimCache()
    }

    /**
     * Удаление "хвоста" базы: оставляет только 2500 самых свежих записей.
     */
    @Query("""
        DELETE FROM dht_nodes 
        WHERE userHash NOT IN (
            SELECT userHash FROM dht_nodes 
            ORDER BY lastSeen DESC 
            LIMIT 2500
        )
    """)
    suspend fun trimCache()

    /**
     * Обновление сетевых данных узла при прямом контакте (UDP/NSD).
     * Если узла нет, он будет добавлен при следующей синхронизации с сервером.
     */
    @Query("""
        UPDATE dht_nodes 
        SET ip = :newIp, port = :newPort, publicKey = :pubKey, lastSeen = :timestamp 
        WHERE userHash = :hash
    """)
    suspend fun updateNetworkInfo(
        hash: String, 
        newIp: String, 
        newPort: Int, 
        pubKey: String, 
        timestamp: Long
    )

    /**
     * Отметка узла как успешно синхронизированного с сервером.
     */
    @Query("""
        UPDATE dht_nodes
        SET isSynced = 1
        WHERE userHash = :hash
    """)
    suspend fun markSynced(hash: String)

    /**
     * Удаление старых записей, даже если их меньше 2500.
     * timestampThreshold — порог в миллисекундах (например, System.currentTimeMillis() - 30 дней).
     */
    @Query("DELETE FROM dht_nodes WHERE lastSeen < :timestampThreshold")
    suspend fun deleteStaleNodes(timestampThreshold: Long)
}
