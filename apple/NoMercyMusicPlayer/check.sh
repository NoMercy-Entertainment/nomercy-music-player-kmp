#!/usr/bin/env bash
# Build and test the SwiftUI half of the music drop-in, on iOS and on tvOS.
#
# macOS only, and it says so rather than failing obscurely elsewhere. Run from
# the module root:
#
#   ./gradlew assembleNoMercyMusicPlayerXCFramework
#   ./apple/NoMercyMusicPlayer/check.sh
set -euo pipefail

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "xcodebuild not found — the Apple views build on macOS" >&2
  exit 0
fi

FRAMEWORK="build/XCFrameworks/release/NoMercyMusicPlayer.xcframework"
if [ ! -d "$FRAMEWORK" ]; then
  echo "no xcframework — run ./gradlew assembleNoMercyMusicPlayerXCFramework first" >&2
  exit 1
fi

cd apple/NoMercyMusicPlayer

for destination in "generic/platform=iOS" "generic/platform=tvOS"; do
  echo "building for $destination"
  xcodebuild build \
    -scheme NoMercyMusicPlayer-Package \
    -destination "$destination" \
    -quiet
done

# By id, not by name. Simulator names carry their generation in brackets —
# "Apple TV 4K (3rd generation)" — and matching the readable part of that gives
# a name no device has, which xcodebuild reports by listing every destination it
# does know about, visionOS included.
device_id() {
  xcrun simctl list devices available | grep -m1 "$1" | grep -oE '[0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12}'
}

# The -Package scheme, not the product one. SPM generates a scheme per product
# plus one for the package, and only the package scheme carries the test target.
run_tests() {
  local udid
  udid="$(device_id "$2")"
  if [ -z "$udid" ]; then
    echo "no $1 simulator installed — install one from Xcode > Settings > Components" >&2
    exit 1
  fi
  echo "testing on $2 ($udid)"
  xcodebuild test -scheme NoMercyMusicPlayer-Package -destination "id=$udid" -quiet
}

run_tests iOS "iPhone"
run_tests tvOS "Apple TV"

echo "Apple music views: build on iOS and tvOS, behaviour gates green on both"
