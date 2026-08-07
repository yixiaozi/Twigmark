#!/usr/bin/env bash
# Generate 2000 mind maps and compare baseline SAX search vs MindMapNodeSearchIndex.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/tmp/jdk8}"
export PATH="$JAVA_HOME/bin:$PATH"
FILES="${1:-2000}"
BENCH_ROOT="${2:-/tmp/docear-mcp-search-bench}"

echo "Compiling freeplane (includes MindMapNodeSearchIndex + benchmark)..."
"$ROOT/tools/apache-ant-1.10.14/bin/ant" -f "$ROOT/freeplane/ant/build.xml" build -q

CP="$ROOT/freeplane/build"
# HtmlUtils / LogUtils live in freeplane build; ResourceController may be pulled — benchmark avoids needing full app.
echo "Running benchmark with $FILES files..."
java -cp "$CP" org.freeplane.core.util.MindMapNodeSearchIndexBenchmark \
  --files "$FILES" --root "$BENCH_ROOT" --keep

echo ""
echo "Artifact corpus kept at: $BENCH_ROOT"
echo "Disk index cache: $BENCH_ROOT/_index-cache"
