package thwiply.elopenmike.com.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Deterministic replacement for the injected [Clock]; tests move time explicitly. */
class MutableClock(private var epochMillis: Long) : Clock() {
    fun advanceTo(newEpochMillis: Long) {
        epochMillis = newEpochMillis
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(epochMillis)

    override fun millis(): Long = epochMillis
}
