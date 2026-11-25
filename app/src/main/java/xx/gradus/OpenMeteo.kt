package xx.gradus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Open-Meteo: forecast and place search, free and without a key. The data is
 * CC BY 4.0, credited in the About dialog.
 */
object OpenMeteo {
    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val SEARCH = "https://geocoding-api.open-meteo.com/v1/search"
    private const val TIMEOUT_MS = 15_000
    private const val SEARCH_RESULTS = 10

    private const val CURRENT_FIELDS = "temperature_2m,apparent_temperature,relative_humidity_2m," +
        "precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day," +
        "pressure_msl,surface_pressure"
    private const val HOURLY_FIELDS = "temperature_2m,relative_humidity_2m,precipitation," +
        "weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day"
    private const val DAILY_FIELDS = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
        "sunrise,sunset,daylight_duration,uv_index_max,relative_humidity_2m_mean"

    /** Wind always comes in m/s; the unit the user picked is applied when it is shown. */
    fun forecastUrl(latitude: Double, longitude: Double): String =
        "$FORECAST?latitude=${coord(latitude)}&longitude=${coord(longitude)}" +
            "&current=$CURRENT_FIELDS&hourly=$HOURLY_FIELDS&daily=$DAILY_FIELDS" +
            "&wind_speed_unit=ms&timezone=auto&forecast_days=$FORECAST_DAYS"

    fun searchUrl(query: String, language: String): String =
        "$SEARCH?name=${URLEncoder.encode(query.trim(), "UTF-8")}" +
            "&count=$SEARCH_RESULTS&language=$language&format=json"

    suspend fun forecast(place: Place): String = get(forecastUrl(place.latitude, place.longitude))

    suspend fun search(query: String, language: String): List<Place> =
        parseSearch(get(searchUrl(query, language)))

    private fun coord(value: Double) = String.format(Locale.US, "%.4f", value)

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** The search answer; no "results" key at all means nothing was found. */
fun parseSearch(text: String): List<Place> {
    val results = JSONObject(text).optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { i ->
        val o = results.optJSONObject(i) ?: return@mapNotNull null
        val lat = o.optDouble("latitude", Double.NaN)
        val lon = o.optDouble("longitude", Double.NaN)
        val name = o.text("name")
        if (lat.isNaN() || lon.isNaN() || name.isEmpty()) return@mapNotNull null
        Place(name, regionOf(o.text("admin1"), o.text("country")), lat, lon)
    }
}
