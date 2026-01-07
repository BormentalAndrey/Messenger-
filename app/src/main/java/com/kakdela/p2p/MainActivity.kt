package com.kakdela.p2p

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.ui.navigation.NavGraph
import com.kakdela.p2p.ui.theme.KakdelaTheme

class MainActivity : ComponentActivity() {

    // Инициализируем репозиторий здесь, чтобы P2P-стек жил вместе с приложением
    private lateinit var identityRepository: IdentityRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        identityRepository = IdentityRepository(applicationContext)

        setContent {
            KakdelaTheme {
                val navController = rememberNavController()
                // Передаем identityRepository в NavGraph, чтобы экраны могли его использовать
                NavGraph(navController, identityRepository)
            }
        }
    }
}

