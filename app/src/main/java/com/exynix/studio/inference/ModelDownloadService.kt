package com.exynix.studio.inference

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ModelDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "exynix_download"
        const val NOTIF_ID = 1001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ExynNix")
            .setContentText("Downloading model...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
        startForeground(NOTIF_ID, notif)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
