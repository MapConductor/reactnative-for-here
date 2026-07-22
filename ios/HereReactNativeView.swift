import Combine
import MapConductorCore
import MapConductorForHERE
import MapConductorReactMarkerClustering
import MapConductorReactNativeCore
import SwiftUI
import UIKit

@objc(MCHereReactNativeView)
public final class HereReactNativeView: UIView {
    @objc public var eventHandler: ((String, [String: Any]) -> Void)?

    private let model = ReactNativeHereModel()
    private lazy var hostingController = UIHostingController(
        rootView: ReactNativeHereRoot(model: model, extensionHost: model.extensionHost)
    )

    private var markersById: [String: MarkerState] = [:]
    private let markerIngestQueue = DispatchQueue(
        label: "com.mapconductor.react.here.marker-ingest",
        qos: .userInitiated
    )
    private var ingestCompositionGeneration: Int?
    private var ingestPendingMarkers: [MarkerState] = []
    private var ingestMarkerIcons: [(any MarkerIconProtocol)?] = []
    private var activeCompositionGeneration: Int?
    private var pendingMarkerUpdates: [[String: Any]] = []
    private var markerScaleViewId = 0
    private var infoBubblePositions: [(id: String, point: GeoPoint)] = []
    private var emittedEmptyMarkerScreenPositions = false
    private var emittedEmptyInfoBubbleScreenPositions = false

    public override init(frame: CGRect) {
        super.init(frame: frame)
        hostingController.view.backgroundColor = .clear
        addSubview(hostingController.view)
        model.emit = { [weak self] name, body in self?.eventHandler?(name, body) }
        model.localExtensionFactory = { type, extensionId, eventSink in
            guard type == "marker-clustering" else { return nil }
            return MarkerClusterExtensionRenderer<HereActualMarker>(extensionId: extensionId, eventSink: eventSink)
        }
        model.onCameraMoveStart = { [weak self] camera in
            self?.eventHandler?("cameraMoveStart", ["cameraPosition": mcCameraPayload(camera)])
            self?.emitMarkerScreenPositions()
            self?.emitInfoBubbleScreenPositions()
        }
        model.onCameraMove = { [weak self] camera in
            self?.eventHandler?("cameraMove", ["cameraPosition": mcCameraPayload(camera)])
            self?.emitMarkerScreenPositions()
            self?.emitInfoBubbleScreenPositions()
        }
        model.onCameraMoveEnd = { [weak self] camera in
            self?.eventHandler?("cameraMoveEnd", ["cameraPosition": mcCameraPayload(camera)])
            self?.emitMarkerScreenPositions()
            self?.emitInfoBubbleScreenPositions()
        }
    }

