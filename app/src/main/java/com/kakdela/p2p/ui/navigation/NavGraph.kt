package com.kakdela.p2p.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import com.kakdela.p2p.ui.auth.RegistrationChoiceScreen

@Composable
fun NavGraph(navController: NavHostController) {
    val currentUser = Firebase.auth.currentUser
    val startDestination = if (currentUser == null) "choice" else "chats"

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val showBottomBar = currentRoute in listOf("chats", "contacts", "entertainment", "settings")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.Black) {
                    NavigationBarItem(
                        selected = currentRoute == "chats",
                        onClick = { navController.navigate("chats") { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Chat, contentDescription = "Чаты") },
                        label = { Text("Чаты") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "contacts",
                        onClick = { navController.navigate("contacts") { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Контакты") },
                        label = { Text("Контакты") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "entertainment",
                        onClick = { navController.navigate("entertainment") { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Развлечения") },
                        label = { Text("Развлечения") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "settings",
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
            composable("choice") { RegistrationChoiceScreen(navController) }
            composable("auth_email") { EmailAuthScreen(navController) { navController.navigate("chats") { popUpTo(0) } } }
            composable("chats") { ChatsListScreen(navController) }
            composable("contacts") { ContactsScreen(navController) }
            composable("entertainment") { EntertainmentScreen(navController) }
            composable("settings") { SettingsScreen() }
            composable("chat/{chatId}") { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: "global"
                val currentUserId = Firebase.auth.currentUser?.uid ?: ""
                ChatScreen(chatId = chatId, currentUserId = currentUserId)
            }
        }
    }
}
