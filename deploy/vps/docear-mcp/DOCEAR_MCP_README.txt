Docear MCP (headless) — 2026-08-18

Install:   /opt/docear/runtime
Start:     /opt/docear/bin/start-docear-mcp.sh
Unit:      docear-mcp.service

Mind maps (Dropbox-synced library):
  /data/mindmaps  ->  /root/Dropbox/mindmaps

Config / _data (LOCAL disk, not Dropbox):
  /data/docear
  Marker files:
    /opt/docear/runtime/working-directory.txt  = /data/mindmaps
    /opt/docear/runtime/config-directory.txt   = /data/docear
  JVM:
    -Dorg.docear.working.directory=/data/mindmaps
    -Dorg.docear.config.directory=/data/docear

MCP: 127.0.0.1:7720  (API key in /opt/docear/mcp-api-key.txt)
Health: curl -sS http://127.0.0.1:7720/health

Backups: /root/docear-data-backups/

Do NOT put indexes, logs, or SQLite DBs back under Dropbox/mindmaps/_data.
