package xx.gradus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Why the forecast on screen is missing or not fresh. */
enum class Problem { NO_PERMISSION, NO_LOCATION, NETWORK }

data class WeatherState(
    /** The place the forecast is for; null for GPS before the first fix. */
    val place: Place? = null,
    val forecast: Forecast? = null,
    /** When [forecast] was fetched, device time; 0 without one. */
    val fetchedAt: Long = 0L,
    val refreshing: Boolean = false,
    /** The last refresh's failure; a cached forecast may still be on screen. */
    val problem: Problem? = null,
)

data class SearchState(
    val query: String = "",
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
    /** A search ran and found nothing. */
    val empty: Boolean = false,
    val failed: Boolean = false,
)

/**
 * The forecast for the selected place: the cached copy at once, a fresh one
 * when it is older than [STALE_AFTER_MS] or on request. [AppSettings.load]
 * must have run before this is created.
 */
class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()

    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private var refreshJob: Job? = null
    private var refreshGeneration = 0
    private var searchJob: Job? = null

    init {
        showCached()
    }

    /** The app came to the front: fetch unless the forecast on screen is recent. */
    fun refreshIfStale() {
        if (refreshJob?.isActive == true) return
        if (isStale(_state.value.fetchedAt, System.currentTimeMillis())) refresh()
    }

    /** Pull-to-refresh and a new selection: fetch now, dropping a fetch under way. */
    fun refresh() {
        refreshJob?.cancel()
        val key = AppSettings.selectedKey.value
        _state.update { it.copy(refreshing = true) }
        val generation = ++refreshGeneration
        refreshJob = viewModelScope.launch {
            try {
                fetch(key)
            } finally {
                // A cancelled fetch must not clear the flag its replacement has just set.
                // Counted rather than compared by job: on the main dispatcher a fetch that
                // fails at once ends here before launch has even returned its job.
                if (generation == refreshGeneration) _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun select(key: String) {
        if (key == AppSettings.selectedKey.value) return
        AppSettings.setSelectedKey(app, key)
        showCached()
        refresh()
    }

    /** A place picked from the search results: kept in the list and shown. */
    fun addAndSelect(place: Place) {
        AppSettings.addPlace(app, place)
        clearSearch()
        select(place.key)
    }

    fun remove(place: Place) {
        val wasSelected = AppSettings.selectedKey.value == place.key
        AppSettings.removePlace(app, place.key)
        ForecastCache.delete(app, place.key)
        if (wasSelected) {
            showCached()
            refresh()
        }
    }

    fun search(query: String) {
        val text = query.trim()
        searchJob?.cancel()
        if (text.isEmpty()) {
            clearSearch()
            return
        }
        _search.value = SearchState(query = text, searching = true)
        searchJob = viewModelScope.launch {
            _search.value = try {
                val found = OpenMeteo.search(text, Locale.getDefault().language)
                SearchState(query = text, results = found, empty = found.isEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchState(query = text, failed = true)
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchState()
    }

    /** What is known without the network: the selected place and its cached forecast. */
    private fun showCached() {
        val key = AppSettings.selectedKey.value
        val place = placeFor(key)
        val cached = ForecastCache.load(app, key)?.let { entry ->
            runCatching { parseForecast(entry.json) }.getOrNull()?.let { it to entry.fetchedAt }
        }
        _state.value = WeatherState(
            place = place,
            forecast = cached?.first,
            fetchedAt = cached?.second ?: 0L,
            refreshing = _state.value.refreshing,
        )
    }

    private fun placeFor(key: String): Place? =
        if (key == GPS_KEY) AppSettings.gpsPlace.value else AppSettings.places.value.firstOrNull { it.key == key }

    private suspend fun fetch(key: String) {
        val place = if (key == GPS_KEY) {
            if (!hasLocationPermission(app)) return fail(key, Problem.NO_PERMISSION)
            val location = currentLocation(app) ?: return fail(key, Problem.NO_LOCATION)
            placeAt(app, location.latitude, location.longitude).also { AppSettings.setGpsPlace(app, it) }
        } else {
            placeFor(key) ?: return
        }
        try {
            val json = OpenMeteo.forecast(place)
            val forecast = parseForecast(json)
            val fetchedAt = System.currentTimeMillis()
            ForecastCache.save(app, key, ForecastCache.Entry(json, fetchedAt))
            if (AppSettings.selectedKey.value != key) return
            _state.update { it.copy(place = place, forecast = forecast, fetchedAt = fetchedAt, problem = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No network, a timeout, an error page or a response that would not parse:
            // the same to the user — the forecast on screen could not be renewed.
            if (AppSettings.selectedKey.value == key) _state.update { it.copy(place = place) }
            fail(key, Problem.NETWORK)
        }
    }

    private fun fail(key: String, problem: Problem) {
        if (AppSettings.selectedKey.value == key) _state.update { it.copy(problem = problem) }
    }
}
