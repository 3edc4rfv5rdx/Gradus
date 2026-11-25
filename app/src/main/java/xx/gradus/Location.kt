package xx.gradus

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** The two permissions asked for together; the user may grant only the approximate one. */
val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

/** Either is enough: a forecast does not need better than a few kilometres. */
fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
}

private const val FIX_TIMEOUT_MS = 15_000L
private const val GEOCODE_TIMEOUT_MS = 10_000L

/**
 * Where the phone is now, or null when no provider can say. Without Google Play
 * services: the platform's fused provider where there is one (Android 12+),
 * then the network, then GPS. A provider the granted permission does not cover
 * throws, and the next one is tried. The last known position is the fallback
 * when no fresh fix arrives in time.
 */
@SuppressLint("MissingPermission") // Checked by hasLocationPermission first.
suspend fun currentLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val enabled = manager.getProviders(true)
    val order = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }.filter { it in enabled }

    for (provider in order) {
        val fix = try {
            withTimeoutOrNull(FIX_TIMEOUT_MS) { freshFix(context, manager, provider) }
        } catch (e: SecurityException) {
            null
        }
        if (fix != null) return fix
    }
    return enabled.mapNotNull {
        try {
            manager.getLastKnownLocation(it)
        } catch (e: SecurityException) {
            null
        }
    }.maxByOrNull { it.time }
}

@SuppressLint("MissingPermission")
private suspend fun freshFix(context: Context, manager: LocationManager, provider: String): Location? =
    suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }

/**
 * A name for where the phone is, from the platform geocoder in the app's
 * language; the coordinates themselves where it has none (no geocoder on the
 * phone, no network, or no answer in time).
 */
suspend fun placeAt(context: Context, latitude: Double, longitude: Double): Place {
    val address = if (Geocoder.isPresent()) {
        withTimeoutOrNull(GEOCODE_TIMEOUT_MS) { geocode(context, latitude, longitude) }
    } else {
        null
    }
    val name = address?.locality ?: address?.subAdminArea ?: address?.adminArea
    return Place(
        name = name ?: String.format(Locale.US, "%.2f, %.2f", latitude, longitude),
        region = if (name == null) "" else regionOf(address?.adminArea?.takeIf { it != name }, address?.countryName),
        latitude = latitude,
        longitude = longitude,
    )
}

private suspend fun geocode(context: Context, latitude: Double, longitude: Double): Address? {
    val geocoder = Geocoder(context, Locale.getDefault())
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }
    } else {
        withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }
}
