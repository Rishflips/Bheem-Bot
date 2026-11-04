package app.bheem.app

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import app.bheem.core.device.DeviceControls

/** The phone's own torch and volume. Neither needs a permission and neither touches the network. */
class AndroidDeviceControls(private val context: Context) : DeviceControls {
    override fun torch(on: Boolean): Boolean = runCatching {
        val manager = context.getSystemService(CameraManager::class.java)
        val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return false
        manager.setTorchMode(id, on)
        true
    }.getOrDefault(false)

    override fun volume(change: String): Boolean = runCatching {
        val audio = context.getSystemService(AudioManager::class.java)
        val direction = when (change) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_MUTE
            else -> return false
        }
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        true
    }.getOrDefault(false)
}
