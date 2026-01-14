package com.kakdela.p2p.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * FileTransferWorker управляет сегментированной передачей файлов через P2P.
 * Реализует логику "сидирования" (раздачи) и "скачивания" чанками.
 */
class FileTransferWorker(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {
    private val TAG = "FileTransferWorker"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seedingFiles = ConcurrentHashMap<String, File>()
    private val activeDownloads = ConcurrentHashMap<String, DownloadSession>()

    private val CHUNK_SIZE = 16 * 1024 // 16KB чанки для UDP стабильности

    private val listener: (String, String, String, String) -> Unit = { type, data, fromIp, fromId ->
        // Проверяем тип WEBRTC_SIGNAL (используется как транспорт в IdentityRepository)
        if (type == "WEBRTC_SIGNAL") {
            try {
                val json = JSONObject(data)
                val signalType = json.optString("sub_type")
                val signalData = json.optString("payload")
                
                when (signalType) {
                    "FILE_CHUNK_REQ" -> handleChunkRequest(signalData, fromIp, fromId)
                    "FILE_CHUNK_DATA" -> handleChunkData(signalData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Signal parsing error", e)
            }
        }
    }

    init { 
        identityRepo.addListener(listener) 
    }

    fun release() {
        identityRepo.removeListener(listener)
        scope.cancel()
    }

    /**
     * Регистрирует файл для раздачи другим участникам.
     */
    fun addFileToSeeding(file: File): String {
        val fileId = UUID.randomUUID().toString()
        seedingFiles[fileId] = file
        Log.i(TAG, "File added to seeding: ${file.name}, ID: $fileId")
        return fileId
    }

    private fun handleChunkRequest(jsonData: String, targetIp: String, targetHash: String) {
        scope.launch {
            try {
                val json = JSONObject(jsonData)
                val fileId = json.getString("file_id")
                val chunkIndex = json.getInt("chunk_index")
                val file = seedingFiles[fileId] ?: return@launch

                RandomAccessFile(file, "r").use { raf ->
                    val offset = chunkIndex.toLong() * CHUNK_SIZE
                    if (offset >= file.length()) return@use
                    
                    raf.seek(offset)
                    val buffer = ByteArray(CHUNK_SIZE)
                    val read = raf.read(buffer)
                    if (read <= 0) return@use

                    val dataToSend = if (read == buffer.size) buffer else buffer.copyOf(read)
                    
                    val chunkDataJson = JSONObject().apply {
                        put("file_id", fileId)
                        put("chunk_index", chunkIndex)
                        put("data", Base64.encodeToString(dataToSend, Base64.NO_WRAP))
                    }

                    val envelope = JSONObject().apply {
                        put("sub_type", "FILE_CHUNK_DATA")
                        put("payload", chunkDataJson.toString())
                    }

                    // ИСПРАВЛЕНО: 3 параметра (IP, Тип, Данные)
                    identityRepo.sendSignaling(targetIp, "WEBRTC_SIGNAL", envelope.toString())
                }
            } catch (e: Exception) { 
                Log.e(TAG, "Error handling chunk request", e) 
            }
        }
    }

    /**
     * Скачивает файл у удаленного узла.
     */
    suspend fun downloadFileP2P(targetIp: String, targetHash: String, fileId: String, fileName: String, totalChunks: Int): File? = withContext(Dispatchers.IO) {
        val outputFile = File(context.getExternalFilesDir(null), fileName)
        val session = DownloadSession(fileId, outputFile, totalChunks)
        activeDownloads[fileId] = session

        try {
            // В продакшене реализуем простую стратегию переповторов
            var attempts = 0
            while (session.getRemainingChunks() > 0 && attempts < 3) {
                for (i in 0 until totalChunks) {
                    if (session.isChunkReceived(i)) continue

                    val reqData = JSONObject().apply { 
                        put("file_id", fileId)
                        put("chunk_index", i) 
                    }

                    val envelope = JSONObject().apply {
                        put("sub_type", "FILE_CHUNK_REQ")
                        put("payload", reqData.toString())
                    }

                    // ИСПРАВЛЕНО: 3 параметра
                    identityRepo.sendSignaling(targetIp, "WEBRTC_SIGNAL", envelope.toString())
                    delay(20) // Пауза во избежание перегрузки UDP буфера
                }
                
                // Ждем чанки текущего прохода
                session.waitForCompletion(10, TimeUnit.SECONDS)
                attempts++
            }

            if (session.getRemainingChunks() == 0) {
                Log.i(TAG, "Download complete: $fileName")
                outputFile
            } else {
                Log.e(TAG, "Download failed: missing ${session.getRemainingChunks()} chunks")
                null
            }
        } finally {
            activeDownloads.remove(fileId)
        }
    }

    private fun handleChunkData(jsonData: String) {
        scope.launch {
            try {
                val json = JSONObject(jsonData)
                val fileId = json.getString("file_id")
                val session = activeDownloads[fileId] ?: return@launch
                val chunkIndex = json.getInt("chunk_index")
                val bytes = Base64.decode(json.getString("data"), Base64.NO_WRAP)

                RandomAccessFile(session.file, "rw").use { raf ->
                    raf.seek(chunkIndex.toLong() * CHUNK_SIZE)
                    raf.write(bytes)
                }
                session.markChunkReceived(chunkIndex)
            } catch (e: Exception) { 
                Log.e(TAG, "Error handling chunk data", e)
            }
        }
    }

    /**
     * Сессия загрузки для отслеживания прогресса и целостности.
     */
    private class DownloadSession(val fileId: String, val file: File, val totalChunks: Int) {
        private val received = java.util.BitSet(totalChunks)
        private val latch = java.util.concurrent.CountDownLatch(totalChunks)
        
        fun markChunkReceived(index: Int) {
            synchronized(received) { 
                if (index < totalChunks && !received.get(index)) { 
                    received.set(index)
                    latch.countDown() 
                } 
            }
        }

        fun isChunkReceived(index: Int): Boolean = synchronized(received) { received.get(index) }

        fun getRemainingChunks(): Int = totalChunks - received.cardinality()
        
        fun waitForCompletion(timeout: Long, unit: TimeUnit) {
            latch.await(timeout, unit)
        }
    }
}
