package com.mapconductor.react.here

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.ComposeView
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import com.mapconductor.compose.CollectAndRenderOverlays
import com.mapconductor.compose.MapViewScope
import com.mapconductor.compose.circle.LocalCircleCollector
import com.mapconductor.compose.groundimage.LocalGroundImageCollector
import com.mapconductor.compose.info.LocalInfoBubbleCollector
import com.mapconductor.compose.polygon.LocalPolygonCollector
import com.mapconductor.compose.polyline.LocalPolylineCollector
import com.mapconductor.compose.raster.LocalRasterLayerCollector
import com.mapconductor.core.ResourceProvider
import com.mapconductor.react.wrapper.MapViewWrapperEventEmitter
import com.mapconductor.react.wrapper.MapViewWrapperScreenPositions
import com.mapconductor.react.wrapper.WrapperInfoBubblePosition
import com.mapconductor.core.circle.CircleCapableInterface
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.groundimage.GroundImageCapableInterface
import com.mapconductor.core.map.LocalMapOverlayRegistry
import com.mapconductor.core.map.LocalMapServiceRegistry
import com.mapconductor.core.map.LocalMapViewController
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapOverlayRegistry
import com.mapconductor.core.map.MutableMapServiceRegistry
import com.mapconductor.core.marker.MarkerOverlay
import com.mapconductor.core.marker.MarkerIconInterface
import com.mapconductor.core.marker.MarkerState
import com.mapconductor.core.marker.MarkerTilingOptions
import com.mapconductor.core.polygon.PolygonCapableInterface
import com.mapconductor.core.polyline.PolylineCapableInterface
import com.mapconductor.core.raster.RasterLayerCapableInterface
import com.mapconductor.core.tileserver.TileServerRegistry
import com.mapconductor.here.HereMapDesign
import com.mapconductor.here.HereMapViewController
import com.mapconductor.here.HereViewHolder
import com.mapconductor.here.HereMapViewControllerStore
import com.mapconductor.here.HereViewScope
import com.mapconductor.here.circle.HereCircleController
import com.mapconductor.here.circle.HereCircleOverlayRenderer
import com.mapconductor.here.groundimage.HereGroundImageController
import com.mapconductor.here.groundimage.HereGroundImageOverlayRenderer
import com.mapconductor.here.marker.HereMarkerController
import com.mapconductor.here.polygon.HerePolygonController
import com.mapconductor.here.polygon.HerePolygonOverlayRenderer
import com.mapconductor.here.polyline.HerePolylineController
import com.mapconductor.here.polyline.HerePolylineOverlayRenderer
import com.mapconductor.here.raster.HereRasterLayerController
import com.mapconductor.here.raster.HereRasterLayerOverlayRenderer
import com.mapconductor.react.extensions.NativeMapExtensionHostState
import com.mapconductor.react.here.circle.circleStateFromReadableMap
import com.mapconductor.react.here.circle.circleStatesFromReadableArray
import com.mapconductor.react.here.polyline.polylineStateFromReadableMap
import com.mapconductor.react.here.polyline.polylineStatesFromReadableArray
import com.mapconductor.react.here.polygon.polygonStateFromReadableMap
import com.mapconductor.react.here.polygon.polygonStatesFromReadableArray
import com.mapconductor.react.marker.MarkerScaleBridge
import com.mapconductor.react.marker.applyNativeMarkerUpdate
import com.mapconductor.react.marker.decodeNativeMarkerBatch
import com.mapconductor.react.marker.decodeNativeMarkerIcon
import com.mapconductor.react.marker.decodeNativeMarkerState
import com.mapconductor.react.groundimage.groundImageStateFromReadableMap
import com.mapconductor.react.groundimage.groundImageStatesFromReadableArray
import com.mapconductor.react.raster.rasterLayerStateFromReadableMap
import com.mapconductor.react.raster.rasterLayerStatesFromReadableArray
import com.here.sdk.mapview.MapProjection as HereMapProjection
import com.here.sdk.mapview.MapRenderMode
import com.here.sdk.mapview.MapView
import com.here.sdk.mapview.MapViewOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * ラッパーの外側にある補助。
 *
 * - [RenderNativeExtensions]: 拡張モジュール（ヒートマップ等）を Compose で重ねる。
 *   コレクタの更新ハンドラを張り、CompositionLocal 経由でコントローラを配る。
 * - `markerTilingOptionsFromReadableMap`: JS から来たタイリング設定を読む。
 *
 * ラッパー本体（HereMapViewWrapper）は地図とブリッジの段取りに専念させ、
 * ここには「重ねて描くもの」と設定の読み取りを置く。
 */
