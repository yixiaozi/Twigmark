#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/build_web_audit_test"
STUBS="$ROOT/scripts/test-stubs"
rm -rf "$BUILD"
mkdir -p "$BUILD"
JAVA_HOME="${JAVA_HOME:-/home/ubuntu/jdk8}"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 1.8 -target 1.8 -d "$BUILD" \
  "$STUBS/org/docear/plugin/mcp/DocearMcpConfig.java" \
  "$STUBS/org/freeplane/core/util/LogUtils.java" \
  "$ROOT/src/org/docear/plugin/mcp/json/JsonValue.java" \
  "$ROOT/src/org/docear/plugin/mcp/json/JsonWriter.java" \
  "$ROOT/src/org/docear/plugin/mcp/server/McpRole.java" \
  "$ROOT/src/org/docear/plugin/mcp/server/McpPrincipal.java" \
  "$ROOT/src/org/docear/plugin/mcp/server/McpPermissions.java" \
  "$ROOT/src/org/docear/plugin/mcp/audit/McpAuditQuery.java" \
  "$ROOT/src/org/docear/plugin/mcp/audit/McpAuditLabels.java" \
  "$ROOT/src/org/docear/plugin/mcp/webchat/WebAuditFilters.java" \
  "$ROOT/src/org/docear/plugin/mcp/webchat/WebAuditStandaloneTest.java"
"$JAVA_HOME/bin/java" -cp "$BUILD" org.docear.plugin.mcp.webchat.WebAuditStandaloneTest
