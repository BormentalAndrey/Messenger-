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
    private const val BASE_URL = "http://kakdela.infinityfree.me/"
    private const val API_ENDPOINT = "http://kakdela.infinityfree.me/api.php"
    
    private const val PAGE_LOAD_TIMEOUT_MS = 30_000L
    private const val REQUEST_TIMEOUT_MS   = 20_000L
    private const val MAX_RETRIES          = 3

    private var webView: WebView? = null
    private val isReady = AtomicBoolean(false)
    private val mutex = Mutex()
    private val gson = Gson()

    fun init(context: Context) {
        if (webView != null) return

        Handler(Looper.getMainLooper()).post {
            try {
                webView = WebView(context.applicationContext).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        // Использование актуального User-Agent, чтобы сервер не считал нас ботом
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val cookies = CookieManager.getInstance().getCookie(url)
                            Log.d(TAG, "Page loaded: $url, Cookies: $cookies")
                            
                            // Проверка на наличие защитной куки InfinityFree
                            if (url != null && url.contains("kakdela")) {
                                if (cookies != null && cookies.contains("__test")) {
                                    isReady.set(true)
                                    Log.i(TAG, "Security bypass successful. API Ready.")
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
                
                Log.d(TAG, "Loading BASE_URL to trigger protection...")
                webView?.loadUrl(BASE_URL)

            } catch (e: Exception) {
                Log.e(TAG, "WebView init error", e)
            }
        }
    }

    // --- PUBLIC METHODS ---

    suspend fun announceSelf(payload: UserPayload): ServerResponse {
        val bodyJson = gson.toJson(payload)
        val url = "$API_ENDPOINT?action=add_user"
        return executeRequest(url, "POST", bodyJson)
    }

    suspend fun getAllNodes(): ServerResponse {
        val url = "$API_ENDPOINT?action=list_users"
        return executeRequest(url, "GET", null)
    }

    // --- CORE LOGIC ---

    private suspend fun executeRequest(url: String, method: String, bodyJson: String?): ServerResponse = mutex.withLock {
        repeat(MAX_RETRIES) { attempt ->
            try {
                waitForReady()
                
                val rawResponse = withTimeout(REQUEST_TIMEOUT_MS) {
                    performJsFetch(url, method, bodyJson)
                }

                if (rawResponse.trim().startsWith("<")) {
                    throw Exception("Received HTML instead of JSON (likely protection redirect)")
                }

                return@withLock gson.fromJson(rawResponse, ServerResponse::class.java)

            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                
                // Если ошибка, пробуем переинициализировать WebView (обновить куки)
                isReady.set(false)
                withContext(Dispatchers.Main) { 
                    webView?.loadUrl(BASE_URL) 
                }
                delay(3000) // Пауза перед следующей попыткой
            }
        }
        return@withLock ServerResponse(success = false, error = "Failed after $MAX_RETRIES attempts")
    }

    private suspend fun waitForReady() {
        withTimeout(PAGE_LOAD_TIMEOUT_MS) {
            while (!isReady.get()) {
                delay(1000)
            }
        }
    }

    private suspend fun performJsFetch(url: String, method: String, bodyJson: String?): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            // Уникальное имя моста для каждой операции, чтобы избежать конфликтов
            val bridgeName = "JSBridge_${System.currentTimeMillis()}"
            
            val bridge = object {
                @JavascriptInterface
                fun onSuccess(result: String) {
                    Log.d(TAG, "JS Fetch Success")
                    cleanup()
                    if (cont.isActive) cont.resume(result)
                }
                
                @JavascriptInterface
                fun onError(error: String) {
                    Log.e(TAG, "JS Fetch Error: $error")
                    cleanup()
                    if (cont.isActive) cont.resumeWithException(Exception(error))
                }

                private fun cleanup() {
                    Handler(Looper.getMainLooper()).post { 
                         webView?.removeJavascriptInterface(bridgeName) 
                    }
                }
            }

            webView?.addJavascriptInterface(bridge, bridgeName)

            // Экранирование JSON для корректной вставки в JS строку
            val safePayload = bodyJson ?: "null"
            
            val js = """
                (function() {
                    try {
                        const payloadData = $safePayload;
                        const options = {
                            method: '$method',
                            headers: { 'Content-Type': 'application/json' },
                            credentials: 'include'
                        };
                        
                        if (payloadData && '$method' !== 'GET') {
                            options.body = JSON.stringify(payloadData);
                        }

                        fetch('$url', options)
                        .then(r => {
                            if (!r.ok) throw new Error('HTTP status ' + r.status);
                            return r.text();
                        })
                        .then(text => {
                            // Проверяем, что это JSON, а не HTML страница ошибки
                            try {
                                JSON.parse(text);
                                window.$bridgeName.onSuccess(text);
                            } catch(e) {
                                window.$bridgeName.onError('Response is not JSON: ' + text.substring(0, 100));
                            }
                        })
                        .catch(err => {
                            window.$bridgeName.onError(err.toString());
                        });
                    } catch(err) {
                        window.$bridgeName.onError(err.toString());
                    }
                })();
            """.trimIndent()

            webView?.evaluateJavascript(js, null)
        }
    }

    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            webView?.destroy()
            webView = null
            isReady.set(false)
        }
    }
}
