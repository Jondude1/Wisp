package com.jondude1.wisp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.jondude1.wisp.agent.AgentController

class WispApp : Application() {
    lateinit var controller: AgentController
        private set

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_AGENT, "Agent tasks", NotificationManager.IMPORTANCE_LOW)
        )
        controller = AgentController(this, Settings(this))
    }

    companion object {
        const val CHANNEL_AGENT = "agent"
    }
}
