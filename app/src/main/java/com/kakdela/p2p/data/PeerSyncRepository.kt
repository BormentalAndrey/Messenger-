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
 * Реализует децентрализованный обмен таблицами маршрутизации (Gossip Protocol).
 * Позволяет узлам находить друг друга через "общих знакомых", даже если сервер отключен.
 */
class PeerSyncRepository(
    private val identityRepository: IdentityRepository,
    private val database: ChatDatabase
) {

    private val TAG = "PeerSyncRepository"
    private val nodeDao = database.nodeDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ограничение частоты обработки обновлений от конкретного пира */
    private val lastSyncFromPeer = ConcurrentHashMap<String, Long>()

    private companion object {
        const val SYNC_INTERVAL = 60_000L         // Рассылка раз в минуту
        const val MAX_NODES_PER_PACKET = 8        // Оптимально для MTU (чтобы пакет не дробился)
        const val PEER_SYNC_TYPE = "PEER_SYNC"
        const val MIN_SYNC_INTERVAL_FROM_PEER = 30_000L // Защита от спама пакетами
    }

    /* ============================================================
       ЖИЗНЕННЫЙ ЦИКЛ
       ============================================================ */

    fun start() {
        scope.launch {
            // Небольшая пауза перед стартом, чтобы IdentityRepository успел открыть сокет
            delay(5000) 
            startGossipLoop()
        }
        Log.i(TAG, "Gossip engine initialized")
    }

    fun stop() {
        scope.cancel()
        Log.i(TAG, "Gossip engine stopped")
    }

    /* ============================================================
       GOSSIP LOOP (РАССЫЛКА СВОЕЙ ТАБЛИЦЫ)
       ============================================================ */

    private suspend fun startGossipLoop() {
        while (scope.isActive) {
            try {
                performGossipSync()
            } catch (e: Exception) {
                Log.w(TAG, "Gossip sync iteration failed: ${e.message}")
            }
            delay(SYNC_INTERVAL)
        }
    }

    /**
     * Выбирает случайные цели и отправляет им информацию об узлах, которые мы знаем.
     */
    private suspend fun performGossipSync() {
        // Собираем список всех потенциальных целей для отправки данных
        val activePeers = (identityRepository.swarmPeers.keys + identityRepository.wifiPeers.keys).toMutableList()
        
        // Если активных соединений нет (сервер упал, Wi-Fi пуст), пробуем "простучать" 3 случайных узла из БД
        if (activePeers.isEmpty()) {
            val historicalNodes = nodeDao.getRecentNodes(10)
                .map { it.userHash }
                .filter { it != identityRepository.getMyId() }
            activePeers.addAll(historicalNodes)
        }

        if (activePeers.isEmpty()) return

        // Выбираем 2 случайные цели для распространения сплетен (Gossip Fan-out)
        val targets = activePeers.shuffled().take(2)

        // Берем случайную выборку из нашей БД, чтобы постепенно обновить всю сеть
        val nodesToSend = nodeDao.getAllNodes().shuffled().take(MAX_NODES_PER_PACKET)
        if (nodesToSend.isEmpty()) return

        val payload = buildSyncPayload(nodesToSend)

        targets.forEach { peerHash ->
            if (peerHash == identityRepository.getMyId()) return@forEach
            
            // Ищем IP: сначала в оперативной памяти (активные), потом в БД (история)
            val ip = identityRepository.swarmPeers[peerHash] 
                ?: identityRepository.wifiPeers[peerHash]
                ?: nodeDao.getNodeByHash(peerHash)?.ip

            if (!ip.isNullOrBlank() && ip != "0.0.0.0") {
                identityRepository.sendUdp(ip, PEER_SYNC_TYPE, payload)
                Log.d(TAG, "Gossip: shared knowledge with $peerHash at $ip")
            }
        }
    }

    /* ============================================================
       INCOMING SYNC (ОБРАБОТКА ЧУЖИХ ТАБЛИЦ)
       ============================================================ */

    /**
     * Вызывается из IdentityRepository при получении пакета типа PEER_SYNC.
     */
    suspend fun handleIncoming(data: String, fromHash: String) {
        val now = System.currentTimeMillis()
        val last = lastSyncFromPeer[fromHash] ?: 0
        
        if (now - last < MIN_SYNC_INTERVAL_FROM_PEER) return
        lastSyncFromPeer[fromHash] = now

        try {
            val json = JSONObject(data)
            val nodesArray = json.optJSONArray("nodes") ?: return
            
            mergeIncomingNodes(nodesArray)
        } catch (e: Exception) {
            Log.e(TAG, "Malformed peer-sync packet from $fromHash", e)
        }
    }

    /* ============================================================
       MERGE LOGIC (ОБНОВЛЕНИЕ КАРТЫ СЕТИ)
       ============================================================ */

    private suspend fun mergeIncomingNodes(nodes: JSONArray) {
        withContext(Dispatchers.IO) {
            for (i in 0 until nodes.length()) {
                val obj = nodes.getJSONObject(i)
                val hash = obj.getString("hash")
                
                // Пропускаем себя
                if (hash == identityRepository.getMyId()) continue

                val ip = obj.optString("ip", "0.0.0.0")
                val port = obj.optInt("port", 8888)
                val publicKey = obj.optString("publicKey", "")
                val lastSeen = obj.optLong("lastSeen", 0L)

                if (hash.isBlank() || publicKey.isBlank()) continue

                val local = nodeDao.getNodeByHash(hash)

                if (local == null) {
                    // Мы не знали об этом пользователе раньше. Добавляем и пытаемся связаться.
                    nodeDao.upsert(NodeEntity(hash, "", ip, port, publicKey, "", lastSeen))
                    identityRepository.sendUdp(ip, "PING", "discovery")
                } else {
                    // Безопасность: если PublicKey не совпадает, игнорируем (защита от захвата ID)
                    if (local.publicKey.isNotEmpty() && local.publicKey != publicKey) {
                        Log.w(TAG, "Security alert: $hash reported new public key. Rejected.")
                        continue
                    }

                    // Обновляем данные только если информация от другого пира новее нашей
                    if (lastSeen > local.lastSeen) {
                        nodeDao.updateNetworkInfo(hash, ip, port, local.publicKey, lastSeen)
                        
                        // Если IP изменился, пингуем узел по новому адресу
                        if (local.ip != ip) {
                            identityRepository.sendUdp(ip, "PING", "ip_update")
                        }
                    }
                }
            }
        }
    }

    /* ============================================================
       PAYLOAD BUILDING
       ============================================================ */

    private fun buildSyncPayload(nodes: List<NodeEntity>): String {
        val arr = JSONArray()
        nodes.forEach {
            val obj = JSONObject().apply {
                put("hash", it.userHash)
                put("ip", it.ip)
                put("port", it.port)
                put("publicKey", it.publicKey)
                put("lastSeen", it.lastSeen)
            }
            arr.put(obj)
        }
        return JSONObject().put("nodes", arr).toString()
    }
}
