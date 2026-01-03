package com.kakdela.p2p.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PacmanScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = {

            WebView(context).apply {

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // ---------- НАСТРОЙКИ ----------
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.mediaPlaybackRequiresUserGesture = false

                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()

                // ---------- ЗАПУСК ИГРЫ ----------
                loadUrl("file:///android_asset/pacman/index.html")
            }
        },
        update = { }
    )

    // ---------- КОРРЕКТНОЕ УНИЧТОЖЕНИЕ ----------
    DisposableEffect(Unit) {
        onDispose {
            // ничего не нужно
        }
    }
}
