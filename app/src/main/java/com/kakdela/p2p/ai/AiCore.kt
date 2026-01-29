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

// --- Database (Память ИИ) ---

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

// --- Hybrid AI Engine ---

object HybridAiEngine {
    private val modelPriorityList = listOf(
        "gemini-2.0-flash", // Быстрый и новый
        "gemini-1.5-flash",
        "gemini-1.5-pro"
    )

    private fun createModel(modelName: String) = GenerativeModel(modelName, BuildConfig.GEMINI_API_KEY)

    suspend fun getResponse(context: Context, userPrompt: String): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context).knowledgeDao()
        val hasInternet = NetworkUtils.isNetworkAvailable(context)

        // 1. Поиск знаний в локальной базе (Long Term Memory)
        val keywords = userPrompt.split(" ").filter { it.length > 4 }.joinToString(" ")
        val similarKnowledge = if (keywords.isNotEmpty()) db.findSimilar(keywords) else emptyList()
        val learnedContext = similarKnowledge.joinToString("\n") { "Human: ${it.query}\nAI: ${it.answer}" }

        // 2. Попытка использовать Gemini (Облако)
        if (hasInternet && BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            var retryDelay = 1000L
            for (modelName in modelPriorityList) {
                try {
                    val webInfo = try { WebSearcher.search(userPrompt) } catch (e: Exception) { "" }
                    
                    val systemPrompt = """
                        Контекст из прошлого опыта:
                        $learnedContext
                        Инфо из веба: $webInfo
                        
                        Вопрос пользователя: $userPrompt
                        Ответь кратко и полезно на русском языке.
                    """.trimIndent()

                    val response = createModel(modelName).generateContent(systemPrompt).text ?: ""
                    
                    if (response.isNotBlank()) {
                        // Обучение: сохраняем успешный ответ в базу
                        db.insert(KnowledgeEntity(query = userPrompt, answer = response))
                        return@withContext response
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("429") || msg.contains("quota")) {
                        delay(retryDelay)
                        retryDelay *= 2
                    } else {
                        // Если ошибка не связана с квотой (например, 500), пробуем следующую модель или переходим к локальной
                        continue 
                    }
                }
            }
        }

        // 3. Fallback: Локальная Llama (Оффлайн)
        try {
            // Проверяем, инициализирована ли модель в памяти
            if (LlamaBridge.isLibAvailable() && LlamaBridge.isReady()) {
                
                // Формат промпта специфичен для Phi-3
                val localPrompt = """
                    <|system|>
                    Ты полезный ассистент. Отвечай на русском языке.
                    Используй этот контекст:
                    $learnedContext
                    <|end|>
                    <|user|>
                    $userPrompt
                    <|end|>
                    <|assistant|>
                """.trimIndent()
                
                return@withContext LlamaBridge.prompt(localPrompt)
            } else {
                // Если модель не готова, проверяем файл
                if (!ModelDownloadManager.isInstalled(context)) {
                    return@withContext "Интернет недоступен, а локальная модель не скачана. Нажмите кнопку загрузки."
                } else {
                    return@withContext "Инициализация локального мозга... Попробуйте через пару секунд."
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Ошибка: ${e.message}. Проверьте соединение или перезапустите приложение."
        }
    }
}

// --- Utilities ---

object NetworkUtils {
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

object WebSearcher {
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        try {
            // DuckDuckGo Instant Answer API (простой пример)
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
