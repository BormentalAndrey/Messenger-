package com.kakdela.p2p.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс доступа к данным сообщений.
 * Реализует реактивное обновление через Flow и оптимизированные SQL-запросы.
 */
@Dao
interface MessageDao {

    /**
     * Получение всей переписки с конкретным пиром.
     */
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        ORDER BY timestamp ASC
    """)
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    /**
     * ОПТИМИЗИРОВАНО ДЛЯ KSP/ROOM:
     * Получает список последних сообщений для каждого уникального chatId.
     * Мы используем подзапрос для поиска максимального времени, что гарантирует
     * корректную работу Flow и совместимость с парсером Room.
     */
    @Query("""
        SELECT * FROM messages 
        WHERE messageId IN (
            SELECT messageId FROM messages AS m2
            WHERE m2.chatId = messages.chatId
            ORDER BY m2.timestamp DESC, m2.messageId DESC
            LIMIT 1
        )
        AND chatId != 'global'
        ORDER BY timestamp DESC
    """)
    fun observeLastMessages(): Flow<List<MessageEntity>>

    /**
     * Вставка или обновление сообщения. 
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    /**
     * Массовая вставка.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    /**
     * Атомарное обновление статуса доставки.
     */
    @Query("UPDATE messages SET status = :status WHERE messageId = :id")
    suspend fun updateStatus(id: String, status: String)

    /**
     * Пометка всех входящих сообщений в чате как прочитанных.
     */
    @Query("""
        UPDATE messages 
        SET isRead = 1 
        WHERE chatId = :chatId AND isMe = 0 AND isRead = 0
    """)
    suspend fun markChatAsRead(chatId: String)

    /**
     * Подсчет общего количества непрочитанных входящих сообщений.
     */
    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0 AND isMe = 0")
    fun getUnreadCountGlobal(): Flow<Int>

    /**
     * Проверка существования переписки.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE chatId = :chatId LIMIT 1)")
    suspend fun hasMessages(chatId: String): Boolean

    /**
     * Получение конкретного сообщения по ID.
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
     * Очистка старых сообщений (гигиена базы данных).
     */
    @Query("DELETE FROM messages WHERE timestamp < :expiryTime")
    suspend fun clearOldMessages(expiryTime: Long)
}
