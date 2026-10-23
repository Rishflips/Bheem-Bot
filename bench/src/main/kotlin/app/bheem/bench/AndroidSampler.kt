package app.bheem.bench

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import app.bheem.eval.run.ProcStatus
import app.bheem.eval.run.ResourceSample
import app.bheem.eval.run.ResourceSampler
import java.io.File

class AndroidSampler(private val context: Context) : ResourceSampler {
    override fun sample(label: String): ResourceSample {
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val status = File("/proc/self/status").readText()
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val tempTenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return ResourceSample(
            label = label,
            pssMb = memory.totalPss / 1024L,
            peakRssMb = (ProcStatus.kb(status, "VmHWM") ?: 0L) / 1024L,
            thermalStatus = power.currentThermalStatus,
            batteryPct = if (level >= 0) level * 100 / scale else -1,
            batteryTempC = tempTenths / 10.0,
        )
    }
}
