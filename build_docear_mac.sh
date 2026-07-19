#!/bin/bash
# Docear Mac 版本发布脚本
set -e

# 配置
PROJECT_DIR="/Users/wangyang/Develop/Docear-Desktop"
TEMP_DIR="/Users/wangyang/Temp"
APPLICATIONS_DIR="/Applications"
ANT_CMD="${PROJECT_DIR}/tools/apache-ant-1.10.14/bin/ant"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 帮助信息
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Docear Mac 版本构建与发布脚本"
    echo ""
    echo "Options:"
    echo "  -h, --help          Show this help message and exit"
    echo "  --skip-build        Skip building and use existing build artifacts"
    echo "  --check             Check system requirements and exit"
    echo "  --no-applications   Do not copy Docear.app into /Applications"
    echo ""
    echo "System Requirements:"
    echo "  - Java Development Kit (JDK) 8 (prefer Temurin 8 in /Library/Java/JavaVirtualMachines)"
    echo "  - Ant 1.10.14 (included in project)"
    echo ""
    echo "How to install JDK 8 on macOS:"
    echo "  1. Download Eclipse Temurin 8 from: https://adoptium.net/temurin/releases/?version=8"
    echo "  2. Install the .pkg file"
    echo "  3. Verify installation: /usr/libexec/java_home -V"
    echo "  4. Set JAVA_HOME to Temurin (avoid JavaAppletPlugin):"
    echo "     export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"
    echo ""
    echo "Typical rebuild + install:"
    echo "  export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"
    echo "  ./build_docear_mac.sh"
    echo "  # Result: docear_framework/build4mac/Docear.app and /Applications/Docear.app"
    echo ""
}

