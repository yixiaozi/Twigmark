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

$installDir = $null
$subDirs = Get-ChildItem -Path $extractDir -Directory -ErrorAction SilentlyContinue
foreach ($subDir in $subDirs) {
    if (Test-Path (Join-Path $subDir.FullName "freeplanelauncher.jar")) {
        $installDir = $subDir.FullName
        break
    }
}

if ($null -eq $installDir) {
    throw "Could not find Docear install folder under $extractDir"
}

Write-Output "Published Docear packages to $TargetDir"
Write-Output "Install folder: $installDir"

$launcherPath = Join-Path $installDir "docear.exe"
if (-not (Test-Path $launcherPath)) {
    $launcherPath = Join-Path $installDir "Docear.exe"
}

if (-not $NoLaunch) {
    if (Test-Path $launcherPath) {
        Write-Output "Stopping existing Docear instances ..."
        Get-CimInstance Win32_Process -Filter "Name='javaw.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -match 'freeplanelauncher\.jar' } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Start-Sleep -Seconds 2
        Remove-Item "$env:APPDATA\Docear\single_instance.lock" -Force -ErrorAction SilentlyContinue
        Write-Output "Launching Docear from $launcherPath ..."
        Start-Process -FilePath $launcherPath
    }
    else {
        Write-Warning "Docear.exe not found in $installDir"
    }
}
