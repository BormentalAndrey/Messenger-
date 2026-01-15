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

    // Основной URL для WebView и тестовой инициализации
    private const val BASE_URL = "http://kakdela.infinityfree.me/api.php?action=announce"
    
    // Основной API endpoint (можно заменить на свой продакшн URL)
    private const val API_ENDPOINT = "http://kakdela.infinityfree.me/api.php?action=announce"

    private const val PAGE_LOAD_TIMEOUT_MS = 30_000L
    private const val REQUEST_TIMEOUT_MS   = 20_000L
    private const val MAX_RETRIES          = 3

    private var webView: WebView? = null
    private val isReady = AtomicBoolean(false)
    private val mutex = Mutex()
    private val gson = Gson()

    /**
     * Инициализация WebView для выполнения JS fetch-запросов.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun init(context: Context) {
        if (webView != null) return

        Handler(Looper.getMainLooper()).post {
            try {
                webView = WebView(context.applicationContext).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val cookies = CookieManager.getInstance().getCookie(url)
                            if (url != null && url.contains("kakdela")) {
                                if (cookies != null && cookies.contains("__test")) {
                                    isReady.set(true)
                                    Log.i(TAG, "Protection passed. API Ready.")
                                }
                            }
                        }
                    }
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.setAcceptThirdPartyCookies(webView!!, true)
                }
                webView?.loadUrl(BASE_URL)

            } catch (e: Exception) {
                Log.e(TAG, "WebView init error", e)
            }
        }
    }

    /**
     * Анонсирует локального пользователя на сервере.
     */
    suspend fun announceSelf(payload: UserPayload): ServerResponse {
        val bodyJson = gson.toJson(payload)
        val url = "$API_ENDPOINT?action=add_user"
        return executeRequest(url, "POST", bodyJson)
    }

    /**
     * Получает список всех узлов с сервера.
     */
    suspend fun getAllNodes(): ServerResponse {
        val url = "$API_ENDPOINT?action=list_users"
        return executeRequest(url, "GET", null)
    }

    /**
     * Универсальный метод выполнения запроса через JS fetch внутри WebView.
     */
    private suspend fun executeRequest(url: String, method: String, bodyJson: String?): ServerResponse = mutex.withLock {
        repeat(MAX_RETRIES) { attempt ->
            try {
                waitForReady()
                val rawResponse = withTimeout(REQUEST_TIMEOUT_MS) {
                    performJsFetch(url, method, bodyJson)
                }

                if (rawResponse.trim().startsWith("<")) {
                    throw Exception("HTML received instead of JSON")
                }

                return@withLock gson.fromJson(rawResponse, ServerResponse::class.java)

            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                isReady.set(false)
                withContext(Dispatchers.Main) { webView?.loadUrl(BASE_URL) }
                delay(2000)
            }
        }
        return@withLock ServerResponse(success = false, error = "Connection failed")
    }

    /**
     * Ждём готовности WebView к выполнению запросов.
     */
    private suspend fun waitForReady() {
        withTimeout(PAGE_LOAD_TIMEOUT_MS) {
            while (!isReady.get()) delay(500)
        }
    }

    /**
     * Выполняет JS fetch запрос внутри WebView и возвращает результат.
     */
    private suspend fun performJsFetch(url: String, method: String, bodyJson: String?): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val bridgeName = "JSBridge_${System.currentTimeMillis()}"

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
                    Handler(Looper.getMainLooper()).post {
                        webView?.removeJavascriptInterface(bridgeName)
                    }
                }
            }

            webView?.addJavascriptInterface(bridge, bridgeName)

            // Передаем JSON как строку
            val jsData = bodyJson ?: "null"

            val js = """
                (function() {
                    try {
                        const rawData = $jsData;
                        fetch('$url', {
                            method: '$method',
                            headers: { 'Content-Type': 'application/json' },
                            credentials: 'include',
                            body: rawData ? JSON.stringify(rawData) : null
                        })
                        .then(r => r.text())
                        .then(t => { window.$bridgeName.onSuccess(t); })
                        .catch(e => { window.$bridgeName.onError(e.toString()); });
                    } catch(e) { window.$bridgeName.onError(e.toString()); }
                })();
            """.trimIndent()

            webView?.evaluateJavascript(js, null)
        }
    }

    /**
     * Очистка ресурсов WebView.
     */
    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            webView?.destroy()
            webView = null
        }
    }
}
