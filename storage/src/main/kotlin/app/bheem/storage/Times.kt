package app.bheem.storage

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object Times {
    fun toEpochMillis(at: LocalDateTime, zone: ZoneId): Long = at.atZone(zone).toInstant().toEpochMilli()

    fun fromEpoch(epochMillis: Long, zoneId: String): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of(zoneId))
}
