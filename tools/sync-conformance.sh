#!/usr/bin/env bash
# Vendor the conformance kit and the pinned contract into this repo's tests.
#
# The kit is one logic source in core-kmp; every repo gets a copy because CI
# checks out one repository at a time and a test that reached across the
# monorepo would pass here and fail there.
#
# contract.lock is the sha256 of the vendored contract. The drift alarm compares
# it against what the web trio has published: a copy that silently falls behind
# is a native port measuring itself with last month's ruler.
#
#   tools/sync-conformance.sh            # core, in place
#   tools/sync-conformance.sh <repo-dir> # video or music, from core
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
core="${1:-$here}"

if [ ! -d "$core/conformance-kit" ]; then
  echo "no conformance-kit in $core — that is the single source and it is missing" >&2
  exit 1
fi

kit_dst="$here/src/jvmTest/kotlin/tv/nomercy/player/conformance"

# Emptied first, not merged into. A kit file that gets renamed would otherwise
# leave its old copy behind for good, and two files declaring the same class is
# a redeclaration error nobody can trace back to a rename.
rm -rf "$kit_dst"
mkdir -p "$kit_dst" "$here/contract" "$here/scenarios"

cp "$core"/conformance-kit/*.kt "$kit_dst/"

# Only when syncing from elsewhere. Copying core's fixtures over its own is a
# no-op at best and, if the paths ever diverge, a file copied onto itself.
if [ "$core" != "$here" ]; then
  cp "$core/contract/contract.json" "$here/contract/contract.json"
  cp "$core/scenarios/scenarios.json" "$here/scenarios/scenarios.json"
fi

sha256sum "$here/contract/contract.json" | awk '{print $1}' > "$here/contract.lock"

# The scenarios too. The contract says what the surface is and the scenarios say
# how it behaves, and a repo measuring behaviour with an edited copy is the
# quieter of the two failures: the shape gate still passes and the ordering it
# asserts is whatever somebody typed.
sha256sum "$here/scenarios/scenarios.json" | awk '{print $1}' > "$here/scenarios.lock"

echo "vendored $(ls "$kit_dst" | wc -l | tr -d ' ') kit file(s)"
echo "locked contract $(cut -c1-16 < "$here/contract.lock") scenarios $(cut -c1-16 < "$here/scenarios.lock")"
