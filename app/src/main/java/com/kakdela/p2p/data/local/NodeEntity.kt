package com.kakdela.p2p.data.local

import androidx.room.*

/**
 * Сущность узла распределенной сети (DHT/Gossip).
 * Хранит данные о 2500 активных "мини-серверах" для обеспечения связи.
 */
@Entity(tableName = "dht_nodes")
data class NodeEntity(
    @PrimaryKey
    val userHash: String,      // Уникальный идентификатор (Sha256 от публичного ключа)
    val email: String? = null, 
    val passwordHash: String? = null, // Только для локального профиля владельца
    val phone: String? = null, 
    val ip: String,            
    val port: Int = 8888,             
    val publicKey: String,     
    val lastSeen: Long = System.currentTimeMillis()
)

@Dao
interface NodeDao {

    // --- ПОИСК И АВТОРИЗАЦИЯ ---

    @Query("SELECT * FROM dht_nodes WHERE userHash = :hash LIMIT 1")
    suspend fun getNodeByHash(hash: String): NodeEntity?

    @Query("SELECT * FROM dht_nodes WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): NodeEntity?

    /**
     * Возвращает список из 2500 последних активных узлов.
     * Используется для инициализации роевого поиска.
     */
    @Query("SELECT * FROM dht_nodes ORDER BY lastSeen DESC LIMIT 2500")
    suspend fun getAllNodes(): List<NodeEntity>

    // --- ОБНОВЛЕНИЕ И ОЧИСТКА ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<NodeEntity>)

    /**
     * Транзакция для обновления кэша: вставляем новые данные и сразу удаляем лишние.
     * Это гарантирует, что база данных не раздуется выше 2500 записей.
     */
    @Transaction
    suspend fun updateCache(nodes: List<NodeEntity>) {
        insertAll(nodes)
        trimDatabase()
    }

    /**
     * Оставляет только 2500 самых свежих узлов.
     */
    @Query("""
        DELETE FROM dht_nodes 
        WHERE userHash NOT IN (
            SELECT userHash FROM dht_nodes 
            ORDER BY lastSeen DESC 
            LIMIT 2500
        )
    """)
    suspend fun trimDatabase()

    /**
     * Обновляет сетевой адрес узла. Используется, когда узел ответил на роевой запрос
     * или был обнаружен в Wi-Fi сети.
     */
    @Query("""
        UPDATE dht_nodes 
        SET ip = :newIp, lastSeen = :timestamp, publicKey = :pubKey 
        WHERE userHash = :hash
    """)
    suspend fun updateNetworkInfo(hash: String, newIp: String, pubKey: String, timestamp: Long)

    @Query("DELETE FROM dht_nodes")
    suspend fun clearAll()
}
