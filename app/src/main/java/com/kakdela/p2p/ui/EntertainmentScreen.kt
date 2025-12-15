package com.kakdela.p2p.ui

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController

@Composable
fun EntertainmentScreen(navController: NavHostController) {
    Column {
        Button(onClick = { navController.navigate("chat/global") }) {
            Text("ЧёКаВо? (глобальный чат)")
        }

        AndroidView(
            factory = {
                WebView(it).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = WebViewClient()
                    loadUrl("https://pikabu.ru")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
