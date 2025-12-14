package com.kakdela.p2p.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kakdela.p2p.ui.ChatScreen
import com.kakdela.p2p.ui.auth.PhoneAuthScreen
import com.kakdela.p2p.ui.ChatsListScreen  // Если добавите список чатов позже

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "auth"  // Сначала авторизация
    ) {
        composable("auth") {
            PhoneAuthScreen(navController = navController) {
                // После успешного входа переходим к чатам
                navController.navigate("chats") {
                    popUpTo("auth") { inclusive = true }
                }
            }
        }

        composable("chats") {
            // ChatsListScreen(navController)  // Когда добавите список чатов
            // Пока для теста — сразу глобальный чат
            ChatScreen(chatId = "global", currentUserId = Firebase.auth.currentUser?.uid ?: "")
        }

        composable("chat/{chatId}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: "global"
            ChatScreen(chatId = chatId, currentUserId = Firebase.auth.currentUser?.uid ?: "")
        }
    }
}
