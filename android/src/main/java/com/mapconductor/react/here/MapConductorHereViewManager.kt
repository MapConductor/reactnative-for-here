package com.mapconductor.react.here

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class MapConductorHereViewManager : SimpleViewManager<HereMapViewWrapper>() {
    override fun getName(): String = REACT_CLASS

    override fun createViewInstance(reactContext: ThemedReactContext): HereMapViewWrapper =
        HereMapViewWrapper(reactContext)

    override fun onAfterUpdateTransaction(view: HereMapViewWrapper) {
        super.onAfterUpdateTransaction(view)
        view.initializeMapIfNeeded()
    }

    // `accessKeyId`/`accessKeySecret` are intentionally not handled here: Android's HERE SDK
    // reads `<meta-data android:name="HERE_ACCESS_KEY_ID"/>` / `HERE_ACCESS_KEY_SECRET` off the
    // host app's own AndroidManifest.xml instead (see HereViewControllerStore.initSDK and this
    // package's build.gradle, which injects those meta-data values via manifestPlaceholders).
    // Unhandled view props are silently ignored by React Native, so passing them from JS is safe.

    @ReactProp(name = "cameraPosition")
    fun setCameraPosition(
        view: HereMapViewWrapper,
        cameraPosition: ReadableMap?,
    ) {
        view.setCameraPosition(cameraPosition)
    }

    @ReactProp(name = "mapDesignType")
    fun setMapDesignType(
        view: HereMapViewWrapper,
        mapDesignType: String?,
    ) {
        view.setMapDesignType(mapDesignType)
    }

    @ReactProp(name = "infoBubblePositions")
    fun setInfoBubblePositions(
        view: HereMapViewWrapper,
        positions: ReadableArray?,
    ) {
        view.setInfoBubblePositions(positions)
    }

    @ReactProp(name = "markerTilingOptions")
    fun setMarkerTilingOptions(
        view: HereMapViewWrapper,
        options: ReadableMap?,
    ) {
        view.setMarkerTilingOptions(options)
    }

    override fun receiveCommand(
        root: HereMapViewWrapper,
        commandId: String,
        args: ReadableArray?,
    ) {
        when (commandId) {
            "moveCamera" -> root.moveCamera(args?.getMap(0))
            "animateCamera" -> root.animateCamera(args?.getMap(0), args?.getInt(1) ?: 0)
            "fitBounds" -> root.fitBounds(args?.getMap(0), args?.getInt(1) ?: 0)
            "clearOverlays" -> root.clearOverlays()
            "compositionMarkers" -> root.compositionMarkers(args?.getMap(0))
            "beginMarkerComposition" ->
                root.beginMarkerComposition(
                    generation = args?.getInt(0) ?: return,
                    iconDictionary = if (args.size() > 1 && !args.isNull(1)) args.getArray(1) else null,
                )
            "appendMarkerComposition" ->
                root.appendMarkerComposition(
                    generation = args?.getInt(0) ?: return,
                    sequence = args.getInt(1),
                    payload = args.getMap(2),
                )
            "commitMarkerComposition" -> root.commitMarkerComposition(args?.getInt(0) ?: return)
            "updateMarker" -> root.updateMarker(args?.getMap(0))
            "compositionCircles" -> root.compositionCircles(args?.getArray(0))
            "updateCircle" -> root.updateCircle(args?.getMap(0))
            "compositionPolylines" -> root.compositionPolylines(args?.getArray(0))
            "updatePolyline" -> root.updatePolyline(args?.getMap(0))
            "compositionPolygons" -> root.compositionPolygons(args?.getArray(0))
            "updatePolygon" -> root.updatePolygon(args?.getMap(0))
            "compositionRasterLayers" -> root.compositionRasterLayers(args?.getArray(0))
            "updateRasterLayer" -> root.updateRasterLayer(args?.getMap(0))
            "compositionGroundImages" -> root.compositionGroundImages(args?.getArray(0))
            "updateGroundImage" -> root.updateGroundImage(args?.getMap(0))
            "upsertNativeMapExtension" ->
                root.upsertNativeMapExtension(
                    extensionId = args?.getString(0) ?: return,
                    type = args.getString(1) ?: return,
                    payload = args.getMap(2),
                )
            "removeNativeMapExtension" ->
                root.removeNativeMapExtension(args?.getString(0) ?: return)
        }
    }

    override fun onDropViewInstance(view: HereMapViewWrapper) {
        view.onDropViewInstance()
        super.onDropViewInstance(view)
    }

    override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> =
        mutableMapOf(
            "topMapLoaded" to mapOf("registrationName" to "onMapLoaded"),
            "topMarkerCompositionBatchProcessed" to
                mapOf("registrationName" to "onMarkerCompositionBatchProcessed"),
            "topMapClick" to mapOf("registrationName" to "onMapClick"),
            "topMapLongClick" to mapOf("registrationName" to "onMapLongClick"),
            "topCameraMoveStart" to mapOf("registrationName" to "onCameraMoveStart"),
            "topCameraMove" to mapOf("registrationName" to "onCameraMove"),
            "topCameraMoveEnd" to mapOf("registrationName" to "onCameraMoveEnd"),
            "topMarkerClick" to mapOf("registrationName" to "onMarkerClick"),
            "topCircleClick" to mapOf("registrationName" to "onCircleClick"),
            "topGroundImageClick" to mapOf("registrationName" to "onGroundImageClick"),
            "topPolylineClick" to mapOf("registrationName" to "onPolylineClick"),
            "topPolygonClick" to mapOf("registrationName" to "onPolygonClick"),
            "topMarkerDragStart" to mapOf("registrationName" to "onMarkerDragStart"),
            "topMarkerDrag" to mapOf("registrationName" to "onMarkerDrag"),
            "topMarkerDragEnd" to mapOf("registrationName" to "onMarkerDragEnd"),
            "topMarkerAnimateStart" to mapOf("registrationName" to "onMarkerAnimateStart"),
            "topMarkerAnimateEnd" to mapOf("registrationName" to "onMarkerAnimateEnd"),
            "topMarkerScreenPositions" to mapOf("registrationName" to "onMarkerScreenPositions"),
            "topInfoBubbleScreenPositions" to mapOf("registrationName" to "onInfoBubbleScreenPositions"),
            "topNativeMapExtensionEvent" to mapOf("registrationName" to "onNativeMapExtensionEvent"),
        )

    companion object {
        const val REACT_CLASS = "HereMapView"
    }
}
