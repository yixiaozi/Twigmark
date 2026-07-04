# Download and install a Java 8 JRE with JavaFX (BellSoft Liberica Full)
# for embedded Draw.io in Docear.
#
# NOTE: Draw.io embed is disabled in this fork. This script is kept for reference only.
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\setup-drawio-javafx.ps1
#
# Output:
#   docear_framework\build\jre\   (bundled into docear_windows.zip)
#
# Download cache (persists across "ant clean"):
#   docear_framework\cache\javafx\

param(
    [string] $Version = "8u462+11"
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$buildJreDir = Join-Path $repoRoot "docear_framework\build\jre"
$cacheDir = Join-Path $repoRoot "docear_framework\cache\javafx"
$cachedJreDir = Join-Path $cacheDir "jre"
$zipName = "bellsoft-jdk${Version}-windows-amd64-full.zip"
$zipPath = Join-Path $cacheDir $zipName
$url = "https://download.bell-sw.com/java/$Version/$zipName"
$jfxrtJar = Join-Path $buildJreDir "lib\ext\jfxrt.jar"
$buildJavaExe = Join-Path $buildJreDir "bin\java.exe"

function Test-JavaFxJre([string] $jreRoot) {
    return (Test-Path (Join-Path $jreRoot "bin\java.exe")) -and (Test-Path (Join-Path $jreRoot "lib\ext\jfxrt.jar"))
}

function Copy-JreTree([string] $source, [string] $destination) {
    if (Test-Path $destination) {
        Remove-Item -Path $destination -Recurse -Force
    }
    Copy-Item -Path $source -Destination $destination -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null

# Rebuild only copies build\jre into the zip; ant clean wipes build\ but not cache\.
if (Test-JavaFxJre $buildJreDir) {
    Write-Host "JavaFX JRE already present: $buildJreDir (skip download/extract)"
    exit 0
}

# Migrate zip from older cache locations (one-time).
if (!(Test-Path $zipPath) -or (Get-Item $zipPath).Length -lt 1000000) {
    $legacyZipPaths = @(
        (Join-Path $repoRoot "docear_plugin_drawio\lib\javafx-cache\$zipName"),
        (Join-Path $repoRoot "docear_framework\build\javafx-cache\$zipName")
    )
    foreach ($legacyZip in $legacyZipPaths) {
        if ((Test-Path $legacyZip) -and (Get-Item $legacyZip).Length -ge 1000000) {
            Write-Host "Reusing cached JDK zip from $legacyZip"
            Copy-Item -Path $legacyZip -Destination $zipPath -Force
            break
        }
    }
}

if (!(Test-Path $zipPath) -or (Get-Item $zipPath).Length -lt 1000000) {
    Write-Host "Downloading Liberica Full JDK $Version (includes JavaFX)..."
    Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing -TimeoutSec 600
}
else {
    Write-Host "Using cached JDK zip: $zipPath"
}

if (!(Test-JavaFxJre $cachedJreDir)) {
    Write-Host "Extracting JRE to cache $cachedJreDir ..."
    $extractRoot = Join-Path $cacheDir "extract-$Version"
    if (Test-Path $extractRoot) {
        Remove-Item -Path $extractRoot -Recurse -Force
    }
    Expand-Archive -Path $zipPath -DestinationPath $extractRoot -Force

    $jreSource = Get-ChildItem -Path $extractRoot -Recurse -Directory -Filter "jre" |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
        Select-Object -First 1

    if ($null -eq $jreSource) {
        throw "Could not find jre/bin/java.exe inside $zipName"
    }

    Copy-JreTree $jreSource.FullName $cachedJreDir
    Remove-Item -Path $extractRoot -Recurse -Force -ErrorAction SilentlyContinue
}
else {
    Write-Host "Using cached extracted JRE: $cachedJreDir"
}

Write-Host "Copying JRE to $buildJreDir ..."
Copy-JreTree $cachedJreDir $buildJreDir

if (!(Test-JavaFxJre $buildJreDir)) {
    throw "Bundled JRE is missing JavaFX (jfxrt.jar): $buildJreDir"
}

$versionOutput = cmd /c "`"$buildJavaExe`" -version 2>&1"
Write-Host "JavaFX JRE ready: $buildJavaExe"
Write-Host $versionOutput
Write-Host "Done. Rebuild Docear so docear_windows.zip includes build\jre."
