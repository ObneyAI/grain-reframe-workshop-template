#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

verification_root=$(mktemp -d "${TMPDIR:-/tmp}/grain-dev-data-test.XXXXXX")
storage_path="$verification_root/storage-test"
unmarked_path="$verification_root/unmarked"

cleanup() {
  if [ -d "$storage_path" ]; then
    bb dev reset --storage-dir "$storage_path" --yes >/dev/null 2>&1 || true
  fi
  if [ -d "$unmarked_path" ]; then
    rmdir "$unmarked_path" 2>/dev/null || true
  fi
  rmdir "$verification_root" 2>/dev/null || true
}
trap cleanup EXIT

bb dev seed --storage-dir "$storage_path" >/dev/null
test -f "$storage_path/.grain-development-storage"
bb dev reset --storage-dir "$storage_path" --yes >/dev/null
test ! -e "$storage_path"

mkdir "$unmarked_path"
if bb dev reset --storage-dir "$unmarked_path" --yes >/dev/null 2>&1; then
  echo "Development reset accepted an unmarked custom directory." >&2
  exit 1
fi
rmdir "$unmarked_path"

if APP_ENV=production bb dev reset --yes >/dev/null 2>&1; then
  echo "Development reset ran under APP_ENV=production." >&2
  exit 1
fi

if bb dev reset --storage-dir / --yes >/dev/null 2>&1; then
  echo "Development reset accepted the filesystem root." >&2
  exit 1
fi

if bb dev reset --storage-dir "${TMPDIR:-/tmp}" --yes >/dev/null 2>&1; then
  echo "Development reset accepted the system temporary root." >&2
  exit 1
fi

echo "Development data lifecycle safety checks passed."
