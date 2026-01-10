package com.kakdela.p2p.data.local

import androidx.room.*

/**
 * Data Access Object для работы с узлами сети (DHT).
 * Обеспечивает персистентность данных об активных пирах.
 */
@Dao
interface NodeDao {

    @Query("SELECT * FROM dht_nodes WHERE user_hash = :hash LIMIT 1")
    suspend fun getNodeByHash(hash: String): NodeEntity?

    @Query("SELECT * FROM dht_nodes WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): NodeEntity?

    /**
     * Получение списка активных узлов для роевого поиска.
     * Ограничение в 2500 записей предотвращает переполнение памяти устройства.
     */
    @Query("SELECT * FROM dht_nodes ORDER BY last_seen DESC LIMIT 2500")
    suspend fun getAllNodes(): List<NodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeEntity>)

    /**
     * Атомарное обновление кэша.
     * Если список пуст, транзакция не запускается для экономии ресурсов.
     */
    @Transaction
    suspend fun updateCache(nodes: List<NodeEntity>) {
        if (nodes.isEmpty()) return
        insertNodes(nodes)
        trimCache()
    }

    /**
     * Очистка базы данных до 2500 самых свежих записей.
     * Используется корректный синтаксис удаления через сравнение с ID.
     */
    @Query("""
        DELETE FROM dht_nodes 
        WHERE user_hash NOT IN (
            SELECT user_hash FROM (
                SELECT user_hash FROM dht_nodes 
                ORDER BY last_seen DESC 
                LIMIT 2500
            )
        )
    """)
    suspend fun trimCache()

    /**
     * Обновление сетевой информации при прямом контакте (UDP) или через рой.
     * Используется именование колонок, строго соответствующее NodeEntity.
     */
    @Query("""
        UPDATE dht_nodes 
        SET ip_address = :newIp, last_seen = :timestamp, public_key = :pubKey 
        WHERE user_hash = :hash
    """)
    suspend fun updateNetworkInfo(hash: String, newIp: String, pubKey: String, timestamp: Long)

    @Query("DELETE FROM dht_nodes")
    suspend fun clearAll()
}
