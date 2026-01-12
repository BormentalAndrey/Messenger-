package com.kakdela.p2p.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

@SuppressLint("StaticFieldLeak")
object WebViewApiClient {

    private const val TAG = "WebViewApiClient"
    private const val BASE_URL = "http://kakdela.infinityfree.me/"
    private const val API_URL = "http://kakdela.infinityfree.me/api.php"
    
    // Таймаут на выполнение одного JS-запроса (включая ожидание сети внутри WebView)
    private const val REQUEST_TIMEOUT_MS = 45_000L 

    private var webView: WebView? = null
    
    // Флаг готовности (пройден ли антибот)
    private val isReady = AtomicBoolean(false)
    
    // Мьютекс для последовательного выполнения (WebView однопоточен)
    private val mutex = Mutex()
    private val gson = Gson()

    /**
     * Инициализация WebView. Вызывается в Application.onCreate()
     */
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
                        // Маскировка под Chrome (удаляем маркер WebView)
                        userAgentString = userAgentString.replace("; wv", "")
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d(TAG, "Page loaded: $url")
                            // Если загрузился наш домен, считаем, что куки получены
                            if (url?.contains("kakdela.infinityfree.me") == true) {
                                isReady.set(true)
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            return false // Разрешаем редиректы внутри WebView для работы защиты
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            // Логи из JS для отладки антибота (теперь включено)
                            Log.d("WebViewConsole", "[JS] ${consoleMessage?.message()}")
                            return true
                        }
                    }
                }
                
                Log.i(TAG, "Starting Anti-Bot Warmup...")
                webView?.loadUrl(BASE_URL)

            } catch (e: Exception) {
                Log.e(TAG, "Error init WebView: ${e.message}")
            }
        }
    }

    /**
     * Очистка ресурсов при завершении работы (опционально)
     */
    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            try {
                webView?.stopLoading()
                webView?.clearHistory()
                webView?.removeAllViews()
                webView?.destroy()
                webView = null
                isReady.set(false)
                Log.d(TAG, "WebView destroyed")
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying WebView: ${e.message}")
            }
        }
    }

    /**
     * Публичный метод регистрации.
     * Принимает Wrapper, сериализует его и отправляет.
     */
    suspend fun announceSelf(wrapper: UserRegistrationWrapper): ServerResponse {
        val jsonBody = gson.toJson(wrapper)
        return executeRequest("add_user", "POST", jsonBody)
    }

    /**
     * Публичный метод получения списка узлов.
     */
    suspend fun getAllNodes(): ServerResponse {
        return executeRequest("list_users", "GET", null)
    }

    /**
     * Универсальный исполнитель запросов через JS fetch с защитой от ошибок.
     */
    private suspend fun executeRequest(
        action: String,
        method: String,
        bodyJson: String?
    ): ServerResponse {
        return mutex.withLock {
            var attempt = 0
            while (attempt < 3) {
                try {
                    // 1. Ждем прогрева (максимум 30 сек)
                    waitForReady()

                    // 2. Выполняем JS fetch с таймаутом
                    val jsonResult = withTimeout(REQUEST_TIMEOUT_MS) {
                        performJsFetch(action, method, bodyJson)
                    }
                    
                    // 3. Парсим ответ
                    return gson.fromJson(jsonResult, ServerResponse::class.java)

                } catch (e: Exception) {
                    Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                    
                    // Если ошибка похожа на HTML (антибот) или таймаут
                    isReady.set(false)
                    withContext(Dispatchers.Main) {
                        webView?.loadUrl(BASE_URL) // Перезагружаем главную для обновления кук
                    }
                    attempt++
                    // Небольшая задержка перед следующей попыткой
                    kotlinx.coroutines.delay(2000)
                }
            }
            return ServerResponse(success = false, error = "connection_failed_after_retries")
        }
    }

    private suspend fun waitForReady() {
        if (isReady.get()) return
        try {
            withTimeout(30_000) {
                while (!isReady.get()) {
                    kotlinx.coroutines.delay(500)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wait for ready timeout. Proceeding anyway (might fail).")
        }
    }

    /**
     * Внедряет JS код fetch.
     * ИСПРАВЛЕНО: Безопасная передача bodyJson без инъекций строк.
     */
    private suspend fun performJsFetch(
        action: String,
        method: String,
        bodyJson: String?
    ): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val bridgeName = "AndroidBridge_${System.currentTimeMillis()}"
            val url = "$API_URL?action=$action"
            
            // Если bodyJson null, передаем null. Если есть JSON, передаем его как ОБЪЕКТНЫЙ ЛИТЕРАЛ JS.
            // Мы НЕ оборачиваем bodyJson в кавычки, так как это уже валидная JSON-строка (например {"a":"b"}).
            // В JS это превратится в: const payload = {"a":"b"};
            val jsPayloadVar = bodyJson ?: "null"
            
            val bridge = WebViewBridge { result ->
                webView?.removeJavascriptInterface(bridgeName) // Cleanup
                if (continuation.isActive) {
                    result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWith(Result.failure(it)) }
                    )
                }
            }

            webView?.addJavascriptInterface(bridge, bridgeName)

            val jsCode = """
                (function() {
                    try {
                        const url = '$url';
                        const method = '$method';
                        // ВАЖНО: jsPayloadVar вставляется как код, а не строка.
                        // Если bodyJson = {"key":"val'ue"}, то JS код будет: const rawData = {"key":"val'ue"};
                        const rawData = $jsPayloadVar;
                        
                        // Если данные есть, превращаем их обратно в строку для отправки, иначе null
                        const bodyData = rawData ? JSON.stringify(rawData) : null;

                        fetch(url, {
                            method: method,
                            headers: {
                                'Content-Type': 'application/json'
                            },
                            body: bodyData
                        })
                        .then(response => response.text())
                        .then(text => {
                            try {
                                // Проверка: если это не JSON, то JSON.parse выбросит ошибку
                                const json = JSON.parse(text);
                                $bridgeName.onSuccess(text);
                            } catch(e) {
                                // Сервер вернул HTML (антибот) или мусор
                                console.error('Invalid JSON received: ' + text.substring(0, 100));
                                $bridgeName.onError('INVALID_JSON_HTML_DETECTED');
                            }
                        })
                        .catch(err => {
                            console.error('Fetch error: ' + err);
                            $bridgeName.onError(err.toString());
                        });
                    } catch (e) {
                        console.error('Script injection error: ' + e);
                        $bridgeName.onError('SCRIPT_ERROR: ' + e.toString());
                    }
                })();
            """.trimIndent()

            webView?.evaluateJavascript(jsCode, null)
        }
    }
}
