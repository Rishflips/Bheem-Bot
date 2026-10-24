package app.bheem.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.PowerManager
import app.bheem.models.host.Capability
import app.bheem.models.host.Governor
import app.bheem.models.host.LoadHooks
import app.bheem.models.host.ModelHost
import java.util.concurrent.Executor

class PhoneConditions(context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val battery = context.getSystemService(BatteryManager::class.java)

    fun capability(): Capability = Governor.capability(power.isPowerSaveMode, power.currentThermalStatus, battery.isCharging)

    fun watchHeat(host: ModelHost, executor: Executor): AutoCloseable {
        val listener = PowerManager.OnThermalStatusChangedListener { status -> if (Governor.shouldUnload(status)) host.trim() }
        power.addThermalStatusListener(executor, listener)
        return AutoCloseable { power.removeThermalStatusListener(listener) }
    }
}

class ForegroundLoadHooks(private val context: Context) : LoadHooks {
    override fun loadStarted() {
        ModelLoadService.loadActive = true
        try {
            context.startForegroundService(Intent(context, ModelLoadService::class.java))
        } catch (_: RuntimeException) {
            // the app is not allowed to start a foreground service from here; the load still runs while the app is in front
        }
    }

    override fun loadFinished() {
        ModelLoadService.loadActive = false
    }
}

class ModelLoadService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stopWhenDone = object : Runnable {
        override fun run() {
            if (loadActive) handler.postDelayed(this, POLL_MS) else stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Getting ready", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Bheem is getting ready")
            .setContentText("Loading the on-device model")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        handler.removeCallbacks(stopWhenDone)
        handler.post(stopWhenDone)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        timedOut()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        timedOut()
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopWhenDone)
        super.onDestroy()
    }

    private fun timedOut() {
        timeoutListener?.invoke()
        stopSelf()
    }

    companion object {
        private const val CHANNEL = "model"
        private const val NOTIFICATION_ID = 41
        private const val POLL_MS = 500L

        @Volatile
        var loadActive = false

        @Volatile
        var timeoutListener: (() -> Unit)? = null
    }
}
