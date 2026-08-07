#!/bin/bash
# Twigmark Mac 版本发布脚本
set -e

# 配置
PROJECT_DIR="/Users/wangyang/Develop/Docear-Desktop"
TEMP_DIR="/Users/wangyang/Temp"
APPLICATIONS_DIR="/Applications"
ANT_CMD="${PROJECT_DIR}/tools/apache-ant-1.10.14/bin/ant"
APP_NAME="Twigmark"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 帮助信息
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Twigmark Mac 版本构建与发布脚本"
    echo ""
    echo "Options:"
    echo "  -h, --help          Show this help message and exit"
    echo "  --skip-build        Skip building and use existing build artifacts"
    echo "  --check             Check system requirements and exit"
    echo "  --no-applications   Do not copy Twigmark.app into /Applications"
    echo ""
    echo "System Requirements:"
    echo "  - Java Development Kit (JDK) 8 (Apple Silicon 优先 Azul Zulu 8 aarch64)"
    echo "  - Ant 1.10.14 (included in project)"
    echo ""
    echo "How to install JDK 8 on macOS (Apple Silicon):"
    echo "  1. Download Azul Zulu 8 aarch64 JDK:"
    echo "     https://www.azul.com/downloads/?version=java-8-lts&os=macos&architecture=arm-64-bit&package=jdk"
    echo "  2. Install, or unpack into ~/Library/Java/JavaVirtualMachines/zulu-8.jdk"
    echo "  3. Verify: file \$(/usr/libexec/java_home -v 1.8)/bin/java   # should say arm64"
    echo ""
    echo "Intel Mac / fallback: Eclipse Temurin 8 x86_64 is still supported (Rosetta on Apple Silicon)."
    echo ""
    echo "Typical rebuild + install:"
    echo "  ./build_docear_mac.sh"
    echo "  # Result: docear_framework/build4mac/Twigmark.app and /Applications/Twigmark.app"
    echo ""
}

