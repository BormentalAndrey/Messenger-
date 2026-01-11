package com.kakdela.p2p.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс доступа к данным (DAO) для управления сообщениями.
 * Обеспечивает реактивное обновление UI через Flow и поддержку фоновых задач.
 */
@Dao
interface MessageDao {

    /**
     * Получение всей переписки с конкретным чатом.
     * Используется на экране сообщений. Обновляется автоматически при изменении данных.
     */
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        ORDER BY timestamp ASC
    """)
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    /**
     * Получает список последних сообщений из каждого чата для главного экрана.
     * Исключает системный чат 'global'.
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
     * Сохранение или обновление сообщения. 
     * Используется при отправке и получении новых данных.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    /**
     * Массовая вставка сообщений.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    /**
     * Атомарное обновление статуса (PENDING, SENT, DELIVERED, FAILED).
     */
    @Query("UPDATE messages SET status = :status WHERE messageId = :id")
    suspend fun updateStatus(id: String, status: String)

    /**
     * Выборка сообщений, время отправки которых наступило или уже прошло.
     * Критически важен для корректной работы фонового планировщика (WorkManager).
     *
     * @param currentTime Текущее время в ms (System.currentTimeMillis()).
     */
    @Query("""
        SELECT * FROM messages 
        WHERE scheduledTime IS NOT NULL 
        AND scheduledTime <= :currentTime 
        AND status = 'SCHEDULED'
    """)
    suspend fun getPendingScheduledMessages(currentTime: Long): List<MessageEntity>

    /**
     * Помечает все непрочитанные входящие сообщения в конкретном чате как прочитанные.
     */
    @Query("""
        UPDATE messages 
        SET isRead = 1 
        WHERE chatId = :chatId AND isMe = 0 AND isRead = 0
    """)
    suspend fun markChatAsRead(chatId: String)

    /**
     * Реактивный подсчет общего количества непрочитанных сообщений во всех чатах.
     */
    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0 AND isMe = 0")
    fun getUnreadCountGlobal(): Flow<Int>

    /**
     * Проверяет наличие любых сообщений в чате.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE chatId = :chatId LIMIT 1)")
    suspend fun hasMessages(chatId: String): Boolean

    /**
     * Поиск конкретного сообщения по его уникальному ID.
     */
    @Query("SELECT * FROM messages WHERE messageId = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    /**
     * Удаление объекта сообщения из базы.
     */
    @Delete
    suspend fun delete(message: MessageEntity)

    /**
     * Полное удаление истории переписки с конкретным пользователем.
     */
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChatHistory(chatId: String)

    /**
     * Удаление старых записей для оптимизации размера БД.
     * @param expiryTime Временная метка, до которой сообщения будут удалены.
     */
    @Query("DELETE FROM messages WHERE timestamp < :expiryTime")
    suspend fun clearOldMessages(expiryTime: Long)
}
