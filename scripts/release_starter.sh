#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

if ! java -version >/dev/null 2>&1 && [[ -x /opt/homebrew/opt/openjdk@21/bin/java ]]; then
  export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
fi
if ! command -v bun >/dev/null 2>&1 && [[ -x "$HOME/.bun/bin/bun" ]]; then
  export PATH="$HOME/.bun/bin:$PATH"
fi

tag="${1:-}"
if [[ ! "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: $0 vMAJOR.MINOR.PATCH" >&2
  exit 2
fi

if [[ "$(git branch --show-current)" != "main" ]]; then
  echo "Starter releases must be tagged from main." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Starter releases require a clean worktree." >&2
  git status --short >&2
  exit 1
fi

if git rev-parse --verify --quiet "refs/tags/$tag" >/dev/null; then
  echo "Tag already exists: $tag" >&2
  exit 1
fi

echo "==> Verifying committed files in a freshly initialized copy"
./scripts/verify_fresh_clone.sh

echo "==> Verifying development and production browser contracts"
bun run test:browser

git tag --annotate "$tag" --message "Grain starter $tag"
echo "Created verified local tag $tag. Push main and the tag only after the remote CI gate passes."
