import type React from 'react';
import type { GeoPoint, MapViewHolder, Offset } from '@mapconductor/js-sdk-core';
import type { HereMapViewRef } from './HereTypeAlias.native';

export class HereMapViewHolder implements MapViewHolder<HereMapViewRef | null, null> {
  readonly map = null;

  constructor(private readonly nativeRef: React.RefObject<HereMapViewRef | null>) {}

  get mapView(): HereMapViewRef | null {
    return this.nativeRef.current;
  }

  toScreenOffset(_position: GeoPoint): null {
    return null;
  }

  async fromScreenOffset(_offset: Offset): Promise<GeoPoint | null> {
    return null;
  }

  fromScreenOffsetSync(_offset: Offset): GeoPoint | null {
    return null;
  }
}
