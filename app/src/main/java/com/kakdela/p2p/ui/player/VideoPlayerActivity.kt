package com.kakdela.p2p.ui.player

import android.Manifest
import android.content.ContentUris
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AlphaAnimation
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var videoTitle: TextView
    private lateinit var controlsRoot: View

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }
    private var isFullscreen = false
    
    private var videoPlaylist = mutableListOf<VideoModel>()

    // Регистрация запроса разрешений
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadVideosFromDevice()
        } else {
            Toast.makeText(this, "Нужен доступ к файлам для поиска видео", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        initViews()
        initializePlayer()
        setupControls()
        checkPermissionsAndLoad()
    }

    private fun checkPermissionsAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                loadVideosFromDevice()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun loadVideosFromDevice() {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME
        )

        val query = contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DISPLAY_NAME} ASC")
        
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

            videoPlaylist.clear()
            player.clearMediaItems()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                
                val video = VideoModel(name, contentUri.toString())
                videoPlaylist.add(video)
                player.addMediaItem(MediaItem.fromUri(contentUri))
            }
        }

        if (videoPlaylist.isNotEmpty()) {
            player.prepare()
            videoTitle.text = videoPlaylist[0].title
        } else {
            videoTitle.text = "Видео не найдены"
        }
    }

    private fun initViews() {
        playerView = findViewById<PlayerView>(R.id.player_view)
        controlsRoot = findViewById<View>(R.id.controls_root)
        btnPlayPause = findViewById<ImageButton>(R.id.btn_play_pause)
        btnNext = findViewById<ImageButton>(R.id.btn_next)
        btnPrev = findViewById<ImageButton>(R.id.btn_prev)
        btnFullscreen = findViewById<ImageButton>(R.id.btn_fullscreen)
        seekBar = findViewById<SeekBar>(R.id.seek_bar)
        currentTime = findViewById<TextView>(R.id.current_time)
        totalTime = findViewById<TextView>(R.id.total_time)
        videoTitle = findViewById<TextView>(R.id.video_title)
    }

    private fun initializePlayer() {
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        player = ExoPlayer.Builder(this, renderersFactory).build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                if (index < videoPlaylist.size) {
                    videoTitle.text = videoPlaylist[index].title
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
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

