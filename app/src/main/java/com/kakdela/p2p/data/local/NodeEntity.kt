package com.kakdela.p2p.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "dht_nodes")
data class NodeEntity(
    @PrimaryKey
    val userHash: String,      
    val email: String,         
    val passwordHash: String,  
    val phone: String,         
    val ip: String,            
    val port: Int,             
    val publicKey: String,     
    val lastSeen: Long         
)

@Dao
interface NodeDao {

    @Query("SELECT * FROM dht_nodes WHERE userHash = :hash LIMIT 1")
    suspend fun getNode(hash: String): NodeEntity?

    @Query("SELECT * FROM dht_nodes WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): NodeEntity?

    @Query("SELECT * FROM dht_nodes ORDER BY lastSeen DESC")
    suspend fun getAllNodes(): List<NodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity)

    /**
     * Специальный метод для P2P-обучения. 
     * Обновляет только сетевые данные, не затрагивая почту и хеш пароля.
     */
    @Query("""
        UPDATE dht_nodes 
        SET ip = :newIp, lastSeen = :timestamp, publicKey = :pubKey 
        WHERE userHash = :hash
    """)
    suspend fun updateNetworkInfo(hash: String, newIp: String, pubKey: String, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<NodeEntity>)

    @Query("""
        DELETE FROM dht_nodes 
        WHERE userHash NOT IN (
            SELECT userHash FROM dht_nodes 
            ORDER BY lastSeen DESC 
            LIMIT 2500
        )
    """)
    suspend fun trimDatabase()
}
