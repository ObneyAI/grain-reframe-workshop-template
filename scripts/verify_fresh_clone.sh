#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)

for command in git tar bb npm node clojure curl allium clj-kondo rg; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Fresh-clone verification requires '$command' on PATH." >&2
    exit 1
  fi
done

temporary_root=$(mktemp -d "${TMPDIR:-/tmp}/grain-reframe-template-clone.XXXXXX")
clone_root="$temporary_root/acceptance-app"
storage_root="$temporary_root/storage"
server_started="false"

cleanup() {
  if [ "$server_started" = "true" ] && [ -d "$clone_root" ]; then
    (cd "$clone_root" && ./scripts/dev down) >/dev/null 2>&1 || true
  fi
  rm -rf "$temporary_root"
}
trap cleanup EXIT
trap 'exit 130' HUP INT TERM

mkdir -p "$clone_root"
starter_ref=$(git -C "$repo_root" rev-parse --short HEAD)
git -C "$repo_root" archive --format=tar HEAD | tar -xf - -C "$clone_root"

port=$(node -e '
  const net = require("node:net");
  const server = net.createServer();
  server.listen(0, "127.0.0.1", () => {
    process.stdout.write(String(server.address().port));
    server.close();
  });
')

echo "==> Initialize tracked files from $starter_ref"
(
  cd "$clone_root"
  STARTER_REF="$starter_ref" bb scripts/init_project.bb \
    --slug acceptance-app \
    --name "Acceptance App" \
    --port "$port" \
    --locale en-CA \
    --time-zone America/Chicago
)

echo "==> Establish the initialized Git baseline required by Polylith"
(
  cd "$clone_root"
  git init --quiet
  git add --all
  git -c user.name="Grain Re-frame Template Acceptance" \
      -c user.email="acceptance@grain-reframe-workshop-template.local" \
      commit --quiet --message="Initialize acceptance app"
)

echo "==> Install JavaScript dependencies from the lockfile"
(cd "$clone_root" && npm ci)

echo "==> Prove the lockfile-installed shadcn CLI extension path"
(
  cd "$clone_root"
  npm run shadcn:add -- badge
  test -f ui/shadcn/src/components/ui/badge.tsx
  npm test --workspace @grain/shadcn
  npm run build --workspace @grain/shadcn
)

echo "==> Prepare Clojure dependencies from the fresh tree"
(cd "$clone_root" && clojure -P -M:dev && clojure -P -M:poly)

echo "==> Run the complete quality gate"
(cd "$clone_root" && ./scripts/verify-specs.sh)

echo "==> Start the initialized application on $port"
(
  cd "$clone_root"
  env \
    APP_ENV=development \
    APP_NAME="Acceptance App" \
    APP_HTTP_PORT="$port" \
    APP_BASE_URL="http://localhost:$port" \
    APP_DEV_PORTLESS=false \
    APP_DEV_INFRA=false \
    APP_TENANT_ID="11111111-1111-4111-8111-111111111111" \
    APP_AUTH_COOKIE_NAME="acceptance-app-session" \
    APP_STORAGE_DIR="$storage_root" \
    ./scripts/dev up
)
server_started="true"
(cd "$clone_root" && ./scripts/dev status)

assert_status() {
  expected=$1
  url=$2
  actual=$(curl --silent --show-error --output "$temporary_root/response" \
                --write-out '%{http_code}' "$url")
  if [ "$actual" != "$expected" ]; then
    echo "Expected HTTP $expected from $url, received $actual." >&2
    exit 1
  fi
}

assert_status 200 "http://localhost:$port/healthcheck"
assert_status 200 "http://localhost:$port/"
if ! rg -q '<meta name="app-locale" content="en-CA">' "$temporary_root/response" || \
   ! rg -q '<meta name="app-time-zone" content="America/Chicago">' "$temporary_root/response"; then
  echo "Initialized locale/time-zone settings were not exposed in the SPA document." >&2
  exit 1
fi
assert_status 200 "http://localhost:$port/auth/sign-up"
assert_status 200 "http://localhost:$port/examples/routes?record-id=example-record&tab=history"
assert_status 404 "http://localhost:$port/not-a-real-page"

if ! rg -q '<div id="root"></div>' "$temporary_root/response"; then
  echo "Unknown browser route did not return the SPA document." >&2
  exit 1
fi

(cd "$clone_root" && ./scripts/dev down)
server_started="false"

echo "Fresh-clone acceptance passed for $starter_ref on port $port."
