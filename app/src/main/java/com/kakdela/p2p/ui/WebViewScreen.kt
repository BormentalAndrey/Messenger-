package com.kakdela.p2p.ui

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.kakdela.p2p.network.CookieStore // Создадим этот объект ниже
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, title: String, navController: NavHostController) {
    val decodedUrl = try {
        URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
    } catch (e: Exception) { url }

    val isInfinityFree = decodedUrl.contains("infinityfree.me", ignoreCase = true)
    var webView: WebView? by remember { mutableStateOf(null) }

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
                    // InfinityFree требует JS для генерации куки
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        
                        // Если мы на странице вашего API
                        if (url != null && url.contains("infinityfree.me")) {
                            val cookies = CookieManager.getInstance().getCookie(url)
                            Log.d("WebViewAuth", "Перехвачены куки: $cookies")
                            
                            // Сохраняем куку __test в наше хранилище для Retrofit
                            if (cookies != null && cookies.contains("__test=")) {
                                CookieStore.updateCookie(cookies)
                                // Теперь Retrofit сможет делать запросы!
                            }
                        }
                    }
                }

                loadUrl(decodedUrl)
                webView = this
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
