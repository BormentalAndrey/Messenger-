package com.kakdela.p2p.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NodeDao {

    /**
     * Получение узла по userHash (основной идентификатор).
     */
    @Query("SELECT * FROM dht_nodes WHERE userHash = :hash LIMIT 1")
    suspend fun getNodeByHash(hash: String): NodeEntity?

    /**
     * Получение узла по номеру телефона (если есть).
     * Используется для быстрого локального поиска контактов.
     */
    @Query("SELECT * FROM dht_nodes WHERE phone = :phone LIMIT 1")
    suspend fun getNodeByPhone(phone: String): NodeEntity?

    /**
     * Получение ограниченного списка самых свежих узлов.
     * Используется для bootstrap / gossip / UI.
     */
    @Query("SELECT * FROM dht_nodes ORDER BY lastSeen DESC LIMIT :limit")
    suspend fun getRecentNodes(limit: Int): List<NodeEntity>

    /**
     * Получение всех узлов (жёсткий лимит 2500).
     * Гарантирует, что база не разрастётся.
     */
    @Query("SELECT * FROM dht_nodes ORDER BY lastSeen DESC LIMIT 2500")
    suspend fun getAllNodes(): List<NodeEntity>

    /**
     * Вставка или обновление одного узла.
     * Используется при прямом контакте или серверной синхронизации.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: NodeEntity)

    /**
     * Пакетная вставка или обновление узлов.
     * Основной метод при синхронизации с сервером или DHT.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(nodes: List<NodeEntity>)

    /**
     * Обновление сетевых данных узла при прямом контакте (UDP / NSD / LAN).
     * Обновляет lastSeen всегда.
     */
    @Query("""
        UPDATE dht_nodes 
        SET ip = :newIp,
            port = :newPort,
            publicKey = :pubKey,
            lastSeen = :timestamp
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
     * Помечает узел как успешно синхронизированный с сервером.
     * Используется для оптимизации повторных sync-запросов.
     */
    @Query("""
        UPDATE dht_nodes
        SET isSynced = 1
        WHERE userHash = :hash
    """)
    suspend fun markSynced(hash: String)

    /**
     * Комплексное обновление кэша узлов.
     *
     * 1. Сохраняет новые данные (upsert)
     * 2. Очищает базу, оставляя только 2500 самых свежих
     *
     * Выполняется атомарно.
     */
    @Transaction
    suspend fun updateCache(nodes: List<NodeEntity>) {
        if (nodes.isEmpty()) return
        upsertAll(nodes)
        trimCache()
    }

    /**
     * Удаление "хвоста" базы.
     * Оставляет только 2500 самых свежих записей по lastSeen.
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
     * Удаление устаревших узлов по времени.
     * Используется для периодической очистки (например, старше 30 дней).
     */
    @Query("DELETE FROM dht_nodes WHERE lastSeen < :timestampThreshold")
    suspend fun deleteStaleNodes(timestampThreshold: Long)
}
