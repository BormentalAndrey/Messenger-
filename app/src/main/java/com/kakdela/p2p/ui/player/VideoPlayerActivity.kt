package com.kakdela.p2p.ui.player

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AlphaAnimation
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kakdela.p2p.R

data class VideoModel(val title: String, val url: String)

class VideoPlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    private lateinit var controlsRoot: View
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var videoTitle: TextView

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    private var isFullscreen = false

    // Плейлист видео
    private val playlist = listOf(
        VideoModel("Big Buck Bunny", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
        VideoModel("Elephants Dream", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"),
        VideoModel("For Bigger Blazes", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
    )
    private var currentVideoIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        initViews()
        initializePlayer()
        setupControls()
        showControls()
    }

    private fun initViews() {
        playerView = findViewById(R.id.player_view)
        controlsRoot = findViewById(R.id.controls_root)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnPrev = findViewById(R.id.btn_prev)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        seekBar = findViewById(R.id.seek_bar)
        currentTime = findViewById(R.id.current_time)
        totalTime = findViewById(R.id.total_time)
        videoTitle = findViewById(R.id.video_title)
    }

    private fun initializePlayer() {
        // Поддержка всех форматов обеспечивается стандартным ExoPlayer.Builder
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        // Загружаем весь список в плеер для бесшовного переключения
        playlist.forEach { video ->
            val mediaItem = MediaItem.Builder()
                .setUri(video.url)
                // Можно добавить MIME тип, если формат специфичный (например, HLS/Dash)
                .build()
            player.addMediaItem(mediaItem)
        }

        player.prepare()
        player.playWhenReady = true

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Обновляем заголовок при смене видео
                currentVideoIndex = player.currentMediaItemIndex
                videoTitle.text = playlist[currentVideoIndex].title
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalTime.text = formatTime(player.duration)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(
                    if (isPlaying) androidx.media3.ui.R.drawable.exo_icon_pause
                    else androidx.media3.ui.R.drawable.exo_icon_play
                )
                if (isPlaying) startSeekBarUpdate()
            }
        })
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
            resetHideTimer()
        }

        btnNext.setOnClickListener {
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            resetHideTimer()
        }

        btnPrev.setOnClickListener {
            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
            resetHideTimer()
        }

        btnFullscreen.setOnClickListener {
            toggleFullscreen()
            resetHideTimer()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && player.duration > 0) {
                    val position = progress.toLong() * player.duration / 1000
                    player.seekTo(position)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { hideHandler.removeCallbacks(hideRunnable) }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { resetHideTimer() }
        })

        playerView.setOnClickListener {
            if (controlsRoot.visibility == View.VISIBLE) hideControls() else showControls()
        }
    }

    private fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 5000)
    }

    // ... (Методы formatTime, startSeekBarUpdate, showControls, hideControls, toggleFullscreen, hideSystemUi такие же, как в вашем коде)
    // Добавьте их из вашего исходника без изменений
    
    private fun startSeekBarUpdate() {
        hideHandler.removeCallbacks(updateSeekBarRunnable)
        hideHandler.post(updateSeekBarRunnable)
    }

    private val updateSeekBarRunnable: Runnable = object : Runnable {
        override fun run() {
            if (player.duration > 0) {
                val progress = (player.currentPosition * 1000 / player.duration).toInt()
                seekBar.progress = progress.coerceIn(0, 1000)
                currentTime.text = formatTime(player.currentPosition)
            }
            if (player.isPlaying) hideHandler.postDelayed(this, 500)
        }
    }

    private fun showControls() {
        controlsRoot.visibility = View.VISIBLE
        val anim = AlphaAnimation(0f, 1f).apply { duration = 300 }
        controlsRoot.startAnimation(anim)
        resetHideTimer()
    }

    private fun hideControls() {
        val anim = AlphaAnimation(1f, 0f).apply { duration = 300 }
        controlsRoot.startAnimation(anim)
        controlsRoot.visibility = View.GONE
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            hideSystemUi()
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            btnFullscreen.setImageResource(androidx.media3.ui.R.drawable.exo_icon_fullscreen_exit)
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            showSystemUi()
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            btnFullscreen.setImageResource(androidx.media3.ui.R.drawable.exo_icon_fullscreen_enter)
        }
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun showSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        hideHandler.removeCallbacksAndMessages(null)
    }
}

