package xx.gradus

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/*
 * The moon, worked out on the phone: the forecast service does not send it.
 * The position is the leading terms of Meeus' lunar theory (Astronomical
 * Algorithms, ch. 47), good to about a tenth of a degree, which puts moonrise
 * and moonset within a minute or two. Free of Android, so the tests run it.
 */

/** The moon for the Now screen; times in the place's local time. */
data class MoonNow(
    /** Null on a day the moon does not rise, or does not set. */
    val rise: LocalDateTime?,
    val set: LocalDateTime?,
    /** Degrees east of the sun, 0 to 360: 0 new, 90 first quarter, 180 full, 270 last quarter. */
    val elongation: Double,
    /** How far along its way across the sky, 0 at rise and 1 at set; null while below the horizon. */
    val progress: Float?,
)

/** The moon as it is at the forecast's current reading; null without the place's coordinates or offset. */
fun moonNow(forecast: Forecast): MoonNow? {
    val lat = forecast.latitude
    val lon = forecast.longitude
    val offset = forecast.utcOffset ?: return null
    if (lat.isNaN() || lon.isNaN()) return null
    val now = forecast.current.time
    val nowMs = now.toInstant(offset).toEpochMilli()
    val (rise, set) = moonRiseSet(now.toLocalDate(), offset, lat, lon)
    return MoonNow(rise, set, moonElongation(nowMs), moonProgress(nowMs, lat, lon))
}

/** The first moonrise and the first moonset of [date], local to [offset]. */
fun moonRiseSet(date: LocalDate, offset: ZoneOffset, lat: Double, lon: Double): Pair<LocalDateTime?, LocalDateTime?> {
    val from = date.atStartOfDay().toInstant(offset).toEpochMilli()
    val events = horizonCrossings(from, from + DAY_MS, lat, lon)
    fun local(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atOffset(offset).toLocalDateTime() }
    return local(events.firstOrNull { it.rising }?.ms) to local(events.firstOrNull { !it.rising }?.ms)
}

/**
 * Between the last rise and the next set, 0 to 1; null below the horizon, and
 * when either lies further than [PASS_SEARCH_MS] away — near the poles, where
 * the moon can stay up for days.
 */
fun moonProgress(nowMs: Long, lat: Double, lon: Double): Float? {
    if (moonAltitude(nowMs, lat, lon) <= 0) return null
    val rise = horizonCrossings(nowMs - PASS_SEARCH_MS, nowMs, lat, lon).lastOrNull { it.rising }?.ms ?: return null
    val set = horizonCrossings(nowMs, nowMs + PASS_SEARCH_MS, lat, lon).firstOrNull { !it.rising }?.ms ?: return null
    return ((nowMs - rise).toDouble() / (set - rise)).toFloat().coerceIn(0f, 1f)
}

/** The moon's ecliptic longitude less the sun's, 0 to 360. */
fun moonElongation(ms: Long): Double {
    val t = centuries(ms)
    val m = rad(357.52911 + 35999.05029 * t)
    val sunLongitude = 280.46646 + 36000.76983 * t +
        (1.914602 - 0.004817 * t) * sin(m) + 0.019993 * sin(2 * m) + 0.000289 * sin(3 * m)
    return norm(moonEcliptic(t).longitude - sunLongitude)
}

/**
 * Degrees the moon's upper limb stands above the horizon as it is seen —
 * parallax, the moon's half-width and refraction counted — so it is zero at
 * the moment of rise and set.
 */
fun moonAltitude(ms: Long, lat: Double, lon: Double): Double {
    val jd = julianDay(ms)
    val t = (jd - J2000) / 36525
    val moon = moonEcliptic(t)
    val l = rad(moon.longitude)
    val b = rad(moon.latitude)
    val eps = rad(23.439291 - 0.0130042 * t)
    val ra = atan2(sin(l) * cos(eps) - tan(b) * sin(eps), cos(l))
    val dec = asin(sin(b) * cos(eps) + cos(b) * sin(eps) * sin(l))
    val siderealTime = rad(280.46061837 + 360.98564736629 * (jd - J2000) + lon)
    val phi = rad(lat)
    val alt = asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(siderealTime - ra))
    val parallax = asin(EARTH_RADIUS_KM / moon.distanceKm)
    val semiDiameter = asin(MOON_RADIUS_KM / moon.distanceKm)
    return deg(alt - parallax * cos(alt) + semiDiameter) + REFRACTION_DEG
}

