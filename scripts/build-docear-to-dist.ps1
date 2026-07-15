# Build Docear, publish to E:\Temp\DocearDist, extract, and launch docear.exe.
# Library/data (mind maps, workspace) stays at E:\yixiaozi — not touched by this script.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\build-docear-to-dist.ps1
#
# Optional:
#   -SkipBuild          skip ant; reuse existing docear_framework\dist\docear_windows.zip
#   -TargetDir <path>    override install dir (default: E:\Temp\DocearDist)
#   -NoLaunch            do not start Docear after extraction
#
# Also verifies the relationship-graph OSGi layout (tagfilter Export-Package +
# TagGroupFilterBarFactory) so 「关系图」does not ship with NoClassDefFoundError.

param(
    [switch] $SkipBuild,
    [string] $TargetDir = "E:\Temp\DocearDist",
    [switch] $NoLaunch
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$antPath = Join-Path $repoRoot "tools\apache-ant-1.10.14\bin\ant.bat"
$buildFile = Join-Path $repoRoot "docear_framework\ant\build.xml"
$distDir = Join-Path $repoRoot "docear_framework\dist"
$frameworkBuildPlugins = Join-Path $repoRoot "docear_framework\build\plugins"
$runtimeScript = Join-Path $PSScriptRoot "docear-runtime.ps1"

. $runtimeScript

& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "ensure-build-metadata.ps1") | Out-Null

# Fail fast: source tree must export tagfilter before we spend time on ant.
Assert-RelationshipGraphSourceManifest -RepoRoot $repoRoot

$candidates = @(
    "C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot",
    "C:\Program Files\Eclipse Adoptium\jdk-8.0.412.8-hotspot",
    "C:\Program Files\Eclipse Adoptium\jdk-8.0.392.8-hotspot"
)
foreach ($jdk in $candidates) {
    if (Test-Path $jdk) {
        $env:JAVA_HOME = $jdk
        $env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
        break
    }
}

if (!(Test-Path $antPath)) {
    throw "Ant not found at $antPath"
}
if (!(Test-Path $buildFile)) {
    throw "Build file not found at $buildFile"
}

if (-not $SkipBuild) {
    Push-Location $repoRoot
    try {
        & $antPath -f $buildFile docear-dist
        if ($LASTEXITCODE -ne 0) {
            throw "Docear build failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }

    if (Test-Path $frameworkBuildPlugins) {
        Assert-RelationshipGraphPluginLayout -PluginsRoot $frameworkBuildPlugins -Context "docear_framework/build/plugins"
    }
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
    throw "Expected package not found: $windowsZip"
}

$extractDir = Join-Path $TargetDir "docear_windows"
Write-Output "Stopping running Docear before deploy ..."
Stop-RunningDocear
Write-Output "Extracting $windowsZip to $extractDir ..."

if (Test-Path $extractDir) {
    try {
        Remove-Item -Path $extractDir -Recurse -Force -ErrorAction Stop
    }
    catch {
        $backupName = "docear_windows.old." + (Get-Date -Format "yyyyMMdd-HHmmss")
        Write-Warning "Could not delete $extractDir (files in use?). Renaming to $backupName"
        Write-Warning "Close Docear if it is running from the old install, then rebuild."
        Rename-Item -Path $extractDir -NewName $backupName -Force
    }
}

Expand-Archive -Path $windowsZip -DestinationPath $extractDir -Force
Write-Output "Extraction completed."

$installDir = Find-DocearInstallDir -RootDir $extractDir
if ($null -eq $installDir) {
    throw "Could not find Docear install folder under $extractDir"
}

$installPlugins = Join-Path $installDir "plugins"
Assert-RelationshipGraphPluginLayout -PluginsRoot $installPlugins -Context "installed $installPlugins"

Write-Output "Published Docear packages to $TargetDir"
Write-Output "Install folder: $installDir"

if (-not $NoLaunch) {
    Start-DocearFromInstallDir -InstallDir $installDir | Out-Null
}
