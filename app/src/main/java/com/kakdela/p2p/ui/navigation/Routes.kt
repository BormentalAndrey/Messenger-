package com.kakdela.p2p.ui.navigation

/**
 * Объект со всеми маршрутами приложения.
 * Использование констант исключает ошибки опечаток при навигации.
 */
object Routes {
    // --- СЛУЖЕБНЫЕ ---
    const val SPLASH = "splash"
    
    // --- АВТОРИЗАЦИЯ ---
    const val CHOICE = "choice"
    const val AUTH_EMAIL = "auth_email"
    const val AUTH_PHONE = "auth_phone"

    // --- ГЛАВНЫЕ ЭКРАНЫ (BOTTOM BAR) ---
    const val CHATS = "chats"
    const val DEALS = "deals"
    const val ENTERTAINMENT = "entertainment"
    const val SETTINGS = "settings"

    // --- КОНТАКТЫ И ПЕРЕПИСКА ---
    const val CONTACTS = "contacts"
    /**
     * Маршрут чата требует ID партнера.
     * Шаблон: chat/{chatId}
     */
    const val CHAT_ROOT = "chat"
    const val CHAT_SCREEN = "$CHAT_ROOT/{chatId}"

    // --- ИНСТРУМЕНТЫ ---
    const val CALCULATOR = "calculator"
    const val TEXT_EDITOR = "text_editor"
    const val AI_CHAT = "ai_chat"

    // --- ДОСУГ И МЕДИА ---
    const val MUSIC = "music"
    const val TIC_TAC_TOE = "tic_tac_toe"
    const val CHESS = "chess"
    const val PACMAN = "pacman"
    const val JEWELS = "jewels"
    const val SUDOKU = "sudoku"

    /**
     * Вспомогательная функция для генерации пути к чату.
     * Использовать так: navController.navigate(Routes.buildChatRoute(userHash))
     */
    fun buildChatRoute(chatId: String): String {
        return "$CHAT_ROOT/$chatId"
    }
}