    public required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        if markerScaleViewId != 0 {
            MCMarkerScaleBridge.remove(viewId: markerScaleViewId)
        }
        model.extensionHost.dispose()
    }

    public override func layoutSubviews() {
        super.layoutSubviews()
        hostingController.view.frame = bounds
        emitMarkerScreenPositions()
        emitInfoBubbleScreenPositions()
    }

    // MARK: - Credentials / camera / design

    // HERE has no manifest-style credential source on iOS (unlike Android's
    // `<meta-data android:name="HERE_ACCESS_KEY_ID"/>`), so these two props must be supplied by
    // the RN app and are read lazily by `sdkInitialize` in `ReactNativeHereRoot.body`, which HERE
    // calls once when the underlying MapView actually initializes.
    @objc public func setAccessKeyId(_ value: String?) {
        model.accessKeyId = value
    }

    @objc public func setAccessKeySecret(_ value: String?) {
        model.accessKeySecret = value
    }

    @objc public func setCameraPosition(_ payload: [String: Any]) {
        if let camera = mcCameraPosition(payload) { model.state.moveCameraTo(cameraPosition: camera) }
    }

    @objc public func setMapDesignType(_ value: String?) {
        model.state.mapDesignType = stringToHereMapDesign(value)
    }

    @objc public func moveCamera(_ payload: [String: Any], duration: Double) {
        if let camera = mcCameraPosition(payload) {
            model.state.moveCameraTo(cameraPosition: camera, durationMillis: Int64(duration))
        }
    }

    @objc public func fitBounds(_ bounds: [String: Any], padding: Int) {
        model.state.fitBounds(bounds: mcGeoRectBounds(bounds), padding: padding)
    }

    // MARK: - Overlays

    @objc public func clearOverlays() {
        activeCompositionGeneration = nil
        pendingMarkerUpdates.removeAll()
        markerIngestQueue.async { [weak self] in
            self?.ingestCompositionGeneration = nil
            self?.ingestPendingMarkers.removeAll()
            self?.ingestMarkerIcons.removeAll()
        }
        markersById.removeAll()
        model.markers = []
        model.circles = []
        model.polygons = []
        model.polylines = []
        model.groundImages = []
        model.rasterLayers = []
        infoBubblePositions = []
        emitMarkerScreenPositions()
        emitInfoBubbleScreenPositions()
    }

    @objc public func setMarkerTilingOptions(_ payload: [String: Any]?, viewId: Int) {
        if markerScaleViewId != 0 && markerScaleViewId != viewId {
            MCMarkerScaleBridge.remove(viewId: markerScaleViewId)
        }
        markerScaleViewId = viewId
        MCMarkerScaleBridge.invalidate(viewId: viewId)
        let iconScaleCallback: ((MarkerState, Int) -> Double)? =
            mcBool(payload?["hasIconScaleCallback"], default: false) && viewId != 0
                ? { state, zoom in
                    MCMarkerScaleBridge.requestScale(viewId: viewId, markerId: state.id, zoom: zoom)
                }
                : nil
        model.tiling = MarkerTilingOptions(
            enabled: mcBool(payload?["enabled"], default: true),
            debugTileOverlay: mcBool(payload?["debugTileOverlay"], default: false),
            minMarkerCount: mcInt(payload?["minMarkerCount"], default: 2000),
            cacheSize: mcInt(payload?["cacheSize"], default: 8 * 1024 * 1024),
            iconScaleCallback: iconScaleCallback
        )
    }

    @objc public func beginMarkerComposition(_ value: Int, icons: [[String: Any]]) {
        activeCompositionGeneration = value
        markerIngestQueue.async { [weak self] in
            guard let self else { return }
            self.ingestCompositionGeneration = value
            self.ingestPendingMarkers.removeAll(keepingCapacity: true)
            self.ingestMarkerIcons = mcMarkerIcons(icons)
        }
    }

    @objc public func appendMarkerComposition(_ value: Int, sequence: Int, payload: [String: Any]) {
        markerIngestQueue.async { [weak self] in
            guard let self, self.ingestCompositionGeneration == value else { return }
            self.ingestPendingMarkers.append(contentsOf: mcMarkerStatesFromBatch(
                payload,
                sharedIcons: self.ingestMarkerIcons,
                onEvent: { [weak self] name, marker in self?.emitMarkerEvent(name, marker) }
            ))
            DispatchQueue.main.async { [weak self] in
                guard let self, self.activeCompositionGeneration == value else { return }
                self.eventHandler?("markerCompositionBatchProcessed", ["generation": value, "sequence": sequence])
            }
        }
    }

    @objc public func commitMarkerComposition(_ value: Int) {
        markerIngestQueue.async { [weak self] in
            guard let self, self.ingestCompositionGeneration == value else { return }
            let markers = self.ingestPendingMarkers
            self.ingestPendingMarkers.removeAll()
            self.ingestMarkerIcons.removeAll()
            self.ingestCompositionGeneration = nil
            DispatchQueue.main.async { [weak self] in
                guard let self, self.activeCompositionGeneration == value else { return }
                self.model.markers = markers
                self.markersById = Dictionary(uniqueKeysWithValues: markers.map { ($0.id, $0) })
                self.activeCompositionGeneration = nil
                let updates = self.pendingMarkerUpdates
                self.pendingMarkerUpdates.removeAll()
                updates.forEach(self.applyMarkerUpdate)
                self.emitMarkerScreenPositions()
                self.emitInfoBubbleScreenPositions()
            }
        }
    }

    @objc public func updateMarker(_ payload: [String: Any]) {
        if activeCompositionGeneration != nil {
            pendingMarkerUpdates.append(payload)
            return
        }
        applyMarkerUpdate(payload)
        emitMarkerScreenPositions()
        emitInfoBubbleScreenPositions()
    }

    private func applyMarkerUpdate(_ payload: [String: Any]) {
        guard let id = payload["id"] as? String else { return }
        if let existing = markersById[id] {
            mcApplyMarkerUpdate(payload, to: existing)
            model.markers = model.markers
        } else if let state = mcMarkerState(payload, onEvent: { [weak self] name, marker in self?.emitMarkerEvent(name, marker) }) {
            markersById[state.id] = state
            model.markers.append(state)
        }
    }

    @objc public func compositionCircles(_ payload: [[String: Any]]) {
        model.circles = mcCircleStates(payload, onClick: { [weak self] id, event in
            self?.eventHandler?("circleClick", ["circleId": id, "point": mcPointPayload(event.clicked)])
        })
    }

    @objc public func updateCircle(_ payload: [String: Any]) {
        guard let state = mcCircleState(payload, onClick: { [weak self] id, event in
            self?.eventHandler?("circleClick", ["circleId": id, "point": mcPointPayload(event.clicked)])
        }) else { return }
        var circles = model.circles
        if let index = circles.firstIndex(where: { $0.id == state.id }) { circles[index] = state } else { circles.append(state) }
        model.circles = circles
    }

    @objc public func compositionPolygons(_ payload: [[String: Any]]) {
        model.polygons = mcPolygonStates(payload, onClick: { [weak self] id, event in
            self?.eventHandler?("polygonClick", ["polygonId": id, "point": mcPointPayload(event.clicked)])
        })
    }

    @objc public func updatePolygon(_ payload: [String: Any]) {
        guard let state = mcPolygonState(payload, onClick: { [weak self] id, event in
            self?.eventHandler?("polygonClick", ["polygonId": id, "point": mcPointPayload(event.clicked)])
        }) else { return }
        var polygons = model.polygons
        if let index = polygons.firstIndex(where: { $0.id == state.id }) { polygons[index] = state } else { polygons.append(state) }
        model.polygons = polygons
    }

    @objc public func compositionPolylines(_ payload: [[String: Any]]) {
        model.polylines = mcPolylineStates(payload, onClick: { [weak self] id, event in
            self?.eventHandler?("polylineClick", ["polylineId": id, "point": mcPointPayload(event.clicked)])
        })
    }

    @objc public func updatePolyline(_ payload: [String: Any]) {
        guard let state = mcPolylineState(payload, onClick: { [weak self] id, event in
            self?.eventHandler?("polylineClick", ["polylineId": id, "point": mcPointPayload(event.clicked)])
        }) else { return }
        var polylines = model.polylines
        if let index = polylines.firstIndex(where: { $0.id == state.id }) { polylines[index] = state } else { polylines.append(state) }
        model.polylines = polylines
    }

    @objc public func compositionGroundImages(_ payload: [[String: Any]]) {
        model.groundImages = mcGroundImageStates(payload, onClick: { [weak self] id, event in
            guard let clicked = event.clicked else { return }
            self?.eventHandler?("groundImageClick", ["groundImageId": id, "point": mcPointPayload(clicked)])
        })
    }

    @objc public func updateGroundImage(_ payload: [String: Any]) {
        guard let state = mcGroundImageState(payload, onClick: { [weak self] id, event in
            guard let clicked = event.clicked else { return }
            self?.eventHandler?("groundImageClick", ["groundImageId": id, "point": mcPointPayload(clicked)])
        }) else { return }
        var groundImages = model.groundImages
        if let index = groundImages.firstIndex(where: { $0.id == state.id }) { groundImages[index] = state } else { groundImages.append(state) }
        model.groundImages = groundImages
    }

    @objc public func compositionRasterLayers(_ payload: [[String: Any]]) {
        model.rasterLayers = mcRasterLayerStates(payload)
    }

    @objc public func updateRasterLayer(_ payload: [String: Any]) {
        guard let state = mcRasterLayerState(payload) else { return }
        var rasterLayers = model.rasterLayers
        if let index = rasterLayers.firstIndex(where: { $0.id == state.id }) { rasterLayers[index] = state } else { rasterLayers.append(state) }
        model.rasterLayers = rasterLayers
    }

    @objc public func setInfoBubblePositions(_ positions: [[String: Any]]) {
        infoBubblePositions = positions.compactMap { entry in
            guard let id = entry["id"] as? String, let point = mcGeoPoint(entry) else { return nil }
            return (id: id, point: point)
        }
        emitInfoBubbleScreenPositions()
    }

    @objc public func upsertNativeMapExtension(_ extensionId: String, type: String, payload: [String: Any]) {
        model.extensionHost.upsert(extensionId: extensionId, type: type, payload: payload)
    }

    @objc public func removeNativeMapExtension(_ extensionId: String) {
        model.extensionHost.remove(extensionId: extensionId)
    }

    // MARK: - Events

    private func emitMarkerEvent(_ name: String, _ marker: MarkerState) {
        switch name {
        case "markerClick", "markerAnimateStart", "markerAnimateEnd":
            eventHandler?(name, ["markerId": marker.id])
        case "markerDragStart", "markerDrag", "markerDragEnd":
            eventHandler?(name, ["markerId": marker.id, "point": mcPointPayload(GeoPoint.from(position: marker.position))])
            // The dragged marker's geo position just changed but the camera didn't, so the
            // screen-position emissions that normally follow a camera move (see
            // model.onCameraMoveStart/Move/End in init()) never fire on their own here -
            // without this, an open InfoBubble anchored to this marker would stay frozen at
            // the pre-drag location.
            emitMarkerScreenPositions()
            emitInfoBubbleScreenPositions()
        default:
            break
        }
    }

    private func emitMarkerScreenPositions() {
        let tilingActive = model.markers.count >= model.tiling.minMarkerCount
        if tilingActive || model.markers.isEmpty {
            if emittedEmptyMarkerScreenPositions { return }
            emittedEmptyMarkerScreenPositions = true
            eventHandler?("markerScreenPositions", ["positions": []])
            return
        }
        emittedEmptyMarkerScreenPositions = false
        guard let holder = model.state.getMapViewHolder() else { return }
        let positions: [[String: Any]] = model.markers.compactMap { marker in
            guard let offset = holder.toScreenOffset(position: marker.position) else { return nil }
            return ["markerId": marker.id, "x": offset.x, "y": offset.y]
        }
        eventHandler?("markerScreenPositions", ["positions": positions])
    }

    private func emitInfoBubbleScreenPositions() {
        if infoBubblePositions.isEmpty {
            if emittedEmptyInfoBubbleScreenPositions { return }
            emittedEmptyInfoBubbleScreenPositions = true
            eventHandler?("infoBubbleScreenPositions", ["positions": []])
            return
        }
        emittedEmptyInfoBubbleScreenPositions = false
        guard let holder = model.state.getMapViewHolder() else { return }
        let positions: [[String: Any]] = infoBubblePositions.compactMap { entry in
            guard let offset = holder.toScreenOffset(position: entry.point) else { return nil }
            return ["id": entry.id, "x": offset.x, "y": offset.y]
        }
        eventHandler?("infoBubbleScreenPositions", ["positions": positions])
    }
}

