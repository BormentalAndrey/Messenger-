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

// --- Database ---

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
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-3-flash-preview",
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemma-3-27b-it",
        "gemma-3-12b-it",
        "gemma-3-4b-it"
    )

    private fun createModel(modelName: String) = GenerativeModel(modelName, BuildConfig.GEMINI_API_KEY)

    suspend fun getResponse(context: Context, userPrompt: String): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context).knowledgeDao()
        val hasInternet = NetworkUtils.isNetworkAvailable(context)

        val keywords = userPrompt.split(" ").filter { it.length > 4 }.joinToString(" ")
        val similarKnowledge = if (keywords.isNotEmpty()) db.findSimilar(keywords) else emptyList()
        val learnedContext = similarKnowledge.joinToString("\n") { "Q: ${it.query}\nA: ${it.answer}" }

        if (hasInternet && BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            var retryDelay = 500L
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
                        db.insert(KnowledgeEntity(query = userPrompt, answer = response))
                        return@withContext response
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("429") || msg.contains("quota") || msg.contains("500") || msg.contains("503") || msg.contains("404")) {
                        if (msg.contains("429")) {
                            delay(retryDelay)
                            retryDelay *= 2
                        }
                        continue
                    } else break
                }
            }
        }

        // Локальная Llama
        try {
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
        } catch (e: Exception) { e.printStackTrace() }

        return@withContext "Нет сети и локальная модель не готова. Подключитесь к интернету или скачайте модель."
    }
}

// --- Utilities ---

object NetworkUtils {
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}

object AiMemoryStore {
    private val memory = Collections.synchronizedList(mutableListOf<String>())
    fun remember(text: String) {
        if (text.length > 20) memory.add(text.take(200))
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
