package com.dchat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.sqlcipher.database.SupportFactory

// Сущность контакта
@androidx.room.Entity(tableName = "contacts")
data class ContactEntity(
    @androidx.room.PrimaryKey val phoneHash: String, // Ключ поиска
    val phoneNumberRaw: String, // Для отображения (хранится зашифрованно SQLCipher)
    val name: String,
    val userId: String,
    val publicKey: String, // Для шифрования сообщений ему
    val trustLevel: Int = 1
)

@Database(entities = [ContactEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
}

// DAO
@androidx.room.Dao
interface ContactDao {
    @androidx.room.Query("SELECT * FROM contacts WHERE phoneHash = :hash LIMIT 1")
    suspend fun getContactByHash(hash: String): ContactEntity?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)
    
    @androidx.room.Query("SELECT COUNT(*) FROM contacts")
    suspend fun getCount(): Int
}

object DatabaseBuilder {
    private var INSTANCE: AppDatabase? = null

    // Пароль для БД должен храниться в Android Keystore!
    // Для примера передаем строку.
    fun getInstance(context: Context, passphrase: ByteArray): AppDatabase {
        if (INSTANCE == null) {
            val factory = SupportFactory(passphrase) // SQLCipher factory
            INSTANCE = Room.databaseBuilder(context, AppDatabase::class.java, "dchat_secure.db")
                .openHelperFactory(factory) // Включаем шифрование
                .build()
        }
        return INSTANCE!!
    }
}
