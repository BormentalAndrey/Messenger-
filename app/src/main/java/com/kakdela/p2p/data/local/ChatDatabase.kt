package com.kakdela.p2p.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Главная БД мессенджера.
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

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_p2p_secure.db"
                )
                .fallbackToDestructiveMigration()
                // Режим WAL позволяет одновременно читать и писать в БД, 
                // что важно, когда устройство работает как мини-сервер.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
