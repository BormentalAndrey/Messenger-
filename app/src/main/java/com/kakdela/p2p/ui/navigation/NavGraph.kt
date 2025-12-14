package com.kakdela.p2p.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.ui.ChatScreen          // ← Добавьте этот импорт!
import com.kakdela.p2p.ui.ChatsListScreen
import com.kakdela.p2p.ui.auth.EmailAuthScreen

@Composable
fun NavGraph(navController: NavHostController) {
    val currentUser = Firebase.auth.currentUser
    val startDestination = if (currentUser == null) "auth" else "chats"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Экран авторизации/регистрации по email
        composable("auth") {
            EmailAuthScreen(
                navController = navController,
                onAuthSuccess = {
                    navController.navigate("chats") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        // Список чатов
        composable("chats") {
            ChatsListScreen(navController = navController)
        }

        // Конкретный чат
        composable("chat/{chatId}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: "global"
            val currentUserId = Firebase.auth.currentUser?.uid ?: ""

            ChatScreen(
                chatId = chatId,
                currentUserId = currentUserId
            )
        }
    }
}
