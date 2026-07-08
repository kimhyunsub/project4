package com.attendance.androidapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.attendance.androidapp.model.CompanySetting
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object AttendanceGeofenceRegistrar {
    private const val WORKPLACE_GEOFENCE_ID = "attendance-workplace-geofence"

    fun refreshWorkplaceGeofence(context: Context, companySetting: CompanySetting) {
        if (!hasRequiredLocationPermissions(context)) {
            return
        }

        val appContext = context.applicationContext
        val geofencingClient = LocationServices.getGeofencingClient(appContext)
        val pendingIntent = createPendingIntent(appContext)

        geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
            addWorkplaceGeofence(appContext, companySetting, pendingIntent)
        }
    }

    private fun hasRequiredLocationPermissions(context: Context): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        return hasFineLocation && hasBackgroundLocation
    }

    @SuppressLint("MissingPermission")
    private fun addWorkplaceGeofence(
        context: Context,
        companySetting: CompanySetting,
        pendingIntent: PendingIntent
    ) {
        val radiusMeters = companySetting.allowedRadiusMeters.coerceAtLeast(100).toFloat()
        val geofence = Geofence.Builder()
            .setRequestId(WORKPLACE_GEOFENCE_ID)
            .setCircularRegion(companySetting.latitude, companySetting.longitude, radiusMeters)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()

        LocationServices.getGeofencingClient(context)
            .addGeofences(request, pendingIntent)
    }

    private fun createPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            7301,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
