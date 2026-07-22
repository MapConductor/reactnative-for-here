import type React from 'react';
import type { HostComponent, NativeMethods } from 'react-native';
import type { NativeHereViewProps } from './HereViewNativeComponent';

export type HereMapViewRef = React.ComponentRef<HostComponent<NativeHereViewProps>> & NativeMethods;
export type HereActualMap = null;
