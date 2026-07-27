#!/bin/bash
# Docear 直接运行脚本 - 不依赖 macOS 应用包装（与 Docear.app 启动器逻辑一致）

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCEAR_DIR="$PROJECT_DIR/docear_framework/build4mac/Docear.app/Contents/Resources/Java"
LOG_DIR="${HOME}/Library/Logs/Docear"
LOG_FILE="${LOG_DIR}/launch.log"
mkdir -p "$LOG_DIR"

java_cpu_arch() {
    local info
    info="$(file "$1" 2>/dev/null || true)"
    case "$info" in
        *arm64*|*aarch64*) echo "arm64" ;;
        *x86_64*) echo "x86_64" ;;
        *) echo "unknown" ;;
    esac
}

is_jdk8_home() {
    local home="$1"
    [ -x "${home}/bin/java" ] || return 1
    case "$home" in
        *"JavaAppletPlugin"*|*"Internet Plug-Ins"*) return 1 ;;
    esac
    "${home}/bin/java" -version 2>&1 | grep -qE 'version "1\.8'
}

find_java() {
    local home candidate prefer=""
    local -a matches=()

    if [ "$(uname -m)" = "arm64" ]; then
        prefer="arm64"
    fi

    for home in \
        /Library/Java/JavaVirtualMachines/*/Contents/Home \
        "${HOME}/Library/Java/JavaVirtualMachines/"*/Contents/Home
    do
        is_jdk8_home "$home" || continue
        matches+=("$home")
    done

    if [ -n "$prefer" ]; then
        for home in "${matches[@]}"; do
            if [ "$(java_cpu_arch "${home}/bin/java")" = "$prefer" ]; then
                echo "$home"
                return 0
            fi
        done
    fi

    if [ "${#matches[@]}" -gt 0 ]; then
        echo "${matches[0]}"
        return 0
    fi

    if [ -x "/usr/libexec/java_home" ]; then
        while IFS= read -r home; do
            is_jdk8_home "$home" || continue
            if [ -n "$prefer" ] && [ "$(java_cpu_arch "${home}/bin/java")" != "$prefer" ]; then
                continue
            fi
            echo "$home"
            return 0
        done < <(/usr/libexec/java_home -v 1.8 -a arm64 2>/dev/null; /usr/libexec/java_home -v 1.8 2>/dev/null; /usr/libexec/java_home 2>/dev/null)
        while IFS= read -r home; do
            is_jdk8_home "$home" || continue
            echo "$home"
            return 0
        done < <(/usr/libexec/java_home -v 1.8 2>/dev/null; /usr/libexec/java_home 2>/dev/null)
    fi

    candidate="$(command -v java 2>/dev/null || true)"
    [ -z "$candidate" ] && [ -x /usr/bin/java ] && candidate="/usr/bin/java"
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then
        if "$candidate" -version 2>&1 | grep -qE 'version "1\.8'; then
            dirname "$(dirname "$candidate")"
            return 0
        fi
    fi

    return 1
}

JAVA_HOME="$(find_java)" || {
    echo "未找到 Java 8。Apple Silicon 请安装 Azul Zulu 8 aarch64，或放到 ~/Library/Java/JavaVirtualMachines/" >&2
    exit 1
}
export JAVA_HOME
JAVACMD="${JAVA_HOME}/bin/java"
[ -x "$JAVACMD" ] || JAVACMD="$(command -v java)"

echo "使用 JAVA_HOME: $JAVA_HOME ($(java_cpu_arch "$JAVACMD"))"
echo "运行目录: $DOCEAR_DIR"
cd "$DOCEAR_DIR" || exit 1
FREEDIR="$(pwd)"

echo "正在启动 Docear..."
{
    echo "========== $(date) =========="
    echo "run_docear.sh JAVA_HOME=$JAVA_HOME arch=$(java_cpu_arch "$JAVACMD")"
} >>"$LOG_FILE"

JAVA_ARGS=(
    -Xmx1536m
    -Xdock:name=Docear
    -Dapple.laf.useScreenMenuBar=true
    -Dcom.apple.macos.useScreenMenuBar=true
    -Dsun.java2d.opengl=false
    -Dorg.freeplane.param1="$1"
    -Dorg.freeplane.param2="$2"
    -Dorg.freeplane.param3="$3"
    -Dorg.freeplane.param4="$4"
    -Dorg.knopflerfish.framework.bundlestorage=memory
    -Dorg.freeplane.globalresourcedir="${FREEDIR}/resources"
    -Dorg.knopflerfish.gosg.jars=reference:file:"${FREEDIR}/core/"
    -jar "${FREEDIR}/framework.jar"
    -xargs "${FREEDIR}/props.xargs"
    -xargs "${FREEDIR}/init.xargs"
)

if [ "$(uname -m)" = "arm64" ] && [ "$(java_cpu_arch "$JAVACMD")" = "x86_64" ]; then
    echo "Running x86_64 JVM under Rosetta: $JAVACMD"
    exec arch -x86_64 "$JAVACMD" "${JAVA_ARGS[@]}"
fi

exec "$JAVACMD" "${JAVA_ARGS[@]}"
