package com.kakdela.p2p.data.local

import android.content.Context
import androidx.room.*
import com.kakdela.p2p.data.Message
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<Message>)
}

@Database(entities = [Message::class], version = 2, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                // Шифрование БД паролем
                val factory = SupportOpenHelperFactory("secure_db_password".toByteArray())
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

