require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "RNBluetoothEscposPrinter"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.author       = package["author"]["name"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.platform     = :ios, "13.0"
  s.swift_version = "5.9"
  s.source       = { :git => package["repository"]["url"], :tag => "#{s.version}" }
  s.source_files = "ios/Sources/**/*.{swift}"
  s.requires_arc = true
  s.dependency "React-Core"
  s.pod_target_xcconfig = {
    "SWIFT_OBJC_BRIDGING_HEADER" => "$(PODS_TARGET_SRCROOT)/ios/Sources/RNBluetoothEscposPrinter-Bridging-Header.h",
    "DEFINES_MODULE" => "YES"
  }
end