package com.attendance.androidapp.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.attendance.androidapp.model.CompanySetting
import com.attendance.androidapp.model.UiLocation
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
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(17.0)
            }
        },
        update = { mapView ->
            val companyPoint = GeoPoint(companySetting.latitude, companySetting.longitude)
            mapView.controller.setCenter(companyPoint)
            mapView.overlays.clear()

            val companyMarker = Marker(mapView).apply {
                position = companyPoint
                title = companySetting.companyName
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

            currentLocation?.let {
                val currentPoint = GeoPoint(it.latitude, it.longitude)
                val currentMarker = Marker(mapView).apply {
                    position = currentPoint
                    title = "현재 위치"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(currentMarker)
            }

            mapView.invalidate()
        }
    )
}