# 修复 Docear.app
fix_docear_app() {
    local app_path="$1"
    echo -e "${YELLOW}正在修复 $app_path ...${NC}"
    
    # 1. 创建启动脚本：优先完整 JDK 8，跳过浏览器插件 JRE；避免 ImageIcon Dock 图标 NPE
    cat > "$app_path/Contents/MacOS/Docear" << 'EOF'
#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONTENTS_DIR="$(dirname "$SCRIPT_DIR")"
JAVA_DIR="$CONTENTS_DIR/Resources/Java"
LOG_DIR="${HOME}/Library/Logs/Docear"
LOG_FILE="${LOG_DIR}/launch.log"
mkdir -p "$LOG_DIR"
exec >>"$LOG_FILE" 2>&1
echo "========== $(date) =========="
echo "Launching Docear from: $0"

show_error() {
    osascript -e "display alert \"Docear 无法启动\" message \"$1\" as critical" 2>/dev/null || true
    echo "ERROR: $1"
    exit 1
}

find_java() {
    local candidate home

    # 1) 优先：/Library/Java/JavaVirtualMachines 下的完整 JDK 8（Temurin/Zulu 等）
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

    # 2) 其次：java_home，但排除浏览器插件目录
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

    # 3) 最后：PATH 或 /usr/bin/java
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

JAVA_HOME="$(find_java)" || show_error "未找到 Java 8。请安装 Eclipse Temurin 8：\nhttps://adoptium.net/temurin/releases/?version=8"
export JAVA_HOME
JAVACMD="${JAVA_HOME}/bin/java"
[ -x "$JAVACMD" ] || JAVACMD="$(command -v java)"
[ -x "$JAVACMD" ] || show_error "Java 可执行文件不存在：${JAVA_HOME}/bin/java"

echo "Using JAVA_HOME=$JAVA_HOME"
echo "Using JAVACMD=$JAVACMD"
"$JAVACMD" -version 2>&1 || show_error "Java 版本检查失败，请确认已安装 JDK 8。"

cd "$JAVA_DIR" || show_error "找不到应用资源目录：$JAVA_DIR"
FREEDIR="$(pwd)"

# 仅设置 Dock 名称；不使用 -Xdock:icon（Java 8 会触发 Uncaught error fetching image NPE）
# 应用图标由 Info.plist 的 CFBundleIconFile (docear.icns) 提供
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

# Apple Silicon 上若 JVM 为 x86_64，通过 Rosetta 启动更稳定
if [ "$(uname -m)" = "arm64" ] && file "$JAVACMD" 2>/dev/null | grep -q "x86_64"; then
    echo "Running x86_64 JVM under Rosetta: $JAVACMD"
    exec arch -x86_64 "$JAVACMD" "${JAVA_ARGS[@]}"
fi

exec "$JAVACMD" "${JAVA_ARGS[@]}"
EOF
    
    # 2. 设置执行权限
    chmod +x "$app_path/Contents/MacOS/Docear"
    
    # 3. 修改 Info.plist
    local plist_path="$app_path/Contents/Info.plist"
    
    /usr/libexec/PlistBuddy -c "Set :CFBundleExecutable Docear" "$plist_path" 2>/dev/null || {
        sed -i '' 's/FreeplaneJavaApplicationStub/Docear/g' "$plist_path"
    }
    
    # 4. 删除旧的启动器
    rm -f "$app_path/Contents/MacOS/FreeplaneJavaApplicationStub"
    
    echo -e "${GREEN}✓ $app_path 修复完成！${NC}"
}

# 检查系统要求
check_requirements() {
    echo -e "${YELLOW}Checking system requirements...${NC}"
    echo ""
    
    # 检查 Java
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
        echo -e "${GREEN}✓ JAVA_HOME is set to: $JAVA_HOME${NC}"
    else
        echo -e "${RED}✗ JAVA_HOME not properly set or javac not found!${NC}"
        echo ""
        echo -e "${YELLOW}Checking installed Java versions...${NC}"
        /usr/libexec/java_home -V || true
        echo ""
        echo -e "${YELLOW}Please install JDK 8 and set JAVA_HOME!${NC}"
        echo "See help (-h) for more information"
        return 1
    fi
    
    # 检查 Ant
    if [ -x "$ANT_CMD" ]; then
        echo -e "${GREEN}✓ Ant found at: $ANT_CMD${NC}"
    elif command -v ant &> /dev/null; then
        echo -e "${GREEN}✓ System Ant found${NC}"
        ANT_CMD="ant"
    else
        echo -e "${RED}✗ Ant not found!${NC}"
        return 1
    fi
    
    echo ""
    echo -e "${GREEN}All requirements met!${NC}"
    return 0
}

# 主程序
main() {
    local skip_build=0
    local check_only=0
    local install_applications=1
    
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            --skip-build)
                skip_build=1
                shift
                ;;
            --check)
                check_only=1
                shift
                ;;
            --no-applications)
                install_applications=0
                shift
                ;;
            *)
                echo -e "${RED}Unknown option: $1${NC}"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 打印头部信息
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}Docear Mac 版本构建与发布${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    
    # Prefer Temurin/Zulu JDK 8 in JVMs folder; java_home -v 1.8 may pick JavaAppletPlugin
    if [ -z "$JAVA_HOME" ] || [[ "$JAVA_HOME" == *"JavaAppletPlugin"* ]]; then
        if [ -x "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java" ]; then
            export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"
        else
            export JAVA_HOME=$(/usr/libexec/java_home -v 1.8 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)
        fi
    fi
    
    # 检查是否仅检查需求
    if [ $check_only -eq 1 ]; then
        check_requirements
        exit $?
    fi
    
    # 检查需求
    if ! check_requirements; then
        exit 1
    fi
    
    DOCEAR_APP="$PROJECT_DIR/docear_framework/build4mac/Docear.app"
    
    # 检查是否需要构建
    if [ $skip_build -eq 0 ]; then
        echo ""
        echo -e "${YELLOW}开始构建 Docear...${NC}"
        echo ""
        
        # 先构建 freeplane_ant
        echo -e "${YELLOW}步骤 1/4: 构建 freeplane_ant...${NC}"
        cd "$PROJECT_DIR/freeplane_ant"
        mkdir -p bin dist
        
        # 如果源代码存在，尝试编译
        if [ -d src ]; then
            echo -e "${YELLOW}Compiling freeplane_ant...${NC}"
            "$JAVA_HOME/bin/javac" -cp "$PROJECT_DIR/tools/apache-ant-1.10.14/lib/ant.jar" -d bin src/org/freeplane/ant/*.java 2>/dev/null || true
            cd bin
            jar cvf ../dist/freeplaneant.jar org/ 2>/dev/null || true
            cd ..
        fi
        
        # 现在构建完整的 Docear
        echo ""
        echo -e "${YELLOW}步骤 2/4: 构建 Docear 应用...${NC}"
        cd "$PROJECT_DIR/docear_framework"
        "$ANT_CMD" -f ant/build.xml clean macosxapp || {
            echo -e "${YELLOW}Trying just 'macosxapp' without clean...${NC}"
            "$ANT_CMD" -f ant/build.xml macosxapp
        }
    fi
    
    # 验证构建成功
    if [ ! -d "$DOCEAR_APP" ]; then
        echo -e "${RED}错误: 构建失败，未找到 Docear.app 在 $DOCEAR_APP${NC}"
        exit 1
    fi
    
    echo ""
    echo -e "${YELLOW}步骤 3/4: 修复 Docear.app 启动器...${NC}"
    fix_docear_app "$DOCEAR_APP"
    
    echo ""
    echo -e "${GREEN}构建成功！${NC}"
    echo -e "${GREEN}应用位于: $DOCEAR_APP${NC}"
    
    # 复制到 Temp 目录
    echo ""
    echo -e "${YELLOW}正在复制到 $TEMP_DIR ...${NC}"
    mkdir -p "$TEMP_DIR"
    rm -rf "$TEMP_DIR/Docear.app"
    cp -R "$DOCEAR_APP" "$TEMP_DIR/"
    
    # 安装到 /Applications
    if [ $install_applications -eq 1 ]; then
        echo ""
        echo -e "${YELLOW}步骤 4/4: 安装到 $APPLICATIONS_DIR/Docear.app ...${NC}"
        if [ -d "$APPLICATIONS_DIR/Docear.app" ]; then
            # Quit running app if possible
            osascript -e 'tell application "Docear" to quit' 2>/dev/null || true
            sleep 1
            rm -rf "$APPLICATIONS_DIR/Docear.app"
        fi
        if cp -R "$DOCEAR_APP" "$APPLICATIONS_DIR/Docear.app"; then
            echo -e "${GREEN}已安装: $APPLICATIONS_DIR/Docear.app${NC}"
            xattr -dr com.apple.quarantine "$APPLICATIONS_DIR/Docear.app" 2>/dev/null || true
        else
            echo -e "${RED}复制到 /Applications 失败（可能需要权限）。可手动执行:${NC}"
            echo "  rm -rf /Applications/Docear.app && cp -R \"$DOCEAR_APP\" /Applications/"
            exit 1
        fi
    else
        echo ""
        echo -e "${YELLOW}已跳过安装到 /Applications（--no-applications）${NC}"
    fi
    
    # 验证复制
    if [ -d "$TEMP_DIR/Docear.app" ]; then
        echo -e "${GREEN}========================================${NC}"
        echo -e "${GREEN}完成！${NC}"
        echo -e "${GREEN}构建产物: $DOCEAR_APP${NC}"
        echo -e "${GREEN}Temp 副本: $TEMP_DIR/Docear.app${NC}"
        if [ $install_applications -eq 1 ]; then
            echo -e "${GREEN}Applications: $APPLICATIONS_DIR/Docear.app${NC}"
        fi
        echo -e "${GREEN}========================================${NC}"
        
        echo ""
        echo -e "${YELLOW}运行方式:${NC}"
        if [ $install_applications -eq 1 ]; then
            echo "  open -a Docear"
            echo "  或: open $APPLICATIONS_DIR/Docear.app"
        fi
        echo "  open $DOCEAR_APP"
    else
        echo -e "${RED}复制失败！${NC}"
        exit 1
    fi
}

# 运行主程序
main "$@"
