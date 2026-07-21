package com.tekutekunikki.mizunomi

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object HydrationReminderNotifications {
    private const val ChannelId = "hydration_reminders"
    private const val ChannelName = "Hydration reminders"
    private const val NotificationIdBase = 2000

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            ChannelName,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Reminders when today's hydration pace is behind."
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showHydrationReminder(context: Context, checkHour: Int) {
        if (!canNotify(context)) {
            return
        }

        createChannel(context)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = "水分補給のタイミングです"
        val body = "今日のペースより少し遅れています。まずは一杯飲みませんか？"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ChannelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NotificationIdBase + checkHour, notification)
    }
}
