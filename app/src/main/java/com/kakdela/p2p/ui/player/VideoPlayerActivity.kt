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
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kakdela.p2p.R

data class VideoModel(val title: String, val url: String)

class VideoPlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var btnPlayPause: AppCompatImageButton
    private lateinit var btnNext: AppCompatImageButton
    private lateinit var btnPrev: AppCompatImageButton
    private lateinit var btnFullscreen: AppCompatImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var videoTitle: TextView
    private lateinit var controlsRoot: View

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }
    private var isFullscreen = false

    // Плейлист видео
    private val playlist = listOf(
        VideoModel("Big Buck Bunny", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
        VideoModel("Elephants Dream", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"),
        VideoModel("For Bigger Blazes", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        initViews()
        initializePlayer()
        setupControls()
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
        // Поддержка расширенных форматов видео через программные декодеры
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        player = ExoPlayer.Builder(this, renderersFactory).build()
        playerView.player = player

        // Добавление плейлиста в плеер
        playlist.forEach { video ->
            player.addMediaItem(MediaItem.fromUri(video.url))
        }

        player.prepare()
        player.playWhenReady = true

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Обновление заголовка при переключении
                val index = player.currentMediaItemIndex
                videoTitle.text = playlist.getOrNull(index)?.title ?: "Видео"
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Использование системных иконок для предотвращения ошибок AAPT
                btnPlayPause.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
                if (isPlaying) startSeekBarUpdate()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalTime.text = formatTime(player.duration)
                }
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

        playerView.setOnClickListener {
            if (controlsRoot.visibility == View.VISIBLE) hideControls() else showControls()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser && player.duration > 0) {
                    player.seekTo((p.toLong() * player.duration) / 1000)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) { hideHandler.removeCallbacks(hideRunnable) }
            override fun onStopTrackingTouch(s: SeekBar?) { resetHideTimer() }
        })
    }

    private fun startSeekBarUpdate() {
        hideHandler.post(object : Runnable {
            override fun run() {
                if (player.isPlaying) {
                    val current = player.currentPosition
                    val duration = player.duration
                    if (duration > 0) {
                        seekBar.progress = (current * 1000 / duration).toInt()
                        currentTime.text = formatTime(current)
                    }
                    hideHandler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun showControls() {
        controlsRoot.visibility = View.VISIBLE
        val anim = AlphaAnimation(0f, 1f).apply { duration = 300 }
        controlsRoot.startAnimation(anim)
        resetHideTimer()
    }

    private fun hideControls() {
        if (controlsRoot.visibility == View.GONE) return
        val anim = AlphaAnimation(1f, 0f).apply { duration = 300 }
        controlsRoot.startAnimation(anim)
        controlsRoot.visibility = View.GONE
    }

    private fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 5000)
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            hideSystemUi()
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            showSystemUi()
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN 
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
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
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacksAndMessages(null)
        player.release()
    }
}