# 修复 Twigmark.app
fix_mac_app() {
    local app_path="$1"
    # 模板在版本库内（不依赖 build4mac，clean 后仍可用）
    local launcher_src="$PROJECT_DIR/docear_framework/macos/Twigmark"
    echo -e "${YELLOW}正在修复 $app_path ...${NC}"
    
    # 1. 使用仓库内维护的启动脚本：Apple Silicon 优先 arm64 JDK 8，避免 Rosetta
    if [ ! -f "$launcher_src" ]; then
        echo -e "${RED}找不到启动器模板: $launcher_src${NC}"
        exit 1
    fi
    cp "$launcher_src" "$app_path/Contents/MacOS/Twigmark"
    
    # 2. 设置执行权限
    chmod +x "$app_path/Contents/MacOS/Twigmark"
    
    # 3. 修改 Info.plist
    local plist_path="$app_path/Contents/Info.plist"
    
    /usr/libexec/PlistBuddy -c "Set :CFBundleExecutable Twigmark" "$plist_path" 2>/dev/null || {
        sed -i '' 's/FreeplaneJavaApplicationStub/Twigmark/g' "$plist_path"
    }
    /usr/libexec/PlistBuddy -c "Set :CFBundleName Twigmark" "$plist_path" 2>/dev/null || true
    /usr/libexec/PlistBuddy -c "Set :CFBundleDisplayName Twigmark" "$plist_path" 2>/dev/null || \
        /usr/libexec/PlistBuddy -c "Add :CFBundleDisplayName string Twigmark" "$plist_path" 2>/dev/null || true
    /usr/libexec/PlistBuddy -c "Set :CFBundleIconFile twigmark.icns" "$plist_path" 2>/dev/null || true
    
    # 4. 删除旧的启动器
    rm -f "$app_path/Contents/MacOS/FreeplaneJavaApplicationStub"
    rm -f "$app_path/Contents/MacOS/Docear"
    
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
    echo -e "${GREEN}Twigmark Mac 版本构建与发布${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    
    # Prefer arm64 JDK 8 (Zulu/Temurin) on Apple Silicon; avoid JavaAppletPlugin / Rosetta when possible
    if [ -z "$JAVA_HOME" ] || [[ "$JAVA_HOME" == *"JavaAppletPlugin"* ]]; then
        local _home=""
        if [ "$(uname -m)" = "arm64" ]; then
            for _home in \
                "${HOME}/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home" \
                "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home" \
                "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"
            do
                if [ -x "${_home}/bin/javac" ] && file "${_home}/bin/java" 2>/dev/null | grep -q arm64; then
                    export JAVA_HOME="$_home"
                    break
                fi
            done
        fi
        if [ -z "$JAVA_HOME" ] || [[ "$JAVA_HOME" == *"JavaAppletPlugin"* ]]; then
            if [ -x "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/javac" ]; then
                export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"
            else
                export JAVA_HOME=$(/usr/libexec/java_home -v 1.8 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)
            fi
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
    
    TWIGMARK_APP="$PROJECT_DIR/docear_framework/build4mac/${APP_NAME}.app"
    
    # 检查是否需要构建
    if [ $skip_build -eq 0 ]; then
        echo ""
        echo -e "${YELLOW}开始构建 Twigmark...${NC}"
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
        
        # 现在构建完整的 Twigmark Mac 应用
        echo ""
        echo -e "${YELLOW}步骤 2/4: 构建 Twigmark 应用...${NC}"
        cd "$PROJECT_DIR/docear_framework"
        "$ANT_CMD" -f ant/build.xml clean macosxapp || {
            echo -e "${YELLOW}Trying just 'macosxapp' without clean...${NC}"
            "$ANT_CMD" -f ant/build.xml macosxapp
        }
    fi
    
    # 验证构建成功
    if [ ! -d "$TWIGMARK_APP" ]; then
        echo -e "${RED}错误: 构建失败，未找到 ${APP_NAME}.app 在 $TWIGMARK_APP${NC}"
        exit 1
    fi
    
    echo ""
    echo -e "${YELLOW}步骤 3/4: 修复 ${APP_NAME}.app 启动器...${NC}"
    fix_mac_app "$TWIGMARK_APP"
    
    echo ""
    echo -e "${GREEN}构建成功！${NC}"
    echo -e "${GREEN}应用位于: $TWIGMARK_APP${NC}"
    
    # 复制到 Temp 目录
    echo ""
    echo -e "${YELLOW}正在复制到 $TEMP_DIR ...${NC}"
    mkdir -p "$TEMP_DIR"
    rm -rf "$TEMP_DIR/${APP_NAME}.app"
    cp -R "$TWIGMARK_APP" "$TEMP_DIR/"
    
    # 安装到 /Applications
    if [ $install_applications -eq 1 ]; then
        echo ""
        echo -e "${YELLOW}步骤 4/4: 安装到 $APPLICATIONS_DIR/${APP_NAME}.app ...${NC}"
        # Quit legacy Docear / Twigmark if running
        osascript -e 'tell application "Twigmark" to quit' 2>/dev/null || true
        osascript -e 'tell application "Docear" to quit' 2>/dev/null || true
        sleep 1
        rm -rf "$APPLICATIONS_DIR/${APP_NAME}.app"
        rm -rf "$APPLICATIONS_DIR/Docear.app"
        if cp -R "$TWIGMARK_APP" "$APPLICATIONS_DIR/${APP_NAME}.app"; then
            echo -e "${GREEN}已安装: $APPLICATIONS_DIR/${APP_NAME}.app${NC}"
            xattr -dr com.apple.quarantine "$APPLICATIONS_DIR/${APP_NAME}.app" 2>/dev/null || true
        else
            echo -e "${RED}复制到 /Applications 失败（可能需要权限）。可手动执行:${NC}"
            echo "  rm -rf /Applications/${APP_NAME}.app && cp -R \"$TWIGMARK_APP\" /Applications/"
            exit 1
        fi
    else
        echo ""
        echo -e "${YELLOW}已跳过安装到 /Applications（--no-applications）${NC}"
    fi
    
    # 验证复制
    if [ -d "$TEMP_DIR/${APP_NAME}.app" ]; then
        echo -e "${GREEN}========================================${NC}"
        echo -e "${GREEN}完成！${NC}"
        echo -e "${GREEN}构建产物: $TWIGMARK_APP${NC}"
        echo -e "${GREEN}Temp 副本: $TEMP_DIR/${APP_NAME}.app${NC}"
        if [ $install_applications -eq 1 ]; then
            echo -e "${GREEN}Applications: $APPLICATIONS_DIR/${APP_NAME}.app${NC}"
        fi
        echo -e "${GREEN}========================================${NC}"
        
        echo ""
        echo -e "${YELLOW}运行方式:${NC}"
        if [ $install_applications -eq 1 ]; then
            echo "  open -a Twigmark"
            echo "  或: open $APPLICATIONS_DIR/${APP_NAME}.app"
        fi
        echo "  open $TWIGMARK_APP"
    else
        echo -e "${RED}复制失败！${NC}"
        exit 1
    fi
}

# 运行主程序
main "$@"
