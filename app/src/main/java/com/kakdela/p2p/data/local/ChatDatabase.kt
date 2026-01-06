package com.kakdela.p2p.data.local

import android.content.Context
import androidx.room.*
import com.kakdela.p2p.data.Message
import kotlinx.coroutines.flow.Flow
import net.sqlcipher.database.SQLiteDatabase // ВАЖНО
import net.sqlcipher.database.SupportOpenHelperFactory // ВАЖНО

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity) // Используйте Entity, если в базе Room

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
                SQLiteDatabase.loadLibs(context) // Загрузка нативных либ
                val factory = SupportOpenHelperFactory("secure_password".toByteArray())
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_db"
                )
                .openHelperFactory(factory)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

