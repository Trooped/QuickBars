package dev.trooped.tvquickbars.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.background.BackgroundHaConnectionManager
import dev.trooped.tvquickbars.background.HAStateStore
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


/**
 * A [LifecycleService] responsible for managing the background connection to Home Assistant.
 *
 * This service performs the following key functions:
 * - Starts and manages the [BackgroundHaConnectionManager] to maintain a persistent connection.
 * - Displays a foreground notification to inform the user about the connection status.
 * - Monitors the health of the [BackgroundHaConnectionManager] and restarts it if it stops unexpectedly
 *   or if there hasn't been a successful connection for a prolonged period.
 * - Updates the foreground notification text based on the connection state changes from [HAStateStore].
 * - Ensures the service is sticky, meaning it will be restarted by the system if killed.
 * - Handles proper cleanup and stopping of the [BackgroundHaConnectionManager] when the service is destroyed.
 */
class HAConnectionService : LifecycleService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val channelId = "haquickbars_bg"
    private val notifId = 42
    private val TAG = "HAConnection"

    private val serviceMonitorJob = scope.launch {
        while (isActive) {
            delay(15 * 60 * 1000) // Every 15 minutes

            // Check if BackgroundHaConnectionManager is still running
            if (!BackgroundHaConnectionManager.isRunning()) {
                Log.w(TAG, "Background manager stopped unexpectedly, restarting")
                BackgroundHaConnectionManager.start(applicationContext)
            }

            // Check last successful connection time
            val lastConnection = BackgroundHaConnectionManager.getLastSuccessfulConnectionTime()
            if (lastConnection > 0 && System.currentTimeMillis() - lastConnection > 60 * 60 * 1000) {
                Log.w(TAG, "No successful connection in over an hour, restarting manager")
                BackgroundHaConnectionManager.stop()
                delay(1000)
                BackgroundHaConnectionManager.start(applicationContext)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()
        makeChannel()


        val n = buildNotification("Connecting…")
        // Use the types variant to comply with Android 14+ FGS type checks
        ServiceCompat.startForeground(
            this, notifId, n,
            /*foregroundServiceType=*/android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        HAStateStore.connection.onEach { state ->
            val text = when (state) {
                is dev.trooped.tvquickbars.ha.ConnectionState.Connected -> "Connected"
                is dev.trooped.tvquickbars.ha.ConnectionState.Connecting -> "Connecting…"
                is dev.trooped.tvquickbars.ha.ConnectionState.Error -> "Error – ${state.reason}"
                is dev.trooped.tvquickbars.ha.ConnectionState.Disconnected -> "Disconnected"
            }
            val updated = buildNotification(text)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notifId, updated)
        }.launchIn(scope)

    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        Log.d("HAConnection", "Service starting, BackgroundManager running: ${BackgroundHaConnectionManager.isRunning()}")
        super.onStartCommand(intent, flags, startId)
        BackgroundHaConnectionManager.start(this)
        // Stay up – if killed, system restarts us
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("HAConnection", "Service being destroyed, shutting down background connection")

        serviceMonitorJob.cancel()

        super.onDestroy()
        BackgroundHaConnectionManager.stop()
    }

    private fun makeChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(channelId, "HA QuickBars", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.icon_svg)
            .setContentTitle("QuickBars")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}