final class ReactNativeHereModel: ObservableObject {
    let state = HereMapViewState()
    @Published var markers: [MarkerState] = []
    @Published var circles: [CircleState] = []
    @Published var polygons: [PolygonState] = []
    @Published var polylines: [PolylineState] = []
    @Published var groundImages: [GroundImageState] = []
    @Published var rasterLayers: [RasterLayerState] = []
    @Published var tiling = MarkerTilingOptions.Default

    var accessKeyId: String?
    var accessKeySecret: String?

    var emit: (String, [String: Any]) -> Void = { _, _ in }
    var onCameraMoveStart: (MapCameraPosition) -> Void = { _ in }
    var onCameraMove: (MapCameraPosition) -> Void = { _ in }
    var onCameraMoveEnd: (MapCameraPosition) -> Void = { _ in }
    var localExtensionFactory: NativeMapExtensionLocalFactory?

    lazy var extensionHost = NativeMapExtensionHost(
        eventSink: { [weak self] extensionId, eventName, payload in
            self?.emit("nativeMapExtensionEvent", ["extensionId": extensionId, "eventName": eventName, "payload": payload])
        },
        localFactory: { [weak self] type, extensionId, eventSink in
            self?.localExtensionFactory?(type, extensionId, eventSink)
        }
    )
}

