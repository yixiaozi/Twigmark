#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/build_mindmap_paths_test"
rm -rf "$BUILD"
mkdir -p "$BUILD"
JAVA_HOME="${JAVA_HOME:-/home/ubuntu/jdk8}"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 1.8 -target 1.8 -d "$BUILD" \
  "$ROOT/src/org/docear/plugin/mcp/service/McpMindMapPaths.java" \
  "$ROOT/src/org/docear/plugin/mcp/service/McpMindMapPathsStandaloneTest.java"
"$JAVA_HOME/bin/java" -cp "$BUILD" org.docear.plugin.mcp.service.McpMindMapPathsStandaloneTest
