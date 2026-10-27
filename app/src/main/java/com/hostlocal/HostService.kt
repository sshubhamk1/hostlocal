package com.hostlocal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Foreground service owning FTP + mDNS. Both are bound to the active LAN IP
 * (hotspot, else Wi-Fi) and restarted whenever it changes or disappears.
 */
class HostService : Service() {
    private var ftp: FtpHost? = null
    private var mdns: MdnsHost? = null
    private var boundIp: String? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var watcher: ScheduledExecutorService? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())

        val folder = File(Settings(this).folderPath)
        if (!folder.isDirectory) {
            lastError = "Folder not found: ${folder.path}"
            stopSelf()
            return START_NOT_STICKY
        }
        lastError = null

        if (watcher == null) startWatcher(folder)
        running = true
        return START_STICKY
    }

    private fun startWatcher(folder: File) {
        multicastLock = getSystemService(WifiManager::class.java)
            .createMulticastLock("hostlocal-mdns").apply {
                setReferenceCounted(false)
                acquire()
            }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hostlocal:ftp").apply {
                setReferenceCounted(false)
                acquire()
            }
        watcher = Executors.newSingleThreadScheduledExecutor().also { ex ->
            ex.scheduleWithFixedDelay({ syncToHotspot(folder) }, 0, 3, TimeUnit.SECONDS)
        }
    }

    private fun syncToHotspot(folder: File) {
        val ip = Hotspot.currentAddress()
        if (ip == boundIp && (ip == null || (ftp != null && mdns != null))) return

        stopServers()
        boundIp = ip
        if (ip == null) return

        ftpError = null
        mdnsError = null
        try {
            ftp = FtpHost(folder, ip).also { it.start() }
            ftpAddress = ip
        } catch (e: Exception) {
            ftp = null
            ftpError = e.message ?: e.javaClass.simpleName
        }
        try {
            mdns = MdnsHost(ip).also { it.start() }
            mdnsAddress = ip
        } catch (e: Exception) {
            mdns = null
            mdnsError = e.message ?: e.javaClass.simpleName
        }
    }

    private fun stopServers() {
        runCatching { ftp?.stop() }
        runCatching { mdns?.stop() }
        ftp = null
        mdns = null
        ftpAddress = null
        mdnsAddress = null
    }

    override fun onDestroy() {
        watcher?.shutdownNow()
        watcher = null
        val ex = Executors.newSingleThreadExecutor()
        ex.execute { stopServers(); ex.shutdown() }
        boundIp = null
        multicastLock?.release()
        multicastLock = null
        wakeLock?.release()
        wakeLock = null
        running = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "HostLocal", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HostLocal running")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.hostlocal.STOP"
        private const val CHANNEL_ID = "hostlocal"
        private const val NOTIFICATION_ID = 1

        @Volatile var running = false
            private set

        /** LAN IP FTP / mDNS are currently bound to; null if not serving. */
        @Volatile var ftpAddress: String? = null
            private set
        @Volatile var mdnsAddress: String? = null
            private set

        @Volatile var ftpError: String? = null
            private set
        @Volatile var mdnsError: String? = null
            private set
        @Volatile var lastError: String? = null
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, HostService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, HostService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
