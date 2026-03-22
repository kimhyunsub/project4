package com.attendance.androidapp.util

import com.attendance.androidapp.model.UiLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object DistanceUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun calculateMeters(from: UiLocation, latitude: Double, longitude: Double): Double {
        val latitudeDistance = Math.toRadians(latitude - from.latitude)
        val longitudeDistance = Math.toRadians(longitude - from.longitude)

        val a = sin(latitudeDistance / 2) * sin(latitudeDistance / 2) +
            cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(latitude)) *
            sin(longitudeDistance / 2) * sin(longitudeDistance / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}
