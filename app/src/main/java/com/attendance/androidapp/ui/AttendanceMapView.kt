package com.attendance.androidapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.attendance.androidapp.model.CompanySetting
import com.attendance.androidapp.model.UiLocation
import kotlin.math.cos
import org.osmdroid.util.BoundingBox
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
fun AttendanceMapView(
    modifier: Modifier = Modifier,
    context: Context,
    companySetting: CompanySetting,
    currentLocation: UiLocation?
) {
    DisposableEffect(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                controller.setZoom(15.5)
            }
        },
        update = { mapView ->
            val companyPoint = GeoPoint(companySetting.latitude, companySetting.longitude)
            mapView.overlays.clear()

            val currentPoint = currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
            val distanceToCompany = currentPoint?.distanceToAsDouble(companyPoint)
            val shouldUseCompactCurrentLocation = distanceToCompany != null && distanceToCompany < 90.0
            val displayedCurrentPoint = if (currentPoint != null && shouldUseCompactCurrentLocation) {
                offsetGeoPoint(currentPoint, northMeters = 32.0, eastMeters = 48.0)
            } else {
                currentPoint
            }
            val isInsideCompanyRadius = distanceToCompany == null || distanceToCompany <= companySetting.allowedRadiusMeters

            val companyMarker = Marker(mapView).apply {
                position = companyPoint
                title = companySetting.companyName
                icon = createCompanyMarkerDrawable(context)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(companyMarker)

            val radiusCircle = Polygon().apply {
                points = Polygon.pointsAsCircle(companyPoint, companySetting.allowedRadiusMeters.toDouble())
                fillColor = if (isInsideCompanyRadius) 0x1F1463FF else 0x19DC2626
                strokeColor = if (isInsideCompanyRadius) 0x8C1463FF.toInt() else 0xBFDC2626.toInt()
                strokeWidth = 3f
            }
            mapView.overlays.add(radiusCircle)

            if (displayedCurrentPoint != null) {
                val currentMarker = Marker(mapView).apply {
                    position = displayedCurrentPoint
                    title = "현재 위치"
                    icon = createCurrentLocationMarkerDrawable(context)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(currentMarker)
            }

            if (currentPoint != null) {
                val latitudeGap = kotlin.math.abs(companyPoint.latitude - currentPoint.latitude)
                val longitudeGap = kotlin.math.abs(companyPoint.longitude - currentPoint.longitude)

                if (latitudeGap < 0.00001 && longitudeGap < 0.00001) {
                    mapView.controller.setZoom(15.5)
                    mapView.controller.setCenter(currentPoint)
                } else {
                    runCatching {
                        val boundingBox = BoundingBox.fromGeoPointsSafe(listOf(companyPoint, currentPoint))
                        mapView.post {
                            runCatching {
                                mapView.zoomToBoundingBox(boundingBox, true, 260)
                            }.onFailure {
                                mapView.controller.setZoom(15.0)
                                mapView.controller.setCenter(currentPoint)
                            }
                        }
                    }.onFailure {
                        mapView.controller.setZoom(15.0)
                        mapView.controller.setCenter(currentPoint)
                    }
                }
            } else {
                mapView.controller.setZoom(15.5)
                mapView.controller.setCenter(companyPoint)
            }

            mapView.invalidate()
        }
    )
}

private fun offsetGeoPoint(point: GeoPoint, northMeters: Double, eastMeters: Double): GeoPoint {
    val latitudeOffset = northMeters / 111320.0
    val longitudeOffset = eastMeters / (111320.0 * cos(Math.toRadians(point.latitude)))
    return GeoPoint(point.latitude + latitudeOffset, point.longitude + longitudeOffset)
}

private fun createCompanyMarkerDrawable(context: Context): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(34.dp(context), 34.dp(context), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F2937.toInt() }
    val softBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5EDF7.toInt() }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF94A3B8.toInt() }
    val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF64748B.toInt() }

    canvas.drawCircle(17.dpF(context), 17.dpF(context), 17.dpF(context), basePaint)
    canvas.drawRoundRect(RectF(9.dpF(context), 9.dpF(context), 25.dpF(context), 25.dpF(context)), 4.dpF(context), 4.dpF(context), softBluePaint)
    canvas.drawRoundRect(RectF(11.dpF(context), 11.dpF(context), 23.dpF(context), 14.dpF(context)), 2.dpF(context), 2.dpF(context), linePaint)
    canvas.drawRect(11.dpF(context), 16.dpF(context), 14.dpF(context), 19.dpF(context), windowPaint)
    canvas.drawRect(16.dpF(context), 16.dpF(context), 19.dpF(context), 19.dpF(context), windowPaint)
    canvas.drawRect(21.dpF(context), 16.dpF(context), 24.dpF(context), 19.dpF(context), windowPaint)
    canvas.drawRect(15.dpF(context), 21.dpF(context), 19.dpF(context), 25.dpF(context), windowPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createCurrentLocationMarkerDrawable(context: Context): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(34.dp(context), 34.dp(context), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1463FF.toInt() }
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF8FAFC.toInt() }

    canvas.drawCircle(17.dpF(context), 17.dpF(context), 17.dpF(context), bluePaint)
    canvas.drawCircle(17.dpF(context), 13.dpF(context), 5.dpF(context), whitePaint)
    canvas.drawRoundRect(RectF(9.dpF(context), 18.dpF(context), 25.dpF(context), 27.dpF(context)), 9.dpF(context), 9.dpF(context), whitePaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun Int.dp(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()

private fun Int.dpF(context: Context): Float =
    this * context.resources.displayMetrics.density
