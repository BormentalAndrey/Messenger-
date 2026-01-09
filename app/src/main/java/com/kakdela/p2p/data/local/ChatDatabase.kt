package com.kakdela.p2p.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Основная база данных мессенджера.
 * Хранит историю сообщений и распределенный кэш узлов (DHT/Gossip).
 */
@Database(
    entities = [
        MessageEntity::class, 
        NodeEntity::class
    ],
    version = 3, // Обновлено для поддержки расширенных полей NodeEntity
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun nodeDao(): NodeDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        /**
         * Получение синглтона базы данных.
         * Использует Double-Check Locking для потокобезопасности.
         */
        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_p2p_secure.db"
                )
                // Позволяет избежать крашей при обновлении схемы данных в P2P-сетях
                .fallbackToDestructiveMigration()
                // Оптимизация для многопоточной работы мини-сервера
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING) 
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
