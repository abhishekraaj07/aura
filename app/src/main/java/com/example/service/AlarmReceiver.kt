package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("ALARM_LABEL") ?: "Alarm"
        val id = intent.getLongExtra("ALARM_ID", 1001L).toInt()

        NotificationHelper.showNotification(
            context = context,
            channelId = NotificationHelper.CHANNEL_ALARMS,
            notificationId = id,
            title = "Alarm: $label",
            content = "Time to wake up!"
        )
    }
}
