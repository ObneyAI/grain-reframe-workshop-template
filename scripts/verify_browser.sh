#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

mode="${1:-}"
if [[ "$mode" != "development" && "$mode" != "production" ]]; then
  echo "Usage: $0 development|production" >&2
  exit 2
fi

if ! java -version >/dev/null 2>&1 && [[ -x /opt/homebrew/opt/openjdk@21/bin/java ]]; then
  export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
fi

missing=()
for command_name in curl java clojure node npm; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    missing+=("$command_name")
  fi
done
if [[ "${#missing[@]}" -gt 0 ]]; then
  echo "Missing required browser-test tools: ${missing[*]}" >&2
  exit 1
fi

run_dir="$(mktemp -d "${TMPDIR:-/tmp}/grain-reframe-template-browser.XXXXXX")"
backend_pid=""
compiler_pid=""

cleanup() {
  local pid
  for pid in "$backend_pid" "$compiler_pid"; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
  done
  rm -rf "$run_dir"
}
trap cleanup EXIT INT TERM

port="$(node -e '
  const net = require("net");
  const server = net.createServer();
  server.listen(0, "127.0.0.1", () => {
    process.stdout.write(String(server.address().port));
    server.close();
  });
')"
base_url="http://127.0.0.1:$port"

echo "==> Building $mode browser assets"
npm run build --workspace @grain/shadcn
if [[ "$mode" == "production" ]]; then
  npm run build --workspace ui/web-app
else
  npm run css:build --workspace ui/web-app
  (cd ui/web-app && npx shadow-cljs compile app)
  (cd ui/web-app && exec npx shadow-cljs watch app) >"$run_dir/shadow.log" 2>&1 &
  compiler_pid="$!"
fi

echo "==> Starting isolated backend at $base_url"
env \
  APP_ENV=development \
  APP_HTTP_PORT="$port" \
  APP_BASE_URL="$base_url" \
  APP_STORAGE_DIR="$run_dir/storage" \
  APP_AUTH_COOKIE_NAME="grain-reframe-template-browser-$mode" \
  clojure -M:dev -m app.dev.main >"$run_dir/backend.log" 2>&1 &
backend_pid="$!"

ready="false"
for _attempt in $(seq 1 60); do
  if curl --silent --fail "$base_url/healthcheck" >/dev/null 2>&1; then
    ready="true"
    break
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    break
  fi
  sleep 1
done

if [[ "$ready" != "true" ]]; then
  echo "Backend failed to become healthy. Backend log:" >&2
  sed -n '1,240p' "$run_dir/backend.log" >&2
  if [[ -f "$run_dir/shadow.log" ]]; then
    echo "Shadow CLJS log:" >&2
    sed -n '1,240p' "$run_dir/shadow.log" >&2
  fi
  exit 1
fi

echo "==> Running $mode browser contract"
if ! PLAYWRIGHT_BASE_URL="$base_url" npx playwright test; then
  echo "Backend log:" >&2
  sed -n '1,240p' "$run_dir/backend.log" >&2
  if [[ -f "$run_dir/shadow.log" ]]; then
    echo "Shadow CLJS log:" >&2
    sed -n '1,240p' "$run_dir/shadow.log" >&2
  fi
  exit 1
fi

echo "$mode browser contract passed."
