package com.kakdela.p2p.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        ORDER BY timestamp ASC
    """)
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    /**
     * Обновление статуса сообщения (Отправлено/Доставлено/Ожидание)
     */
    @Query("UPDATE messages SET status = :status WHERE messageId = :id")
    suspend fun updateStatus(id: String, status: String)

    /**
     * Удаление старых сообщений (опционально, для экономии места)
     */
    @Query("DELETE FROM messages WHERE timestamp < :expiryTime")
    suspend fun clearOldMessages(expiryTime: Long)
}
