package com.kakdela.p2p.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNav(navController: NavHostController) {
    val items = listOf("chats", "contacts", "entertainment", "settings")
    val icons = listOf("Чаты", "Контакты", "Развлечения", "=")

    NavigationBar {
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

        items.forEachIndexed { index, route ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                } },
                icon = { Text(icons[index]) },
                label = { Text(icons[index]) }
            )
        }
    }
}
