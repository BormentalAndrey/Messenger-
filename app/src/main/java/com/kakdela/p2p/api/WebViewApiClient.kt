package com.kakdela.p2p.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Класс для синхронизации кук между WebView (который проходит защиту)
 * и OkHttp (который делает запросы).
 */
class WebkitCookieJar : CookieJar {
    private val cookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val cookieHeader = cookieManager.getCookie(urlString) ?: return emptyList()

        val cookies = mutableListOf<Cookie>()
        val splitCookies = cookieHeader.split(";")
        for (cookieStr in splitCookies) {
            Cookie.parse(url, cookieStr.trim())?.let { cookies.add(it) }
        }
        return cookies
    }
}

@SuppressLint("StaticFieldLeak")
object WebViewApiClient {

    private const val TAG = "WebViewApiClient"
    private const val BASE_URL = "http://kakdela.infinityfree.me/api.php"

    // Таймауты
    private const val PAGE_LOAD_TIMEOUT_MS = 30_000L
    private const val REQUEST_TIMEOUT_MS   = 20_000L
    private const val MAX_RETRIES          = 3

    private var webView: WebView? = null
    private val isReady = AtomicBoolean(false)
    private val mutex = Mutex()
    private val gson = Gson()

    // OkHttp клиент
    private var okHttpClient: OkHttpClient? = null
    private var realUserAgent: String = "" // UA который использует WebView

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    @SuppressLint("SetJavaScriptEnabled")
    fun init(context: Context) {
        if (webView != null) return

        Handler(Looper.getMainLooper()).post {
            try {
                // 1. Настраиваем CookieManager глобально
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)

                // 2. Создаем WebView
                webView = WebView(context.applicationContext).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        // Позволяем системе самой выбрать корректный UserAgent для этого устройства
                    }
                    
                    // 3. ЗАХВАТЫВАЕМ User-Agent. Это критически важно для единой сессии.
                    realUserAgent = settings.userAgentString
                    Log.d(TAG, "Captured User-Agent: $realUserAgent")

                    // 4. Инициализируем OkHttp с тем же UA и общими куками
                    initOkHttp()

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d(TAG, "Page loaded: $url")
                            checkProtection(url)
                        }
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(webView!!, true)
                }
                
                // Загружаем скрипт, чтобы "прогреть" куки защиты
                webView?.loadUrl(BASE_URL)

            } catch (e: Exception) {
                Log.e(TAG, "WebView init error", e)
            }
        }
    }

    private fun initOkHttp() {
        okHttpClient = OkHttpClient.Builder()
            .cookieJar(WebkitCookieJar()) // Используем общие куки с WebView
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                // Подменяем User-Agent на тот, что у WebView, чтобы сервер не видел подвоха
                val request = original.newBuilder()
                    .header("User-Agent", realUserAgent)
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private fun checkProtection(url: String?) {
        // Проверяем наличие куки __test, которую ставит InfinityFree
        if (url != null && url.contains("kakdela")) {
            val cookies = CookieManager.getInstance().getCookie(url)
            if (cookies != null && cookies.contains("__test")) {
                if (!isReady.get()) {
                    isReady.set(true)
                    Log.i(TAG, "Protection passed. Session Synchronized. Ready for API.")
                }
            }
        }
    }

    /**
     * Отправляет данные о себе (POST)
     */
    suspend fun announceSelf(payload: UserPayload): ServerResponse {
        val bodyJson = gson.toJson(payload)
        // action передаем в URL, данные в теле. Это стандартный и надежный способ.
        val url = "$BASE_URL?action=announce" 
        return executeRequest(url, "POST", bodyJson)
    }

    /**
     * Получает список узлов (GET)
     */
    suspend fun getAllNodes(): ServerResponse {
        val url = "$BASE_URL?action=get_nodes"
        return executeRequest(url, "GET", null)
    }

    /**
     * Универсальный метод выполнения запроса через OkHttp с поддержкой обхода защиты
     */
    private suspend fun executeRequest(url: String, method: String, bodyJson: String?): ServerResponse = mutex.withLock {
        // Пробуем выполнить запрос несколько раз
        repeat(MAX_RETRIES) { attempt ->
            try {
                // 1. Ждем пока WebView добудет куки
                waitForReady()

                // 2. Если OkHttp еще не создан (редкий случай), ждем
                if (okHttpClient == null) throw Exception("OkHttp client not initialized yet")

                return@withLock withContext(Dispatchers.IO) {
                    val requestBuilder = Request.Builder().url(url)
                    
                    if (method == "POST" && bodyJson != null) {
                        requestBuilder.post(bodyJson.toRequestBody(JSON_TYPE))
                    } else {
                        requestBuilder.get()
                    }

                    // Выполняем запрос
                    val response = okHttpClient!!.newCall(requestBuilder.build()).execute()
                    val responseBodyStr = response.body?.string()

                    // Обработка ошибок HTTP
                    if (!response.isSuccessful) {
                        // 403/503 обычно значат, что защита сработала или кука протухла
                        if (response.code == 403 || response.code == 503 || response.code == 400) {
                             throw Exception("HTTP ${response.code} - Session invalid")
                        }
                        return@withContext ServerResponse(false, "HTTP error ${response.code}")
                    }
                    
                    // Проверка: не вернул ли сервер HTML-заглушку вместо JSON
                    if (responseBodyStr == null || responseBodyStr.trim().startsWith("<")) {
                        throw Exception("HTML received instead of JSON. Protection active.")
                    }

                    // Все отлично, парсим ответ
                    return@withContext gson.fromJson(responseBodyStr, ServerResponse::class.java)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                
                // Если произошла ошибка сессии, сбрасываем флаг и перезагружаем WebView
                isReady.set(false)
                withContext(Dispatchers.Main) { 
                    webView?.loadUrl(BASE_URL) 
                }
                // Ждем перед следующей попыткой
                delay(3000)
            }
        }
        return@withLock ServerResponse(success = false, error = "Connection failed after $MAX_RETRIES retries")
    }

    private suspend fun waitForReady() {
        // Если уже готовы, выходим сразу
        if (isReady.get()) return

        // Если нет, принудительно грузим страницу
        withContext(Dispatchers.Main) {
            webView?.loadUrl(BASE_URL)
        }

        // Ждем в цикле
        try {
            withTimeout(PAGE_LOAD_TIMEOUT_MS) {
                while (!isReady.get()) delay(500)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Timeout waiting for cookie protection")
        }
    }

    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            webView?.destroy()
            webView = null
        }
    }
}
