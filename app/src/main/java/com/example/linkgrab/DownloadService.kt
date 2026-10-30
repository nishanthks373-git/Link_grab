package com.example.linkgrab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service whose only job is to keep the process alive (and show a
 * notification) while downloads are queued or running. The work itself happens in
 * [DownloadRepository].
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Starting downloads…", -1f),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        if (watcher == null) {
            watcher = scope.launch {
                DownloadRepository.items.collect { list ->
                    val active = list.filter { it.isActive }
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val running = active.firstOrNull { it.status == Status.RUNNING }
                        val text = "${active.size} download(s) in progress"
                        val notification = buildNotification(text, running?.progress ?: -1f)
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, progress: Float): Notification {
        val openApp = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("LinkGrab")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .apply {
                if (progress in 0f..1f) setProgress(100, (progress * 100).toInt(), false)
                else setProgress(0, 0, true)
            }
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }
    }
}
