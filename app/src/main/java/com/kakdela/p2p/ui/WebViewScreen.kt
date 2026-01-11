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
 * Универсальный WebView экран.
 * 1. Открывает любые сайты.
 * 2. Обходит защиту InfinityFree (перехват кук).
 * 3. Блокирует агрессивную рекламу и редиректы TikTok.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, title: String, navController: NavHostController) {
    // Декодируем входящий URL
    val decodedUrl = try {
        URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
    } catch (e: Exception) { 
        url 
    }

    val isTikTok = decodedUrl.contains("tiktok.com", ignoreCase = true)
    val isInfinityFree = decodedUrl.contains("infinityfree.me", ignoreCase = true)

    var webView: WebView? by remember { mutableStateOf(null) }

    // Обработка кнопки "Назад"
    BackHandler(enabled = true) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            navController.popBackStack()
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    
                    // Универсальный десктопный User-Agent для обхода ограничений
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                }

                webViewClient = object : WebViewClient() {
                    
                    // Обработка кликов по ссылкам
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val targetUrl = request?.url.toString()

                        // 1. Обработка системных ссылок (телефон, почта)
                        if (targetUrl.startsWith("tel:") || targetUrl.startsWith("mailto:")) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                            return true
                        }

                        // 2. Блокировка редиректов TikTok в магазины приложений
                        if (isTikTok) {
                            if (targetUrl.contains("play.google.com") || 
                                targetUrl.contains("apps.apple.com") ||
                                targetUrl.contains("download")
                            ) {
                                return true // Блокируем переход
                            }
                        }
                        
                        return false // Разрешаем обычный переход внутри WebView
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        
                        // ПЕРЕХВАТ КУКИ ДЛЯ API
                        if (url != null && url.contains("infinityfree.me")) {
                            val cookies = CookieManager.getInstance().getCookie(url)
                            if (cookies != null && cookies.contains("__test=")) {
                                Log.d("WebViewAuth", "Cookie __test получена успешно")
                                CookieStore.updateCookie(cookies)
                                
                                // Если это был технический вход для API, закрываем экран автоматически
                                if (url.contains("api.php")) {
                                    navController.popBackStack()
                                }
                            }
                        }

                        // Инъекция скрипта защиты для TikTok
                        if (isTikTok) {
                            val blockScript = """
                                (function() {
                                    window.open = function() { return null; };
                                    console.log('Anti-Redirect Script Active');
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(blockScript, null)
                        }
                    }
                }

                webChromeClient = WebChromeClient()
                loadUrl(decodedUrl)
                webView = this
            }
        },
        update = {
            // Если URL изменился во время работы компонента
            // it.loadUrl(decodedUrl) 
        },
        modifier = Modifier.fillMaxSize()
    )
}
