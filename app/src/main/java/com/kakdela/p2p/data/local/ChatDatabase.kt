package com.kakdela.p2p.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.JournalMode

/**
 * Основная база данных для P2P чата.
 * Хранит сообщения и информацию об узлах (NodeEntity).
 */
@Database(
    entities = [MessageEntity::class, NodeEntity::class],
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
         * Получение singleton инстанса базы данных.
         */
        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_p2p_secure.db"
                )
                    .fallbackToDestructiveMigration() // Автоматическая миграция при смене версии
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING) // Улучшенная производительность при параллельных операциях
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
