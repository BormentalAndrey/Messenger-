package com.kakdela.p2p.data.local

import androidx.room.*

@Dao
interface NodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeEntity>)

    /**
     * Получение узла по хешу для роевого ответа (Мини-сервер)
     */
    @Query("SELECT * FROM nodes WHERE userHash = :hash LIMIT 1")
    suspend fun getNodeByHash(hash: String): NodeEntity?

    /**
     * Получение списка всех узлов для роевого опроса (DHT)
     * Сортировка по lastSeen гарантирует, что мы опрашиваем активных участников
     */
    @Query("SELECT * FROM nodes ORDER BY lastSeen DESC LIMIT 2500")
    suspend fun getAllNodes(): List<NodeEntity>

    /**
     * Удаление лишних узлов, превышающих лимит 2500 (Очистка БД)
     */
    @Query("""
        DELETE FROM nodes WHERE userHash NOT IN (
            SELECT userHash FROM nodes ORDER BY lastSeen DESC LIMIT 2500
        )
    """)
    suspend fun trimCache()
}
