package com.kakdela.p2p.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.ui.ChatScreen
import com.kakdela.p2p.ui.ChatsListScreen
import com.kakdela.p2p.ui.ContactsScreen
import com.kakdela.p2p.ui.EntertainmentScreen
import com.kakdela.p2p.ui.SettingsScreen
import com.kakdela.p2p.ui.auth.EmailAuthScreen
import com.kakdela.p2p.ui.auth.PhoneAuthScreen
import com.kakdela.p2p.ui.auth.RegistrationChoiceScreen

@Composable
fun NavGraph(navController: NavHostController) {
    val currentUser = Firebase.auth.currentUser
    val startDestination = if (currentUser == null) "choice" else "chats"

    val currentRoute by navController.currentBackStackEntryAsState()
    val currentRouteName = currentRoute?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRouteName in listOf("chats", "contacts", "entertainment", "settings")) {
                NavigationBar(containerColor = Color.Black) {
                    NavigationBarItem(
                        selected = currentRouteName == "chats",
                        onClick = { navController.navigate("chats") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Чаты") },
                        label = { Text("Чаты") }
                    )
                    NavigationBarItem(
                        selected = currentRouteName == "contacts",
                        onClick = { navController.navigate("contacts") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Contacts, contentDescription = "Контакты") },
                        label = { Text("Контакты") }
                    )
                    NavigationBarItem(
                        selected = currentRouteName == "entertainment",
                        onClick = { navController.navigate("entertainment") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Развлечения") },
                        label = { Text("Развлечения") }
                    )
                    NavigationBarItem(
                        selected = currentRouteName == "settings",
                        onClick = { navController.navigate("settings") { launchSingleTop = true } },
                        icon = { Text("=", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                        label = { Text("Настройки") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Выбор способа регистрации
            composable("choice") {
                RegistrationChoiceScreen(navController = navController)
            }

            // Регистрация по email (доступ к развлечениям)
            composable("auth_email") {
                EmailAuthScreen(navController = navController) {
                    navController.navigate("chats") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            // Регистрация по номеру телефона (доступ к личным чатам и контактам)
            composable("auth_phone") {
                PhoneAuthScreen(navController = navController) {
                    navController.navigate("chats") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            // Список чатов
            composable("chats") {
                ChatsListScreen(navController = navController)
            }

            // Контакты (только после входа по номеру)
            composable("contacts") {
                ContactsScreen(navController = navController)
            }

            // Развлечения (ЧёКаВо? + Pikabu)
            composable("entertainment") {
                EntertainmentScreen(navController = navController)
            }

            // Настройки
            composable("settings") {
                SettingsScreen()
            }

            // Конкретный чат
            composable("chat/{chatId}") { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: "global"
                val currentUserId = Firebase.auth.currentUser?.uid ?: ""
                ChatScreen(chatId = chatId, currentUserId = currentUserId)
            }
        }
    }
}
