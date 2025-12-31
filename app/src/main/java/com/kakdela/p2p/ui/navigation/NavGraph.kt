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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.*
import com.kakdela.p2p.model.ChatMessage
import com.google.firebase.auth.FirebaseAuth

object Routes {
    const val SPLASH = "splash"
    const val CHOICE = "choice"
    const val AUTH_EMAIL = "auth_email"
    const val AUTH_PHONE = "auth_phone"
    const val CHATS = "chats"
    const val DEALS = "deals"
    const val ENTERTAINMENT = "entertainment"
    const val SETTINGS = "settings"
    const val CONTACTS = "contacts"
    const val CALCULATOR = "calculator"
    const val TIC_TAC_TOE = "tic_tac_toe"
    const val CHESS = "chess"
    const val PACMAN = "pacman"
    const val JEWELS = "jewels"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Скрываем BottomBar на экране Splash и экранах входа
    val showBottomBar = currentRoute !in listOf(Routes.SPLASH, Routes.CHOICE, Routes.AUTH_EMAIL, Routes.AUTH_PHONE)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color(0xFF0A0A0A)) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.CHATS,
                        onClick = { navController.navigate(Routes.CHATS) { popUpTo(Routes.CHATS) { inclusive = true }; launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
                        label = { Text("Чаты") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Cyan, indicatorColor = Color(0xFF002222))
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.DEALS,
                        onClick = { navController.navigate(Routes.DEALS) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Checklist, null) },
                        label = { Text("Дела") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Magenta, indicatorColor = Color(0xFF220022))
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.ENTERTAINMENT,
                        onClick = { navController.navigate(Routes.ENTERTAINMENT) { launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.PlayCircleOutline, null) },
                        label = { Text("Развлечения") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Green, indicatorColor = Color(0xFF002200))
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Settings, null) },
                        label = { Text("Настройки") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, indicatorColor = Color.Gray)
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
            // Исправленный Splash с логикой проверки
            composable(Routes.SPLASH) { 
                SplashScreen {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        navController.navigate(Routes.CHATS) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.CHOICE) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                }
            }

            composable(Routes.CHOICE) { RegistrationChoiceScreen({ navController.navigate(Routes.AUTH_EMAIL) }, { navController.navigate(Routes.AUTH_PHONE) }) }
            composable(Routes.AUTH_EMAIL) { EmailAuthScreen(navController) { navController.navigate(Routes.CHATS) { popUpTo(Routes.CHOICE) { inclusive = true } } } }
            composable(Routes.AUTH_PHONE) { PhoneAuthScreen { navController.navigate(Routes.CHATS) { popUpTo(Routes.CHOICE) { inclusive = true } } } }
            
            composable(Routes.CHATS) { ChatsListScreen(navController) }
            composable(Routes.CONTACTS) { ContactsScreen { userId -> navController.navigate("chat/$userId") } }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.DEALS) { DealsScreen(navController) }
            composable(Routes.ENTERTAINMENT) { EntertainmentScreen(navController) }
            
            composable(Routes.CALCULATOR) { CalculatorScreen() }
            composable(Routes.TIC_TAC_TOE) { TicTacToeScreen() }
            composable(Routes.CHESS) { ChessScreen() }
            composable(Routes.PACMAN) { PacmanScreen() }
            composable(Routes.JEWELS) { JewelsBlastScreen() }

            composable(
                route = "webview/{url}/{title}",
                arguments = listOf(navArgument("url") { type = NavType.StringType }, navArgument("title") { type = NavType.StringType })
            ) { backStackEntry ->
                val url = backStackEntry.arguments?.getString("url") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: ""
                WebViewScreen(url = url, title = title, navController = navController)
            }

            composable("chat/{chatId}") { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: "global"
                val vm: ChatViewModel = viewModel()
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                LaunchedEffect(chatId) { vm.initChat(chatId, uid) }
                val msgs by vm.messages.collectAsState()
                ChatScreen(chatId, msgs.map { ChatMessage(it.id.toString(), it.text, it.senderId, it.timestamp, it.senderId == uid) }, { vm.sendMessage(it) }, { t, d -> vm.scheduleMessage(t, d) })
            }
        }
    }
}

