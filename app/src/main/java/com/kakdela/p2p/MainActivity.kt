package com.kakdela.p2p

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.services.P2PService
import com.kakdela.p2p.ui.navigation.NavGraph
import com.kakdela.p2p.ui.theme.KakdelaTheme

class MainActivity : ComponentActivity() {

    private lateinit var identityRepository: IdentityRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        identityRepository = IdentityRepository(applicationContext)

        val serviceIntent = Intent(this, P2PService::class.java)
        startForegroundService(serviceIntent)

        setContent {
            KakdelaTheme {
                val navController = rememberNavController()
                val repo = remember { identityRepository }

                NavGraph(navController = navController, identityRepository = repo)
            }
        }
    }

    override fun onDestroy() {
        identityRepository.stopP2PNode()
        super.onDestroy()
    }
}
