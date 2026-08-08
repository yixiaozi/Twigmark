#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
test -f "$ROOT/README.md"
test -f "$ROOT/UPSTREAM.md"
test -f "$ROOT/migration/PINNED_VERSION"
test -f "$ROOT/migration/FEATURE_PORT.md"
pin="$(tr -d '[:space:]' < "$ROOT/migration/PINNED_VERSION")"
echo "Twigmark2 scaffold OK (pinned upstream: $pin)"
