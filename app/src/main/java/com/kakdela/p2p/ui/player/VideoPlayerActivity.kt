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
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.kakdela.p2p.R
import kotlin.math.max

data class VideoModel(val title: String, val uri: android.net.Uri)

class VideoPlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var btnPlaylist: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var videoTitle: TextView
    private lateinit var controlsRoot: View

    private val uiHandler = Handler(Looper.getMainLooper())
    private var isFullscreen = false
    private val playlist = mutableListOf<VideoModel>()
    
    // Runnable для скрытия контроллеров через 3 секунды
    private val hideControlsRunnable = Runnable { controlsRoot.visibility = View.GONE }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) loadVideos() 
            else Toast.makeText(this, "Доступ к видео запрещен", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        initViews()
        initPlayer()
        setupControls()
        checkPermission()
    }

    private fun initViews() {
        playerView = findViewById(R.id.player_view)
        controlsRoot = findViewById(R.id.controls_root)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnPrev = findViewById(R.id.btn_prev)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        btnPlaylist = findViewById(R.id.btn_playlist)
        seekBar = findViewById(R.id.seek_bar)
        currentTime = findViewById(R.id.current_time)
        totalTime = findViewById(R.id.total_time)
        videoTitle = findViewById(R.id.video_title)

        seekBar.max = 1000
    }

    private fun initPlayer() {
        // Поддержка расширений (софтверные декодеры, если аппаратные не тянут)
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        player = ExoPlayer.Builder(this, renderersFactory).build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
                if (isPlaying) startProgressUpdater()
                else uiHandler.removeCallbacksAndMessages(null)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalTime.text = format(player.duration)
                }
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val i = player.currentMediaItemIndex
                if (i in playlist.indices) {
                    videoTitle.text = playlist[i].title
                }
            }
        })
    }

    private fun loadVideos() {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
        val cursor = contentResolver.query(uri, projection, null, null, null)

        playlist.clear()
        player.clearMediaItems()

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol)
                val videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                playlist.add(VideoModel(name, videoUri))
                player.addMediaItem(MediaItem.fromUri(videoUri))
            }
        }
        player.prepare()
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener {
            resetHideTimer()
            if (player.isPlaying) player.pause() else player.play()
        }

        btnNext.setOnClickListener { resetHideTimer(); if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
        btnPrev.setOnClickListener { resetHideTimer(); if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }
        btnFullscreen.setOnClickListener { resetHideTimer(); toggleFullscreen() }
        btnPlaylist.setOnClickListener { resetHideTimer(); showPlaylist() }

        playerView.setOnClickListener {
            if (controlsRoot.visibility == View.VISIBLE) {
                controlsRoot.visibility = View.GONE
            } else {
                controlsRoot.visibility = View.VISIBLE
                resetHideTimer()
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser && player.duration > 0) {
                    val pos = player.duration * p / 1000
                    currentTime.text = format(pos)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { uiHandler.removeCallbacksAndMessages(null) }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                if (player.duration > 0) {
                    val pos = player.duration * (sb?.progress ?: 0) / 1000
                    player.seekTo(pos)
                }
                startProgressUpdater()
                resetHideTimer()
            }
        })
    }

    private fun resetHideTimer() {
        uiHandler.removeCallbacks(hideControlsRunnable)
        uiHandler.postDelayed(hideControlsRunnable, 3000)
    }

    private fun startProgressUpdater() {
        uiHandler.post(object : Runnable {
            override fun run() {
                if (player.isPlaying && player.duration > 0) {
                    val progress = (player.currentPosition * 1000 / max(1, player.duration)).toInt()
                    seekBar.progress = progress
                    currentTime.text = format(player.currentPosition)
                }
                uiHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun showPlaylist() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_playlist, null)
        val rv = view.findViewById<RecyclerView>(R.id.rv_playlist)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = PlaylistAdapter(playlist) { index ->
            player.seekTo(index, 0)
            player.play()
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        requestedOrientation = if (isFullscreen) 
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE 
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (isFullscreen) controller?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            else controller?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
    }

    private fun format(ms: Long): String {
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun checkPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) loadVideos()
        else permissionLauncher.launch(perm)
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
        player.release()
    }

    inner class PlaylistAdapter(private val list: List<VideoModel>, private val click: (Int) -> Unit) : 
        RecyclerView.Adapter<PlaylistAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tv_video_title)
            val thumb: ImageView = v.findViewById(R.id.iv_thumbnail)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_video, p, false))

        override fun onBindViewHolder(h: VH, i: Int) {
            val item = list[i]
            h.title.text = item.title
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    contentResolver.loadThumbnail(item.uri, Size(200, 120), null)
                else
                    MediaStore.Video.Thumbnails.getThumbnail(contentResolver, ContentUris.parseId(item.uri), 
                        MediaStore.Video.Thumbnails.MINI_KIND, null)
                h.thumb.setImageBitmap(bmp)
            } catch (e: Exception) {
                h.thumb.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            h.itemView.setOnClickListener { click(i) }
        }
        override fun getItemCount() = list.size
    }
}

