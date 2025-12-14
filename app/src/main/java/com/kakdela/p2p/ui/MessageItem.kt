package com.kakdela.p2p.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kakdela.p2p.data.Message

@Composable
fun MessageItem(message: Message) {
    Text(
        text = message.text,
        modifier = Modifier.padding(8.dp)
    )
}
