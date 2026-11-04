package app.bheem.core.device

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Network { WIFI, MOBILE, OFFLINE, UNKNOWN }

/** What the phone knows about itself at one moment. Taken when a question is asked, never cached. A field is null when the phone would not say. */
data class DeviceSnapshot(
    val now: LocalDateTime,
    val zone: ZoneId,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val network: Network = Network.UNKNOWN,
    val doNotDisturb: Boolean? = null,
    val storageFreeGb: Int? = null,
    val storageTotalGb: Int? = null,
    val nextAlarm: LocalDateTime? = null,
) {
    /** One short line for the start of a model prompt, so the model knows the date and the time instead of guessing. */
    fun nowLine(): String = buildString {
        append("Now: ").append(now.format(DATE_SHORT)).append(", ").append(now.format(CLOCK)).append(' ')
            .append(now.atZone(zone).format(ZONE_NAME)).append('.')
        if (batteryPercent != null) {
            append(" Battery ").append(batteryPercent).append('%')
            when (charging) {
                true -> append(", charging")
                false -> append(", not charging")
                null -> Unit
            }
            append('.')
        }
        when (network) {
            Network.WIFI -> append(" Wi-Fi.")
            Network.MOBILE -> append(" Mobile data.")
            Network.OFFLINE -> append(" Offline.")
            Network.UNKNOWN -> Unit
        }
    }

    private companion object {
        val DATE_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        val ZONE_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH)
    }
}

/** Reads the phone. The app implements it; tests give a fixed snapshot. */
fun interface DeviceSensor {
    fun read(): DeviceSnapshot
}
