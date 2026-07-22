package com.mapconductor.react.here

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.VisibleRegion

// Unlike MapLibre, HERE's own `MapCameraPosition.toMapCameraUpdate()` /
// `toHereDisplayCamera()` extensions (in android-for-here) already convert between the
// core "Google-like" zoom scale and HERE's altitude-based zoom internally, so this file
// only needs the generic ReadableMap <-> core MapCameraPosition conversions - no
// provider-specific zoom offset like reactnative-for-maplibre's equivalent file has.

fun MapCameraPosition.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        putMap("position", position.toWritableMap())
        putMap("center", position.toWritableMap())
        putDouble("zoom", zoom)
        putDouble("bearing", bearing)
        putDouble("tilt", tilt)
        visibleRegion?.let { putMap("visibleRegion", it.toWritableMap()) }
    }

private fun GeoPointInterface.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        putDouble("latitude", latitude)
        putDouble("longitude", longitude)
        putDouble("altitude", altitude ?: 0.0)
    }

private fun GeoRectBounds.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        southWest?.let { putMap("southWest", it.toWritableMap()) }
        northEast?.let { putMap("northEast", it.toWritableMap()) }
    }

private fun VisibleRegion.toWritableMap(): WritableMap =
    Arguments.createMap().apply {
        putMap("bounds", bounds.toWritableMap())
        nearLeft?.let { putMap("nearLeft", it.toWritableMap()) }
        nearRight?.let { putMap("nearRight", it.toWritableMap()) }
        farLeft?.let { putMap("farLeft", it.toWritableMap()) }
        farRight?.let { putMap("farRight", it.toWritableMap()) }
    }

fun MapCameraPosition.Companion.fromReadableMap(map: ReadableMap?): MapCameraPosition {
    val positionMap =
        when {
            map == null -> null
            map.hasKey("position") -> map.getMap("position")
            map.hasKey("center") -> map.getMap("center")
            else -> null
        }

    return MapCameraPosition(
        position = GeoPoint.fromReadableMap(positionMap) ?: GeoPoint(0.0, 0.0),
        zoom = map?.getDoubleOrNull("zoom") ?: 0.0,
        bearing = map?.getDoubleOrNull("bearing") ?: 0.0,
        tilt = map?.getDoubleOrNull("tilt") ?: 0.0,
    )
}
