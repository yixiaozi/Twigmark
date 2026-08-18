#!/usr/bin/env bash
# Package Freeplane + Docear MCP plugins and upload to a Linux VPS.
# Usage:
#   MCP_API_KEY=tm_xxx ./deploy-vps-runtime.sh root@HOST
# Optional env:
#   MCP_HOST=127.0.0.1 MCP_PORT=7720
#   SSH_IDENTITY_FILE=/path/to/key
#   MCP_OPEN_FIREWALL=1          # also open UFW for MCP_PORT (off by default)
#   SKIP_DATA_MIGRATE=1          # do not move Dropbox _data to /data/docear
set -euo pipefail
TARGET="${1:?usage: $0 root@host}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

MCP_HOST="${MCP_HOST:-127.0.0.1}"
MCP_PORT="${MCP_PORT:-7720}"
SSH_IDENTITY_FILE="${SSH_IDENTITY_FILE:-}"
MCP_OPEN_FIREWALL="${MCP_OPEN_FIREWALL:-0}"
SKIP_DATA_MIGRATE="${SKIP_DATA_MIGRATE:-0}"

ssh_cmd() {
	if [[ -n "$SSH_IDENTITY_FILE" ]]; then
		ssh -i "$SSH_IDENTITY_FILE" -o StrictHostKeyChecking=accept-new -o IdentitiesOnly=yes "$@"
	else
		ssh "$@"
	fi
}

if [[ -z "${MCP_API_KEY:-}" ]]; then
	EXISTING_KEY="$(ssh_cmd "$TARGET" 'cat /opt/docear/mcp-api-key.txt 2>/dev/null || cat /opt/docear/bin/mcp-api-key.txt 2>/dev/null || true' | tr -d '\r\n' || true)"
	if [[ -n "$EXISTING_KEY" ]]; then
		MCP_API_KEY="$EXISTING_KEY"
		echo "Reusing existing MCP_API_KEY from server"
	else
		MCP_API_KEY="tm_$(openssl rand -hex 24 2>/dev/null || python3 -c 'import secrets;print(secrets.token_hex(24))')"
		echo "Generated MCP_API_KEY (also written to /opt/docear/mcp-api-key.txt on server)"
	fi
fi

mkdir -p "$STAGE/runtime/plugins" "$STAGE/bin"
RUNTIME_SRC=""
if [[ -d "$ROOT/docear_framework/build/core" ]]; then
	RUNTIME_SRC="$ROOT/docear_framework/build"
elif [[ -d "$ROOT/freeplane_framework/build/core" ]]; then
	RUNTIME_SRC="$ROOT/freeplane_framework/build"
fi
if [[ -n "$RUNTIME_SRC" ]]; then
	rsync -a \
		--exclude 'freeplane.exe' --exclude 'freeplaneConsole.exe' --exclude '*.dll' --exclude '*.bat' \
		--exclude 'docear.exe' --exclude 'workspace/' \
		"$RUNTIME_SRC/" "$STAGE/runtime/"
fi
# Always refresh Freeplane core jars when present (needed for EmptyDirectorySeeder etc.)
if [[ -f "$ROOT/freeplane/dist/lib/freeplaneviewer.jar" ]]; then
	mkdir -p "$STAGE/runtime/core/org.freeplane.core/lib"
	cp -f "$ROOT/freeplane/dist/lib/freeplaneviewer.jar" "$STAGE/runtime/core/org.freeplane.core/lib/"
	cp -f "$ROOT/freeplane/dist/lib/freeplaneeditor.jar" "$STAGE/runtime/core/org.freeplane.core/lib/" 2>/dev/null || true
fi
if [[ -d "$ROOT/docear_plugin_core/dist/org.docear.plugin.core" ]]; then
	rsync -a "$ROOT/docear_plugin_core/dist/org.docear.plugin.core/" "$STAGE/runtime/plugins/org.docear.plugin.core/"
fi
if [[ -d "$ROOT/docear_plugin_ai/dist/org.docear.plugin.ai" ]]; then
	rsync -a "$ROOT/docear_plugin_ai/dist/org.docear.plugin.ai/" "$STAGE/runtime/plugins/org.docear.plugin.ai/"
fi
if [[ -d "$ROOT/docear_plugin_mcp/dist/org.docear.plugin.mcp" ]]; then
	rsync -a "$ROOT/docear_plugin_mcp/dist/org.docear.plugin.mcp/" "$STAGE/runtime/plugins/org.docear.plugin.mcp/"
fi
if [[ -d "$ROOT/docear_plugin_drawio/dist/org.docear.plugin.drawio" ]]; then
	rsync -a "$ROOT/docear_plugin_drawio/dist/org.docear.plugin.drawio/" "$STAGE/runtime/plugins/org.docear.plugin.drawio/"
fi
if [[ -d "$ROOT/freeplane_plugin_workspace/dist/org.freeplane.plugin.workspace" ]]; then
	rsync -a "$ROOT/freeplane_plugin_workspace/dist/org.freeplane.plugin.workspace/" "$STAGE/runtime/plugins/org.freeplane.plugin.workspace/"
fi

# Marker files: maps stay in the Dropbox-synced library; _data / indexes stay local.
printf '%s\n' '/data/mindmaps' > "$STAGE/runtime/working-directory.txt"
printf '%s\n' '/data/docear' > "$STAGE/runtime/config-directory.txt"

