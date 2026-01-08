package com.kakdela.p2p.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Сущность узла (Node) в локальной базе данных P2P.
 * Используется для хранения информации о пользователях сети.
 */
@Entity(tableName = "dht_nodes")
data class NodeEntity(
    @PrimaryKey val userHash: String,  // Уникальный хеш пользователя (например, hash(email + password))
    val email: String,                 // Email пользователя (для поиска)
    val passwordHash: String,          // Локальный хеш пароля (не хранить plain text)
    val phone: String,                 // Номер телефона для сверки с контактами
    val ip: String,                    // Последний известный IP для P2P соединений
    val port: Int,                      // Порт для P2P соединений
    val publicKey: String,             // Публичный ключ пользователя для шифрования
    val lastSeen: Long                 // Временная метка последнего контакта с этим узлом
)

/**
 * DAO для работы с таблицей dht_nodes
 */
@Dao
interface NodeDao {

    /**
     * Получить конкретного узла по userHash.
     * Используется для P2P соединений или отправки сообщений.
     */
    @Query("SELECT * FROM dht_nodes WHERE userHash = :hash LIMIT 1")
    suspend fun getNode(hash: String): NodeEntity?

    /**
     * Получить всех узлов из базы.
     * Используется для фильтрации по контактам или синхронизации.
     */
    @Query("SELECT * FROM dht_nodes")
    suspend fun getAllNodes(): List<NodeEntity>

    /**
     * Вставка одного узла в базу.
     * Если узел уже существует (по primary key), происходит обновление.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity)

    /**
     * Вставка списка узлов в базу.
     * Используется при синхронизации с сервером.
     * Существующие записи заменяются.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<NodeEntity>)
}
