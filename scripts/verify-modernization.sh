#!/usr/bin/env bash
# Twigmark modernization gate — safe for Cloud Agents.
# NEVER stops a running Docear/Twigmark instance, NEVER deploys to install dirs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PASS=0
FAIL=0
SKIP=0

ok()   { echo "[OK]   $*"; PASS=$((PASS+1)); }
bad()  { echo "[FAIL] $*"; FAIL=$((FAIL+1)); }
skip() { echo "[SKIP] $*"; SKIP=$((SKIP+1)); }

echo "==== Twigmark modernization verify ===="
echo "Repo: $ROOT"
echo "Java: $(java -version 2>&1 | head -1 || true)"
echo

# --- Phase 1: structure & safety ---
if [[ -f docs/modernization/ROADMAP.md && -f docs/modernization/ACCEPTANCE.md ]]; then
  ok "modernization docs present"
else
  bad "missing docs/modernization/{ROADMAP,ACCEPTANCE}.md"
fi

# Ensure verify entrypoints do not *invoke* deploy helpers (mentions in comments OK).
if rg -n 'Stop-RunningDocear\s|build-docear\.bat\s' scripts/verify-mm-fixtures.py .github/workflows/modernization-verify.yml 2>/dev/null; then
  bad "verify path references deploy/stop helpers"
else
  ok "verify path has no deploy/stop side effects"
fi

# --- Phase 2: Java 1.8 compile target ---
BAD_JAVA=$(rg -n 'name="java_source_version"\s+value="1\.[0-7]"' --glob '**/build.xml' || true)
if [[ -z "${BAD_JAVA}" ]]; then
  ok "all ant modules use java_source_version >= 1.8"
else
  bad "ant modules still below 1.8:"$'\n'"$BAD_JAVA"
fi

# --- Phase 3: Gradle entry ---
if [[ -f settings.gradle.kts || -f settings.gradle ]]; then
  ok "Gradle settings present"
else
  bad "missing Gradle settings"
fi

# --- Phase 4/5/6: theme & defaults ---
if rg -n 'lookandfeel\s*=\s*com\.formdev\.flatlaf\.FlatLightLaf' freeplane/viewer-resources/freeplane.properties >/dev/null; then
  ok "default Look&Feel is FlatLaf Light"
else
  bad "default Look&Feel is not FlatLaf Light"
fi

if rg -n 'isDarkLafActive|applyChromePalette|ui_density' freeplane/src/org/freeplane/core/ui/theme/DocearUiTheme.java >/dev/null; then
  ok "DocearUiTheme has dark palette / density hooks"
else
  bad "DocearUiTheme missing dark/density modernization hooks"
fi

if rg -n 'standardbackgroundcolor\s*=\s*#f2f4f7' freeplane/viewer-resources/freeplane.properties >/dev/null; then
  ok "map canvas default uses modern canvas color"
else
  bad "map canvas default not updated"
fi

# --- Phase 7: product scripts ---
if [[ -x scripts/backup-mindmaps.sh || -f scripts/backup-mindmaps.sh ]]; then
  ok "backup-mindmaps script present"
else
  bad "missing scripts/backup-mindmaps.sh"
fi

# --- Phase 8: twigmark2 scaffold ---
if [[ -f twigmark2/README.md && -f twigmark2/UPSTREAM.md ]]; then
  ok "twigmark2 scaffold present"
else
  bad "missing twigmark2 scaffold"
fi

# --- Fixtures ---
if [[ -f scripts/verify-mm-fixtures.py ]]; then
  if python3 scripts/verify-mm-fixtures.py; then
    ok "mindmap fixture XML validation"
  else
    bad "mindmap fixture validation failed"
  fi
else
  bad "missing scripts/verify-mm-fixtures.py"
fi

# --- Optional: compile freeplane + key plugins with bundled Ant + current JDK ---
ANT="$ROOT/tools/apache-ant-1.10.14/bin/ant"
if [[ -x "$ANT" && -n "$(command -v javac || true)" ]]; then
  echo
  echo "==== Optional compile chain (ant, no deploy) ===="
  set +e
  "$ANT" -f freeplane_ant/build.xml jar && \
  "$ANT" -f freeplane/ant/build.xml osgi_dist && \
  "$ANT" -f freeplane_plugin_workspace/ant/build.xml dist && \
  "$ANT" -f docear_plugin_core/ant/build.xml dist && \
  "$ANT" -f docear_plugin_mcp/ant/build.xml dist
  CHAIN=$?
  set -e
  if [[ $CHAIN -eq 0 ]]; then
    ok "compile chain (freeplane osgi + workspace + core + mcp) under $(javac -version 2>&1)"
  else
    bad "compile chain failed (exit $CHAIN) — see log above"
  fi
else
  skip "ant/javac not available for compile probe"
fi

# --- Optional standalone Java tests (no GUI) ---
if [[ -f freeplane/dist/org.freeplane.core/org.freeplane.core.jar || -f freeplane/build ]]; then
  :
fi

echo
echo "==== Summary: pass=$PASS fail=$FAIL skip=$SKIP ===="
if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi
echo "Modernization gate PASSED (no deploy / no process stop)."
exit 0
