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
    const val CHOICE = "choice"
    const val AUTH_EMAIL = "auth_email"
    const val AUTH_PHONE = "auth_phone"
    const val CHATS = "chats"
    const val CONTACTS = "contacts"
    const val DEALS = "deals"
    const val ENTERTAINMENT = "entertainment"
    const val SETTINGS = "settings"
    const val CHAT = "chat/{chatId}"
}

@Composable
fun NavGraph(navController: NavHostController) {

    val currentUser = Firebase.auth.currentUser
    val isLoggedIn = currentUser != null

    /** ✅ ЕДИНСТВЕННОЕ место, где происходит переход после логина */
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            navController.navigate(Routes.CHATS) {
                popUpTo(Routes.CHOICE) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val startDestination = if (isLoggedIn) Routes.CHATS else Routes.CHOICE

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.CHATS,
        Routes.DEALS,
        Routes.ENTERTAINMENT,
        Routes.SETTINGS,
        Routes.CONTACTS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.Black) {

                    NavigationBarItem(
                        selected = currentRoute == Routes.CHATS,
                        onClick = {
                            navController.navigate(Routes.CHATS) { launchSingleTop = true }
                        },
                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
                        label = { Text("Чаты") }
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.DEALS,
                        onClick = {
                            navController.navigate(Routes.DEALS) { launchSingleTop = true }
                        },
                        icon = { Icon(Icons.Filled.Checklist, null) },
                        label = { Text("Дела") }
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.ENTERTAINMENT,
                        onClick = {
                            navController.navigate(Routes.ENTERTAINMENT) { launchSingleTop = true }
                        },
                        icon = { Icon(Icons.Outlined.PlayCircleOutline, null) },
                        label = { Text("Развлечения") }
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        },
                        icon = { Icon(Icons.Filled.Settings, null) },
                        label = { Text("Настройки") }
                    )
                }
            }
        }
    ) { padding ->

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {

            composable(Routes.CHOICE) {
                RegistrationChoiceScreen(
                    onEmail = { navController.navigate(Routes.AUTH_EMAIL) },
                    onPhone = { navController.navigate(Routes.AUTH_PHONE) }
                )
            }

            /** ❌ БЕЗ navigate() внутри */
            composable(Routes.AUTH_EMAIL) {
                EmailAuthScreen()
            }

            /** ❌ БЕЗ navigate() внутри */
            composable(Routes.AUTH_PHONE) {
                PhoneAuthScreen()
            }

            composable(Routes.CHATS) {
                ChatsListScreen(navController)
            }

            composable(Routes.CONTACTS) {
                ContactsScreen(navController)
            }

            composable(Routes.ENTERTAINMENT) {
                EntertainmentScreen(navController)
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(navController)
            }

            composable(Routes.DEALS) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Экран «Дела» в разработке", color = Color.White)
                }
            }

            composable(Routes.CHAT) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: "global"
                ChatScreen(chatId, Firebase.auth.currentUser?.uid ?: "")
            }
        }
    }
}
