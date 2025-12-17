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
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth
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
    const val CALCULATOR = "calculator"
    const val TIC_TAC_TOE = "tictactoe"
    const val CHESS = "chess"
    const val PACMAN = "pacman"
    const val JEWELS = "jewels"
    const val CHAT = "chat/{chatId}"
    const val WEB_VIEW = "webview/{url}/{title}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.CHATS, 
        Routes.DEALS, 
        Routes.ENTERTAINMENT, 
        Routes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.CHATS,
                        onClick = { 
                            navController.navigate(Routes.CHATS) { 
                                popUpTo(Routes.CHATS) { inclusive = true }
                                launchSingleTop = true 
                            } 
                        },
                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
                        label = { Text("Чаты") }
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.DEALS,
                        onClick = { 
                            navController.navigate(Routes.DEALS) { 
                                launchSingleTop = true 
                            } 
                        },
                        icon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                        label = { Text("Дела") }
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.ENTERTAINMENT,
                        onClick = { 
                            navController.navigate(Routes.ENTERTAINMENT) { 
                                launchSingleTop = true 
                            } 
                        },
                        icon = { Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null) },
                        label = { Text("Развлечения") }
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { 
                            navController.navigate(Routes.SETTINGS) { 
                                launchSingleTop = true 
                            } 
                        },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Настройки") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier
                .padding(padding)
                .background(Color.Black)
        ) {
            composable(Routes.SPLASH) { SplashScreen(navController) }
            
            composable(Routes.CHOICE) {
                RegistrationChoiceScreen(
                    onEmail = { navController.navigate(Routes.AUTH_EMAIL) },
                    onPhone = { navController.navigate(Routes.AUTH_PHONE) }
                )
            }

            composable(Routes.AUTH_EMAIL) {
                EmailAuthScreen(navController = navController) {
                    // ИСПРАВЛЕНИЕ: Очищаем весь стек до Splash включительно
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            composable(Routes.AUTH_PHONE) {
                PhoneAuthScreen {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            
            composable(Routes.CHATS) { ChatsListScreen(navController) }
            composable(Routes.CONTACTS) { ContactsScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.DEALS) { DealsScreen(navController) }
            composable(Routes.ENTERTAINMENT) { EntertainmentScreen(navController) }
            
            composable(Routes.CALCULATOR) { CalculatorScreen() }
            composable(Routes.TIC_TAC_TOE) { TicTacToeScreen() }
            composable(Routes.CHESS) { ChessScreen() }
            composable(Routes.PACMAN) { PacmanScreen() }
            composable(Routes.JEWELS) { JewelsBlastScreen() }

            composable(
                route = Routes.WEB_VIEW,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { entry ->
                WebViewScreen(
                    url = entry.arguments?.getString("url") ?: "",
                    title = entry.arguments?.getString("title") ?: "Браузер",
                    navController = navController
                )
            }

            composable(Routes.CHAT) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: "global"
                ChatScreen(chatId, Firebase.auth.currentUser?.uid ?: "")
            }
        }
    }
}

