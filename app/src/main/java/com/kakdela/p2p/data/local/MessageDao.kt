package com.kakdela.p2p.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс доступа к данным сообщений.
 * Обеспечивает реактивное обновление UI через Flow и эффективное управление P2P-логами.
 */
@Dao
interface MessageDao {

    /**
     * Получение всей переписки с конкретным пиром.
     * Используется в ChatScreen.
     */
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        ORDER BY timestamp ASC
    """)
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    /**
     * ОПТИМИЗИРОВАНО ДЛЯ ПРОДАКШНА:
     * Получает список последних сообщений для каждого уникального chatId.
     * Использует обобщенное табличное выражение (CTE) и оконную функцию ROW_NUMBER.
     * Это гарантирует, что каждый чат представлен ровно одним (самым свежим) сообщением.
     */
    @Query("""
        WITH RankedMessages AS (
            SELECT *,
                   ROW_NUMBER() OVER (
                       PARTITION BY chatId 
                       ORDER BY timestamp DESC, messageId DESC
                   ) as rn
            FROM messages
            WHERE chatId != 'global'
        )
        SELECT 
            messageId, chatId, senderId, receiverId, text, timestamp, 
            isMe, isRead, status, messageType, fileName, fileMime, 
            fileBytes, contactPhone
        FROM RankedMessages
        WHERE rn = 1
        ORDER BY timestamp DESC
    """)
    fun observeLastMessages(): Flow<List<MessageEntity>>

    /**
     * Вставка или обновление сообщения. 
     * Если сообщение с таким ID уже есть (например, статус обновился), оно будет перезаписано.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    /**
     * Массовая вставка. Полезна при синхронизации истории.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    /**
     * Атомарное обновление статуса (например, с PENDING на SENT_WIFI).
     */
    @Query("UPDATE messages SET status = :status WHERE messageId = :id")
    suspend fun updateStatus(id: String, status: String)

    /**
     * Отмечает все входящие сообщения от конкретного пользователя как прочитанные.
     */
    @Query("""
        UPDATE messages 
        SET isRead = 1 
        WHERE chatId = :chatId AND isMe = 0 AND isRead = 0
    """)
    suspend fun markChatAsRead(chatId: String)

    /**
     * Возвращает общее количество непрочитанных сообщений во всех чатах.
     * Идеально подходит для отображения счетчика на главном экране или иконке.
     */
    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0 AND isMe = 0")
    fun getUnreadCountGlobal(): Flow<Int>

    /**
     * Проверяет, есть ли хотя бы одно сообщение в данном чате.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE chatId = :chatId LIMIT 1)")
    suspend fun hasMessages(chatId: String): Boolean

    /**
     * Поиск конкретного сообщения.
     */
    @Query("SELECT * FROM messages WHERE messageId = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    /**
     * Удаление одного сообщения.
     */
    @Delete
    suspend fun delete(message: MessageEntity)

    /**
     * Полное удаление диалога.
     */
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChatHistory(chatId: String)

    /**
     * Удаление старых сообщений для экономии места (согласно лимитам устройства).
     */
    @Query("DELETE FROM messages WHERE timestamp < :expiryTime")
    suspend fun clearOldMessages(expiryTime: Long)
}
