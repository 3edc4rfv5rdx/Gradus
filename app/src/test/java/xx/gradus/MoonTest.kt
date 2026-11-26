package xx.gradus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Reference values from MET Norway's moon service (api.met.no, sunrise 3.0) for Kyiv. */
class MoonTest {
    private val lat = 50.45
    private val lon = 30.52
    private val kyiv = ZoneOffset.ofHours(2)

    private fun assertNear(expected: String, actual: LocalDateTime?) {
        assertNotNull(actual)
        val minutes = abs(ChronoUnit.MINUTES.between(LocalDateTime.parse(expected), actual))
        assertTrue("expected $expected, got $actual", minutes <= 2)
    }

    private fun ms(local: String) = LocalDateTime.parse(local).toInstant(kyiv).toEpochMilli()

    @Test
    fun setsInTheMorningAndRisesInTheEvening() {
        val (rise, set) = moonRiseSet(LocalDate.parse("2026-11-26"), kyiv, lat, lon)
        assertNear("2026-11-26T17:14", rise)
        assertNear("2026-11-26T10:04", set)
    }

    @Test
    fun risesBeforeDawnAndSetsInTheAfternoon() {
        val (rise, set) = moonRiseSet(LocalDate.parse("2026-12-05"), kyiv, lat, lon)
        assertNear("2026-12-05T03:59", rise)
        assertNear("2026-12-05T13:31", set)
    }

    @Test
    fun elongationMatchesThePhase() {
        assertEquals(198.25, moonElongation(ms("2026-11-26T00:00")), 0.5)
        assertEquals(313.79, moonElongation(ms("2026-12-05T00:00")), 0.5)
    }

    @Test
    fun noProgressWhileTheMoonIsDown() {
        // Set at 10:04, risen again at 17:14.
        assertNull(moonProgress(ms("2026-11-26T14:00"), lat, lon))
    }

    @Test
    fun progressRunsFromRiseToSet() {
        // Risen at 17:14 on the 26th, set at 10:57 on the 27th: midway about 02:05.
        val p = moonProgress(ms("2026-11-27T02:05"), lat, lon)
        assertNotNull(p)
        assertEquals(0.5f, p!!, 0.02f)
    }

    @Test
    fun noMoonWithoutAnOffset() {
        val sample = """{"latitude": 50.45, "longitude": 30.52,
            "current": {"time": "2026-11-26T12:00"}, "hourly": {"time": []}, "daily": {"time": []}}"""
        assertNull(moonNow(parseForecast(sample)))
        val withOffset = sample.replace("{\"latitude\"", "{\"utc_offset_seconds\": 7200, \"latitude\"")
        assertNotNull(moonNow(parseForecast(withOffset)))
    }
}
