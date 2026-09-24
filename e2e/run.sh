#!/usr/bin/env bash
# Runs the frontend end-to-end checks against a running instance.
#
#   ./e2e/run.sh                                  # against http://localhost:8080
#   BASE=https://quiz-api-....run.app \
#     ADMIN_CODE=<the deployed code> ./e2e/run.sh # against Cloud Run
#
# Needs Node 22+ (for the built-in WebSocket client) and a Chrome or Chromium
# binary. No npm install: the whole harness is two files and the DevTools
# Protocol. Point CHROME at your own binary if the default is not present.
set -euo pipefail

CHROME="${CHROME:-$HOME/Library/Caches/ms-playwright/chromium_headless_shell-1234/chrome-headless-shell-mac-arm64/chrome-headless-shell}"
PORT="${CDP_PORT:-9333}"
PROFILE="$(mktemp -d)"

if [ ! -x "$CHROME" ]; then
  echo "No Chrome at $CHROME - set CHROME=/path/to/chrome (or chrome-headless-shell)." >&2
  exit 1
fi

"$CHROME" --headless --remote-debugging-port="$PORT" --user-data-dir="$PROFILE" \
          --no-first-run --no-default-browser-check --disable-gpu about:blank \
          > "$PROFILE/chrome.log" 2>&1 &
CHROME_PID=$!
trap 'kill "$CHROME_PID" 2>/dev/null || true; rm -rf "$PROFILE"' EXIT

CDP_PORT="$PORT" node "$(dirname "$0")/frontend.mjs"
