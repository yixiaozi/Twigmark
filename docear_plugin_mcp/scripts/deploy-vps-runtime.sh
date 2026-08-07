#!/usr/bin/env bash
# Package Freeplane + Docear MCP plugins and upload to a Linux VPS.
# Usage:
#   MCP_API_KEY=tm_xxx ./deploy-vps-runtime.sh root@HOST
# Optional env:
#   MCP_HOST=0.0.0.0 MCP_PORT=7720
set -euo pipefail
TARGET="${1:?usage: $0 root@host}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

MCP_HOST="${MCP_HOST:-0.0.0.0}"
MCP_PORT="${MCP_PORT:-7720}"
if [[ -z "${MCP_API_KEY:-}" ]]; then
  MCP_API_KEY="tm_$(openssl rand -hex 24 2>/dev/null || python3 -c 'import secrets;print(secrets.token_hex(24))')"
  echo "Generated MCP_API_KEY (also written to /opt/docear/mcp-api-key.txt on server)"
fi

mkdir -p "$STAGE/runtime/plugins" "$STAGE/bin"
if [[ -d "$ROOT/freeplane_framework/build" ]]; then
  rsync -a \
    --exclude 'freeplane.exe' --exclude 'freeplaneConsole.exe' --exclude '*.dll' --exclude '*.bat' \
    "$ROOT/freeplane_framework/build/" "$STAGE/runtime/"
fi
# Always refresh Freeplane core jars when present (needed for EmptyDirectorySeeder etc.)
if [[ -f "$ROOT/freeplane/dist/lib/freeplaneviewer.jar" ]]; then
  mkdir -p "$STAGE/runtime/core/org.freeplane.core/lib"
  cp -f "$ROOT/freeplane/dist/lib/freeplaneviewer.jar" "$STAGE/runtime/core/org.freeplane.core/lib/"
  cp -f "$ROOT/freeplane/dist/lib/freeplaneeditor.jar" "$STAGE/runtime/core/org.freeplane.core/lib/" 2>/dev/null || true
fi
rsync -a "$ROOT/docear_plugin_core/dist/org.docear.plugin.core/" "$STAGE/runtime/plugins/org.docear.plugin.core/"
if [[ -d "$ROOT/docear_plugin_ai/dist/org.docear.plugin.ai" ]]; then
  rsync -a "$ROOT/docear_plugin_ai/dist/org.docear.plugin.ai/" "$STAGE/runtime/plugins/org.docear.plugin.ai/"
fi
rsync -a "$ROOT/docear_plugin_mcp/dist/org.docear.plugin.mcp/" "$STAGE/runtime/plugins/org.docear.plugin.mcp/"

cat > "$STAGE/bin/start-docear-mcp.sh" <<EOF
#!/bin/bash
set -euo pipefail
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
RUNTIME=/opt/docear/runtime
DATA=/data/docear
MAPS=/data/mindmaps
mkdir -p "\$DATA" "\$MAPS" /var/log/docear
cd "\$RUNTIME"
exec xvfb-run -a -s "-screen 0 1280x800x24" java -Xms256m -Xmx1024m -XX:+UseG1GC \\
  -Dorg.knopflerfish.framework.bundlestorage=memory \\
  -Dorg.freeplane.globalresourcedir="\$RUNTIME/resources" \\
  -Dorg.knopflerfish.gosg.jars=reference:file:"\$RUNTIME/core/" \\
  -Dorg.docear.working.directory="\$MAPS" \\
  -Dorg.freeplane.userfpdir="\$DATA" \\
  -Dgit.repo.path="\$MAPS" \\
  -Dmcp.enabled=true \\
  -Dmcp.host=${MCP_HOST} \\
  -Dmcp.port=${MCP_PORT} \\
  -Dmcp.auth.enabled=true \\
  -Dmcp.auth.apiKey=${MCP_API_KEY} \\
  -Dmcp.web.enabled=true \\
  -Dmcp.skipFullTagScan=true \\
  -Dmcp.edtTimeoutMs=90000 \\
  -Duser.language=zh -Duser.country=CN \\
  -jar "\$RUNTIME/framework.jar" \\
  -xargs "\$RUNTIME/props.xargs" \\
  -xargs "\$RUNTIME/init.xargs"
EOF
chmod +x "$STAGE/bin/start-docear-mcp.sh"
printf '%s\n' "$MCP_API_KEY" > "$STAGE/bin/mcp-api-key.txt"

cat > "$STAGE/bin/docear-mcp.service" <<'EOF'
[Unit]
Description=Docear headless MCP server
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/docear/runtime
Environment=LANG=en_US.UTF-8
Environment=LC_ALL=en_US.UTF-8
ExecStart=/opt/docear/bin/start-docear-mcp.sh
Restart=on-failure
RestartSec=5
StandardOutput=append:/var/log/docear/mcp.out.log
StandardError=append:/var/log/docear/mcp.err.log

[Install]
WantedBy=multi-user.target
EOF

ssh "$TARGET" 'mkdir -p /opt/docear/runtime /opt/docear/bin /data/mindmaps /data/docear /var/log/docear'
if [[ -d "$STAGE/runtime/core" ]] || [[ -d "$STAGE/runtime/plugins" ]]; then
  tar -C "$STAGE/runtime" -czf - . | ssh "$TARGET" 'tar -C /opt/docear/runtime -xzf -'
fi
tar -C "$STAGE/bin" -czf - . | ssh "$TARGET" "tar -C /opt/docear/bin -xzf - && chmod +x /opt/docear/bin/start-docear-mcp.sh && chmod 600 /opt/docear/bin/mcp-api-key.txt && cp /opt/docear/bin/mcp-api-key.txt /opt/docear/mcp-api-key.txt && cp /opt/docear/bin/docear-mcp.service /etc/systemd/system/docear-mcp.service && (command -v ufw >/dev/null && ufw allow ${MCP_PORT}/tcp comment 'Twigmark MCP/Web' || true) && systemctl daemon-reload && systemctl enable --now docear-mcp && sleep 8 && curl -sS http://127.0.0.1:${MCP_PORT}/health || true"
echo "Deployed to $TARGET"
echo "MCP:  http://${TARGET#*@}:${MCP_PORT}/mcp"
echo "Web:  http://${TARGET#*@}:${MCP_PORT}/web/"
echo "Key:  (server) /opt/docear/mcp-api-key.txt"
echo "Auth: Authorization: Bearer <key>"
