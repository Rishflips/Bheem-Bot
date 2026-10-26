package app.bheem.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import app.bheem.downloader.ChecksumFailed
import app.bheem.downloader.DownloadFailed
import app.bheem.downloader.DownloadPaused
import app.bheem.downloader.DownloadPolicy
import app.bheem.downloader.NotEnoughSpace
import app.bheem.downloader.PauseReason
import app.bheem.downloader.ResumableDownloader
import app.bheem.downloader.UrlTransport
import app.bheem.models.host.FetchResult
import app.bheem.models.host.ModelDownloader
import app.bheem.models.host.ModelFile
import app.bheem.models.host.ModelFileWriter

object Distribution {
    fun downloader(context: Context): ModelDownloader? = NetworkModelDownloader(context.applicationContext)
}

private class NetworkFacts(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val battery = context.getSystemService(BatteryManager::class.java)

    fun pauseReason(cellularAllowed: Boolean): PauseReason? {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return DownloadPolicy.check(
            connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            unmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
            cellularAllowed = cellularAllowed,
            batterySaver = power.isPowerSaveMode,
            charging = battery.isCharging,
        )
    }
}

private class NetworkModelDownloader(private val context: Context) : ModelDownloader {
    override suspend fun fetch(file: ModelFile, writer: ModelFileWriter, cellularAllowed: Boolean, onProgress: (Long, Long) -> Unit): FetchResult {
        val url = file.url ?: return FetchResult.Failed("this model has no download address")
        val facts = NetworkFacts(context)
        val downloader = ResumableDownloader(UrlTransport(trace = { if (BuildConfig.DEBUG) Log.i("BheemDownload", it) }), freeSpace = { context.filesDir.usableSpace }, guard = { facts.pauseReason(cellularAllowed) })
        ModelDownloadService.begin(context)
        return try {
            downloader.download(url, file, writer, onProgress)
            FetchResult.Done
        } catch (e: DownloadPaused) {
            FetchResult.Paused(pauseText(e.reason), offerCellular = e.reason == PauseReason.NoWifi)
        } catch (e: NotEnoughSpace) {
            FetchResult.Failed("not enough free space: the download needs about ${e.needed / MB} MB and ${e.free / MB} MB is free")
        } catch (e: ChecksumFailed) {
            FetchResult.Failed("the downloaded file did not match its checksum twice, so it was deleted. Try again later")
        } catch (e: DownloadFailed) {
            FetchResult.Failed(e.message.orEmpty())
        } finally {
            ModelDownloadService.end()
        }
    }

    private fun pauseText(reason: PauseReason) = when (reason) {
        PauseReason.Offline -> "There is no connection. The download continues from where it stopped when you resume."
        PauseReason.NoWifi -> "Waiting for an unmetered Wi-Fi connection. The download is large."
        PauseReason.BatterySaver -> "Paused because battery saver is on. Plug in or turn it off, then resume."
    }

    private companion object {
        const val MB = 1_000_000L
    }
}

class ModelDownloadService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastStartId = 0
    private val stopWhenDone = object : Runnable {
        override fun run() {
            if (active) handler.postDelayed(this, POLL_MS) else stopSelf(lastStartId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.download_title))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        handler.removeCallbacks(stopWhenDone)
        handler.post(stopWhenDone)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        stopSelf(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopWhenDone)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "download"
        private const val NOTIFICATION_ID = 42
        private const val POLL_MS = 500L

        @Volatile
        private var active = false

        fun begin(context: Context) {
            active = true
            try {
                context.startForegroundService(Intent(context, ModelDownloadService::class.java))
            } catch (_: RuntimeException) {
                // not allowed to start a foreground service from here; the download runs while the app is in front
            }
        }

        fun end() {
            active = false
        }
    }
}