struct ReactNativeHereRoot: View {
    @ObservedObject var model: ReactNativeHereModel
    @ObservedObject var extensionHost: NativeMapExtensionHost

    var body: some View {
        HereMapView(
            state: model.state,
            onMapLoaded: { _ in model.emit("mapLoaded", [:]) },
            onMapClick: { point in
                if !extensionHost.dispatchMapClick(point, zoom: model.state.cameraPosition.zoom) {
                    model.emit("mapClick", ["point": mcPointPayload(point)])
                }
            },
            onMapLongClick: { model.emit("mapLongClick", ["point": mcPointPayload($0)]) },
            onCameraMoveStart: { model.onCameraMoveStart($0) },
            onCameraMove: { model.onCameraMove($0) },
            onCameraMoveEnd: { model.onCameraMoveEnd($0) },
            sdkInitialize: {
                guard let accessKeyId = model.accessKeyId, let accessKeySecret = model.accessKeySecret,
                      !accessKeyId.isEmpty, !accessKeySecret.isEmpty
                else {
                    NSLog(
                        "[MapConductor] HereMapView: pass `accessKeyId`/`accessKeySecret` props to " +
                            "initialize the HERE SDK on iOS (Android reads them from " +
                            "AndroidManifest.xml meta-data instead)."
                    )
                    return
                }
                do {
                    try hereKeyInitialize(accessKeyId: accessKeyId, accessKeySecret: accessKeySecret)
                } catch {
                    NSLog("[MapConductor] HERE SDK initialization failed: \(error)")
                }
            },
            content: {
                var content = MapViewContent()
                content.markers = model.markers.map(Marker.init(state:))
                content.circles = model.circles.map(Circle.init(state:))
                content.polygons = model.polygons.map(Polygon.init(state:))
                content.polylines = model.polylines.map(Polyline.init(state:))
                content.groundImages = model.groundImages.map(GroundImage.init(state:))
                content.rasterLayers = model.rasterLayers.map(RasterLayer.init(state:))
                content.markerTilingOptions = model.tiling
                mcMergeMapViewContent(extensionHost.content, into: &content)
                return content
            }
        )
    }
}
