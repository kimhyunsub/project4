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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

private const val MAP_ZOOM = 14.35
private const val MIN_VIEW_RADIUS_METERS = 600.0
private const val RADIUS_VIEW_MARGIN = 1.2
private const val MAP_BOUNDING_PADDING_PX = 96

@Composable
fun AttendanceMapView(
    modifier: Modifier = Modifier,
    context: Context,
    companySetting: CompanySetting,
    currentLocation: UiLocation?,
    displayLocationName: String
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
                controller.setZoom(MAP_ZOOM)
            }
        },
        update = { mapView ->
            val companyPoint = GeoPoint(companySetting.latitude, companySetting.longitude)
            mapView.overlays.clear()

            val currentPoint = currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
            val distanceToCompany = currentPoint?.distanceToAsDouble(companyPoint)
            val isInsideCompanyRadius = distanceToCompany == null || distanceToCompany <= companySetting.allowedRadiusMeters

            val companyMarker = Marker(mapView).apply {
                position = companyPoint
                title = displayLocationName
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

            if (currentPoint != null) {
                val currentMarker = Marker(mapView).apply {
                    position = currentPoint
                    title = "현재 위치"
                    icon = createCurrentLocationMarkerDrawable(context)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(currentMarker)
            }

            val viewportBoundingBox = createViewportBoundingBox(
                center = companyPoint,
                radiusMeters = max(
                    MIN_VIEW_RADIUS_METERS,
                    companySetting.allowedRadiusMeters.toDouble() * RADIUS_VIEW_MARGIN
                ),
                currentPoint = currentPoint
            )
            mapView.post {
                runCatching {
                    mapView.zoomToBoundingBox(viewportBoundingBox, true, MAP_BOUNDING_PADDING_PX)
                }.onFailure {
                    mapView.controller.setZoom(MAP_ZOOM)
                    mapView.controller.setCenter(currentPoint ?: companyPoint)
                }
            }

            mapView.invalidate()
        }
    )
}

private fun createViewportBoundingBox(
    center: GeoPoint,
    radiusMeters: Double,
    currentPoint: GeoPoint?
): BoundingBox {
    val latitudeDelta = radiusMeters / 111_320.0
    val longitudeDelta = radiusMeters / (111_320.0 * cos(Math.toRadians(center.latitude)))

    var north = center.latitude + latitudeDelta
    var south = center.latitude - latitudeDelta
    var east = center.longitude + longitudeDelta
    var west = center.longitude - longitudeDelta

    if (currentPoint != null) {
        north = max(north, currentPoint.latitude)
        south = min(south, currentPoint.latitude)
        east = max(east, currentPoint.longitude)
        west = min(west, currentPoint.longitude)
    }

    return BoundingBox(north, east, south, west)
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
