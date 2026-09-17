package com.liuli.weather.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.liuli.weather.R
import com.liuli.weather.data.model.LocationInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** 基于 LocationManager 的定位（不依赖 Google Play Services，国产设备更稳）。 */
object LocationRepository {

    private const val FRESH_WINDOW_MS = 10 * 60 * 1000L
    private const val REQUEST_TIMEOUT_MS = 15_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun getCurrentLocation(context: Context): LocationInfo? =
        suspendCancellableCoroutine { cont ->
            val appContext = context.applicationContext
            val lm =
                appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm == null || !hasPermission(appContext)) {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val lastKnown = PROVIDERS.mapNotNull { p ->
                try {
                    if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null
                } catch (e: SecurityException) {
                    null
                }
            }.maxByOrNull { it.time }

            lastKnown?.takeIf {
                System.currentTimeMillis() - it.time < FRESH_WINDOW_MS
            }?.let {
                if (cont.isActive) cont.resume(toLocationInfo(appContext, it))
                return@suspendCancellableCoroutine
            }

            val handler = Handler(Looper.getMainLooper())
            val timeout = Runnable {
                if (cont.isActive) cont.resume(lastKnown?.let { toLocationInfo(appContext, it) })
            }
            handler.postDelayed(timeout, REQUEST_TIMEOUT_MS)

            val listener = LocationListener { location ->
                handler.removeCallbacks(timeout)
                if (cont.isActive) cont.resume(toLocationInfo(appContext, location))
            }

            try {
                for (p in PROVIDERS) {
                    if (lm.isProviderEnabled(p)) {
                        @Suppress("DEPRECATION")
                        lm.requestSingleUpdate(p, listener, null)
                    }
                }
            } catch (e: Exception) {
                handler.removeCallbacks(timeout)
                if (cont.isActive) cont.resume(lastKnown?.let { toLocationInfo(appContext, it) })
            }

            cont.invokeOnCancellation { handler.removeCallbacks(timeout) }
        }

    private val PROVIDERS = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )

    private fun toLocationInfo(context: Context, location: Location): LocationInfo =
        LocationInfo(
            name = reverseName(context, location.latitude, location.longitude),
            lat = location.latitude,
            lng = location.longitude,
            isGps = true
        )

    private fun reverseName(context: Context, lat: Double, lng: Double): String {
        return try {
            @Suppress("DEPRECATION")
            val address = Geocoder(context, Locale.CHINESE)
                .getFromLocation(lat, lng, 1)
                ?.firstOrNull()
            address?.let {
                it.locality ?: it.subAdminArea ?: it.adminArea
            } ?: context.getString(R.string.location_current)
        } catch (e: Exception) {
            context.getString(R.string.location_current)
        }
    }
}
