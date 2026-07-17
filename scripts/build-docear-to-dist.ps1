# 一键编译 Docear → 发布到 E:\Temp\DocearDist → 解压 → 启动 docear.exe
# 导图数据仍在 E:\yixiaozi，本脚本不会动你的思维导图。
#
# 用法（任选其一）:
#   双击仓库根目录: 一键编译并启动.bat
#   或在仓库根目录执行:
#     powershell -ExecutionPolicy Bypass -File .\scripts\build-docear-to-dist.ps1
#
# 可选参数:
#   -SkipBuild          跳过 ant，只用已有 docear_windows.zip 部署
#   -TargetDir <path>   安装目录（默认 E:\Temp\DocearDist）
#   -NoLaunch           编完不自动启动

param(
    [switch] $SkipBuild,
    [string] $TargetDir = "E:\Temp\DocearDist",
    [switch] $NoLaunch
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$antPath = Join-Path $repoRoot "tools\apache-ant-1.10.14\bin\ant.bat"
$buildFile = Join-Path $repoRoot "docear_framework\ant\build.xml"
$distDir = Join-Path $repoRoot "docear_framework\dist"
$frameworkBuildPlugins = Join-Path $repoRoot "docear_framework\build\plugins"
$runtimeScript = Join-Path $PSScriptRoot "docear-runtime.ps1"

. $runtimeScript

function Write-Step([string] $Message) {
    Write-Host ""
    Write-Host "==== $Message ====" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Docear 一键编译 / 部署" -ForegroundColor Green
Write-Host "仓库: $repoRoot"
Write-Host "目标: $TargetDir"
Write-Host ""

& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "ensure-build-metadata.ps1") | Out-Null

Write-Step "检查关系图源码清单"
Assert-RelationshipGraphSourceManifest -RepoRoot $repoRoot

Write-Step "定位 JDK 8"
$jdkHome = Find-Jdk8Home
if ($null -eq $jdkHome) {
    throw @"
未找到 JDK 8（需要带 javac 的 JDK，不是仅 JRE）。

请安装 Eclipse Temurin / Adoptium JDK 8，常见路径:
  C:\Program Files\Eclipse Adoptium\jdk-8.0.xxx-hotspot

或设置环境变量 JAVA_HOME 指向 JDK 8 根目录后再运行本脚本。
"@
}
$env:JAVA_HOME = $jdkHome
$env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
Write-Host "JAVA_HOME = $env:JAVA_HOME"
& java -version 2>&1 | ForEach-Object { Write-Host $_ }

if (!(Test-Path $antPath)) {
    throw "找不到 Ant: $antPath（仓库应自带 tools\apache-ant-1.10.14）"
}
if (!(Test-Path $buildFile)) {
    throw "找不到构建文件: $buildFile"
}

if (-not $SkipBuild) {
    Write-Step "开始 Ant 完整构建（含 freeplane jar + 插件 + Windows zip，可能需几分钟）"
    Push-Location $repoRoot
    try {
        & $antPath -f $buildFile docear-dist
        if ($LASTEXITCODE -ne 0) {
            throw "Docear 构建失败，退出码 $LASTEXITCODE。请向上翻看 ant 报错。"
        }
    }
    finally {
        Pop-Location
    }

    if (Test-Path $frameworkBuildPlugins) {
        Write-Step "校验构建产物（关系图）"
        Assert-RelationshipGraphPluginLayout -PluginsRoot $frameworkBuildPlugins -Context "docear_framework/build/plugins"
    }
}
else {
    Write-Host "已跳过编译（-SkipBuild），使用现有 zip 部署。"
}

New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null

$patterns = @("docear_windows.zip", "docear_windows.zip.MD5", "gitinfo-*.txt", "history_en.txt")
foreach ($pat in $patterns) {
    Get-ChildItem -Path $distDir -Filter $pat -ErrorAction SilentlyContinue | ForEach-Object {
        Copy-Item -Path $_.FullName -Destination (Join-Path $TargetDir $_.Name) -Force
    }
}

$windowsZip = Join-Path $distDir "docear_windows.zip"
if (!(Test-Path $windowsZip)) {
    throw "未找到打包结果: $windowsZip（请去掉 -SkipBuild 重新完整编译）"
}

$extractDir = Join-Path $TargetDir "docear_windows"
Write-Step "停止正在运行的 Docear"
Stop-RunningDocear
Write-Host "解压到 $extractDir ..."

if (Test-Path $extractDir) {
    try {
        Remove-Item -Path $extractDir -Recurse -Force -ErrorAction Stop
    }
    catch {
        $backupName = "docear_windows.old." + (Get-Date -Format "yyyyMMdd-HHmmss")
        Write-Warning "无法删除旧目录（文件可能被占用），改名为 $backupName"
        Write-Warning "若仍异常，请手动关掉 Docear 后再运行。"
        Rename-Item -Path $extractDir -NewName $backupName -Force
    }
}

Expand-Archive -Path $windowsZip -DestinationPath $extractDir -Force
Write-Host "解压完成。"

$installDir = Find-DocearInstallDir -RootDir $extractDir
if ($null -eq $installDir) {
    throw "在 $extractDir 下找不到 Docear 安装目录（缺少 freeplanelauncher.jar）"
}

Write-Step "校验安装目录"
$installPlugins = Join-Path $installDir "plugins"
Assert-RelationshipGraphPluginLayout -PluginsRoot $installPlugins -Context "installed $installPlugins"
Assert-CalendarHubLayout -InstallDir $installDir -Context "installed $installDir"

Write-Host ""
Write-Host "发布完成" -ForegroundColor Green
Write-Host "  包目录: $TargetDir"
Write-Host "  程序目录: $installDir"
Write-Host "  安排中心快捷键: Ctrl+Shift+D"
Write-Host ""

if (-not $NoLaunch) {
    Write-Step "启动 Docear"
    Start-DocearFromInstallDir -InstallDir $installDir | Out-Null
}
else {
    Write-Host "已跳过启动（-NoLaunch）。可手动运行: $installDir\docear.exe"
}
