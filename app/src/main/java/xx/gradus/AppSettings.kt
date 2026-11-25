package xx.gradus

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Light/dark override; SYSTEM follows the device setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How many accents the palette offers; the colours themselves are in ui/Common.kt. */
const val ACCENT_COUNT = 6

/**
 * Process-wide settings, backed by SharedPreferences and exposed as [StateFlow]
 * for Compose. Call [load] once before the UI reads them.
 */
object AppSettings {
    private const val PREFS = "gradus"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_ACCENT = "accent_index"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_PLACES = "places"
    private const val KEY_SELECTED = "selected_place"
    private const val KEY_GPS_PLACE = "gps_place"
    private const val KEY_WIND_UNIT = "wind_unit"
    private const val KEY_WIND_DIRECTION = "wind_direction"
    private const val KEY_PRESSURE_UNIT = "pressure_unit"
    private const val KEY_TEMP_UNIT = "temp_unit"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentIndex = MutableStateFlow(0)
    val accentIndex: StateFlow<Int> = _accentIndex.asStateFlow()

    /** The places the user has added, in the order added. */
    private val _places = MutableStateFlow<List<Place>>(emptyList())
    val places: StateFlow<List<Place>> = _places.asStateFlow()

    /** Which forecast is shown: [GPS_KEY], or a saved place's [Place.key]. */
    private val _selectedKey = MutableStateFlow(GPS_KEY)
    val selectedKey: StateFlow<String> = _selectedKey.asStateFlow()

    /** Where the phone was at the last fix, with its name; null before the first one. */
    private val _gpsPlace = MutableStateFlow<Place?>(null)
    val gpsPlace: StateFlow<Place?> = _gpsPlace.asStateFlow()

    private val _windUnit = MutableStateFlow(WindUnit.MS)
    val windUnit: StateFlow<WindUnit> = _windUnit.asStateFlow()

    private val _windDirection = MutableStateFlow(WindDirectionStyle.ARROW)
    val windDirection: StateFlow<WindDirectionStyle> = _windDirection.asStateFlow()

    private val _tempUnit = MutableStateFlow(TempUnit.CELSIUS)
    val tempUnit: StateFlow<TempUnit> = _tempUnit.asStateFlow()

    private val _pressureUnit = MutableStateFlow(PressureUnit.MMHG)
    val pressureUnit: StateFlow<PressureUnit> = _pressureUnit.asStateFlow()

    fun load(context: Context) {
        val prefs = prefs(context)
        _themeMode.value = enumOr(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM)
        _accentIndex.value = prefs.getInt(KEY_ACCENT, 0).coerceIn(0, ACCENT_COUNT - 1)
        _places.value = prefs.getString(KEY_PLACES, null)?.let(::decodePlaces).orEmpty()
        _gpsPlace.value = prefs.getString(KEY_GPS_PLACE, null)?.let { decodePlaces(it).firstOrNull() }
        // A selection whose place is gone from the list falls back to the phone's own position.
        val selected = prefs.getString(KEY_SELECTED, null) ?: GPS_KEY
        _selectedKey.value = if (_places.value.any { it.key == selected }) selected else GPS_KEY
        _windUnit.value = enumOr(prefs.getString(KEY_WIND_UNIT, null), WindUnit.MS)
        _windDirection.value = enumOr(prefs.getString(KEY_WIND_DIRECTION, null), WindDirectionStyle.ARROW)
        _pressureUnit.value = enumOr(prefs.getString(KEY_PRESSURE_UNIT, null), PressureUnit.MMHG)
        _tempUnit.value = enumOr(prefs.getString(KEY_TEMP_UNIT, null), TempUnit.CELSIUS)
    }

    /** Added at the end; a place already in the list is not added twice. */
    fun addPlace(context: Context, place: Place) {
        if (_places.value.any { it.key == place.key }) return
        setPlaces(context, _places.value + place)
    }

    fun removePlace(context: Context, key: String) {
        setPlaces(context, _places.value.filterNot { it.key == key })
        if (_selectedKey.value == key) setSelectedKey(context, GPS_KEY)
    }

    private fun setPlaces(context: Context, places: List<Place>) {
        _places.value = places
        prefs(context).edit { putString(KEY_PLACES, encodePlaces(places)) }
    }

