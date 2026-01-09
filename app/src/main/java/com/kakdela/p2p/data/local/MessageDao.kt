package com.kakdela.p2p.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс доступа к данным сообщений.
 * Обеспечивает реактивное обновление UI через Flow и управление статусами P2P-доставки.
 */
@Dao
interface MessageDao {

    /**
     * Получение всех сообщений конкретного чата.
     * Используется во ViewMode для отображения переписки в реальном времени.
     */
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        ORDER BY timestamp ASC
    """)
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    /**
     * Получение последнего сообщения для каждого чата (для списка диалогов).
     */
    @Query("""
        SELECT * FROM messages 
        WHERE timestamp IN (SELECT MAX(timestamp) FROM messages GROUP BY chatId)
        ORDER BY timestamp DESC
    """)
    fun observeLastMessages(): Flow<List<MessageEntity>>

    /**
     * Вставка одного сообщения. 
     * Если ID совпадает (например, при получении подтверждения доставки), запись обновляется.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    /**
     * Массовая вставка (используется при синхронизации с сервером или роем).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    /**
     * Обновление статуса доставки.
     * Важно для визуализации этапов: PENDING -> SENT_P2P -> DELIVERED.
     */
    @Query("UPDATE messages SET status = :status WHERE messageId = :id")
    suspend fun updateStatus(id: String, status: String)

    /**
     * Пометка сообщений как прочитанных.
     */
    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND isMe = 0")
    suspend fun markChatAsRead(chatId: String)

    /**
     * Получение конкретного сообщения по ID.
     */
    @Query("SELECT * FROM messages WHERE messageId = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    /**
     * Удаление конкретного сообщения.
     */
    @Delete
    suspend fun delete(message: MessageEntity)

    /**
     * Очистка истории сообщений в конкретном чате.
     */
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChatHistory(chatId: String)

    /**
     * Автоматическая очистка старых данных для экономии места на устройстве.
     */
    @Query("DELETE FROM messages WHERE timestamp < :expiryTime")
    suspend fun clearOldMessages(expiryTime: Long)
}
