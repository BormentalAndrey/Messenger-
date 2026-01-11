package com.kakdela.p2p.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущность сообщения для P2P-мессенджера.
 * Реализует хранение текстовых данных, метаданных файлов и параметров планирования.
 * Соответствует требованиям Технического Задания по защищенному хранению и статусам доставки.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId"]), 
        Index(value = ["timestamp"]),
        Index(value = ["status"]),
        Index(value = ["scheduledTime"]) // Индекс оптимизирован для выборки отложенных задач WorkManager-ом
    ]
)
data class MessageEntity(
    /**
     * Уникальный идентификатор сообщения.
     * Генерируется как UUID для исходящих или берется из протокола для входящих.
     */
    @PrimaryKey
    val messageId: String,

    /**
     * Идентификатор чата (SHA-256 публичного ключа собеседника).
     */
    val chatId: String,      

    /**
     * Идентификатор отправителя (User ID).
     */
    val senderId: String,    

    /**
     * Идентификатор получателя (User ID).
     */
    val receiverId: String,  
    
    /**
     * Содержимое сообщения.
     * Для текстовых сообщений — зашифрованная строка (E2EE).
     * Для файлов — текстовое описание или локальный путь.
     */
    val text: String,        

    /**
     * Время создания/получения сообщения в миллисекундах.
     */
    val timestamp: Long,     

    /**
     * Флаг направления сообщения: true — исходящее, false — входящее.
     */
    val isMe: Boolean,       

    /**
     * Статус прочтения сообщения пользователем на данном устройстве.
     */
    val isRead: Boolean = false,
    
    /**
     * Текущий статус жизненного цикла сообщения:
     * PENDING - ожидает первичной обработки
     * SCHEDULED - запланировано на будущее время
     * SENT - успешно отправлено в P2P сеть
     * DELIVERED - получено подтверждение доставки
     * FAILED - ошибка после всех попыток ретрая
     */
    val status: String = "PENDING",

    /**
     * Время в ms (Unix Epoch), когда сообщение должно быть отправлено.
     * null для мгновенных сообщений.
     */
    val scheduledTime: Long? = null,

    /**
     * Тип контента: "TEXT", "IMAGE", "FILE", "AUDIO".
     */
    val messageType: String = "TEXT", 

    /**
     * Оригинальное имя файла (только для медиа-сообщений).
     */
    val fileName: String? = null,

    /**
     * MIME-тип файла (например, "image/jpeg" или "audio/m4a").
     */
    val fileMime: String? = null,
    
    /**
     * Байтовое содержимое для мелких файлов (до 512КБ).
     * Для крупных файлов рекомендуется хранить путь в [text] или [fileName].
     */
    val fileBytes: ByteArray? = null,
    
    /**
     * Номер телефона из Discovery, если сообщение связано с контактом.
     */
    val contactPhone: String? = null 
) {
    /**
     * Проверяет, является ли сообщение медиа-файлом.
     */
    fun isMedia(): Boolean = messageType != "TEXT"
    
    /**
     * Кастомная реализация equals для корректного сравнения Room-сущностей по ID.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MessageEntity
        return messageId == other.messageId
    }

    /**
     * Хэш-код на основе messageId для стабильности в коллекциях.
     */
    override fun hashCode(): Int = messageId.hashCode()
}
