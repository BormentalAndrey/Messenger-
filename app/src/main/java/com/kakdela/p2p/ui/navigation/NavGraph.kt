package com.kakdela.p2p.ui.navigation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kakdela.p2p.R
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.*
import com.kakdela.p2p.ui.chat.ChatScreen // Убедитесь, что путь верный
import com.kakdela.p2p.ui.chat.ChatsListScreen // Убедитесь, что путь верный
import com.kakdela.p2p.ui.player.MusicPlayerScreen
import com.kakdela.p2p.viewmodel.ChatViewModel
import com.kakdela.p2p.viewmodel.ChatViewModelFactory

@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val connected = remember {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        mutableStateOf(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { connected.value = true }
            override fun onLost(network: Network) { connected.value = false }
        }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), 
            callback
        )
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    return connected
}

@Composable
fun NoInternetScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), 
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.no_internet_neon),
                contentDescription = "No Internet",
                modifier = Modifier.size(200.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Требуется подключение к сети", color = Color.White)
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    identityRepository: IdentityRepository
) {
    val isOnline by rememberIsOnline()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(Routes.CHATS, Routes.DEALS, Routes.ENTERTAINMENT, Routes.SETTINGS)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color(0xFF0A0A0A)) {
                    val navItems = listOf(
                        Triple(Routes.CHATS, Icons.Outlined.ChatBubbleOutline, "Чаты"),
                        Triple(Routes.DEALS, Icons.Filled.Checklist, "Дела"),
                        Triple(Routes.ENTERTAINMENT, Icons.Outlined.PlayCircleOutline, "Развлечения"),
                        Triple(Routes.SETTINGS, Icons.Filled.Settings, "Настройки")
                    )

                    navItems.forEach { (route, icon, label) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = { 
                                if (currentRoute != route) {
                                    navController.navigate(route) { 
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true 
                                        restoreState = true
                                    } 
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = when(route) {
                                    Routes.CHATS -> Color.Cyan
                                    Routes.DEALS -> Color.Magenta
                                    Routes.ENTERTAINMENT -> Color.Green
                                    else -> Color.White
                                }, 
                                unselectedIconColor = Color.Gray,
                                indicatorColor = Color(0xFF1A1A1A)
                            )
                        )
                    }
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
                SplashScreen(onTimeout = {
                    val myId = identityRepository.getMyId()
                    val next = if (myId.isNotEmpty()) Routes.CHATS else Routes.CHOICE
                    navController.navigate(next) { popUpTo(Routes.SPLASH) { inclusive = true } }
                })
            }

            composable(Routes.CHOICE) { 
                RegistrationChoiceScreen(
                    onPhone = { navController.navigate(Routes.AUTH_PHONE) },
                    onEmailOnly = { navController.navigate(Routes.AUTH_EMAIL) }
                ) 
            }

            composable(Routes.AUTH_EMAIL) { 
                EmailAuthScreen(onAuthSuccess = { 
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.CHOICE) { inclusive = true }
                    }
                }) 
            }

            composable(Routes.AUTH_PHONE) { 
                PhoneAuthScreen { 
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.CHOICE) { inclusive = true }
                    }
                } 
            }

            // ИСПРАВЛЕНО: Передаем identityRepository, если экран его требует
            composable(Routes.CHATS) { ChatsListScreen(navController, identityRepository) }
            
            composable(Routes.CONTACTS) { ContactsScreen { id -> navController.navigate("chat/$id") } }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.DEALS) { DealsScreen(navController) }
            composable(Routes.ENTERTAINMENT) { EntertainmentScreen(navController) }
            
            composable(Routes.MUSIC) { MusicPlayerScreen() }
            composable(Routes.CALCULATOR) { CalculatorScreen() }
            composable(Routes.TIC_TAC_TOE) { TicTacToeScreen() }
            composable(Routes.CHESS) { ChessScreen() }
            composable(Routes.PACMAN) { PacmanScreen() }
            composable(Routes.JEWELS) { JewelsBlastScreen() }
            composable(Routes.SUDOKU) { SudokuScreen() }
            composable("text_editor") { TextEditorScreen(navController = navController) }

            composable(
                route = "webview/{url}/{title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { backStack ->
                if (isOnline) {
                    WebViewScreen(
                        url = backStack.arguments?.getString("url") ?: "",
                        title = backStack.arguments?.getString("title") ?: "",
                        navController = navController
                    )
                } else {
                    NoInternetScreen()
                }
            }

            composable("chat/{chatId}") { backStack ->
                val chatId = backStack.arguments?.getString("chatId") ?: ""
                
                // Использование фабрики для внедрения зависимостей в ViewModel
                val vm: ChatViewModel = viewModel(
                    factory = ChatViewModelFactory(identityRepository)
                )
                
                val myUid = identityRepository.getMyId()
                
                LaunchedEffect(chatId) { 
                    vm.initChat(chatId, myUid) 
                }
                
                val messages by vm.messages.collectAsState()
                
                // ИСПРАВЛЕНО: Убедитесь, что параметры ChatScreen соответствуют объявлению в самом файле ChatScreen.kt
                ChatScreen(
                    chatPartnerId = chatId,
                    messages = messages,
                    identityRepository = identityRepository,
                    onSendMessage = { text -> vm.sendMessage(text) },
                    onSendFile = { uri, type -> vm.sendFile(uri, type) },
                    onSendAudio = { uri, duration -> vm.sendAudio(uri, duration) },
                    onScheduleMessage = { text, time -> vm.scheduleMessage(text, time) }
                )
            }
        }
    }
}

