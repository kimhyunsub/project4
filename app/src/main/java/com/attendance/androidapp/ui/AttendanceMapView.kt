package com.attendance.androidapp.ui

import android.content.Context
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.attendance.androidapp.model.CompanySetting
import com.attendance.androidapp.model.UiLocation
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
                controller.setZoom(17.0)
            }
        },
        update = { mapView ->
            val companyPoint = GeoPoint(companySetting.latitude, companySetting.longitude)
            mapView.overlays.clear()

            val companyMarker = Marker(mapView).apply {
                position = companyPoint
                title = companySetting.companyName
                icon = createTintedMarkerDrawable(context, 0xFF1463FF.toInt())
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(companyMarker)

            val radiusCircle = Polygon().apply {
                points = Polygon.pointsAsCircle(companyPoint, companySetting.allowedRadiusMeters.toDouble())
                fillColor = 0x221463FF
                strokeColor = 0xFF1463FF.toInt()
                strokeWidth = 3f
            }
            mapView.overlays.add(radiusCircle)

            val currentPoint = currentLocation?.let {
                val currentPoint = GeoPoint(it.latitude, it.longitude)
                val currentMarker = Marker(mapView).apply {
                    position = currentPoint
                    title = "현재 위치"
                    icon = createTintedMarkerDrawable(context, 0xFFDC2626.toInt())
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(currentMarker)
                currentPoint
            }

            if (currentPoint != null) {
                val boundingBox = BoundingBox.fromGeoPointsSafe(listOf(companyPoint, currentPoint))
                mapView.zoomToBoundingBox(boundingBox, true, 160)
            } else {
                mapView.controller.setZoom(17.0)
                mapView.controller.setCenter(companyPoint)
            }

            mapView.invalidate()
        }
    )
}

private fun createTintedMarkerDrawable(context: Context, color: Int) =
    ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.let { drawable ->
        DrawableCompat.setTint(DrawableCompat.wrap(drawable), color)
        drawable
    }
