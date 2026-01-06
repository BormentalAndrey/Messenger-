package com.kakdela.p2p.ui.call

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.getstream.webrtc.android.ui.VideoRenderer

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val targetId = intent.getStringExtra("targetId") ?: "Unknown"

        setContent {
            var isMuted by remember { mutableStateOf(false) }
            var isCameraOn by remember { mutableStateOf(true) }

            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Звонок: $targetId", color = Color.Cyan, style = MaterialTheme.typography.headlineMedium)
                    
                    Spacer(modifier = Modifier.height(50.dp))
                    
                    // Контейнер для видео (GetStream WebRTC)
                    Box(modifier = Modifier.size(300.dp).padding(16.dp)) {
                        Text("Ожидание подключения...", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { isMuted = !isMuted }) {
                            Icon(
                                imageVector = if (isMuted) androidx.compose.material.icons.Icons.Default.MicOff else androidx.compose.material.icons.Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isMuted) Color.Red else Color.White
                            )
                        }
                        
                        Button(
                            onClick = { finish() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Сбросить")
                        }

                        IconButton(onClick = { isCameraOn = !isCameraOn }) {
                            Icon(
                                imageVector = if (isCameraOn) androidx.compose.material.icons.Icons.Default.Videocam else androidx.compose.material.icons.Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
