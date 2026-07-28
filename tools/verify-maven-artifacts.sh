#!/usr/bin/env bash
# Publish into the local Maven repository and check what came out.
#
# The question this answers is not "did the build succeed" — a publication can
# succeed and still be unresolvable, because what a multiplatform consumer
# resolves against is the Gradle Module Metadata, and a target missing from the
# variants array is a target nobody can depend on. That failure surfaces in
# somebody else's build, weeks later, as "could not find a variant matching".
#
# No network and no secrets. Central itself is CI-only; this is the honest dry
# run, and the throwaway consumer in Task 7 is the other half of the proof.
#
#   tools/verify-maven-artifacts.sh nomercy-player-core-kmp 2.0.0-rc.1
set -euo pipefail

group_path="tv/nomercy"
artifact="${1:?usage: verify-maven-artifacts.sh <artifact-id> <version> [targets]}"

# Which variants this artifact is expected to carry. The engines carry all
# seven; the Compose chrome carries two, because a Compose surface on iOS
# fights the native app it would be embedded in and Apple gets SwiftUI
# instead. Hardcoding seven made the chrome unverifiable rather than wrong.
targets="${3:-androidRelease jvm iosArm64 iosSimulatorArm64 iosX64 tvosArm64 tvosSimulatorArm64}"
version="${2:?version required}"
here="$(cd "$(dirname "$0")/.." && pwd)"

( cd "$here" && ./gradlew publishToMavenLocal )

base="$HOME/.m2/repository/$group_path/$artifact/$version"
module="$base/$artifact-$version.module"

if [ ! -f "$module" ]; then
  echo "MISSING module metadata: $module" >&2
  exit 1
fi

# Every variant the module actually declares. The names carry hyphens
# (`jvmApiElements-published`), so the pattern has to allow them: a character
# class of letters and digits alone matched two housekeeping entries and nothing
# else, and the script then reported every target missing from a module that
# had them all.
present="$(grep -oE '"name": "[^"]+"' "$module" | sed 's/.*: "//;s/"//' | sort -u)"

# All seven, on any host. Kotlin/Native cross-compiles klibs for the Apple
# targets from Windows — verified, there is a real 800KB iosArm64 klib in the
# local repository next to this — so the Maven side is complete here. What still
# needs macOS is the XCFramework: a klib is not a framework, and the cinterop the
# subtitle module does needs the Xcode toolchain. That is P29 Task 5's gate, not
# this one's.
missing=0
for target in $targets; do
  # androidRelease publishes as "android*"; the rest carry their own name.
  prefix="$target"
  [ "$target" = "androidRelease" ] && prefix="android"

  echo "$present" | grep -qE "^${prefix}[A-Za-z]*Elements" || {
    echo "MISSING variant: $target"
    missing=1
  }
done

test -f "$base/$artifact-$version.pom" || { echo "MISSING pom"; exit 1; }

# The sources jar is per-target on a multiplatform publication, so its name is
# not predictable from the artifact id. What matters is that one exists at all:
# Central rejects a publication without sources.
ls "$base"/*-sources.jar >/dev/null 2>&1 || { echo "MISSING sources jar"; exit 1; }

if [ "$missing" -eq 0 ]; then
  echo "OK: $artifact:$version carries every target variant, a pom and sources"
else
  echo "FAIL: the module metadata has variant gaps — a consumer on that target cannot resolve" >&2
  exit 1
fi
