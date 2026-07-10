package com.origin.ffmpeg

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class OriginApplication : Application() {

    companion object {
        const val CHANNEL_ID = "ffmpeg_processing"
        lateinit var instance: OriginApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "媒体文件处理进度通知"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
