package com.attendance.androidapp.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationTracker(context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var locationCallback: LocationCallback? = null
    private var timeoutRunnable: Runnable? = null

    @SuppressLint("MissingPermission")
    fun start(onLocation: (Location) -> Unit, onError: (Throwable) -> Unit) {
        stop()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    clearTimeout()
                    onLocation(it)
                }
            }
        }

        timeoutRunnable = Runnable {
            onError(IllegalStateException("현재 위치를 가져오지 못했습니다. 위치 서비스를 켜고 다시 시도해 주세요."))
        }.also {
            mainHandler.postDelayed(it, 12_000L)
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null && location.isFreshEnoughForInitialDisplay()) {
                    clearTimeout()
                    onLocation(location)
                }
            }
            .addOnFailureListener {
                clearTimeout()
                onError(it)
            }

        fusedLocationClient.requestLocationUpdates(
            request,
            checkNotNull(locationCallback),
            Looper.getMainLooper()
        ).addOnFailureListener {
            clearTimeout()
            onError(it)
        }
    }

    fun stop() {
        clearTimeout()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun clearTimeout() {
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
    }

    private fun Location.isFreshEnoughForInitialDisplay(): Boolean {
        val ageMillis = System.currentTimeMillis() - time
        return ageMillis in 0..60_000L && accuracy <= 100f
    }
}
