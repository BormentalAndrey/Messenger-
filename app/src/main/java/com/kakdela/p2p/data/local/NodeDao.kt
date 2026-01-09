package com.kakdela.p2p.data.local

import androidx.room.*

/**
 * Data Access Object для работы с узлами сети (DHT).
 * Запросы используют имена колонок из @ColumnInfo в NodeEntity.
 */
@Dao
interface NodeDao {

    @Query("SELECT * FROM dht_nodes WHERE user_hash = :hash LIMIT 1")
    suspend fun getNodeByHash(hash: String): NodeEntity?

    @Query("SELECT * FROM dht_nodes WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): NodeEntity?

    /**
     * Получение списка из 2500 последних активных узлов для роевого поиска.
     */
    @Query("SELECT * FROM dht_nodes ORDER BY last_seen DESC LIMIT 2500")
    suspend fun getAllNodes(): List<NodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeEntity>)

    /**
     * Атомарное обновление кэша: вставка новых данных и удаление старых свыше лимита 2500.
     */
    @Transaction
    suspend fun updateCache(nodes: List<NodeEntity>) {
        insertNodes(nodes)
        trimCache()
    }

    /**
     * Очистка базы данных до 2500 самых свежих записей.
     */
    @Query("""
        DELETE FROM dht_nodes 
        WHERE user_hash NOT IN (
            SELECT user_hash FROM dht_nodes 
            ORDER BY last_seen DESC 
            LIMIT 2500
        )
    """)
    suspend fun trimCache()

    /**
     * Обновление сетевой информации при прямом контакте или через рой.
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
