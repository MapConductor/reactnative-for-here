import type { ViewProps } from 'react-native';
import { requireNativeComponent } from 'react-native';
import type { GeoPoint, MapCameraPosition, MarkerTilingOptions } from '@mapconductor/js-sdk-core';
import type { NativeMapExtensionEvent } from '@mapconductor/js-sdk-react/native';

export interface NativeHereViewEvent<T> {
  nativeEvent: T;
}

export interface NativeMarkerTilingOptions {
  enabled: boolean;
  debugTileOverlay: boolean;
  minMarkerCount: number;
  cacheSize: number;
  /**
   * A JS function can't cross the RN bridge, so this only signals that
   * `iconScaleCallback` is set; the native wrapper resolves the actual
   * per-marker scale by calling back into JS via MarkerScaleBridge (JSI).
   */
  hasIconScaleCallback: boolean;
}

export interface NativeHereViewProps extends ViewProps {
  accessKeyId?: string;
  accessKeySecret?: string;
  cameraPosition?: {
    position: {
      latitude: number;
      longitude: number;
      altitude?: number | null;
    };
    zoom: number;
    bearing: number;
    tilt: number;
  };
  mapDesignType?: string;
  markerTilingOptions?: NativeMarkerTilingOptions;
  infoBubblePositions?: Array<{
    id: string;
    latitude: number;
    longitude: number;
    altitude?: number | null;
  }>;
  onMapLoaded?: () => void;
  onMarkerCompositionBatchProcessed?: (
    event: NativeHereViewEvent<{ generation: number; sequence: number }>
  ) => void;
  onMapClick?: (event: NativeHereViewEvent<{ point: GeoPoint }>) => void;
  onMapLongClick?: (event: NativeHereViewEvent<{ point: GeoPoint }>) => void;
  onCameraMoveStart?: (event: NativeHereViewEvent<{ cameraPosition: MapCameraPosition }>) => void;
  onCameraMove?: (event: NativeHereViewEvent<{ cameraPosition: MapCameraPosition }>) => void;
  onCameraMoveEnd?: (event: NativeHereViewEvent<{ cameraPosition: MapCameraPosition }>) => void;
  onMarkerClick?: (event: NativeHereViewEvent<{ markerId: string }>) => void;
  onCircleClick?: (event: NativeHereViewEvent<{ circleId: string; point: GeoPoint }>) => void;
  onGroundImageClick?: (
    event: NativeHereViewEvent<{ groundImageId: string; point: GeoPoint }>
  ) => void;
  onPolylineClick?: (event: NativeHereViewEvent<{ polylineId: string; point: GeoPoint }>) => void;
  onPolygonClick?: (event: NativeHereViewEvent<{ polygonId: string; point: GeoPoint }>) => void;
  onMarkerDragStart?: (event: NativeHereViewEvent<{ markerId: string; point: GeoPoint }>) => void;
  onMarkerDrag?: (event: NativeHereViewEvent<{ markerId: string; point: GeoPoint }>) => void;
  onMarkerDragEnd?: (event: NativeHereViewEvent<{ markerId: string; point: GeoPoint }>) => void;
  onMarkerAnimateStart?: (event: NativeHereViewEvent<{ markerId: string }>) => void;
  onMarkerAnimateEnd?: (event: NativeHereViewEvent<{ markerId: string }>) => void;
  onMarkerScreenPositions?: (
    event: NativeHereViewEvent<{
      positions: Array<{ markerId: string; x: number; y: number }>;
    }>
  ) => void;
  onInfoBubbleScreenPositions?: (
    event: NativeHereViewEvent<{
      positions: Array<{ id: string; x: number; y: number }>;
    }>
  ) => void;
  onNativeMapExtensionEvent?: (event: NativeHereViewEvent<NativeMapExtensionEvent>) => void;
}

export function toNativeMarkerTilingOptions(
  markerTilingOptions: MarkerTilingOptions | undefined
): NativeMarkerTilingOptions | undefined {
  if (!markerTilingOptions) return undefined;
  return {
    enabled: markerTilingOptions.enabled,
    debugTileOverlay: markerTilingOptions.debugTileOverlay,
    minMarkerCount: markerTilingOptions.minMarkerCount,
    cacheSize: markerTilingOptions.cacheSize,
    hasIconScaleCallback: markerTilingOptions.iconScaleCallback != null,
  };
}

export function toNativeCameraPosition(cameraPosition: MapCameraPosition | undefined) {
  if (!cameraPosition) return undefined;

  return {
    position: {
      latitude: cameraPosition.position.latitude,
      longitude: cameraPosition.position.longitude,
      altitude: cameraPosition.position.altitude ?? 0,
    },
    zoom: cameraPosition.zoom,
    bearing: cameraPosition.bearing,
    tilt: cameraPosition.tilt,
  };
}

export default requireNativeComponent<NativeHereViewProps>(
  // Align to android/src/main/java/com/mapconductor/react/here/MapConductorHereViewManager.kt (REACT_CLASS)
  // and ios/MapConductorHereViewManager.m (RCT_EXPORT_MODULE).
  'HereMapView'
);
