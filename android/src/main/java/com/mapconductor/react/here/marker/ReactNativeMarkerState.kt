package com.mapconductor.react.here.marker

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.marker.MarkerAnimation
import com.mapconductor.core.marker.MarkerIconInterface
import com.mapconductor.react.here.fromReadableMap
import com.mapconductor.react.here.getDoubleOrNull

data class ReactNativeMarkerState(
    val id: String,
    val position: GeoPoint,
    val clickable: Boolean = true,
    val draggable: Boolean = false,
    val zIndex: Float? = null,
    val icon: ReactNativeMarkerIcon? = null,
    val resolvedIcon: MarkerIconInterface? = null,
    val animation: MarkerAnimation? = null,
) {
    companion object
}

fun ReactNativeMarkerState.Companion.fromReadableMap(map: ReadableMap?): ReactNativeMarkerState? {
    if (map == null) return null
    val id = if (map.hasKey("id") && !map.isNull("id")) map.getString("id") else null
    val position = GeoPoint.fromReadableMap(map.getMap("position"))
    if (id == null || position == null) return null
    return ReactNativeMarkerState(
        id = id,
        position = position,
        clickable = if (map.hasKey("clickable") && !map.isNull("clickable")) map.getBoolean("clickable") else true,
        draggable = if (map.hasKey("draggable") && !map.isNull("draggable")) map.getBoolean("draggable") else false,
        zIndex = map.getDoubleOrNull("zIndex")?.toFloat(),
        icon = ReactNativeMarkerIcon.fromReadableMap(if (map.hasKey("icon") && !map.isNull("icon")) map.getMap("icon") else null),
        animation = if (map.hasKey("animation") && !map.isNull("animation")) {
            runCatching { MarkerAnimation.valueOf(map.getString("animation") ?: "") }.getOrNull()
        } else {
            null
        },
    )
}

fun markerStatesFromReadableArray(array: ReadableArray?): List<ReactNativeMarkerState> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.size()) {
            ReactNativeMarkerState.fromReadableMap(array.getMap(index))?.let(::add)
        }
    }
}

/**
 * Decodes the compressed compositionMarkers() batch payload: structure-of-arrays referring to
 * the composition-level icon dictionary registered by beginMarkerComposition().
 * This avoids ~7 hasKey/isNull/getX JNI calls per marker field that `fromReadableMap` needs, and
 * avoids re-parsing an identical icon definition once per marker.
 */
fun markerStatesFromBatchReadableMap(
    payload: ReadableMap?,
    sharedIcons: List<MarkerIconInterface?>? = null,
): List<ReactNativeMarkerState> {
    if (payload == null) return emptyList()
    val ids = payload.getArray("ids") ?: return emptyList()
    val positions = payload.getArray("positions") ?: return emptyList()
    val clickableArr = payload.getArray("clickable")
    val draggableArr = payload.getArray("draggable")
    val zIndexArr = payload.getArray("zIndex")
    val iconIndexArr = payload.getArray("iconIndex")
    val animationArr = payload.getArray("animation")
    val icons: List<ReactNativeMarkerIcon?> =
        if (sharedIcons != null) {
            emptyList()
        } else {
            val iconDict = payload.getArray("icons")
            if (iconDict == null) {
                emptyList()
            } else {
                (0 until iconDict.size()).map { index -> ReactNativeMarkerIcon.fromReadableMap(iconDict.getMap(index)) }
            }
        }

    return buildList {
        for (index in 0 until ids.size()) {
            val id = ids.getString(index) ?: continue
            val iconIdx = iconIndexArr?.getInt(index) ?: -1
            add(
                ReactNativeMarkerState(
                    id = id,
                    position =
                        GeoPoint(
                            latitude = positions.getDouble(index * 3),
                            longitude = positions.getDouble(index * 3 + 1),
                            altitude = positions.getDouble(index * 3 + 2),
                        ),
                    clickable = clickableArr?.getBoolean(index) ?: true,
                    draggable = draggableArr?.getBoolean(index) ?: false,
                    zIndex = zIndexArr?.getDouble(index)?.toFloat(),
                    icon = icons.getOrNull(iconIdx),
                    resolvedIcon = sharedIcons?.getOrNull(iconIdx),
                    animation =
                        if (animationArr != null && !animationArr.isNull(index)) {
                            runCatching { MarkerAnimation.valueOf(animationArr.getString(index) ?: "") }.getOrNull()
                        } else {
                            null
                        },
                ),
            )
        }
    }
}
