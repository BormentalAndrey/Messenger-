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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.kakdela.p2p.R

data class VideoModel(
    val title: String,
    val uri: android.net.Uri
)

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

    private val handler = Handler(Looper.getMainLooper())
    private val playlist = mutableListOf<VideoModel>()

    private var isFullscreen = false
    private var isUserSeeking = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) loadVideos()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        bindViews()
        initPlayer()
        setupControls()
        checkPermission()
    }

    private fun bindViews() {
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
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalTime.text = format(player.duration)
                    seekBar.max = player.duration.toInt()
                    startProgressUpdater()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(
                    if (isPlaying)
                        android.R.drawable.ic_media_pause
                    else
                        android.R.drawable.ic_media_play
                )
            }
        })
    }

    private fun setupControls() {

        btnPlayPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }

        btnNext.setOnClickListener {
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        }

        btnPrev.setOnClickListener {
            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
        }

        btnFullscreen.setOnClickListener { toggleFullscreen() }
        btnPlaylist.setOnClickListener { showPlaylist() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                player.seekTo(sb.progress.toLong())
                isUserSeeking = false
            }

            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) currentTime.text = format(progress.toLong())
            }
        })
    }

    private fun startProgressUpdater() {
        handler.post(object : Runnable {
            override fun run() {
                if (player.isPlaying && !isUserSeeking) {
                    seekBar.progress = player.currentPosition.toInt()
                    currentTime.text = format(player.currentPosition)
                }
                handler.postDelayed(this, 500)
            }
        })
    }

    private fun loadVideos() {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(
            uri,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME),
            null,
            null,
            null
        )

        playlist.clear()
        player.clearMediaItems()

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol)
                val videoUri = ContentUris.withAppendedId(uri, id)

                playlist.add(VideoModel(name, videoUri))
                player.addMediaItem(MediaItem.fromUri(videoUri))
            }
        }

        if (playlist.isNotEmpty()) {
            videoTitle.text = playlist[0].title
            player.prepare()
        }
    }

    private fun showPlaylist() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_playlist, null)

        val rv = view.findViewById<RecyclerView>(R.id.rv_playlist)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = PlaylistAdapter(playlist) {
            player.seekTo(it, 0)
            player.play()
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        requestedOrientation =
            if (isFullscreen)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (isFullscreen)
                window.insetsController?.hide(
                    WindowInsets.Type.systemBars()
                )
            else
                window.insetsController?.show(
                    WindowInsets.Type.systemBars()
                )
        }
    }

    private fun checkPermission() {
        val perm =
            if (Build.VERSION.SDK_INT >= 33)
                Manifest.permission.READ_MEDIA_VIDEO
            else
                Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadVideos()
        } else {
            permissionLauncher.launch(perm)
        }
    }

    private fun format(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / 60000)
        return String.format("%02d:%02d", m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    inner class PlaylistAdapter(
        private val list: List<VideoModel>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PlaylistAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tv_video_title)
            val thumb: ImageView = v.findViewById(R.id.iv_thumbnail)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_video, p, false))

        override fun onBindViewHolder(h: VH, pos: Int) {
            h.title.text = list[pos].title
            h.itemView.setOnClickListener { onClick(pos) }
        }

        override fun getItemCount() = list.size
    }
}
