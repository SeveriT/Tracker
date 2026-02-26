package com.serkka.tracker

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SongInfo(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false
)

class MediaNotificationListener : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateSongInfo()
        }

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            updateSongInfo()
        }
    }

    private var activeController: MediaController? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateController()
    }

    private fun updateController() {
        val controllers = mediaSessionManager?.getActiveSessions(ComponentName(this, MediaNotificationListener::class.java))
        val newController = controllers?.firstOrNull()

        if (activeController != newController) {
            activeController?.unregisterCallback(callback)
            activeController = newController
            activeController?.registerCallback(callback)
            updateSongInfo()
        }
    }

    private fun updateSongInfo() {
        val metadata = activeController?.metadata
        val playbackState = activeController?.playbackState
        
        val info = SongInfo(
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            isPlaying = playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        )
        _currentSong.value = info
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        updateController()
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        updateController()
    }

    fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    companion object {
        private var instance: MediaNotificationListener? = null
        private val _currentSong = MutableStateFlow(SongInfo())
        val currentSong = _currentSong.asStateFlow()

        fun nextTrack() {
            instance?.skipToNext()
        }
    }
}
