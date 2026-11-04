package app.bheem.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import app.bheem.core.device.DeviceSensor
import app.bheem.core.device.DeviceSnapshot
import app.bheem.core.device.Network
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reads the phone's own state when asked. Nothing here makes a network call: the connection check only reads which kind of connection
 * the phone has. A value the phone will not give comes back null and Bheem says it cannot read it.
 */
class AndroidDeviceSensor(private val context: Context) : DeviceSensor {
    override fun read(): DeviceSnapshot {
        val zone = ZoneId.systemDefault()
        val battery = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val storage = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        return DeviceSnapshot(
            now = LocalDateTime.now(zone),
            zone = zone,
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = if (battery == null) null else plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            network = network(),
            doNotDisturb = doNotDisturb(),
            storageFreeGb = storage?.let { (it.availableBytes / GB).toInt() },
            storageTotalGb = storage?.let { (it.totalBytes / GB).toInt() },
            nextAlarm = runCatching {
                context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
            }.getOrNull(),
        )
    }

    private fun network(): Network = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        when {
            capabilities == null -> Network.OFFLINE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Network.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Network.MOBILE
            else -> Network.UNKNOWN
        }
    }.getOrDefault(Network.UNKNOWN)

    private fun doNotDisturb(): Boolean? = runCatching {
        when (context.getSystemService(NotificationManager::class.java).currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> false
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> null
            else -> true
        }
    }.getOrNull()

    private companion object {
        const val GB = 1_000_000_000L
    }
}
