package com.jondude1.wisp.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.jondude1.wisp.MainActivity
import com.jondude1.wisp.R
import com.jondude1.wisp.WispApp

/** Foreground service whose only job is to keep the process alive while the agent is working. */
class AgentRunService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, WispApp.CHANNEL_AGENT)
            .setSmallIcon(R.drawable.ic_stat_wisp)
            .setContentTitle("Wisp is working")
            .setContentText("Tap to see what it's doing")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        return START_NOT_STICKY
    }
}
