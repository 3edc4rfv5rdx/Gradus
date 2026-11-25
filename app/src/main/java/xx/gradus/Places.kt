package xx.gradus

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** A place the forecast is for: one found by search, or where the phone was. */
data class Place(
    val name: String,
    /** Region and country, for telling two places of one name apart; may be empty. */
    val region: String,
    val latitude: Double,
    val longitude: Double,
) {
    /** Identity in the saved list: the coordinates, about 10 m apart at four decimals. */
    val key: String get() = coordinateKey(latitude, longitude)
}

/** The selection that means "where the phone is"; never a saved place's key. */
const val GPS_KEY = "gps"

fun coordinateKey(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.4f,%.4f", latitude, longitude)

fun Place.toJson(): JSONObject = JSONObject()
    .put("name", name)
    .put("region", region)
    .put("lat", latitude)
    .put("lon", longitude)

fun placeFromJson(o: JSONObject): Place = Place(
    name = o.getString("name"),
    region = o.text("region"),
    latitude = o.getDouble("lat"),
    longitude = o.getDouble("lon"),
)

fun encodePlaces(places: List<Place>): String =
    JSONArray().apply { places.forEach { put(it.toJson()) } }.toString()

/** A damaged entry is dropped rather than losing the whole list. */
fun decodePlaces(text: String): List<Place> {
    val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        array.optJSONObject(i)?.let { runCatching { placeFromJson(it) }.getOrNull() }
    }
}

/** "Odesa Oblast, Ukraine" from the parts the geocoder knows; blanks and repeats left out. */
fun regionOf(vararg parts: String?): String =
    parts.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString(", ")

/** A string field; org.json's optString would turn a JSON null into the word "null". */
fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)
