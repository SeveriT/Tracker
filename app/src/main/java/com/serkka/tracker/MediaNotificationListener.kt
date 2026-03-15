package com.serkka.tracker

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Handler
import android.os.Looper
import android.content.Intent

data class SongInfo(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val packageName: String? = null,
    val position: Long?,
    val duration: Long?,
    val albumArt: Bitmap? = null
)

class MediaNotificationListener : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private val mediaRepository = MediaRepository.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var isUpdatingPosition = false

    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            if (isUpdatingPosition) {
                updateSongInfo()
                handler.postDelayed(this, 1000) // Update every second
            }
        }
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateSongInfo()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateSongInfo()
        }
    }

    private var activeController: MediaController? = null

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaRepository.setNextTrackAction { skipToNext() }
        mediaRepository.setPreviousTrackAction { skipToPrevious() }
        mediaRepository.setTogglePlayPauseAction { togglePlayPause() }
        mediaRepository.setOpenAppAction { openApp() }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateController()
    }

    private fun updateController() {
        val controllers = mediaSessionManager?.getActiveSessions(ComponentName(this, MediaNotificationListener::class.java))
        val newController = controllers?.firstOrNull()

        // Always register callback to the latest controller if it exists
        if (activeController != newController) {
            activeController?.unregisterCallback(callback)
            activeController = newController
            activeController?.registerCallback(callback)
        }
        updateSongInfo()
    }

    private fun updateSongInfo() {
        val metadata = activeController?.metadata
        val playbackState = activeController?.playbackState
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val info = SongInfo(
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = activeController?.packageName,
            position = playbackState?.position,
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION),
            albumArt = albumArt
        )
        mediaRepository.updateSong(info)

        if (isPlaying && !isUpdatingPosition) {
            isUpdatingPosition = true
            handler.postDelayed(positionUpdateRunnable, 1000)
        } else if (!isPlaying && isUpdatingPosition) {
            isUpdatingPosition = false
            handler.removeCallbacks(positionUpdateRunnable)
        }
    }


    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateController()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateController()
    }

    private fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    private fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    private fun togglePlayPause() {
        val playbackState = activeController?.playbackState
        if (playbackState?.state == PlaybackState.STATE_PLAYING) {
            activeController?.transportControls?.pause()
        } else {
            activeController?.transportControls?.play()
        }
    }

    private fun openApp() {
        if (activeController?.packageName == "com.spotify.music") {
            try {
                val intent = Intent().apply {
                    component = ComponentName("com.spotify.music", "androidx.compose.ui.tooling.PreviewActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback to default behavior if special activity fails
            }
        }
        
        activeController?.sessionActivity?.send() ?: run {
            activeController?.packageName?.let { pkg ->
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        isUpdatingPosition = false
        handler.removeCallbacks(positionUpdateRunnable)
        activeController?.unregisterCallback(callback)
    }
}