cat > "$STAGE/bin/start-docear-mcp.sh" <<EOF
#!/bin/bash
set -euo pipefail
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
RUNTIME=/opt/docear/runtime
DATA=/data/docear
MAPS=/data/mindmaps
mkdir -p "\$DATA" "\$MAPS" /var/log/docear
printf '%s\\n' "\$MAPS" > "\$RUNTIME/working-directory.txt"
printf '%s\\n' "\$DATA" > "\$RUNTIME/config-directory.txt"
cd "\$RUNTIME"
exec xvfb-run -a -s "-screen 0 800x600x16" java -Xms64m -Xmx512m -XX:+UseSerialGC -Xss256k -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError \\
  -Dorg.knopflerfish.framework.bundlestorage=memory \\
  -Dorg.freeplane.globalresourcedir="\$RUNTIME/resources" \\
  -Dorg.knopflerfish.gosg.jars=reference:file:"\$RUNTIME/core/" \\
  -Dorg.docear.working.directory="\$MAPS" \\
  -Dorg.docear.config.directory="\$DATA" \\
  -Dorg.freeplane.userfpdir="\$DATA" \\
  -Dgit.repo.path="\$MAPS" \\
  -Dmcp.enabled=true \\
  -Dmcp.host=${MCP_HOST} \\
  -Dmcp.port=${MCP_PORT} \\
  -Dmcp.auth.enabled=true \\
  -Dmcp.auth.apiKey=${MCP_API_KEY} \\
  -Dmcp.web.enabled=true \\
  -Dmcp.web.readOnlyTools=true \\
  -Dmcp.skipFullTagScan=true \\
  -Dmcp.lowMemory=true \\
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

ssh_cmd "$TARGET" 'mkdir -p /opt/docear/runtime /opt/docear/bin /data/mindmaps /data/docear /var/log/docear /root/docear-data-backups'

if [[ "$SKIP_DATA_MIGRATE" != "1" ]]; then
	echo "Moving Dropbox _data off the synced library into /data/docear ..."
	ssh_cmd "$TARGET" 'bash -s' <<'MIGRATE'
set -euo pipefail
systemctl stop docear-mcp 2>/dev/null || true
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p /data/docear /root/docear-data-backups
DATA_SRC=""
if [[ -d /root/Dropbox/mindmaps/_data ]]; then
  DATA_SRC=/root/Dropbox/mindmaps/_data
elif [[ -d /data/mindmaps/_data ]]; then
  DATA_SRC=/data/mindmaps/_data
fi
if [[ -n "$DATA_SRC" ]]; then
  tar -C "$(dirname "$DATA_SRC")" -czf "/root/docear-data-backups/mindmaps-_data-$STAMP.tgz" "$(basename "$DATA_SRC")"
  rsync -a "$DATA_SRC/" /data/docear/
  rm -rf "$DATA_SRC"
  mkdir -p "$DATA_SRC"
  cat > "$DATA_SRC/README.txt" <<'R'
This folder used to hold Docear _data (indexes, logs, AI snapshots).
On this server those files now live outside Dropbox at:
  /data/docear
Do not put SQLite indexes or logs back here.
R
fi
# Keep Dropbox from re-uploading a real _data tree.
IGNORE=/root/Dropbox/mindmaps/.dropboxignore
if [[ -d /root/Dropbox/mindmaps ]]; then
  touch "$IGNORE"
  grep -qxF '_data/' "$IGNORE" 2>/dev/null || echo '_data/' >> "$IGNORE"
  grep -qxF '_data' "$IGNORE" 2>/dev/null || echo '_data' >> "$IGNORE"
fi
printf '%s\n' /data/mindmaps > /opt/docear/runtime/working-directory.txt
printf '%s\n' /data/docear > /opt/docear/runtime/config-directory.txt
echo "Config directory: /data/docear"
ls -la /data/docear | head
MIGRATE
fi

if [[ -d "$STAGE/runtime/core" ]] || [[ -d "$STAGE/runtime/plugins" ]]; then
	tar -C "$STAGE/runtime" -czf - . | ssh_cmd "$TARGET" 'tar -C /opt/docear/runtime -xzf -'
fi
FIREWALL_CMD="true"
if [[ "$MCP_OPEN_FIREWALL" == "1" ]]; then
	FIREWALL_CMD="(command -v ufw >/dev/null && ufw allow ${MCP_PORT}/tcp comment 'Twigmark MCP/Web' || true)"
fi
tar -C "$STAGE/bin" -czf - . | ssh_cmd "$TARGET" "tar -C /opt/docear/bin -xzf - && chmod +x /opt/docear/bin/start-docear-mcp.sh && chmod 600 /opt/docear/bin/mcp-api-key.txt && cp /opt/docear/bin/mcp-api-key.txt /opt/docear/mcp-api-key.txt && cp /opt/docear/bin/docear-mcp.service /etc/systemd/system/docear-mcp.service && ${FIREWALL_CMD} && systemctl daemon-reload && systemctl enable --now docear-mcp && sleep 12 && curl -sS http://127.0.0.1:${MCP_PORT}/health || true"
echo "Deployed to $TARGET"
echo "Maps: /data/mindmaps  (Dropbox library)"
echo "Data: /data/docear    (_data / indexes, local disk)"
echo "MCP:  http://${TARGET#*@}:${MCP_PORT}/mcp  (bound ${MCP_HOST})"
echo "Key:  (server) /opt/docear/mcp-api-key.txt"
echo "Auth: Authorization: Bearer <key>"
