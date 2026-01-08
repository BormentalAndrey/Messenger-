package com.kakdela.p2p.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Узел (Node) P2P/DHT сети + учётные данные пользователя.
 * Используется для:
 *  - аутентификации (email + passwordHash)
 *  - P2P соединений
 *  - DHT / torrent-синхронизации
 */
@Entity(tableName = "dht_nodes")
data class NodeEntity(
    @PrimaryKey
    val userHash: String,      // SHA-256(publicKey) или hash(email+password)
    val email: String,         // email пользователя
    val passwordHash: String,  // SHA-256(password)
    val phone: String,         // номер телефона
    val ip: String,            // последний IP
    val port: Int,             // P2P порт
    val publicKey: String,     // публичный ключ
    val lastSeen: Long         // timestamp последней активности
)

/**
 * DAO для работы с таблицей dht_nodes
 */
@Dao
interface NodeDao {

    /**
     * Получить узел по userHash (P2P / сообщения)
     */
    @Query("SELECT * FROM dht_nodes WHERE userHash = :hash LIMIT 1")
    suspend fun getNode(hash: String): NodeEntity?

    /**
     * Получить пользователя по email (AuthManager)
     */
    @Query("SELECT * FROM dht_nodes WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): NodeEntity?

    /**
     * Получить все узлы (DHT / контакты)
     */
    @Query("SELECT * FROM dht_nodes")
    suspend fun getAllNodes(): List<NodeEntity>

    /**
     * Вставка или обновление узла
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity)

    /**
     * Массовая вставка (torrent-синхронизация)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<NodeEntity>)

    /**
     * Ограничение DHT до 2500 последних узлов
     * (защита от разрастания базы)
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
}
