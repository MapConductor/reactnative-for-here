package com.mapconductor.react.here

import com.mapconductor.here.HereMapDesign

/**
 * Maps the `mapDesignType` string prop to a native [HereMapDesign].
 *
 * The JS side (`@mapconductor/react-for-here`'s `HereMapDesign`) only exposes the 6 dotted-id
 * schemes its own web raster-tile config supports (`normal.day`, `normal.night`, `satellite.day`,
 * `hybrid.day`, `hybrid.night`, `terrain.day`) - narrower than the 14 `MapScheme` values Android's
 * `HereMapDesign` (android-for-here) actually supports (it additionally has Lite/Logistics/
 * RoadNetwork variants). `terrain.day` has no native HERE equivalent scheme, so it falls back to
 * `RoadNetworkDay` (closest available "road detail without full imagery" scheme); RN apps that
 * need the Android-only extra schemes should pass their `MapScheme` name directly (see the
 * `else` branch below) rather than going through the shared web `HereMapDesign` ids.
 */
fun stringToHereMapDesign(value: String?): HereMapDesign =
    when (value) {
        "normal.day", "NormalDay" -> HereMapDesign.NormalDay
        "normal.night", "NormalNight" -> HereMapDesign.NormalNight
        "satellite.day", "Satellite" -> HereMapDesign.Satellite
        "hybrid.day", "HybridDay" -> HereMapDesign.HybridDay
        "hybrid.night", "HybridNight" -> HereMapDesign.HybridNight
        "terrain.day", "RoadNetworkDay" -> HereMapDesign.RoadNetworkDay
        "LiteDay" -> HereMapDesign.LiteDay
        "LiteNight" -> HereMapDesign.LiteNight
        "LiteHybridDay" -> HereMapDesign.LiteHybridDay
        "LiteHybridNight" -> HereMapDesign.LiteHybridNight
        "LogisticsDay" -> HereMapDesign.LogisticsDay
        "LogisticsNight" -> HereMapDesign.LogisticsNight
        "LogisticsHybridDay" -> HereMapDesign.LogisticsHybridDay
        "RoadNetworkNight" -> HereMapDesign.RoadNetworkNight
        else -> HereMapDesign.NormalDay
    }
