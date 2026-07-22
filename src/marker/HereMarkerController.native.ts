import type { MarkerAnimation, MarkerState } from '@mapconductor/js-sdk-core';
import {
  markerIconToNative,
  type NativeMarkerIconPayload,
} from '@mapconductor/js-sdk-react/native';

export interface NativeHereMarkerState {
  id: string;
  position: MarkerState['position'];
  clickable: boolean;
  draggable: boolean;
  zIndex: number;
  icon: NativeMarkerIconPayload | null;
  animation: MarkerAnimation | null;
}

export function markerStateToNative(state: MarkerState): NativeHereMarkerState {
  return {
    id: state.id,
    position: state.position,
    clickable: state.clickable,
    draggable: state.draggable,
    zIndex: state.zIndex,
    icon: markerIconToNative(state.icon),
    animation: state.animation,
  };
}
