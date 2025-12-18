package com.kakdela.p2p.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.*
import com.kakdela.p2p.model.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.CHATS, Routes.DEALS, Routes.ENTERTAINMENT, Routes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.Black, contentColor = Color.White) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.CHATS,
                        onClick = { navController.navigate(Routes.CHATS) { launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
                        label = { Text("Чаты") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.DEALS,
                        onClick = { navController.navigate(Routes.DEALS) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Checklist, null) },
                        label = { Text("Дела") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.ENTERTAINMENT,
                        onClick = { navController.navigate(Routes.ENTERTAINMENT) { launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.PlayCircleOutline, null) },
                        label = { Text("Развлечения") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Settings, null) },
                        label = { Text("Настройки") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(padding).background(Color.Black)
        ) {
            composable(Routes.SPLASH) { SplashScreen(navController) }
            composable(Routes.CHOICE) {
                RegistrationChoiceScreen(
                    onEmail = { navController.navigate(Routes.AUTH_EMAIL) },
                    onPhone = { navController.navigate(Routes.AUTH_PHONE) }
                )
            }
            composable(Routes.AUTH_EMAIL) { EmailAuthScreen(navController) { navController.navigate(Routes.CHATS) } }
            composable(Routes.AUTH_PHONE) { PhoneAuthScreen { navController.navigate(Routes.CHATS) } }
            
            composable(Routes.CHATS) { ChatsListScreen(navController) }
            composable(Routes.CONTACTS) { 
                ContactsScreen(onContactClick = { userId -> navController.navigate("chat/$userId") }) 
            }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.DEALS) { DealsScreen(navController) }
            composable(Routes.ENTERTAINMENT) { EntertainmentScreen(navController) }
            
            // Игры
            composable(Routes.CALCULATOR) { CalculatorScreen() }
            composable(Routes.TIC_TAC_TOE) { TicTacToeScreen() }
            composable(Routes.CHESS) { ChessScreen() }
            composable(Routes.PACMAN) { PacmanScreen() }
            composable(Routes.JEWELS) { JewelsBlastScreen() }

            composable(Routes.CHAT) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: "global"
                
                // ИСПРАВЛЕНИЕ: Передаем необходимые аргументы в ChatScreen
                ChatScreen(
                    chatId = chatId,
                    messages = emptyList(), // Здесь должна быть загрузка из ViewModel
                    onSendMessage = { /* логика */ },
                    onScheduleMessage = { text, time -> /* логика */ }
                )
            }
        }
    }
}

