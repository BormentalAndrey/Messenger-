package com.kakdela.p2p

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.ui.navigation.NavGraph
import com.kakdela.p2p.ui.theme.Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Разрешаем контенту заходить под системные панели (edge-to-edge)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            // Оборачиваем всё приложение в неоновую тему
            Theme {
                val navController = rememberNavController()
                NavGraph(navController)
            }
        }
    }
}

