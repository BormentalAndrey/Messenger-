package com.kakdela.p2p.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.*

object Routes {
    const val SPLASH = "splash"
    const val CHOICE = "choice"
    const val AUTH_EMAIL = "auth_email"
    const val AUTH_PHONE = "auth_phone"
    const val CHATS = "chats"
    const val CONTACTS = "contacts"
    const val DEALS = "deals"
    const val ENTERTAINMENT = "entertainment"
    const val SETTINGS = "settings"
    const val CALCULATOR = "calculator"        // Калькулятор в "Делах"
    const val TIC_TAC_TOE = "tictactoe"        // Крестики-нолики
    const val CHESS = "chess"                  // Шахматы
    const val PACMAN = "pacman"                // Пакман
    const val JEWELS = "jewels"                // Jewels Blast
    const val CHAT = "chat/{chatId}"
}

@Composable
fun NavGraph(navController: NavHostController) {

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.CHATS,
        Routes.DEALS,
        Routes.ENTERTAINMENT,
        Routes.SETTINGS,
        Routes.CONTACTS,
        Routes.CALCULATOR,
        Routes.TIC_TAC_TOE,
        Routes.CHESS,
        Routes.PACMAN,
        Routes.JEWELS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.Black) {

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
            modifier = Modifier.padding(padding)
        ) {

            composable(Routes.SPLASH) { SplashScreen(navController) }

            composable(Routes.CHOICE) {
                RegistrationChoiceScreen(
                    onEmail = { navController.navigate(Routes.AUTH_EMAIL) },
                    onPhone = { navController.navigate(Routes.AUTH_PHONE) }
                )
            }

            composable(Routes.AUTH_EMAIL) {
                EmailAuthScreen(
                    navController = navController,
                    onAuthSuccess = {
                        navController.navigate(Routes.CHATS) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.AUTH_PHONE) {
                PhoneAuthScreen(
                    onSuccess = {
                        navController.navigate(Routes.CHATS) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.CHATS) { ChatsListScreen(navController) }
            composable(Routes.CONTACTS) { ContactsScreen(navController) }
            composable(Routes.ENTERTAINMENT) { EntertainmentScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.DEALS) { DealsScreen(navController) }

            // Калькулятор (из "Дел")
            composable(Routes.CALCULATOR) { CalculatorScreen() }

            // Мини-игры (из "Развлечений")
            composable(Routes.TIC_TAC_TOE) { TicTacToeScreen() }
            composable(Routes.CHESS) { ChessScreen() }
            composable(Routes.PACMAN) { PacmanScreen() }
            composable(Routes.JEWELS) { JewelsBlastScreen() }

            composable(Routes.CHAT) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: "global"
                ChatScreen(chatId, Firebase.auth.currentUser?.uid ?: "")
            }
        }
    }
}
