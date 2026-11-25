package xx.gradus

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/*
 * The forecast as Open-Meteo returns it, in the place's own local time
 * (the request asks for timezone=auto). A value the service sent as null is
 * NaN here, and every formatter writes NaN as a dash — the service leaves
 * gaps, for instance precipitation probability past the models' range.
 */

data class Current(
    val time: LocalDateTime,
    val temp: Double,
    val feelsLike: Double,
    val humidity: Double,
    val precipitation: Double,
    val code: Int,
    val windSpeed: Double,
    val windDirection: Double,
    val windGusts: Double,
    val isDay: Boolean,
    /** Reduced to sea level, hPa: what a forecast in hPa means. */
    val pressureSeaLevel: Double,
    /** At the place's own height, hPa: what a forecast in mmHg means. */
    val pressureSurface: Double,
)

data class Hour(
    val time: LocalDateTime,
    val temp: Double,
    val humidity: Double,
    val precipitationChance: Double,
    val code: Int,
    val windSpeed: Double,
    val windDirection: Double,
    val windGusts: Double,
    val isDay: Boolean,
)

data class Day(
    val date: LocalDate,
    val code: Int,
    val tempMax: Double,
    val tempMin: Double,
    val precipitationSum: Double,
    /** Null where the sun does not rise or set that day. */
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val daylightSeconds: Double,
    val uvIndexMax: Double,
    val humidityMean: Double,
)

data class Forecast(val current: Current, val hours: List<Hour>, val days: List<Day>) {

    /** The next [count] hours, starting with the one the current reading falls in. */
    fun hoursAhead(count: Int = HOURS_AHEAD): List<Hour> {
        val from = current.time.truncatedTo(ChronoUnit.HOURS)
        return hours.filter { !it.time.isBefore(from) }.take(count)
    }

    fun day(date: LocalDate): Day? = days.firstOrNull { it.date == date }
}

/** How far the hourly list looks ahead. */
const val HOURS_AHEAD = 48

/** Days in the week list, today included. */
const val FORECAST_DAYS = 7

/** No weather code: the service sent null. Shown as an unknown sky. */
const val NO_CODE = -1

/** Reads a forecast response; throws [org.json.JSONException] on a malformed one. */
fun parseForecast(text: String): Forecast {
    val root = JSONObject(text)

    val c = root.getJSONObject("current")
    val current = Current(
        time = LocalDateTime.parse(c.getString("time")),
        temp = c.num("temperature_2m"),
        feelsLike = c.num("apparent_temperature"),
        humidity = c.num("relative_humidity_2m"),
        precipitation = c.num("precipitation"),
        code = c.optInt("weather_code", NO_CODE),
        windSpeed = c.num("wind_speed_10m"),
        windDirection = c.num("wind_direction_10m"),
        windGusts = c.num("wind_gusts_10m"),
        isDay = c.optInt("is_day", 1) == 1,
        pressureSeaLevel = c.num("pressure_msl"),
        pressureSurface = c.num("surface_pressure"),
    )

    val h = root.getJSONObject("hourly")
    val hourTimes = h.getJSONArray("time")
    val hours = (0 until hourTimes.length()).map { i ->
        Hour(
            time = LocalDateTime.parse(hourTimes.getString(i)),
            temp = h.numAt("temperature_2m", i),
            humidity = h.numAt("relative_humidity_2m", i),
            precipitationChance = h.numAt("precipitation_probability", i),
            code = h.codeAt("weather_code", i),
            windSpeed = h.numAt("wind_speed_10m", i),
            windDirection = h.numAt("wind_direction_10m", i),
            windGusts = h.numAt("wind_gusts_10m", i),
            isDay = h.optJSONArray("is_day")?.optInt(i, 1) != 0,
        )
    }

    val d = root.getJSONObject("daily")
    val dayDates = d.getJSONArray("time")
    val days = (0 until dayDates.length()).map { i ->
        Day(
            date = LocalDate.parse(dayDates.getString(i)),
            code = d.codeAt("weather_code", i),
            tempMax = d.numAt("temperature_2m_max", i),
            tempMin = d.numAt("temperature_2m_min", i),
            precipitationSum = d.numAt("precipitation_sum", i),
            sunrise = d.timeAt("sunrise", i),
            sunset = d.timeAt("sunset", i),
            daylightSeconds = d.numAt("daylight_duration", i),
            uvIndexMax = d.numAt("uv_index_max", i),
            humidityMean = d.numAt("relative_humidity_2m_mean", i),
        )
    }

    return Forecast(current, hours, days)
}

// org.json's optDouble already turns a null or a missing key into NaN.
private fun JSONObject.num(key: String): Double = optDouble(key, Double.NaN)

private fun JSONObject.numAt(key: String, i: Int): Double =
    optJSONArray(key)?.optDouble(i, Double.NaN) ?: Double.NaN

private fun JSONObject.codeAt(key: String, i: Int): Int {
    val array: JSONArray = optJSONArray(key) ?: return NO_CODE
    return if (array.isNull(i)) NO_CODE else array.optInt(i, NO_CODE)
}

private fun JSONObject.timeAt(key: String, i: Int): LocalDateTime? {
    val array = optJSONArray(key) ?: return null
    if (array.isNull(i)) return null
    return array.optString(i).takeIf { it.isNotEmpty() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
}
