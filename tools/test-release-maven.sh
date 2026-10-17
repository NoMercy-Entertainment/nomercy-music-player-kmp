#!/usr/bin/env bash
set -euo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin" "$tmp/work"

cat > "$tmp/work/gradlew" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
from pathlib import Path
import os

root = Path('build/release-maven')
for index in range(int(os.environ['TEST_ARTIFACTS'])):
    artifact = 'fixture-%03d' % index
    directory = root / 'test' / 'release' / artifact / '9.9.9'
    directory.mkdir(parents=True, exist_ok=True)
    for extension in ('jar', 'klib', 'pom', 'module'):
        main = directory / ('%s-9.9.9.%s' % (artifact, extension))
        for suffix in ('', '.asc'):
            file = Path(str(main) + suffix)
            file.write_text('fixture\n')
            for checksum in ('.md5', '.sha1', '.sha256', '.sha512'):
                Path(str(file) + checksum).write_text('checksum\n')
    for suffix in ('', '.md5', '.sha1', '.sha256', '.sha512'):
        (directory / ('maven-metadata.xml' + suffix)).write_text('metadata\n')
PY
SH
chmod +x "$tmp/work/gradlew"

cat > "$tmp/bin/gh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$1 $2 $3" >> "$TEST_GH_CALLS"
case "$1 $2" in
  'release view')
    if [ "${4:-}" = '--json' ] && [ "${5:-}" = 'assets' ]; then
      printf '{"assets": []}\n'
    else
      exit 1
    fi
    ;;
  'release create') ;;
  'release upload')
    shift 3
    printf '%s\n' "$@" >> "$TEST_GH_UPLOADS"
    ;;
  *) echo "unexpected gh call: $*" >&2; exit 1 ;;
esac
SH
chmod +x "$tmp/bin/gh"

export PATH="$tmp/bin:$PATH"
export TEST_GH_CALLS="$tmp/calls"
export TEST_GH_UPLOADS="$tmp/uploads"
export GITHUB_REF_NAME=v9.9.9

cd "$tmp/work"
export TEST_ARTIFACTS=2
if output=$(/bin/bash "$repo/tools/release-maven.sh" 2>&1); then
  printf '%s\n' "$output"
else
  printf '%s\n' "$output" >&2
  exit 1
fi

python3 - "$TEST_GH_CALLS" "$TEST_GH_UPLOADS" <<'PY'
from pathlib import Path
import sys

calls = Path(sys.argv[1]).read_text().splitlines()
if calls != ['release view v9.9.9', 'release create v9.9.9',
             'release view v9.9.9', 'release upload v9.9.9']:
    raise SystemExit('unexpected gh calls: %r' % calls)

root = Path('build/release-maven')
main = {str(path) for path in root.rglob('*')
        if path.suffix in ('.jar', '.klib', '.pom', '.module')}
expected = main | {path + suffix for path in main for suffix in ('.asc', '.sha1')}
uploaded = Path(sys.argv[2]).read_text().splitlines()
if len(uploaded) != len(set(uploaded)) or set(uploaded) != expected:
    raise SystemExit('upload list differs from main, .asc and .sha1 files: '
                     'missing=%r extra=%r' %
                     (sorted(expected - set(uploaded)), sorted(set(uploaded) - expected)))
if len(uploaded) != 24:
    raise SystemExit('expected 24 uploads, got %d' % len(uploaded))
PY

rm -rf build "$TEST_GH_CALLS" "$TEST_GH_UPLOADS"
export TEST_ARTIFACTS=84 # 84 artifacts x 4 main files x 3 kept files = 1008
if output=$(/bin/bash "$repo/tools/release-maven.sh" 2>&1); then
  echo 'asset-limit case unexpectedly succeeded' >&2
  exit 1
fi
case "$output" in
  *'1008 Maven files exceed the release asset limit'*) ;;
  *) printf '%s\n' "$output" >&2; exit 1 ;;
esac
if [ -e "$TEST_GH_CALLS" ] || [ -e "$TEST_GH_UPLOADS" ]; then
  echo 'asset-limit case called gh' >&2
  exit 1
fi

echo 'release-maven smoke test passed'
