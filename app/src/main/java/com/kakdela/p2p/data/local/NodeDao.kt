package com.kakdela.p2p.data.local

import androidx.room.*

@Dao
interface NodeDao {

    @Query("SELECT * FROM dht_nodes WHERE userHash = :hash LIMIT 1")
    suspend fun getNodeByHash(hash: String): NodeEntity?

    @Query("SELECT * FROM dht_nodes ORDER BY lastSeen DESC LIMIT 2500")
    suspend fun getAllNodes(): List<NodeEntity>

    /**
     * Вставляет или обновляет узел. 
     * Переименовано в 'upsert' для соответствия вызовам в IdentityRepository.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: NodeEntity)

    /**
     * Пакетная вставка или обновление узлов.
     * Переименовано в 'upsertAll' для соответствия вызовам в IdentityRepository.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(nodes: List<NodeEntity>)

    /**
     * Комплексное обновление: вставляем новые данные и сразу удаляем излишки, 
     * оставляя только 2500 самых свежих участников роя.
     */
    @Transaction
    suspend fun updateCache(nodes: List<NodeEntity>) {
        if (nodes.isEmpty()) return
        
        // Используем переименованный метод upsertAll
        upsertAll(nodes)
        
        // Удаляем всех, кто не попал в ТОП-2500 по времени последнего визита
        trimCache()
    }

    /**
     * Удаляет "хвост" базы данных.
     * Оставляет только 2500 записей с самым большим lastSeen.
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
     * Обновление сетевых данных при прямом контакте (UDP/NSD).
     * Если пира нет в базе, он будет добавлен при следующей синхронизации с сервером.
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
     * Дополнительный метод: удаляет совсем старые записи (например, старше 30 дней),
     * даже если их меньше 2500. Полезно для гигиены базы.
     */
    @Query("DELETE FROM dht_nodes WHERE lastSeen < :timestampThreshold")
    suspend fun deleteStaleNodes(timestampThreshold: Long)
}
