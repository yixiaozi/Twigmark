#!/usr/bin/env bash
# Package Freeplane + Docear MCP plugins and upload to a Linux VPS.
# Usage: ./deploy-vps-runtime.sh root@HOST
set -euo pipefail
TARGET="${1:?usage: $0 root@host}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

mkdir -p "$STAGE/runtime/plugins" "$STAGE/bin"
rsync -a \
  --exclude 'freeplane.exe' --exclude 'freeplaneConsole.exe' --exclude '*.dll' --exclude '*.bat' \
  "$ROOT/freeplane_framework/build/" "$STAGE/runtime/"
rsync -a "$ROOT/docear_plugin_core/dist/org.docear.plugin.core/" "$STAGE/runtime/plugins/org.docear.plugin.core/"
rsync -a "$ROOT/docear_plugin_ai/dist/org.docear.plugin.ai/" "$STAGE/runtime/plugins/org.docear.plugin.ai/"
rsync -a "$ROOT/docear_plugin_mcp/dist/org.docear.plugin.mcp/" "$STAGE/runtime/plugins/org.docear.plugin.mcp/"

cat > "$STAGE/bin/start-docear-mcp.sh" <<'EOF'
#!/bin/bash
set -euo pipefail
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
RUNTIME=/opt/docear/runtime
DATA=/data/docear
MAPS=/data/mindmaps
mkdir -p "$DATA" "$MAPS" /var/log/docear
cd "$RUNTIME"
# Use Xvfb (not java.awt.headless) so map write paths that touch AWT still work.
# mcp.skipFullTagScan avoids SAX-scanning ~all .mm files (major MCP stall source).
exec xvfb-run -a -s "-screen 0 1280x800x24" java -Xms256m -Xmx1024m -XX:+UseG1GC \
  -Dorg.knopflerfish.framework.bundlestorage=memory \
  -Dorg.freeplane.globalresourcedir="$RUNTIME/resources" \
  -Dorg.knopflerfish.gosg.jars=reference:file:"$RUNTIME/core/" \
  -Dorg.docear.working.directory="$MAPS" \
  -Dorg.freeplane.userfpdir="$DATA" \
  -Dgit.repo.path="$MAPS" \
  -Dmcp.enabled=true \
  -Dmcp.host=127.0.0.1 \
  -Dmcp.port=7720 \
  -Dmcp.skipFullTagScan=true \
  -Dmcp.edtTimeoutMs=90000 \
  -Duser.language=zh -Duser.country=CN \
  -jar "$RUNTIME/framework.jar" \
  -xargs "$RUNTIME/props.xargs" \
  -xargs "$RUNTIME/init.xargs"
EOF
chmod +x "$STAGE/bin/start-docear-mcp.sh"

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
tar -C "$STAGE/runtime" -czf - . | ssh "$TARGET" 'tar -C /opt/docear/runtime -xzf -'
tar -C "$STAGE/bin" -czf - . | ssh "$TARGET" 'tar -C /opt/docear/bin -xzf - && chmod +x /opt/docear/bin/start-docear-mcp.sh && cp /opt/docear/bin/docear-mcp.service /etc/systemd/system/docear-mcp.service && systemctl daemon-reload && systemctl enable --now docear-mcp && sleep 3 && curl -sS http://127.0.0.1:7720/health || true'
echo "Deployed to $TARGET"
