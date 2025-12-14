package com.kakdela.p2p

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.ui.navigation.NavGraph
import com.kakdela.p2p.ui.theme.Theme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Эта строка скрывает стандартный ActionBar (верхнюю панель с названием приложения)
        // Без неё может появляться пустая или неправильная панель, даже с Compose
        supportActionBar?.hide()

        // Опционально: делаем статус-бар и навигационную панель прозрачными/в стиле Material3
        // (можно убрать, если не нужно)
        // WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            Theme {  // Ваша Compose-тема с Material3
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
