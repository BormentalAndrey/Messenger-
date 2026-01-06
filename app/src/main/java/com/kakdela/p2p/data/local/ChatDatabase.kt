package com.kakdela.p2p.data.local

import android.content.Context
import androidx.room.*
// ИСПРАВЛЕНО: Импорты для версии net.zetetic 4.5.4
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): kotlinx.coroutines.flow.Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)
}

@Database(entities = [MessageEntity::class], version = 2, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                // Инициализация нативных библиотек SQLCipher
                SQLiteDatabase.loadLibs(context)
                
                // ВАЖНО: Используйте безопасный способ хранения пароля в реальном приложении
                val factory = SupportOpenHelperFactory("secure_password".toByteArray())
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_secure_db"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}

