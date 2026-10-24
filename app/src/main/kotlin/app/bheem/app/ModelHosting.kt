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

    fun watchHeat(host: ModelHost, executor: Executor) {
        power.addThermalStatusListener(executor) { status -> if (Governor.shouldUnload(status)) host.trim() }
    }
}

class ForegroundLoadHooks(private val context: Context) : LoadHooks {
    override fun loadStarted() {
        try {
            context.startForegroundService(Intent(context, ModelLoadService::class.java))
        } catch (_: RuntimeException) {
            // the app is not allowed to start a foreground service from here; the load still runs while the app is in front
        }
    }

    override fun loadFinished() {
        context.stopService(Intent(context, ModelLoadService::class.java))
    }
}

class ModelLoadService : Service() {
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
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    private companion object {
        const val CHANNEL = "model"
        const val NOTIFICATION_ID = 41
    }
}
