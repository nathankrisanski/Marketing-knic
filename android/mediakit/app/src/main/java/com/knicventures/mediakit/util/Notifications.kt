package com.knicventures.mediakit.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.knicventures.mediakit.MainActivity
import com.knicventures.mediakit.R

object Notifications {

    const val CHANNEL_PROGRESS = "mediakit_progress"
    const val CHANNEL_DONE = "mediakit_done"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "Jobs in progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                "Finished jobs",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun progress(
        context: Context,
        title: String,
        text: String,
        percent: Int?,
    ): android.app.Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_media)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .apply {
                if (percent == null) {
                    setProgress(0, 0, true)
                } else {
                    setProgress(100, percent.coerceIn(0, 100), false)
                }
            }
            .build()
    }

    fun finished(context: Context, id: Int, title: String, text: String) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_media)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        context.getSystemService<NotificationManager>()?.notify(id, notification)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
