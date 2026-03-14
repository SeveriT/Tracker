package com.serkka.tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class TimerForegroundService : Service() {

    private val handler   = Handler(Looper.getMainLooper())
    private val channelId = "timer_channel"
    private val notifId   = 42

    private var elapsedSeconds = 0L
    private var isRunning      = false

    private val ticker = object : Runnable {
        override fun run() {
            if (isRunning) {
                elapsedSeconds++
                // We still update to keep our internal state and notification text sync'd
                updateNotification()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                elapsedSeconds = intent.getLongExtra(EXTRA_ELAPSED, 0L)
                isRunning = true
                createChannel()
                startForeground(notifId, buildNotification())
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, 1000)
            }
            ACTION_PAUSE -> {
                isRunning = false
                handler.removeCallbacks(ticker)
                updateNotification()
            }
            ACTION_RESUME -> {
                elapsedSeconds = intent.getLongExtra(EXTRA_ELAPSED, elapsedSeconds)
                isRunning = true
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, 1000)
                updateNotification()
            }
            ACTION_STOP -> {
                isRunning = false
                handler.removeCallbacks(ticker)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val timeString = formatElapsed(elapsedSeconds)
        // This base time is used by the Chronometer to show live ticking
        val baseTime = System.currentTimeMillis() - (elapsedSeconds * 1000)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Workout: $timeString")
            .setContentText(if (isRunning) "Timer is running" else "Paused")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            // Ticker text shows up briefly in the status bar on older Android versions
            .setTicker("Workout: $timeString") 
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // setUsesChronometer(true) makes the notification show a live timer
            .setUsesChronometer(isRunning)
            .setWhen(baseTime)
            .setShowWhen(isRunning)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Use LOW to stay silent but visible
            .setContentIntent(openIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notifId, buildNotification())
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            channelId,
            "Workout Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply { 
            description = "Shows elapsed workout time"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
    }

    companion object {
        const val ACTION_START  = "com.serkka.tracker.TIMER_START"
        const val ACTION_PAUSE  = "com.serkka.tracker.TIMER_PAUSE"
        const val ACTION_RESUME = "com.serkka.tracker.TIMER_RESUME"
        const val ACTION_STOP   = "com.serkka.tracker.TIMER_STOP"
        const val EXTRA_ELAPSED = "elapsed"
    }
}
