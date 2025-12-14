package com.kakdela.p2p.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kakdela.p2p.ui.ChatScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(chatId = "global")
        }
    }
}
