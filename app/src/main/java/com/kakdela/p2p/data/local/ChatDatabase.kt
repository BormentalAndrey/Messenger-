package com.kakdela.p2p.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Главная база данных мессенджера.
 * Хранит историю сообщений и кэш узлов распределенной сети.
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
         * Используется Double-Checked Locking для потокобезопасности.
         */
        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_p2p_secure.db"
                )
                // Разрешает деструктивную миграцию при изменении версии (удаляет данные).
                // Для продакшена со стабильной схемой стоит использовать именованные миграции.
                .fallbackToDestructiveMigration()
                
                // Режим WAL (Write-Ahead Logging) критически важен для P2P:
                // Он позволяет фоновым процессам (синхронизация узлов) писать в БД,
                // не блокируя при этом чтение для UI (отображение сообщений).
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
