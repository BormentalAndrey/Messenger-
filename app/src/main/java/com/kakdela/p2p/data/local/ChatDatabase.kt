package com.kakdela.p2p.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Единая Room-база для мессенджера:
 * - сообщения
 * - P2P / DHT узлы
 */
@Database(
    entities = [
        MessageEntity::class,
        NodeEntity::class
    ],
    version = 2, // увеличена версия из-за добавления NodeEntity
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
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_db"
                )
                    // в проде допустимо, т.к. данные P2P кэшируемые
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
