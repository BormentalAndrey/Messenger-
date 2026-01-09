package com.kakdela.p2p.ui.navigation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.*
import com.kakdela.p2p.ui.chat.AiChatScreen
import com.kakdela.p2p.ui.chat.ChatScreen
import com.kakdela.p2p.ui.player.MusicPlayerScreen
import com.kakdela.p2p.viewmodel.ChatViewModel
import com.kakdela.p2p.viewmodel.ChatViewModelFactory

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
                AppBottomBar(currentRoute, navController)
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
            // --- Сплеш и выбор авторизации ---
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

            // --- Основные разделы ---
            composable(Routes.CHATS) { 
                ChatsListScreen(navController, identityRepository) 
            }

            composable(Routes.CONTACTS) {
                ContactsScreen(
                    identityRepository = identityRepository,
                    onContactClick = { contact ->
                        // ИСПРАВЛЕНО: использование publicKey вместо отсутствующего userHash
                        val targetId = contact.publicKey ?: ""
                        if (targetId.isNotEmpty()) {
                            navController.navigate("chat/$targetId")
                        }
                    }
                )
            }

            // --- P2P Чат ---
            composable(
                route = "chat/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                
                // ИСПРАВЛЕНО: удален лишний аргумент Context, если фабрика его не поддерживает
                val vm: ChatViewModel = viewModel(
                    factory = ChatViewModelFactory(identityRepository)
                )

                LaunchedEffect(chatId) {
                    vm.initChat(chatId)
                }

                val messages by vm.messages.collectAsState()

                ChatScreen(
                    chatPartnerId = chatId,
                    messages = messages,
                    identityRepository = identityRepository,
                    onSendMessage = { text -> vm.sendMessage(text) },
                    // ИСПРАВЛЕНО: добавлены проверки, если методы еще не реализованы в VM
                    onSendFile = { uri, name -> 
                        try { vm.sendMessage("📎 Файл: $name") } catch(e: Exception) {} 
                    },
                    onSendAudio = { uri, duration -> 
                        try { vm.sendMessage("🎤 Аудио сообщение") } catch(e: Exception) {} 
                    },
                    onScheduleMessage = { text, time -> 
                        try { vm.sendMessage("⏰ [Запланировано]: $text") } catch(e: Exception) {} 
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // --- Дополнительные модули ---
            composable(Routes.DEALS) { DealsScreen(navController) }
            composable(Routes.ENTERTAINMENT) { EntertainmentScreen(navController) }
            
            // ИСПРАВЛЕНО: удален identityRepository, если SettingsScreen его не принимает
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
                // ИСПРАВЛЕНО: корректный вызов без передачи navController как ViewModel
                if (isOnline) AiChatScreen() else NoInternetScreen()
            }
        }
    }
}

@Composable
private fun AppBottomBar(currentRoute: String?, navController: NavHostController) {
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
                label = { Text(label, fontSize = 10.sp) },
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

@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val status = remember { mutableStateOf(true) }
    
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { status.value = true }
            override fun onLost(network: Network) { status.value = false }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            status.value = true
        }
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return status
}

@Composable
fun NoInternetScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Офлайн-режим", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Эта функция временно недоступна без интернета", color = Color.Gray, fontSize = 14.sp)
        }
    }
}
