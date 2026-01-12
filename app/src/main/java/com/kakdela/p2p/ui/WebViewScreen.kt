package com.kakdela.p2p.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.kakdela.p2p.network.CookieStore
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Полноценный продакшн-браузер внутри приложения.
 * Автоматически обходит Anti-Bot защиту InfinityFree и управляет куками.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, title: String, navController: NavHostController) {
    // Декодируем URL для корректной работы с символами
    val decodedUrl = remember(url) {
        try {
            URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            url
        }
    }

    val isTikTok = decodedUrl.contains("tiktok.com", ignoreCase = true)
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Системная кнопка "Назад"
    BackHandler(enabled = true) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            navController.popBackStack()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Настройки WebView
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    // Эмуляция реального браузера
                    userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                }

                // Включаем прием кук
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val targetUrl = request?.url.toString()

                        // Обработка внешних протоколов
                        if (targetUrl.startsWith("tel:") ||
                            targetUrl.startsWith("mailto:") ||
                            targetUrl.startsWith("geo:")
                        ) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                            } catch (e: Exception) {
                                Log.e("WebView", "Не удалось открыть внешнюю ссылку: $targetUrl")
                            }
                            return true
                        }

                        // Блокировка TikTok редиректов в Play Store или рекламные ссылки
                        if (isTikTok && (targetUrl.contains("play.google.com") || targetUrl.contains("app-ad"))) {
                            return true
                        }

                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)

                        // Обновление кук для обхода защиты InfinityFree
                        if (url != null && url.contains("infinityfree.me")) {
                            CookieStore.updateFromWebView(context, url)
                            if (CookieStore.testCookie != null) {
                                Log.i("WebViewAuth", "Сессионный ключ __test обновлен")
                            }

                            // Если был технический запрос к API, закрываем экран автоматически
                            if (url.contains("api.php")) {
                                navController.popBackStack()
                            }
                        }

                        // Подавляем всплывающие окна в TikTok
                        if (isTikTok) {
                            view?.evaluateJavascript(
                                "(function() { window.open = function() { return null; }; })();",
                                null
                            )
                        }
                    }
                }

                webChromeClient = WebChromeClient()
                loadUrl(decodedUrl)
                webViewInstance = this
            }
        },
        onRelease = {
            // Очистка WebView при выходе
            it.stopLoading()
            it.removeAllViews()
            it.destroy()
        }
    )
}
