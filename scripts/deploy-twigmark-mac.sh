#!/usr/bin/env bash
# Deploy Twigmark to /Applications/Twigmark.app (Mac).
# Quits a running Twigmark, syncs plugins (and optionally a full .app), then relaunches.
#
# Usage:
#   ./scripts/deploy-twigmark-mac.sh              # sync plugins from module dist + framework build
#   ./scripts/deploy-twigmark-mac.sh --full       # ant macosxapp then replace entire .app
#   ./scripts/deploy-twigmark-mac.sh --no-launch  # do not start after deploy
#   ./scripts/deploy-twigmark-mac.sh --plugin mermaid
#
# Env:
#   TWIGMARK_APP   default /Applications/Twigmark.app
#   JAVA_HOME      prefer JDK 8

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="${TWIGMARK_APP:-/Applications/Twigmark.app}"
ANT="$REPO_ROOT/tools/apache-ant-1.10.14/bin/ant"
FULL=0
LAUNCH=1
ONLY_PLUGIN=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --full) FULL=1; shift ;;
    --no-launch) LAUNCH=0; shift ;;
    --plugin) ONLY_PLUGIN="${2:-}"; shift 2 ;;
    -h|--help)
      sed -n '1,20p' "$0"
      exit 0
      ;;
    *) echo "Unknown arg: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "$HOME/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home" ]]; then
    export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home"
  elif /usr/libexec/java_home -v 1.8 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
  fi
fi
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$REPO_ROOT/tools/apache-ant-1.10.14/bin:$PATH"

echo "==== Twigmark Mac deploy ===="
echo "Repo: $REPO_ROOT"
echo "App:  $APP"
echo "JAVA_HOME=${JAVA_HOME:-}"

quit_twigmark() {
  if pgrep -f '/Applications/Twigmark.app|Twigmark.app/Contents' >/dev/null 2>&1 \
     || pgrep -ix Twigmark >/dev/null 2>&1; then
    echo "==== Quit Twigmark ===="
    osascript -e 'tell application "Twigmark" to quit' 2>/dev/null || true
    # Force leftover Java/OSGi if needed
    for i in 1 2 3 4 5 6 7 8 9 10; do
      if ! pgrep -f 'Twigmark.app/Contents' >/dev/null 2>&1; then
        break
      fi
      sleep 0.5
    done
    pkill -f 'Twigmark.app/Contents' 2>/dev/null || true
  fi
}

sync_plugin_dir() {
  local name="$1"   # e.g. org.docear.plugin.mermaid
  local src="$2"
  local dest_plugins="$APP/Contents/Resources/Java/plugins"
  local build_plugins="$REPO_ROOT/docear_framework/build/plugins"
  if [[ ! -d "$src" ]]; then
    echo "Missing plugin dist: $src" >&2
    return 1
  fi
  mkdir -p "$dest_plugins" "$build_plugins"
  rm -rf "$dest_plugins/$name" "$build_plugins/$name"
  ditto "$src" "$dest_plugins/$name"
  ditto "$src" "$build_plugins/$name"
  echo "Synced $name -> $dest_plugins/$name"
}

build_mermaid() {
  echo "==== Build docear_plugin_mermaid ===="
  (cd "$REPO_ROOT/docear_plugin_mermaid" && "$ANT" -f ant/build.xml dist)
}

deploy_plugins_incremental() {
  if [[ -n "$ONLY_PLUGIN" ]]; then
    case "$ONLY_PLUGIN" in
      mermaid|org.docear.plugin.mermaid)
        build_mermaid
        sync_plugin_dir "org.docear.plugin.mermaid" \
          "$REPO_ROOT/docear_plugin_mermaid/dist/org.docear.plugin.mermaid"
        ;;
      *)
        echo "Unknown --plugin $ONLY_PLUGIN" >&2
        exit 1
        ;;
    esac
    return
  fi
  # Default: ensure mermaid is built & synced; also copy any framework build/plugins that exist
  if [[ -d "$REPO_ROOT/docear_plugin_mermaid" ]]; then
    build_mermaid
    sync_plugin_dir "org.docear.plugin.mermaid" \
      "$REPO_ROOT/docear_plugin_mermaid/dist/org.docear.plugin.mermaid"
  fi
  local build_plugins="$REPO_ROOT/docear_framework/build/plugins"
  if [[ -d "$build_plugins" ]]; then
    echo "==== Sync framework build/plugins ===="
    mkdir -p "$APP/Contents/Resources/Java/plugins"
    # Copy each plugin dir (overwrite)
    for d in "$build_plugins"/*; do
      [[ -d "$d" ]] || continue
      local base
      base="$(basename "$d")"
      rm -rf "$APP/Contents/Resources/Java/plugins/$base"
      ditto "$d" "$APP/Contents/Resources/Java/plugins/$base"
      echo "  $base"
    done
  fi
}

deploy_full_app() {
  echo "==== Full ant macosxapp (long) ===="
  (cd "$REPO_ROOT/docear_framework" && "$ANT" -f ant/build.xml macosxapp)
  local built="$REPO_ROOT/docear_framework/build4mac/Twigmark.app"
  if [[ ! -d "$built" ]]; then
    echo "macosxapp did not produce $built" >&2
    exit 1
  fi
  # Ensure mermaid is present even if parent build skipped it somehow
  if [[ -d "$REPO_ROOT/docear_plugin_mermaid/dist/org.docear.plugin.mermaid" ]]; then
    mkdir -p "$built/Contents/Resources/Java/plugins"
    rm -rf "$built/Contents/Resources/Java/plugins/org.docear.plugin.mermaid"
    ditto "$REPO_ROOT/docear_plugin_mermaid/dist/org.docear.plugin.mermaid" \
      "$built/Contents/Resources/Java/plugins/org.docear.plugin.mermaid"
  fi
  echo "==== Replace $APP ===="
  local backup="/tmp/Twigmark.app.bak.$$"
  if [[ -d "$APP" ]]; then
    rm -rf "$backup"
    mv "$APP" "$backup" || true
  fi
  ditto "$built" "$APP"
  rm -rf "$backup"
  echo "Installed $APP"
}

quit_twigmark

if [[ ! -d "$APP" ]]; then
  echo "App missing at $APP — forcing full build"
  FULL=1
fi

if [[ "$FULL" -eq 1 ]]; then
  build_mermaid || true
  deploy_full_app
else
  deploy_plugins_incremental
fi

if [[ "$LAUNCH" -eq 1 ]]; then
  echo "==== Launch Twigmark ===="
  open "$APP"
fi

echo "==== Done ===="
