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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("StaticFieldLeak")
object WebViewApiClient {

    private const val TAG = "WebViewApiClient"
    private const val BASE_URL = "http://kakdela.infinityfree.me/api.php"

    private const val PAGE_LOAD_TIMEOUT_MS = 30_000L
    private const val REQUEST_TIMEOUT_MS   = 20_000L
    private const val MAX_RETRIES          = 3

    private var webView: WebView? = null
    private val isReady = AtomicBoolean(false)
    private val mutex = Mutex()
    private val gson = Gson()

    private var okHttpClient: OkHttpClient? = null
    private var realUserAgent: String = ""

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    @SuppressLint("SetJavaScriptEnabled")
    fun init(context: Context) {
        if (webView != null) return

        Handler(Looper.getMainLooper()).post {
            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)

                webView = WebView(context.applicationContext).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    
                    realUserAgent = settings.userAgentString
                    
                    // Инициализируем OkHttp (WebkitCookieJar берется из соседнего файла)
                    initOkHttp()

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            checkProtection(url)
                        }
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(webView!!, true)
                }
                
                webView?.loadUrl(BASE_URL)

            } catch (e: Exception) {
                Log.e(TAG, "WebView init error", e)
            }
        }
    }

    private fun initOkHttp() {
        okHttpClient = OkHttpClient.Builder()
            .cookieJar(WebkitCookieJar()) 
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", realUserAgent)
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private fun checkProtection(url: String?) {
        if (url != null && url.contains("kakdela")) {
            val cookies = CookieManager.getInstance().getCookie(url)
            if (cookies != null && cookies.contains("__test")) {
                if (!isReady.get()) {
                    isReady.set(true)
                    Log.i(TAG, "Protection passed. Session Ready.")
                }
            }
        }
    }

    suspend fun announceSelf(payload: UserPayload): ServerResponse {
        val bodyJson = gson.toJson(payload)
        val url = "$BASE_URL?action=announce" 
        return executeRequest(url, "POST", bodyJson)
    }

    suspend fun getAllNodes(): ServerResponse {
        val url = "$BASE_URL?action=get_nodes"
        return executeRequest(url, "GET", null)
    }

    private suspend fun executeRequest(url: String, method: String, bodyJson: String?): ServerResponse = mutex.withLock {
        repeat(MAX_RETRIES) { attempt ->
            try {
                waitForReady()

                if (okHttpClient == null) throw Exception("OkHttp not initialized")

                return@withLock withContext(Dispatchers.IO) {
                    val requestBuilder = Request.Builder().url(url)
                    
                    if (method == "POST" && bodyJson != null) {
                        requestBuilder.post(bodyJson.toRequestBody(JSON_TYPE))
                    } else {
                        requestBuilder.get()
                    }

                    val response = okHttpClient!!.newCall(requestBuilder.build()).execute()
                    val responseBodyStr = response.body?.string()

                    if (!response.isSuccessful) {
                        if (response.code == 403 || response.code == 503 || response.code == 400) {
                             throw Exception("HTTP ${response.code}")
                        }
                        return@withContext ServerResponse(success = false, error = "HTTP ${response.code}")
                    }
                    
                    if (responseBodyStr == null || responseBodyStr.trim().startsWith("<")) {
                        throw Exception("Session expired or Protection active")
                    }

                    return@withContext gson.fromJson(responseBodyStr, ServerResponse::class.java)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                isReady.set(false)
                withContext(Dispatchers.Main) { webView?.loadUrl(BASE_URL) }
                delay(3000)
            }
        }
        // Используем именованные аргументы, чтобы избежать Type Mismatch
        return@withLock ServerResponse(success = false, error = "Max retries reached")
    }

    private suspend fun waitForReady() {
        if (isReady.get()) return
        withContext(Dispatchers.Main) { webView?.loadUrl(BASE_URL) }
        try {
            withTimeout(PAGE_LOAD_TIMEOUT_MS) {
                while (!isReady.get()) delay(500)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wait for ready timeout")
        }
    }

    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            webView?.destroy()
            webView = null
        }
    }
}