    fun setSelectedKey(context: Context, key: String) {
        _selectedKey.value = key
        prefs(context).edit { putString(KEY_SELECTED, key) }
    }

    fun setGpsPlace(context: Context, place: Place) {
        _gpsPlace.value = place
        prefs(context).edit { putString(KEY_GPS_PLACE, encodePlaces(listOf(place))) }
    }

    fun setWindUnit(context: Context, unit: WindUnit) {
        _windUnit.value = unit
        prefs(context).edit { putString(KEY_WIND_UNIT, unit.name) }
    }

    fun setTempUnit(context: Context, unit: TempUnit) {
        _tempUnit.value = unit
        prefs(context).edit { putString(KEY_TEMP_UNIT, unit.name) }
    }

    fun setPressureUnit(context: Context, unit: PressureUnit) {
        _pressureUnit.value = unit
        prefs(context).edit { putString(KEY_PRESSURE_UNIT, unit.name) }
    }

    fun setWindDirection(context: Context, style: WindDirectionStyle) {
        _windDirection.value = style
        prefs(context).edit { putString(KEY_WIND_DIRECTION, style.name) }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        prefs(context).edit { putString(KEY_THEME, mode.name) }
    }

    fun setAccentIndex(context: Context, index: Int) {
        val clamped = index.coerceIn(0, ACCENT_COUNT - 1)
        _accentIndex.value = clamped
        prefs(context).edit { putInt(KEY_ACCENT, clamped) }
    }

    /**
     * The per-app language below API 33, where the platform does not keep one.
     * Read straight from the file: it is needed while the activity attaches,
     * before [load].
     */
    fun languageTag(context: Context): String = prefs(context).getString(KEY_LANGUAGE, null).orEmpty()

    fun setLanguageTag(context: Context, tag: String) {
        prefs(context).edit { putString(KEY_LANGUAGE, tag) }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// ---------- Language ----------

/** One entry of the language picker; [tag] empty means "follow the device". */
data class LanguageOption(val tag: String, val label: String)

/**
 * The languages this build ships, read from res/xml/locales_config.xml, each
 * named in itself. Read from the XML directly: LocaleConfig, which would do it,
 * needs API 34.
 */
fun supportedLanguages(context: Context, systemLabel: String): List<LanguageOption> {
    val tags = mutableListOf<String>()
    context.resources.getXml(R.xml.locales_config).use { parser ->
        while (parser.next() != XmlResourceParser.END_DOCUMENT) {
            if (parser.eventType == XmlResourceParser.START_TAG && parser.name == "locale") {
                parser.getAttributeValue(ANDROID_NS, "name")?.let(tags::add)
            }
        }
    }
    val languages = tags.map { tag ->
        val locale = Locale.forLanguageTag(tag)
        LanguageOption(
            tag = tag,
            label = locale.getDisplayLanguage(locale)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
        )
    }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    return listOf(LanguageOption("", systemLabel)) + languages
}

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * The per-app language; empty for "follow the device". The platform holds it
 * from API 33, [AppSettings] below that.
 */
fun currentLanguageTag(context: Context): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)
            .applicationLocales
            .takeUnless { it.isEmpty }
            ?.get(0)
            ?.toLanguageTag()
            .orEmpty()
    } else {
        AppSettings.languageTag(context)
    }

/**
 * From API 33 the system persists the choice and recreates the activity;
 * below that the app does both, and [localizedContext] applies it.
 */
fun setLanguageTag(activity: Activity, tag: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    } else {
        AppSettings.setLanguageTag(activity, tag)
        activity.recreate()
    }
}

/**
 * The activity's base context in the chosen language, below API 33; from 33
 * the platform does this itself and [base] comes back as it is.
 */
fun localizedContext(base: Context): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
    val tag = AppSettings.languageTag(base)
    val locale = if (tag.isEmpty()) Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag)
    // The shared About and updater modules pick their strings by the default
    // locale; set back to the device's too, so "follow the device" undoes an
    // earlier choice without a restart.
    Locale.setDefault(locale)
    if (tag.isEmpty()) return base
    // Only the locale is set: an empty Configuration overrides nothing else, so
    // orientation and screen size still follow the device.
    val override = Configuration().apply { setLocale(locale) }
    return base.createConfigurationContext(override)
}