@Composable
internal fun RenderNativeExtensions(
    scope: MapViewScope,
    registry: MapOverlayRegistry,
    controller: HereMapViewController,
    serviceRegistry: MutableMapServiceRegistry,
    host: NativeMapExtensionHostState,
) {
    DisposableEffect(controller) {
        scope.groundImageCollector.setUpdateHandler { state ->
            (controller as GroundImageCapableInterface).let { capable ->
                if (capable.hasGroundImage(state)) capable.updateGroundImage(state)
            }
        }
        scope.rasterLayerCollector.setUpdateHandler { state ->
            (controller as RasterLayerCapableInterface).let { capable ->
                if (capable.hasRasterLayer(state)) capable.updateRasterLayer(state)
            }
        }
        scope.polygonCollector.setUpdateHandler { state ->
            (controller as PolygonCapableInterface).let { capable ->
                if (capable.hasPolygon(state)) capable.updatePolygon(state)
            }
        }
        scope.polylineCollector.setUpdateHandler { state ->
            (controller as PolylineCapableInterface).let { capable ->
                if (capable.hasPolyline(state)) capable.updatePolyline(state)
            }
        }
        scope.circleCollector.setUpdateHandler { state ->
            (controller as CircleCapableInterface).let { capable ->
                if (capable.hasCircle(state)) capable.updateCircle(state)
            }
        }
        onDispose {
            scope.groundImageCollector.setUpdateHandler(null)
            scope.rasterLayerCollector.setUpdateHandler(null)
            scope.polygonCollector.setUpdateHandler(null)
            scope.polylineCollector.setUpdateHandler(null)
            scope.circleCollector.setUpdateHandler(null)
        }
    }

    CollectAndRenderOverlays(
        registry = registry,
        controller = controller,
    )
    CompositionLocalProvider(
        LocalMapOverlayRegistry provides registry,
        LocalMapServiceRegistry provides serviceRegistry,
        LocalMapViewController provides controller,
        LocalInfoBubbleCollector provides scope.bubbleFlow,
        LocalCircleCollector provides scope.circleCollector,
        LocalPolylineCollector provides scope.polylineCollector,
        LocalPolygonCollector provides scope.polygonCollector,
        LocalGroundImageCollector provides scope.groundImageCollector,
        LocalRasterLayerCollector provides scope.rasterLayerCollector,
    ) {
        with(scope) {
            with(host) { RenderExtensions() }
        }
    }
}

internal const val MARKER_TRACE_TAG = "MCMarkerTrace"

internal fun markerTilingOptionsFromReadableMap(map: ReadableMap?, viewId: Int): MarkerTilingOptions {
    if (map == null) return MarkerTilingOptions.Default
    val hasIconScaleCallback = map.getBooleanOrNull("hasIconScaleCallback") ?: false
    return MarkerTilingOptions.Default.copy(
        enabled = map.getBooleanOrNull("enabled") ?: MarkerTilingOptions.Default.enabled,
        debugTileOverlay = map.getBooleanOrNull("debugTileOverlay")
            ?: MarkerTilingOptions.Default.debugTileOverlay,
        minMarkerCount = map.getIntOrNull("minMarkerCount") ?: MarkerTilingOptions.Default.minMarkerCount,
        cacheSize = map.getIntOrNull("cacheSize") ?: MarkerTilingOptions.Default.cacheSize,
        iconScaleCallback =
            if (hasIconScaleCallback) {
                { state: MarkerState, zoom: Int -> MarkerScaleBridge.requestScale(viewId, state.id, zoom) }
            } else {
                null
            },
    )
}
