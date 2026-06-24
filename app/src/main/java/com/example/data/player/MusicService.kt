package com.example.data.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

class MusicService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val player = MusicPlayerManager.instance

        if (player != null && action != null) {
            when (action) {
                ACTION_PLAY, ACTION_PAUSE -> player.togglePlayPause()
                ACTION_NEXT -> player.skipToNext()
                ACTION_PREVIOUS -> player.skipToPrevious()
                ACTION_STOP -> {
                    if (player.isPlaying.value) {
                        player.togglePlayPause()
                    }
                    stopSelf()
                }
            }
        }

        showNotification()
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Horizon Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reprodução em segundo plano do Horizon"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        val player = MusicPlayerManager.instance ?: return
        val track = player.currentTrack.value ?: return
        val isPlaying = player.isPlaying.value

        // PendingIntent para abrir a MainActivity ao tocar na notificação
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        // PendingIntents para ações
        val prevPendingIntent = PendingIntent.getService(
            this, 1, Intent(this, MusicService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, Intent(this, MusicService::class.java).apply { action = playPauseAction },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextPendingIntent = PendingIntent.getService(
            this, 3, Intent(this, MusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        // Configurar botões físicos/visuais na notificação
        val prevActionObj = Notification.Action.Builder(
            android.R.drawable.ic_media_previous, "Anterior", prevPendingIntent
        ).build()
        val playPauseActionObj = Notification.Action.Builder(
            playPauseIcon, if (isPlaying) "Pausar" else "Tocar", playPausePendingIntent
        ).build()
        val nextActionObj = Notification.Action.Builder(
            android.R.drawable.ic_media_next, "Próximo", nextPendingIntent
        ).build()

        // Estilo de Mídia integrado do sistema Android
        val mediaStyle = Notification.MediaStyle()
        player.mediaSessionToken?.let { token ->
            mediaStyle.setMediaSession(token as android.media.session.MediaSession.Token)
        }
        mediaStyle.setShowActionsInCompactView(0, 1, 2)

        builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album.ifEmpty { "Horizon Music" })
            .setContentIntent(openAppIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            .addAction(prevActionObj)
            .addAction(playPauseActionObj)
            .addAction(nextActionObj)
            .setOngoing(isPlaying)

        val notification = builder.build()

        if (isPlaying) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Erro ao iniciar foreground service", e)
                // Fallback para notificação normal se falhar por restrições
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, notification)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                stopForeground(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, notification)
        }
    }
}

// Ações globais do player de segundo plano
const val ACTION_PLAY = "com.example.horizonmusic.PLAY"
const val ACTION_PAUSE = "com.example.horizonmusic.PAUSE"
const val ACTION_NEXT = "com.example.horizonmusic.NEXT"
const val ACTION_PREVIOUS = "com.example.horizonmusic.PREVIOUS"
const val ACTION_STOP = "com.example.horizonmusic.STOP"
const val CHANNEL_ID = "horizon_music_channel"
const val NOTIFICATION_ID = 883
