#!/bin/bash
# Headless Twigmark / Docear MCP on the VPS.
# API key is read from /opt/docear/mcp-api-key.txt (not hardcoded).
set -euo pipefail

export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

RUNTIME=/opt/docear/runtime
DATA=/data/docear
MAPS=/data/mindmaps
API_KEY_FILE=/opt/docear/mcp-api-key.txt
DISPLAY_NUM=99
export DISPLAY=":${DISPLAY_NUM}"

if [[ ! -f "$API_KEY_FILE" ]]; then
  echo "Missing MCP API key file: $API_KEY_FILE" >&2
  exit 1
fi
MCP_API_KEY="$(tr -d '\r\n' < "$API_KEY_FILE")"
if [[ -z "$MCP_API_KEY" ]]; then
  echo "Empty MCP API key in $API_KEY_FILE" >&2
  exit 1
fi

mkdir -p "$DATA" "$MAPS" /var/log/docear
printf '%s\n' "$MAPS" > "$RUNTIME/working-directory.txt"
printf '%s\n' "$DATA" > "$RUNTIME/config-directory.txt"
cd "$RUNTIME"

# Clean stale virtual display from previous crashes.
if pgrep -f "Xvfb :${DISPLAY_NUM}" >/dev/null 2>&1; then
  pkill -f "Xvfb :${DISPLAY_NUM}" || true
  sleep 1
fi
rm -f "/tmp/.X${DISPLAY_NUM}-lock" "/tmp/.X11-unix/X${DISPLAY_NUM}" 2>/dev/null || true

Xvfb ":${DISPLAY_NUM}" -screen 0 800x600x16 -ac +extension GLX +render -noreset &
XVFB_PID=$!

cleanup() {
  kill "$XVFB_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for _ in $(seq 1 30); do
  if [ -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ]; then
    break
  fi
  sleep 0.2
done

if [ ! -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ]; then
  echo "Xvfb failed to start on ${DISPLAY}" >&2
  exit 1
fi

exec java -Xms64m -Xmx512m -XX:+UseSerialGC -Xss256k -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError \
  -Dorg.knopflerfish.framework.bundlestorage=memory \
  -Dorg.freeplane.globalresourcedir="$RUNTIME/resources" \
  -Dorg.knopflerfish.gosg.jars=reference:file:"$RUNTIME/core/" \
  -Dorg.docear.working.directory="$MAPS" \
  -Dorg.docear.config.directory="$DATA" \
  -Dorg.freeplane.userfpdir="$DATA" \
  -Dgit.repo.path="$MAPS" \
  -Dmcp.enabled=true \
  -Dmcp.host=127.0.0.1 \
  -Dmcp.port=7720 \
  -Dmcp.auth.enabled=true \
  -Dmcp.auth.apiKey="${MCP_API_KEY}" \
  -Dmcp.web.enabled=true \
  -Dmcp.web.readOnlyTools=true \
  -Dmcp.publicBaseUrl=https://webchat.mantoublog.top \
  -Dmcp.oauth.role=write \
  -Dmcp.oauth.accessTtlSeconds=0 \
  -Dmcp.skipFullTagScan=true \
  -Dmcp.lowMemory=true \
  -Dmcp.auth.role=owner \
  -Dmcp.edtTimeoutMs=90000 \
  -Duser.language=zh -Duser.country=CN \
  -jar "$RUNTIME/framework.jar" \
  -xargs "$RUNTIME/props.xargs" \
  -xargs "$RUNTIME/init.xargs"
