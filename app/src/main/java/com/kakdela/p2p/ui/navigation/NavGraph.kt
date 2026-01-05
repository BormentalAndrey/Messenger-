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
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.kakdela.p2p.model.ChatMessage
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.*
// Добавлен импорт плеера
import com.kakdela.p2p.ui.player.MusicPlayerScreen
// Новый импорт для редактора
import com.kakdela.p2p.ui.TextEditorScreen

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
                NavigationBar(containerColor = Color(0xFF0A0A0A)) {

                    NavigationBarItem(
                        selected = currentRoute == Routes.CHATS,
                        onClick = {
                            navController.navigate(Routes.CHATS) {
                                popUpTo(Routes.CHATS) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
                        label = { Text("Чаты") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Cyan,
                            indicatorColor = Color(0xFF002222)
                        )
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.DEALS,
                        onClick = {
                            navController.navigate(Routes.DEALS) {
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Filled.Checklist, null) },
                        label = { Text("Дела") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Magenta,
                            indicatorColor = Color(0xFF220022)
                        )
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.ENTERTAINMENT,
                        onClick = {
                            navController.navigate(Routes.ENTERTAINMENT) {
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.PlayCircleOutline, null) },
                        label = { Text("Развлечения") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Green,
                            indicatorColor = Color(0xFF002200)
                        )
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Filled.Settings, null) },
                        label = { Text("Настройки") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            indicatorColor = Color.Gray
                        )
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

            composable(Routes.SPLASH) {
                SplashScreen(
                    onTimeout = {
                        val next = if (FirebaseAuth.getInstance().currentUser != null)
                            Routes.CHATS else Routes.CHOICE

                        navController.navigate(next) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.CHOICE) {
                RegistrationChoiceScreen(
                    onEmail = { navController.navigate(Routes.AUTH_EMAIL) },
                    onPhone = { navController.navigate(Routes.AUTH_PHONE) }
                )
            }

            composable(Routes.AUTH_EMAIL) {
                EmailAuthScreen(navController) {
                    navController.navigate(Routes.CHATS)
                }
            }

            composable(Routes.AUTH_PHONE) {
                PhoneAuthScreen {
                    navController.navigate(Routes.CHATS)
                }
            }

            composable(Routes.CHATS) { ChatsListScreen(navController) }
            composable(Routes.CONTACTS) { ContactsScreen { id -> navController.navigate("chat/$id") } }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.DEALS) { DealsScreen(navController) }

            composable(Routes.ENTERTAINMENT) {
                EntertainmentScreen(navController)
            }

            // 🎵 MP3 ПРОИГРЫВАТЕЛЬ
            composable(Routes.MUSIC) {
                MusicPlayerScreen()
            }

            composable(Routes.CALCULATOR) { CalculatorScreen() }
            composable(Routes.TIC_TAC_TOE) { TicTacToeScreen() }
            composable(Routes.CHESS) { ChessScreen() }
            composable(Routes.PACMAN) { PacmanScreen() }
            composable(Routes.JEWELS) { JewelsBlastScreen() }
            composable(Routes.SUDOKU) { SudokuScreen() }

            // Новый маршрут для текстового редактора
            composable("text_editor") {
                TextEditorScreen(navController = navController)
            }

            composable(
                route = "webview/{url}/{title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { backStack ->
                WebViewScreen(
                    url = backStack.arguments?.getString("url") ?: "",
                    title = backStack.arguments?.getString("title") ?: "",
                    navController = navController
                )
            }

            composable("chat/{chatId}") { backStack ->
                val chatId = backStack.arguments?.getString("chatId") ?: "global"
                // Предполагается, что ChatViewModel у вас уже есть, так как в логах ошибок по нему нет
                val vm: ChatViewModel = viewModel()
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

                LaunchedEffect(chatId) {
                    vm.initChat(chatId, uid)
                }

                val msgs by vm.messages.collectAsState()

                ChatScreen(
                    chatId = chatId,
                    messages = msgs.map {
                        ChatMessage(
                            it.id.toString(),
                            it.text,
                            it.senderId,
                            it.timestamp,
                            it.senderId == uid
                        )
                    },
                    // ИСПРАВЛЕНИЕ: Используем правильные имена параметров как в ChatScreen.kt
                    onSendMessage = { vm.sendMessage(it) },
                    onScheduleMessage = { text, time -> vm.scheduleMessage(text, time) }
                )
            }
        }
    }
}
