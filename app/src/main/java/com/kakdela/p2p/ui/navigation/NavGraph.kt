package com.kakdela.p2p.ui.navigation

import android.app.Application
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
import com.kakdela.p2p.viewmodel.ChatViewModelFactory
import com.kakdela.p2p.ui.ChatViewModel
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Центральный граф навигации. 
 * Отвечает за маршрутизацию между P2P чатами, авторизацией и развлекательными сервисами.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    identityRepository: IdentityRepository
) {
    val isOnline by rememberIsOnline()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Определяем, когда показывать BottomBar (только на главных экранах)
    val showBottomBar = currentRoute in listOf(
        Routes.CHATS, Routes.DEALS, Routes.ENTERTAINMENT, Routes.SETTINGS
    )

    Scaffold(
        bottomBar = { 
            if (showBottomBar) {
                AppBottomBar(currentRoute, navController) 
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            
            // --- СТАРТОВАЯ ЛОГИКА ---
            composable(Routes.SPLASH) {
                SplashScreen {
                    val myId = identityRepository.getMyId()
                    val nextRoute = if (myId.length > 10) Routes.CHATS else Routes.CHOICE
                    navController.navigate(nextRoute) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            }

            // --- АВТОРИЗАЦИЯ ---
            composable(Routes.CHOICE) {
                RegistrationChoiceScreen(
                    onPhone = { navController.navigate(Routes.AUTH_PHONE) },
                    onEmailOnly = { navController.navigate(Routes.AUTH_EMAIL) }
                )
            }

            composable(Routes.AUTH_EMAIL) {
                EmailAuthScreen(identityRepository) {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.CHOICE) { inclusive = true }
                    }
                }
            }

            composable(Routes.AUTH_PHONE) {
                PhoneAuthScreen(identityRepository) {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.CHOICE) { inclusive = true }
                    }
                }
            }

            // --- КОММУНИКАЦИИ (P2P) ---
            composable(Routes.CHATS) { 
                ChatsListScreen(navController, identityRepository) 
            }

            composable(Routes.CONTACTS) {
                ContactsScreen(
                    identityRepository = identityRepository,
                    onContactClick = { contact ->
                        if (contact.userHash.isNotBlank()) {
                            navController.navigate("chat/${contact.userHash}")
                        }
                    }
                )
            }

            composable(
                route = "chat/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                val context = LocalContext.current.applicationContext as Application
                
                // Создаем ViewModel, привязанную к жизненному циклу текущего composable
                val vm: ChatViewModel = viewModel(
                    factory = ChatViewModelFactory(identityRepository, context)
                )

                LaunchedEffect(chatId) {
                    vm.initChat(chatId)
                }
                
                val messagesEntities by vm.messages.collectAsState()

                // Маппинг данных из DB-слоя в UI-слой (Продакшн-подход к разделению моделей)
                val uiMessages = remember(messagesEntities) {
                    messagesEntities.map { entity ->
                        com.kakdela.p2p.data.Message(
                            id = entity.messageId,
                            text = entity.text,
                            senderId = entity.senderId,
                            timestamp = entity.timestamp,
                            isMe = entity.isMe,
                            status = entity.status
                        )
                    }
                }

                ChatScreen(
                    chatPartnerId = chatId,
                    messages = uiMessages, 
                    identityRepository = identityRepository,
                    onSendMessage = { text -> vm.sendMessage(text) },
                    onSendFile = { uri, name -> vm.sendFile(uri.toString(), name) },
                    onSendAudio = { uri, dur -> vm.sendAudio(uri.toString(), dur.toInt()) },
                    onScheduleMessage = { text, time -> vm.scheduleMessage(text, time.toString()) },
                    onBack = { navController.popBackStack() }
                )
            }

            // --- СЕРВИСЫ И ИНСТРУМЕНТЫ ---
            composable(Routes.DEALS) { DealsScreen(navController) }
            composable(Routes.ENTERTAINMENT) { EntertainmentScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController, identityRepository) }
            composable(Routes.MUSIC) { MusicPlayerScreen() }
            composable(Routes.TEXT_EDITOR) { TextEditorScreen(navController) }
            
            // ИГРЫ
            composable(Routes.TIC_TAC_TOE) { TicTacToeScreen() }
            composable(Routes.CHESS) { ChessScreen() }
            composable(Routes.PACMAN) { PacmanScreen() }

            // WEB ВЬЮ (с защитой от передачи некорректных URL)
            composable(
                route = "webview/{url}/{title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { e ->
                val encodedUrl = e.arguments?.getString("url").orEmpty()
                val decodedUrl = remember(encodedUrl) {
                    URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.name())
                }
                val title = e.arguments?.getString("title").orEmpty()
                
                if (isOnline) {
                    WebViewScreen(decodedUrl, title, navController)
                } else {
                    NoInternetScreen { navController.popBackStack() }
                }
            }

            composable(Routes.AI_CHAT) { 
                if (isOnline) AiChatScreen() else NoInternetScreen { navController.popBackStack() } 
            }
        }
    }
}

@Composable
private fun AppBottomBar(currentRoute: String?, navController: NavHostController) {
    NavigationBar(
        containerColor = Color(0xFF010101),
        tonalElevation = 0.dp
    ) {
        val items = listOf(
            Triple(Routes.CHATS, Icons.Outlined.ChatBubbleOutline, "Чаты"),
            Triple(Routes.DEALS, Icons.Filled.Checklist, "Дела"),
            Triple(Routes.ENTERTAINMENT, Icons.Outlined.PlayCircleOutline, "Досуг"),
            Triple(Routes.SETTINGS, Icons.Filled.Settings, "Опции")
        )
        items.forEach { (route, icon, label) ->
            val isSelected = currentRoute == route
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(route) {
                            // Сохраняем состояние вкладок при переключении
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { 
                    Icon(
                        imageVector = icon, 
                        contentDescription = label,
                        tint = if (isSelected) Color.Cyan else Color.Gray
                    ) 
                },
                label = { 
                    Text(
                        text = label, 
                        fontSize = 10.sp,
                        color = if (isSelected) Color.Cyan else Color.Gray
                    ) 
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color(0xFF1A1A1A)
                )
            )
        }
    }
}

/**
 * Реактивное наблюдение за статусом интернета.
 */
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
            status.value = true // Фоллбэк на случай ошибок регистрации
        }

        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return status
}

@Composable
fun NoInternetScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), 
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "Офлайн-режим", 
                color = Color.White, 
                fontSize = 20.sp, 
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Эта функция требует интернет-соединения", 
                color = Color.Gray, 
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Вернуться", color = Color.White)
            }
        }
    }
}
