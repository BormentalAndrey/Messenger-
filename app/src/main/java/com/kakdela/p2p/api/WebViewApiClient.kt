package com.kakdela.p2p.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("StaticFieldLeak")
object WebViewApiClient {

    private const val TAG = "WebViewApiClient"
    
    // ВАЖНО: Убедитесь, что адрес точный (http vs https)
    private const val BASE_URL = "http://kakdela.infinityfree.me/"
    private const val API_URL  = "http://kakdela.infinityfree.me/api.php"
    
    private const val PAGE_LOAD_TIMEOUT_MS = 30_000L
    private const val REQUEST_TIMEOUT_MS   = 20_000L
    private const val RETRY_DELAY_MS       = 2_000L
    private const val MAX_RETRIES          = 3

    private var webView: WebView? = null
    private val isReady = AtomicBoolean(false)
    private val mutex = Mutex() // Очередь запросов (WebView однопоточен)
    private val gson = Gson()

    // =========================
    // INITIALIZATION
    // =========================
    fun init(context: Context) {
        if (webView != null) return

        Handler(Looper.getMainLooper()).post {
            try {
                // Создаем WebView
                webView = WebView(context.applicationContext).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        // Прикидываемся реальным Chrome на Android
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            // Проверяем, прошли ли мы защиту (есть ли кука __test)
                            val cookies = CookieManager.getInstance().getCookie(url)
                            if (url != null && url.contains("infinityfree")) {
                                if (cookies != null && cookies.contains("__test")) {
                                    isReady.set(true)
                                    Log.i(TAG, "SUCCESS: InfinityFree protection passed. API Ready.")
                                } else {
                                    Log.w(TAG, "Waiting for protection bypass... Cookies: $cookies")
                                }
                            }
                        }
                    }
                }

                // Включаем куки (критично для InfinityFree)
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.setAcceptThirdPartyCookies(webView!!, true)
                }

                // Загружаем главную страницу для получения кук защиты
                webView?.loadUrl(BASE_URL)

            } catch (e: Exception) {
                Log.e(TAG, "WebView init error", e)
            }
        }
    }

    // =========================
    // PUBLIC API METHODS
    // =========================

    /**
     * Отправляет данные о себе на сервер (POST)
     */
    suspend fun announceSelf(payload: UserPayload): ServerResponse {
        // Gson создает строку JSON, например: {"hash":"...", ...}
        // Мы передадим её в JS как объект, а не строку
        val bodyJson = gson.toJson(payload)
        return executeRequest("POST", bodyJson)
    }

    /**
     * Получает список всех узлов (GET)
     */
    suspend fun getAllNodes(): ServerResponse {
        return executeRequest("GET", null)
    }

    // =========================
    // CORE LOGIC
    // =========================

    private suspend fun executeRequest(method: String, bodyJson: String?): ServerResponse = mutex.withLock {
        repeat(MAX_RETRIES) { attempt ->
            try {
                waitForReady()
                
                val rawResponse = withTimeout(REQUEST_TIMEOUT_MS) {
                    performJsFetch(method, bodyJson)
                }

                // Проверяем, не вернул ли сервер HTML (если защита снова включилась)
                if (rawResponse.trim().startsWith("<")) {
                    throw Exception("Server returned HTML instead of JSON. Protection active.")
                }

                return@withLock gson.fromJson(rawResponse, ServerResponse::class.java)

            } catch (e: Exception) {
                Log.e(TAG, "Request failed (Attempt ${attempt + 1}/$MAX_RETRIES): ${e.message}")
                
                // Если ошибка, пробуем перезагрузить страницу для обновления кук
                isReady.set(false)
                withContext(Dispatchers.Main) {
                    webView?.loadUrl(BASE_URL)
                }
                delay(RETRY_DELAY_MS)
            }
        }
        return@withLock ServerResponse(success = false, error = "Connection failed after $MAX_RETRIES retries")
    }

    private suspend fun waitForReady() {
        // Ждем макс 30 сек, пока WebView прогрузит защиту
        withTimeout(PAGE_LOAD_TIMEOUT_MS) {
            while (!isReady.get()) {
                delay(500)
            }
        }
    }

    // =========================
    // JS INJECTION (FETCH)
    // =========================
    
    private suspend fun performJsFetch(method: String, bodyJson: String?): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->

            // Уникальное имя для моста, чтобы запросы не пересекались
            val bridgeName = "AndroidBridge_${System.currentTimeMillis()}"

            // Интерфейс для получения ответа из JS
            val bridge = object {
                @JavascriptInterface
                fun onSuccess(result: String) {
                    cleanup()
                    if (cont.isActive) cont.resume(result)
                }

                @JavascriptInterface
                fun onError(error: String) {
                    cleanup()
                    if (cont.isActive) cont.resumeWithException(Exception(error))
                }
                
                fun cleanup() {
                    // Удаляем интерфейс в главном потоке
                    Handler(Looper.getMainLooper()).post {
                        webView?.removeJavascriptInterface(bridgeName)
                    }
                }
            }

            webView?.addJavascriptInterface(bridge, bridgeName)

            // Подготовка данных. 
            // ВАЖНО: Если bodyJson не null, мы вставляем его прямо в код как JS-объект.
            // Gson выдает валидную JSON строку, которая является валидным JS-объектом.
            val payloadScript = if (bodyJson != null) "const payload = $bodyJson;" else "const payload = null;"

            val js = """
                (function() {
                    try {
                        $payloadScript // Здесь создается переменная payload (объект, не строка!)

                        fetch('$API_URL', {
                            method: '$method',
                            headers: {
                                'Content-Type': 'application/json',
                                'Accept': 'application/json'
                            },
                            credentials: 'include', // ОБЯЗАТЕЛЬНО: отправляет куки __test
                            body: payload ? JSON.stringify(payload) : null
                        })
                        .then(response => {
                            if (!response.ok) throw new Error('HTTP ' + response.status);
                            return response.text();
                        })
                        .then(text => {
                            // Проверяем, JSON ли это
                            try {
                                JSON.parse(text);
                                $bridgeName.onSuccess(text);
                            } catch (e) {
                                // Если пришел HTML (ошибка защиты), передаем как ошибку
                                if (text.includes('<html')) {
                                    $bridgeName.onError('BLOCKED_BY_WAF'); 
                                } else {
                                    $bridgeName.onSuccess(text); // Отдаем как есть (возможно debug)
                                }
                            }
                        })
                        .catch(err => {
                            $bridgeName.onError('FETCH_ERROR: ' + err.toString());
                        });

                    } catch (e) {
                        $bridgeName.onError('JS_SCRIPT_ERROR: ' + e.toString());
                    }
                })();
            """.trimIndent()

            webView?.evaluateJavascript(js, null)
        }
    }

    // =========================
    // CLEANUP
    // =========================
    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            try {
                webView?.stopLoading()
                webView?.clearHistory()
                webView?.destroy()
                webView = null
            } catch (e: Exception) {
                Log.e(TAG, "Destroy error", e)
            }
        }
    }
}