private class Crossing(val ms: Long, val rising: Boolean)

/**
 * Every rise and set in [from, to): the altitude sampled every [SAMPLE_MS], a
 * change of sign narrowed down by halving. A moon that grazes the horizon and
 * goes back within one sample is missed; it is up for minutes at most.
 */
private fun horizonCrossings(from: Long, to: Long, lat: Double, lon: Double): List<Crossing> {
    val result = ArrayList<Crossing>(2)
    var a = from
    var altA = moonAltitude(a, lat, lon)
    while (a < to) {
        val b = minOf(a + SAMPLE_MS, to)
        val altB = moonAltitude(b, lat, lon)
        if ((altA > 0) != (altB > 0)) {
            var lo = a
            var hi = b
            while (hi - lo > BISECT_MS) {
                val mid = (lo + hi) / 2
                if ((moonAltitude(mid, lat, lon) > 0) == (altA > 0)) lo = mid else hi = mid
            }
            result += Crossing(hi, rising = altB > 0)
        }
        a = b
        altA = altB
    }
    return result
}

private class Ecliptic(val longitude: Double, val latitude: Double, val distanceKm: Double)

/** Meeus tables 47.A and 47.B, the terms above about 0.03° and 100 km. */
private fun moonEcliptic(t: Double): Ecliptic {
    val lp = 218.3164477 + 481267.88123421 * t
    val d = rad(297.8501921 + 445267.1114034 * t)
    val m = rad(357.5291092 + 35999.0502909 * t)
    val mp = rad(134.9633964 + 477198.8675055 * t)
    val f = rad(93.2720950 + 483202.0175233 * t)
    val longitude = lp +
        6.288774 * sin(mp) +
        1.274027 * sin(2 * d - mp) +
        0.658314 * sin(2 * d) +
        0.213618 * sin(2 * mp) -
        0.185116 * sin(m) -
        0.114332 * sin(2 * f) +
        0.058793 * sin(2 * d - 2 * mp) +
        0.057066 * sin(2 * d - m - mp) +
        0.053322 * sin(2 * d + mp) +
        0.045758 * sin(2 * d - m) -
        0.040923 * sin(m - mp) -
        0.034720 * sin(d) -
        0.030383 * sin(m + mp)
    val latitude =
        5.128122 * sin(f) +
            0.280602 * sin(mp + f) +
            0.277693 * sin(mp - f) +
            0.173237 * sin(2 * d - f) +
            0.055413 * sin(2 * d - mp + f) +
            0.046271 * sin(2 * d - mp - f) +
            0.032573 * sin(2 * d + f) +
            0.017198 * sin(2 * mp + f)
    val distance = 385000.56 -
        20905.355 * cos(mp) -
        3699.111 * cos(2 * d - mp) -
        2955.968 * cos(2 * d) -
        569.925 * cos(2 * mp) +
        48.888 * cos(m) +
        246.158 * cos(2 * d - 2 * mp) -
        152.138 * cos(2 * d - m - mp) -
        170.733 * cos(2 * d + mp) -
        204.586 * cos(2 * d - m) -
        129.620 * cos(m - mp) +
        108.743 * cos(d) +
        104.755 * cos(m + mp)
    return Ecliptic(norm(longitude), latitude, distance)
}

private const val J2000 = 2451545.0
private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val SAMPLE_MS = 10 * 60 * 1000L
private const val BISECT_MS = 1000L
/** Longer than any moon's pass across the sky outside the polar regions. */
private const val PASS_SEARCH_MS = 30 * 60 * 60 * 1000L
private const val EARTH_RADIUS_KM = 6378.14
private const val MOON_RADIUS_KM = 1737.4
/** The standard refraction at the horizon, 34′. */
private const val REFRACTION_DEG = 34.0 / 60

private fun julianDay(ms: Long): Double = ms / 86_400_000.0 + 2440587.5
private fun centuries(ms: Long): Double = (julianDay(ms) - J2000) / 36525
private fun rad(degrees: Double): Double = Math.toRadians(degrees)
private fun deg(radians: Double): Double = Math.toDegrees(radians)
private fun norm(degrees: Double): Double = ((degrees % 360) + 360) % 360
