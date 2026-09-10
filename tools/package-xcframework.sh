#!/usr/bin/env bash
# Assemble the release XCFramework, zip it, and print the checksum SPM needs.
#
# The checksum is the point. A binaryTarget without one resolves anything the URL
# happens to serve, so a consumer's build silently changes when the release asset
# does — which is the supply-chain shape this ecosystem should never ship.
#
# macOS only, and it says so rather than failing: the Apple slices need the Xcode
# toolchain. Kotlin cross-compiles the klibs from Windows, but a klib is not a
# framework and the subtitle module's cinterop needs Xcode either way.
#
#   tools/package-xcframework.sh NoMercyPlayerCore
set -euo pipefail

name="${1:?usage: package-xcframework.sh <FrameworkName>}"
here="$(cd "$(dirname "$0")/.." && pwd)"

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "SKIP: no Xcode on this host — XCFramework packaging runs on macOS."
  exit 0
fi

( cd "$here" && ./gradlew "assemble${name}ReleaseXCFramework" )

xcf="$here/build/XCFrameworks/release/${name}.xcframework"
if [ ! -d "$xcf" ]; then
  echo "MISSING $xcf — the assemble task logged a different output directory" >&2
  exit 1
fi

# Zipped from the parent so the archive contains the .xcframework directory
# rather than its contents. SPM expects the framework at the archive root, and
# an archive of the contents unpacks into something Xcode cannot find.
( cd "$(dirname "$xcf")" && rm -f "${name}.xcframework.zip" && zip -qry "${name}.xcframework.zip" "${name}.xcframework" )

zip_path="$(dirname "$xcf")/${name}.xcframework.zip"
checksum="$(cd "$here" && swift package compute-checksum "$zip_path")"

echo "XCFRAMEWORK_ZIP=$zip_path"
echo "XCFRAMEWORK_CHECKSUM=$checksum"
