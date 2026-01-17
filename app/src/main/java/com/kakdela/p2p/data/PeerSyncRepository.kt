package com.kakdela.p2p.data

import android.util.Log
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * PeerSyncRepository
 *
 * Отвечает за децентрализованную синхронизацию peer-таблицы
 * между пользователями (gossip / peer exchange).
 *
 * Работает поверх IdentityRepository (UDP).
 */
class PeerSyncRepository(
    private val identityRepository: IdentityRepository,
    private val database: ChatDatabase
) {

    private val TAG = "PeerSyncRepository"

    private val nodeDao = database.nodeDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ограничение на частоту sync с одного пира */
    private val lastSyncFromPeer = ConcurrentHashMap<String, Long>()

    private companion object {
        const val SYNC_INTERVAL = 180_000L       // 3 минуты
        const val MAX_NODES_PER_PACKET = 50
        const val PEER_SYNC_TYPE = "PEER_SYNC"
        const val MIN_SYNC_INTERVAL_FROM_PEER = 120_000L // 2 минуты
    }

    /* ============================================================
       ЖИЗНЕННЫЙ ЦИКЛ
       ============================================================ */

    fun start() {
        scope.launch {
            delay(10_000) // даём сети подняться
            startGossipLoop()
        }
        Log.i(TAG, "PeerSyncRepository started")
    }

    fun stop() {
        scope.cancel()
        Log.i(TAG, "PeerSyncRepository stopped")
    }

    /* ============================================================
       GOSSIP LOOP
       ============================================================ */

    private suspend fun startGossipLoop() {
        while (scope.isActive) {
            try {
                performGossipSync()
            } catch (e: Exception) {
                Log.w(TAG, "Gossip sync error: ${e.message}")
            }
            delay(SYNC_INTERVAL)
        }
    }

    /**
     * Выбираем случайных пиров и отправляем им часть нашей peer-таблицы
     */
    private suspend fun performGossipSync() {
        val peers = identityRepository.swarmPeers.keys.toList()
        if (peers.isEmpty()) return

        // Выбираем 1–2 случайных пира
        val targets = peers.shuffled().take(Random.nextInt(1, 3))

        val nodesToSend = nodeDao.getRecentNodes(MAX_NODES_PER_PACKET)
        if (nodesToSend.isEmpty()) return

        val payload = buildSyncPayload(nodesToSend)

        targets.forEach { peerHash ->
            val ip = identityRepository.swarmPeers[peerHash] ?: return@forEach
            identityRepository.sendUdp(ip, PEER_SYNC_TYPE, payload)
            Log.d(TAG, "Sent peer-sync to $peerHash ($ip)")
        }
    }

    /* ============================================================
       INCOMING SYNC
       ============================================================ */

    /**
     * Вызывается из IdentityRepository.processIncomingPacket()
     */
    suspend fun handleIncoming(data: String, fromHash: String) {
        val now = System.currentTimeMillis()

        val last = lastSyncFromPeer[fromHash] ?: 0
        if (now - last < MIN_SYNC_INTERVAL_FROM_PEER) {
            Log.d(TAG, "Ignoring frequent peer-sync from $fromHash")
            return
        }
        lastSyncFromPeer[fromHash] = now

        try {
            val json = JSONObject(data)
            val nodes = json.getJSONArray("nodes")

            mergeIncomingNodes(nodes)
            Log.i(TAG, "Merged ${nodes.length()} nodes from $fromHash")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process peer-sync", e)
        }
    }

    /* ============================================================
       MERGE LOGIC (ANTI-ENTROPY)
       ============================================================ */

    private suspend fun mergeIncomingNodes(nodes: JSONArray) {
        withContext(Dispatchers.IO) {
            for (i in 0 until nodes.length()) {
                val obj = nodes.getJSONObject(i)

                val hash = obj.getString("hash")
                val ip = obj.optString("ip", "0.0.0.0")
                val port = obj.optInt("port", 8888)
                val publicKey = obj.optString("publicKey", "")
                val lastSeen = obj.optLong("lastSeen", 0L)

                if (hash.isBlank() || publicKey.isBlank()) continue

                val local = nodeDao.getNodeByHash(hash)

                // Новый узел
                if (local == null) {
                    nodeDao.upsert(
                        NodeEntity(
                            userHash = hash,
                            phone_hash = "",
                            ip = ip,
                            port = port,
                            publicKey = publicKey,
                            phone = "",
                            lastSeen = lastSeen
                        )
                    )
                    continue
                }

                // Защита: publicKey НИКОГДА не меняется
                if (local.publicKey.isNotEmpty() && local.publicKey != publicKey) {
                    Log.w(TAG, "PublicKey mismatch for $hash — ignoring")
                    continue
                }

                // Обновляем только если информация свежее
                if (lastSeen > local.lastSeen) {
                    nodeDao.updateNetworkInfo(
                        hash,
                        ip,
                        port,
                        local.publicKey.ifBlank { publicKey },
                        lastSeen
                    )
                }
            }
        }
    }

    /* ============================================================
       PAYLOAD BUILDING
       ============================================================ */

    private fun buildSyncPayload(nodes: List<NodeEntity>): String {
        val arr = JSONArray()

        nodes.take(MAX_NODES_PER_PACKET).forEach {
            arr.put(
                JSONObject().apply {
                    put("hash", it.userHash)
                    put("ip", it.ip)
                    put("port", it.port)
                    put("publicKey", it.publicKey)
                    put("lastSeen", it.lastSeen)
                }
            )
        }

        return JSONObject()
            .put("nodes", arr)
            .toString()
    }
}
