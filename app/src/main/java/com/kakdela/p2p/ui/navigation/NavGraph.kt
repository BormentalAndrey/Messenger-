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
import com.google.firebase.auth.FirebaseAuth
import com.kakdela.p2p.R
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.*
import com.kakdela.p2p.ui.player.MusicPlayerScreen
import com.kakdela.p2p.ui.TextEditorScreen
import com.kakdela.p2p.viewmodel.ChatViewModel

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
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.no_internet_neon),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    identityRepository: IdentityRepository // ДОБАВЛЕНО: Репозиторий для P2P функций
) {
    val isOnline by rememberIsOnline()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(Routes.CHATS, Routes.DEALS, Routes.ENTERTAINMENT, Routes.SETTINGS)

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
            composable(Routes.SPLASH) {
                SplashScreen(onTimeout = {
                    val next = if (FirebaseAuth.getInstance().currentUser != null) Routes.CHATS else Routes.CHOICE
                    navController.navigate(next) { popUpTo(Routes.SPLASH) { inclusive = true } }
                })
            }

            // ИСПРАВЛЕНО: Аргументы для RegistrationChoiceScreen
            composable(Routes.CHOICE) { 
                RegistrationChoiceScreen(
                    onPhone = { navController.navigate(Routes.AUTH_PHONE) },
                    onEmailOnly = { navController.navigate(Routes.AUTH_EMAIL) }
                ) 
            }

            // ИСПРАВЛЕНО: Аргументы для EmailAuthScreen (убрали navController, если он не нужен внутри)
            composable(Routes.AUTH_EMAIL) { 
                EmailAuthScreen(onAuthSuccess = { navController.navigate(Routes.CHATS) }) 
            }

            composable(Routes.AUTH_PHONE) { 
                PhoneAuthScreen { navController.navigate(Routes.CHATS) } 
            }

            composable(Routes.CHATS) { ChatsListScreen(navController) }
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

            // ИСПРАВЛЕНО: Интеграция с ChatViewModel и корректные аргументы ChatScreen
            composable("chat/{chatId}") { backStack ->
                val chatId = backStack.arguments?.getString("chatId") ?: ""
                val vm: ChatViewModel = viewModel()
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                
                LaunchedEffect(chatId) { vm.initChat(chatId, uid) }
                
                val messages by vm.messages.collectAsState()
                
                ChatScreen(
                    chatPartnerId = chatId, // ИСПРАВЛЕНО имя параметра
                    messages = messages,
                    identityRepository = identityRepository, // ПЕРЕДАЕМ репозиторий
                    onSendMessage = { text -> vm.sendMessage(text) },
                    onSendFile = { uri, type -> vm.sendFile(uri, type) },
                    onSendAudio = { uri, duration -> vm.sendAudio(uri, duration) },
                    onScheduleMessage = { text, time -> vm.scheduleMessage(text, time) }
                )
            }
        }
    }
}

