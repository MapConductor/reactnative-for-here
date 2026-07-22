import MapConductorForHERE

/// Maps the `mapDesignType` string prop to a native `HereMapDesign`.
///
/// The JS side (`@mapconductor/react-for-here`'s `HereMapDesign`) only exposes the 6 dotted-id
/// schemes its own web raster-tile config supports (`normal.day`, `normal.night`, `satellite.day`,
/// `hybrid.day`, `hybrid.night`, `terrain.day`) - narrower than the 14 schemes native `HereMapDesign`
/// (ios-for-here) actually supports (it additionally has Lite/Logistics/RoadNetwork variants).
/// `terrain.day` has no native HERE equivalent scheme, so it falls back to `.RoadNetworkDay`
/// (closest available "road detail without full imagery" scheme); RN apps that need the
/// iOS-only extra schemes should pass the scheme's exact case name (see below) rather than going
/// through the shared web `HereMapDesign` ids.
func stringToHereMapDesign(_ value: String?) -> HereMapDesign {
    switch value {
    case "normal.day", "NormalDay": return .NormalDay
    case "normal.night", "NormalNight": return .NormalNight
    case "satellite.day", "Satellite": return .Satellite
    case "hybrid.day", "HybridDay": return .HybridDay
    case "hybrid.night", "HybridNight": return .HybridNight
    case "terrain.day", "RoadNetworkDay": return .RoadNetworkDay
    case "LiteDay": return .LiteDay
    case "LiteNight": return .LiteNight
    case "LiteHybridDay": return .LiteHybridDay
    case "LiteHybridNight": return .LiteHybridNight
    case "LogisticsDay": return .LogisticsDay
    case "LogisticsNight": return .LogisticsNight
    case "LogisticsHybridDay": return .LogisticsHybridDay
    case "LogisticsHybridNight": return .LogisticsHybridNight
    case "RoadNetworkNight": return .RoadNetworkNight
    default: return .NormalDay
    }
}
