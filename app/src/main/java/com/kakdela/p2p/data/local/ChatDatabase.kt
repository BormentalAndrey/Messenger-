package com.kakdela.p2p.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Главная база данных мессенджера KakDela P2P.
 * Инкапсулирует работу с локальным хранилищем SQLite через Room.
 * * Version 3: Включает NodeEntity с индексами по phone_hash и user_hash.
 */
@Database(
    entities = [
        MessageEntity::class, 
        NodeEntity::class
    ],
    version = 3, 
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun nodeDao(): NodeDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        /**
         * Получение инстанса БД (Singleton).
         * Реализовано с защитой от многопоточных конфликтов при инициализации.
         */
        fun getDatabase(context: Context): ChatDatabase {
            // Возвращаем существующий инстанс, если он есть
            return INSTANCE ?: synchronized(this) {
                // Повторная проверка внутри блока synchronized (Double-checked locking)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_p2p_secure.db"
                )
                /**
                 * Деструктивная миграция (fallbackToDestructiveMigration):
                 * При изменении версии (например, с 2 на 3) Room очистит все таблицы.
                 * Это необходимо на этапе разработки, чтобы избежать крэшей из-за несовпадения схем.
                 */
                .fallbackToDestructiveMigration()
                
                /**
                 * Режим WAL (Write-Ahead Logging):
                 * Позволяет одновременно выполнять запись (обновление статусов узлов)
                 * и чтение (отображение чатов) без взаимных блокировок.
                 */
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
