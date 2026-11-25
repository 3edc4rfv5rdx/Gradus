package xx.gradus

import kotlin.math.roundToInt

/*
 * Numbers as the screens write them. Free of Android, so the tests can run them;
 * the words around them (units, compass letters) are string resources and are
 * joined on in the UI.
 */

/** How old a forecast may be before opening the app fetches a new one. */
const val STALE_AFTER_MS = 15 * 60 * 1000L

/** No data yet, too old, or the clock went back past the fetch. */
fun isStale(fetchedAt: Long, now: Long): Boolean =
    fetchedAt <= 0L || now < fetchedAt || now - fetchedAt >= STALE_AFTER_MS

/** The dash every missing value is written as. */
const val NO_VALUE = "—"

/** Whole degrees; null for a value the service did not send. */
fun roundedTemp(celsius: Double): Int? = if (celsius.isNaN()) null else celsius.roundToInt()

/** "+3°", "−2°", "0°": the sign spelled out, a true minus rather than a hyphen. */
fun formatTemp(celsius: Double): String {
    val t = roundedTemp(celsius) ?: return NO_VALUE
    return when {
        t > 0 -> "+$t°"
        t < 0 -> "−${-t}°"
        else -> "0°"
    }
}

enum class WindUnit(val perMs: Double) { MS(1.0), KMH(3.6) }

/**
 * Pressure units, each with the reading it is customarily given as: hPa reduced
 * to sea level, as in synoptic reports, and mmHg at the place's own height, as
 * the post-Soviet forecasts give it (about 745 rather than 760 in a city 150 m up).
 */
enum class PressureUnit { HPA, MMHG }

private const val MMHG_PER_HPA = 0.750062

/** The pressure to show in [unit], whole; null for a missing value. */
fun pressureIn(unit: PressureUnit, seaLevelHpa: Double, surfaceHpa: Double): Int? {
    val value = when (unit) {
        PressureUnit.HPA -> seaLevelHpa
        PressureUnit.MMHG -> surfaceHpa * MMHG_PER_HPA
    }
    return if (value.isNaN()) null else value.roundToInt()
}

/** What the wind's direction is shown as. */
enum class WindDirectionStyle { ARROW, LETTERS }

/** Wind speed in [unit], whole numbers; null for a missing value. */
fun windIn(unit: WindUnit, metresPerSecond: Double): Int? =
    if (metresPerSecond.isNaN()) null else (metresPerSecond * unit.perMs).roundToInt()

/**
 * The eight-point compass sector the wind blows from: 0 north, 1 north-east,
 * on clockwise to 7 north-west. -1 for a missing direction.
 */
fun compassPoint(degrees: Double): Int {
    if (degrees.isNaN()) return -1
    return Math.floorMod((degrees / 45.0).roundToInt(), 8)
}

/** A percentage, whole; null for a missing value. */
fun percent(value: Double): Int? = if (value.isNaN()) null else value.roundToInt().coerceIn(0, 100)

/** Daylight split into hours and minutes, rounded to the minute; null for a missing value. */
fun hoursMinutes(seconds: Double): Pair<Int, Int>? {
    if (seconds.isNaN() || seconds < 0) return null
    val minutes = (seconds / 60).roundToInt()
    return minutes / 60 to minutes % 60
}

/**
 * How far the sun is along today's arc, 0 at sunrise and 1 at sunset; null
 * before sunrise, after sunset, or on a day without both.
 */
fun sunProgress(nowMinute: Long, sunriseMinute: Long?, sunsetMinute: Long?): Float? {
    if (sunriseMinute == null || sunsetMinute == null || sunsetMinute <= sunriseMinute) return null
    if (nowMinute < sunriseMinute || nowMinute > sunsetMinute) return null
    return (nowMinute - sunriseMinute).toFloat() / (sunsetMinute - sunriseMinute)
}
