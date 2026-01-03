package com.kakdela.p2p.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.abs

private fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
fun MiniPlayer(
    vm: PlayerViewModel,
    sleepRemaining: Long = 0L,
    onExpandClick: () -> Unit = {}
) {
    val current by vm.currentTrack.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.currentPosition.collectAsState()
    val duration by vm.currentDuration.collectAsState()

    current?.let { track ->
        Surface(
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpandClick)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        if (abs(dragAmount) > 100) { // порог свайпа
                            if (dragAmount > 0) vm.previous() else vm.next()
                            change.consume()
                        }
                    }
                }
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = track.albumArt,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { vm.toggleFavorite(track.id) } // клик по обложке — в избранное
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        MarqueeText(
                            text = track.title,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${track.artist} • ${track.albumTitle}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (sleepRemaining > 0) {
                            Text(
                                text = "Таймер сна: ${sleepRemaining.formatDuration()}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(onClick = { vm.previous() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null)
                    }
                    IconButton(onClick = { vm.togglePlayPause() }) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = { vm.next() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                    }

                    // Кнопка избранного в мини-плеере
                    IconButton(onClick = { vm.toggleFavorite(track.id) }) {
                        Icon(
                            imageVector = if (track.id in vm.favoriteIds.value) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (track.id in vm.favoriteIds.value) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }

                // Тонкий прогресс-бар
                LinearProgressIndicator(
                    progress = { if (duration > 0) position.toFloat() / duration else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    } ?: run {
        // Если ничего не играет — можно скрыть мини-плеер или показать placeholder
        Box(Modifier.height(0.dp))
    }
}

// Простой marquee-эффект для длинных названий (анимация прокрутки)
@Composable
fun MarqueeText(
    text: String,
    fontSize: sp,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition()
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -1000f, // большая величина, чтобы прокрутка была плавной
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing), // скорость прокрутки
            repeatMode = RepeatMode.Restart
        )
    )

    Box(modifier = Modifier.widthIn(max = 200.dp)) { // ограничение ширины
        Text(
            text = text + "   •   " + text, // дублируем для seamless эффекта
            fontSize = fontSize,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .offset(x = offset.dp)
                .width(IntrinsicSize.Max)
        )
    }
}
