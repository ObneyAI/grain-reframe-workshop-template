#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec env APP_DEV_PORTLESS=true "$repo_root/scripts/dev" foreground "$@"
