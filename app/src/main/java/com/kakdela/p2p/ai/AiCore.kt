package com.kakdela.p2p.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.*
import com.google.ai.client.generativeai.GenerativeModel
import com.kakdela.p2p.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit

// --- 1. Database for Learning (Persistent RAG) ---

@Entity(tableName = "knowledge_table")
data class KnowledgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "query_text") val query: String,
    @ColumnInfo(name = "ai_answer") val answer: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(knowledge: KnowledgeEntity)

    @Query("SELECT * FROM knowledge_table WHERE query_text LIKE '%' || :search || '%' ORDER BY timestamp DESC LIMIT 3")
    suspend fun findSimilar(search: String): List<KnowledgeEntity>

    @Query("SELECT COUNT(*) FROM knowledge_table")
    suspend fun getCount(): Int
}

@Database(entities = [KnowledgeEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_teacher_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- 2. Hybrid AI Engine (The Brain) ---

object HybridAiEngine {
    // Список текстовых моделей для перебора (от новых к стабильным + Gemma)
    private val modelPriorityList = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-3-flash-preview",
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemma-3-27b-it",
        "gemma-3-12b-it",
        "gemma-3-4b-it"
    )

    private fun createModel(modelName: String): GenerativeModel {
        return GenerativeModel(
            modelName = modelName,
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    suspend fun getResponse(context: Context, userPrompt: String): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context).knowledgeDao()
        val hasInternet = NetworkUtils.isNetworkAvailable(context)
        
        // 1. Формируем контекст (RAG)
        val keywords = userPrompt.split(" ").filter { it.length > 4 }.joinToString(" ")
        val similarKnowledge = if (keywords.isNotEmpty()) db.findSimilar(keywords) else emptyList()
        val learnedContext = similarKnowledge.joinToString("\n") { 
            "Q: ${it.query}\nA: ${it.answer}" 
        }

        // 2. Сценарий: ЕСТЬ Интернет (Перебор облачных моделей)
        if (hasInternet && BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            var retryDelay = 500L // Начальная задержка при лимитах

            for (modelName in modelPriorityList) {
                try {
                    val webInfo = try { WebSearcher.search(userPrompt) } catch (e: Exception) { "" }
                    
                    val prompt = """
                        Контекст из прошлого опыта:
                        $learnedContext
                        Инфо из веба: $webInfo
                        
                        Вопрос пользователя: $userPrompt
                        Ответь кратко и полезно на русском языке.
                    """.trimIndent()

                    val response = createModel(modelName).generateContent(prompt).text ?: ""
                    
                    if (response.isNotBlank()) {
                        // Сохраняем знания для будущего использования
                        db.insert(KnowledgeEntity(query = userPrompt, answer = response))
                        return@withContext response
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message ?: ""
                    // Если ошибка квоты (429) или сервера (500/503), пробуем следующую модель с задержкой
                    if (errorMsg.contains("429") || errorMsg.contains("quota") || 
                        errorMsg.contains("500") || errorMsg.contains("503") || errorMsg.contains("404")) {
                        
                        if (errorMsg.contains("429")) {
                            delay(retryDelay)
                            retryDelay *= 2 // Увеличиваем паузу
                        }
                        continue 
                    } else {
                        // Если ошибка критическая (например, Auth), выходим из цикла к Llama
                        e.printStackTrace()
                        break
                    }
                }
            }
        }

        // 3. Сценарий: НЕТ Интернета или все облачные модели упали (Локальная Llama)
        // Проверяем наличие LlamaBridge (заглушка должна быть реализована в твоем проекте)
        try {
            // Предполагаем, что LlamaBridge и ModelDownloadManager — это синглтоны или доступные объекты
            // Внимание: проверь импорты для этих объектов!
            if (LlamaBridge.isReady() && ModelDownloadManager.isInstalled(context)) {
                val localPrompt = """
                    <|system|>
                    Ты полезный ассистент. Отвечай на русском языке. Используй эти знания:
                    $learnedContext
                    <|user|>
                    $userPrompt
                    <|assistant|>
                """.trimIndent()
                
                return@withContext LlamaBridge.prompt(localPrompt)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext "Нет сети и локальная модель не готова. Пожалуйста, подключитесь к интернету или скачайте модель в настройках."
    }
}

// --- 3. Utilities ---

object NetworkUtils {
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}

object AiMemoryStore {
    private val memory = Collections.synchronizedList(mutableListOf<String>())
    fun remember(text: String) {
        if (text.length > 20) memory.add(text.take(200)) // Храним только начало для экономии
        if (memory.size > 20) memory.removeAt(0)
    }
    fun context(): String = memory.joinToString("\n")
    fun clear() = memory.clear()
}

object WebSearcher {
    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.duckduckgo.com/?q=$query&format=json&no_redirect=1&skip_disambig=1".toHttpUrlOrNull() ?: return@withContext ""
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    json.optString("AbstractText")
                } else ""
            }
        } catch (e: Exception) { "" }
    }
}
