#!/bin/bash

BASE_DIR="app/src/main/java/com/kakdela/p2p"

# Создаем нужные директории
mkdir -p $BASE_DIR/db
mkdir -p $BASE_DIR/ui/navigation
mkdir -p $BASE_DIR/ui/screens
mkdir -p $BASE_DIR/ui/chat
mkdir -p $BASE_DIR/ui/components
mkdir -p $BASE_DIR/webrtc

echo "Создание файлов..."

# --- App.kt ---
cat > $BASE_DIR/App.kt <<EOL
package com.kakdela.p2p

import android.app.Application
import androidx.room.Room
import com.kakdela.p2p.db.MessageDatabase

class App : Application() {
    lateinit var messageDatabase: MessageDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        messageDatabase = Room.databaseBuilder(
            applicationContext,
            MessageDatabase::class.java,
            "message_database"
        ).build()
    }
}
EOL

# --- MainActivity.kt ---
cat > $BASE_DIR/MainActivity.kt <<EOL
package com.kakdela.p2p

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.ui.navigation.AppNavGraph
import com.kakdela.p2p.ui.theme.KakdelaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KakdelaTheme {
                val navController = rememberNavController()
                AppNavGraph(navController)
            }
        }
    }
}
EOL

# --- db files ---
cat > $BASE_DIR/db/ChatMessage.kt <<EOL
package com.kakdela.p2p.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val chatId: String,
    val author: String,
    val text: String
)
EOL

cat > $BASE_DIR/db/MessageDao.kt <<EOL
package com.kakdela.p2p.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM ChatMessage WHERE chatId = :chatId")
    fun getMessagesForChat(chatId: String): Flow<List<ChatMessage>>

    @Insert
    suspend fun insert(message: ChatMessage)
}
EOL

cat > $BASE_DIR/db/MessageDatabase.kt <<EOL
package com.kakdela.p2p.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ChatMessage::class], version = 1)
abstract class MessageDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
EOL

# --- Navigation ---
cat > $BASE_DIR/ui/navigation/NavGraph.kt <<EOL
package com.kakdela.p2p.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.ui.screens.ChatScreen
import com.kakdela.p2p.ui.screens.ContactsScreen

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = "contacts") {
        composable("contacts") {
            ContactsScreen(onOpenChat = { chatId ->
                navController.navigate("chat/$chatId")
            })
        }
        composable("chat/{chatId}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ChatScreen(chatId = chatId, onBack = { navController.popBackStack() })
        }
    }
}
EOL

# --- ChatViewModel ---
cat > $BASE_DIR/ui/ChatViewModel.kt <<EOL
package com.kakdela.p2p.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.db.ChatMessage
import com.kakdela.p2p.db.MessageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val messageDao: MessageDao,
    private val chatId: String
) : ViewModel() {

    val messagesForChat: Flow<List<ChatMessage>> = messageDao.getMessagesForChat(chatId)

    fun sendMessage(author: String, text: String) {
        viewModelScope.launch {
            messageDao.insert(ChatMessage(author = author, text = text, chatId = chatId))
        }
    }
}
EOL

# --- UI chat ---
cat > $BASE_DIR/ui/chat/VoiceMessageButton.kt <<EOL
package com.kakdela.p2p.ui.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kakdela.p2p.ui.components.VoiceMessageRecorder

@Composable
fun VoiceMessageButton(modifier: Modifier = Modifier, onVoiceSent: (String) -> Unit) {
    var isRecording by remember { mutableStateOf(false) }

    IconButton(
        onClick = { isRecording = !isRecording },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isRecording) "Stop Recording" else "Record Voice"
        )
    }

    if (isRecording) {
        VoiceMessageRecorder(onVoiceSent = onVoiceSent)
    }
}
EOL

cat > $BASE_DIR/ui/components/VoiceMessageRecorder.kt <<EOL
package com.kakdela.p2p.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun VoiceMessageRecorder(onVoiceSent: (String) -> Unit) {
    var offset by remember { mutableStateOf(0f) }
    var isLocked by remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(targetValue = offset)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    offset += dragAmount
                    if (offset < -100f) isLocked = true
                    if (offset > 100f) {
                        CoroutineScope(Dispatchers.Main).launch {
                            offset = 0f
                        }
                    }
                    change.consume()
                }
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = !isLocked) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                tint = Color.Red,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        Text(
            text = "Slide to cancel",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(end = 16.dp)
        )

        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Recording",
            tint = Color.Red
        )

        AnimatedVisibility(visible = !isLocked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Lock",
                tint = Color.Green,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }

    // TODO: логика записи
    // onVoiceSent("path_to_audio")
}
EOL

echo "Все файлы созданы."
