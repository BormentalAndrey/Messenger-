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

    @Query(
        "SELECT * FROM knowledge_table " +
        "WHERE query_text LIKE '%' || :search || '%' " +
        "ORDER BY timestamp DESC LIMIT 3"
    )
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

    /**
     * Приоритет моделей:
     * 1) Быстрые и дешёвые (Flash)
     * 2) Latest alias (авто-обновление)
     * 3) Pro (умнее, но дороже)
     * 4) Gemma (стабильный fallback)
     * 5) Preview (последний шанс)
     */
    private val modelPriorityList = listOf(

        // --- FAST / CHEAP ---
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-2.0-flash-001",
        "gemini-2.0-flash-lite",
        "gemini-2.0-flash-lite-001",
        "gemini-flash-latest",

        // --- SMART ---
        "gemini-2.5-pro",
        "gemini-pro-latest",

        // --- GEMMA (fallback, стабильные) ---
        "gemma-3-27b-it",
        "gemma-3-12b-it",
        "gemma-3-4b-it",
        "gemma-3-1b-it",

        // --- EXPERIMENTAL (последний шанс) ---
        "gemini-3-flash-preview",
        "gemini-exp-1206"
    )

    private fun createModel(modelName: String): GenerativeModel {
        return GenerativeModel(
            modelName = modelName,
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    suspend fun getResponse(context: Context, userPrompt: String): String =
        withContext(Dispatchers.IO) {

            val db = AppDatabase.getDatabase(context).knowledgeDao()
            val hasInternet = NetworkUtils.isNetworkAvailable(context)

            // --- RAG: поиск похожих знаний ---
            val keywords = userPrompt
                .split(" ")
                .filter { it.length > 4 }
                .joinToString(" ")

            val similarKnowledge =
                if (keywords.isNotEmpty()) db.findSimilar(keywords) else emptyList()

            val learnedContext = similarKnowledge.joinToString("\n") {
                "Human: ${it.query}\nAI: ${it.answer}"
            }

            // --- Cloud (Gemini / Gemma) ---
            if (hasInternet && BuildConfig.GEMINI_API_KEY.isNotBlank()) {
                var backoff = 400L

                for (modelName in modelPriorityList) {
                    try {
                        val webInfo = try {
                            WebSearcher.search(userPrompt)
                        } catch (_: Exception) {
                            ""
                        }

                        val prompt = """
                            Контекст из памяти:
                            $learnedContext

                            Информация из интернета:
                            $webInfo

                            Вопрос пользователя:
                            $userPrompt

                            Ответь кратко, по делу, на русском языке.
                        """.trimIndent()

                        val response = createModel(modelName)
                            .generateContent(prompt)
                            .text
                            ?.trim()
                            ?: ""

                        if (response.isNotEmpty()) {
                            db.insert(
                                KnowledgeEntity(
                                    query = userPrompt,
                                    answer = response
                                )
                            )
                            return@withContext response
                        }
                    } catch (e: Exception) {
                        val msg = e.message ?: ""

                        // квоты / временные ошибки → пробуем следующую модель
                        if (
                            msg.contains("429") ||
                            msg.contains("quota", true) ||
                            msg.contains("500") ||
                            msg.contains("503") ||
                            msg.contains("404")
                        ) {
                            delay(backoff)
                            backoff = (backoff * 1.5).toLong().coerceAtMost(3_000L)
                            continue
                        } else {
                            break
                        }
                    }
                }
            }

            // --- Local Llama (Offline) ---
            try {
                if (LlamaBridge.isLibAvailable() && LlamaBridge.isReady()) {
                    val localPrompt = """
                        <|system|>
                        Ты полезный ассистент. Используй этот контекст:
                        $learnedContext
                        <|end|>

                        <|user|>
                        $userPrompt
                        <|end|>

                        <|assistant|>
                    """.trimIndent()

                    return@withContext LlamaBridge.prompt(localPrompt)
                } else {
                    if (hasInternet) {
                        return@withContext "Сервисы ИИ временно недоступны."
                    }
                    if (!ModelDownloadManager.isInstalled(context)) {
                        return@withContext "Для оффлайн-работы скачайте локальную модель."
                    }
                    return@withContext "Локальная модель загружается…"
                }
            } catch (e: Exception) {
                return@withContext "Ошибка системы: ${e.message}"
            }
        }
}

// --- Utilities ---
object NetworkUtils {
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

object WebSearcher {
    private val client = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        try {
            val url =
                "https://api.duckduckgo.com/?q=$query&format=json&no_redirect=1&skip_disambig=1"
                    .toHttpUrlOrNull()
                    ?: return@withContext ""

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ""
                val json = JSONObject(response.body?.string().orEmpty())
                json.optString("AbstractText")
            }
        } catch (_: Exception) {
            ""
        }
    }
}
