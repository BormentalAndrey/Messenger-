package com.kakdela.p2p.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.ui.ChatScreen
import com.kakdela.p2p.ui.ChatsListScreen
import com.kakdela.p2p.ui.auth.PhoneAuthScreen

@Composable
fun NavGraph(navController: NavHostController) {
    val currentUserId = Firebase.auth.currentUser?.uid ?: ""

    NavHost(
        navController = navController,
        startDestination = if (currentUserId.isEmpty()) "auth" else "chats"
    ) {
        // 1. Экран авторизации по номеру телефона
        composable("auth") {
            PhoneAuthScreen(
                navController = navController,
                onAuthSuccess = {
                    // После успешного входа переходим к списку чатов
                    navController.navigate("chats") {
                        popUpTo("auth") { inclusive = true }  // Удаляем auth из стека
                    }
                }
            )
        }

        // 2. Главный экран — список чатов (как в WhatsApp)
        composable("chats") {
            ChatsListScreen(navController = navController)
        }

        // 3. Конкретный чат (личный или глобальный)
        composable("chat/{chatId}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: "global"
            val userId = Firebase.auth.currentUser?.uid ?: ""

            ChatScreen(
                chatId = chatId,
                currentUserId = userId
            )
        }

        // Опционально: экран создания нового чата (по номеру)
        // composable("new_chat") { NewChatScreen(navController) }
    }
}
