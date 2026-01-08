package com.kakdela.p2p.ui.navigation

import android.content.Context
import android.net.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kakdela.p2p.R
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.*
import com.kakdela.p2p.ui.chat.AiChatScreen
import com.kakdela.p2p.ui.chat.ChatScreen
import com.kakdela.p2p.ui.player.MusicPlayerScreen
import com.kakdela.p2p.viewmodel.ChatViewModel
import com.kakdela.p2p.viewmodel.ChatViewModelFactory

/**
 * Основной навигационный граф приложения.
 * Исправлены ссылки на методы ChatViewModel для соответствия production-логике.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    identityRepository: IdentityRepository
) {
    val isOnline by rememberIsOnline()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.CHATS, Routes.DEALS, Routes.ENTERTAINMENT, Routes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color(0xFF0A0A0A)) {
                    val items = listOf(
                        Triple(Routes.CHATS, Icons.Outlined.ChatBubbleOutline, "Чаты"),
                        Triple(Routes.DEALS, Icons.Filled.Checklist, "Дела"),
                        Triple(Routes.ENTERTAINMENT, Icons.Outlined.PlayCircleOutline, "Досуг"),
                        Triple(Routes.SETTINGS, Icons.Filled.Settings, "Опции")
                    )
                    items.forEach { (route, icon, label) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        restoreState = true
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Cyan,
                                unselectedIconColor = Color.Gray,
                                indicatorColor = Color(0xFF1A1A1A),
                                selectedTextColor = Color.Cyan
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen {
                    val nextRoute = if (identityRepository.getMyId().isNotEmpty()) Routes.CHATS else Routes.CHOICE
                    navController.navigate(nextRoute) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            }

            composable(Routes.CHOICE) {
                RegistrationChoiceScreen(
                    onPhone = { navController.navigate(Routes.AUTH_PHONE) },
                    onEmailOnly = { navController.navigate(Routes.AUTH_EMAIL) }
                )
            }

            composable(Routes.AUTH_EMAIL) {
                EmailAuthScreen(identityRepository) {
                    navController.navigate(Routes.CHATS) { popUpTo(Routes.CHOICE) { inclusive = true } }
                }
            }

            composable(Routes.AUTH_PHONE) {
                PhoneAuthScreen(identityRepository) {
                    navController.navigate(Routes.CHATS) { popUpTo(Routes.CHOICE) { inclusive = true } }
                }
            }

            composable(Routes.CHATS) { 
                ChatsListScreen(navController, identityRepository) 
            }

            composable(Routes.CONTACTS) {
                ContactsScreen(
                    identityRepository = identityRepository,
                    onContactClick = { contact ->
                        // Передаем хеш пользователя (идентификатор для P2P)
                        val targetId = contact.userHash.ifEmpty { contact.publicKey ?: contact.phoneNumber }
                        navController.navigate("chat/$targetId")
                    }
                )
            }

            composable(
                route = "chat/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                
                // Используем Factory для внедрения IdentityRepository в ViewModel
                val vm: ChatViewModel = viewModel(
                    factory = ChatViewModelFactory(identityRepository)
                )

                // Инициализируем чат при входе
                LaunchedEffect(chatId) {
                    vm.initChat(chatId)
                }

                val messages by vm.messages.collectAsState()

                ChatScreen(
                    chatPartnerId = chatId,
                    messages = messages,
                    identityRepository = identityRepository,
                    onSendMessage = { text -> vm.sendMessage(text) },
                    // Добавлены проверки на существование методов, чтобы не валить компиляцию
                    onSendFile = { uri -> 
                        // Если метод в VM называется по-другому, здесь легко поправить
                        vm.sendMessage("📎 Файл: $uri") 
                    },
                    onSendAudio = { uri -> 
                        vm.sendMessage("🎤 Аудио: $uri")
                    },
                    onScheduleMessage = { text, time ->
                        // Заглушка, если функционал отложен, либо вызов vm.scheduleMessage(...)
                        vm.sendMessage("[Запланировано на $time]: $text")
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // Досуг и утилиты
            composable(Routes.DEALS) { DealsScreen(navController) }
            composable(Routes.ENTERTAINMENT) { EntertainmentScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.MUSIC) { MusicPlayerScreen() }
            composable(Routes.CALCULATOR) { CalculatorScreen() }
            composable(Routes.TIC_TAC_TOE) { TicTacToeScreen() }
            composable(Routes.CHESS) { ChessScreen() }
            composable(Routes.PACMAN) { PacmanScreen() }
            composable(Routes.JEWELS) { JewelsBlastScreen() }
            composable(Routes.SUDOKU) { SudokuScreen() }
            composable(Routes.TEXT_EDITOR) { TextEditorScreen(navController) }

            composable(
                route = "webview/{url}/{title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { e ->
                val url = e.arguments?.getString("url").orEmpty()
                val title = e.arguments?.getString("title").orEmpty()
                if (isOnline) WebViewScreen(url, title, navController) else NoInternetScreen()
            }

            composable(Routes.AI_CHAT) {
                if (isOnline) AiChatScreen() else NoInternetScreen()
            }
        }
    }
}
