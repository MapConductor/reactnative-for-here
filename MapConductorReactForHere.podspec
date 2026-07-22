require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name = "MapConductorReactForHere"
  s.version = package["version"]
  s.summary = package["description"]
  s.license = package["license"]
  s.author = package["author"]
  s.homepage = "https://github.com/mapconductor/react-sdk"
  s.source = { :path => __dir__ }
  s.platform = :ios, "16.0"
  s.source_files = "ios/*.{h,m,mm,swift}"
  # MapConductorForHERE is a source pod (see ios-sdk/ios-for-here's own podspec), which itself
  # vendors the proprietary heresdk.xcframework directly since HERE has no public CocoaPods spec
  # to depend on - see that podspec's comment and ios-sdk/CLAUDE.md's "iOS Provider Distribution"
  # section for why this deviates from MapLibre/GoogleMaps' plain `s.dependency "VendorSDK"`.
  s.dependency "React-Core"
  s.dependency "MapConductorCore"
  s.dependency "MapConductorReactNativeCore"
  s.dependency "MapConductorReactMarkerClustering"
  s.dependency "MapConductorForHERE"
end
