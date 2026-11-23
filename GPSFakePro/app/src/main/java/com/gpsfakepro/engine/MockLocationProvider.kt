package com.gpsfakepro.engine

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.LocationProvider

/**
 * Wraps Android's official mock-location mechanism
 * ([LocationManager.addTestProvider] / [LocationManager.setTestProviderLocation]).
 *
 * The system grants the app the right to use test providers only when the app is
 * selected under "Select mock location app" in Developer options. If that has not
 * been done, [ensureEnabled] reports a [Result.MockNotAllowed] error instead of
 * crashing, and the UI points the user at the setup instructions.
 *
 * This implementation deliberately does NOT attempt any of: hiding the mock flag
 * from `isFromMockProvider()`, overriding other apps' checks, root-based
 * spoofing, or defeating location-integrity checks.
 */
class MockLocationProvider(context: Context) {

    companion object {
        /** We inject under the GPS provider name so location-dependent apps behave naturally. */
        const val PROVIDER_NAME = LocationManager.GPS_PROVIDER
    }

    sealed class Result {
        data object Ready : Result()
        /** User has not selected this app as the mock-location app. */
        data object MockNotAllowed : Result()
        /** Provider unavailable on this device/build (or an unexpected failure). */
        data class Unavailable(val detail: String?) : Result()
    }

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Registers the test provider and enables it. Idempotent: if the provider was
     * already registered (e.g. after a service restart) it is merely re-enabled.
     */
    fun ensureEnabled(): Result {
        return try {
            try {
                locationManager.addTestProvider(
                    PROVIDER_NAME,
                    false,   // requiresNetwork
                    false,   // requiresSatellite
                    false,   // requiresCell
                    false,   // hasMonetaryCost
                    true,    // supportsAltitude
                    true,    // supportsSpeed
                    true,    // supportsBearing
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
            } catch (_: IllegalArgumentException) {
                // Provider already registered — fine, just enable it.
            }
            locationManager.setTestProviderEnabled(PROVIDER_NAME, true)
            locationManager.setTestProviderStatus(
                PROVIDER_NAME,
                LocationProvider.AVAILABLE,
                null,
                System.currentTimeMillis()
            )
            Result.Ready
        } catch (e: SecurityException) {
            Result.MockNotAllowed
        } catch (e: Exception) {
            Result.Unavailable(e.message)
        }
    }

    /**
     * Pushes one mock location. Throws [SecurityException] if the app lost its
     * mock-location grant while running; the service converts that into a
     * user-visible error.
     */
    fun push(location: Location) {
        locationManager.setTestProviderLocation(PROVIDER_NAME, location)
    }

    /** Disables the test provider. Best-effort; never throws. */
    fun disable() {
        try {
            locationManager.setTestProviderEnabled(PROVIDER_NAME, false)
        } catch (_: Exception) {
            // ignore
        }
    }

    /** Removes the test provider. Best-effort; never throws. */
    fun remove() {
        try {
            locationManager.removeTestProvider(PROVIDER_NAME)
        } catch (_: Exception) {
            // ignore
        }
    }
}
