#!/bin/bash
# Docear 直接运行脚本 - 不依赖 macOS 应用包装（与 Docear.app 启动器逻辑一致）

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCEAR_DIR="$PROJECT_DIR/docear_framework/build4mac/Docear.app/Contents/Resources/Java"
LOG_DIR="${HOME}/Library/Logs/Docear"
LOG_FILE="${LOG_DIR}/launch.log"
mkdir -p "$LOG_DIR"

find_java() {
    local candidate home

    # 1) 优先：完整 JDK 8（Temurin/Zulu 等），跳过浏览器插件
    for home in /Library/Java/JavaVirtualMachines/*/Contents/Home; do
        [ -x "${home}/bin/java" ] || continue
        case "$home" in
            *"JavaAppletPlugin"*) continue ;;
        esac
        if "${home}/bin/java" -version 2>&1 | grep -qE 'version "1\.8'; then
            echo "$home"
            return 0
        fi
    done

    # 2) java_home，排除浏览器插件
    if [ -x "/usr/libexec/java_home" ]; then
        while IFS= read -r home; do
            [ -n "$home" ] || continue
            case "$home" in
                *"JavaAppletPlugin"*|*"Internet Plug-Ins"*) continue ;;
            esac
            if [ -x "${home}/bin/java" ]; then
                echo "$home"
                return 0
            fi
        done < <(/usr/libexec/java_home -v 1.8 2>/dev/null; /usr/libexec/java_home 2>/dev/null)
    fi

    # 3) PATH /usr/bin/java
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
    echo "未找到 Java 8。请安装 Eclipse Temurin 8：https://adoptium.net/temurin/releases/?version=8" >&2
    exit 1
}
export JAVA_HOME
JAVACMD="${JAVA_HOME}/bin/java"
[ -x "$JAVACMD" ] || JAVACMD="$(command -v java)"

echo "使用 JAVA_HOME: $JAVA_HOME"
echo "运行目录: $DOCEAR_DIR"
cd "$DOCEAR_DIR" || exit 1
FREEDIR="$(pwd)"

echo "正在启动 Docear..."
{
    echo "========== $(date) =========="
    echo "run_docear.sh JAVA_HOME=$JAVA_HOME"
} >>"$LOG_FILE"

JAVA_ARGS=(
    -Xmx1024m
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

if [ "$(uname -m)" = "arm64" ] && file "$JAVACMD" 2>/dev/null | grep -q "x86_64"; then
    echo "Running x86_64 JVM under Rosetta: $JAVACMD"
    exec arch -x86_64 "$JAVACMD" "${JAVA_ARGS[@]}"
fi

exec "$JAVACMD" "${JAVA_ARGS[@]}"
