# Ensure a Java 8 JRE with JavaFX (BellSoft Liberica Full) is cached for Draw.io.
#
# IMPORTANT: This script only populates:
#   docear_framework\cache\javafx\jre
# It does NOT write into docear_framework\build\jre before ant clean
# (that race locks files on Windows and breaks "ant clean").
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\setup-drawio-javafx.ps1
#
# build-docear-to-dist.ps1 copies the cache into the installed app after extract.

param(
    [string] $Version = "8u462+11"
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$cacheDir = Join-Path $repoRoot "docear_framework\cache\javafx"
$cachedJreDir = Join-Path $cacheDir "jre"
$zipName = "bellsoft-jdk${Version}-windows-amd64-full.zip"
$zipPath = Join-Path $cacheDir $zipName
$url = "https://download.bell-sw.com/java/$Version/$zipName"

function Test-JavaFxJre([string] $jreRoot) {
    if ([string]::IsNullOrWhiteSpace($jreRoot)) {
        return $false
    }
    $javaExe = Join-Path $jreRoot "bin\java.exe"
    $javawExe = Join-Path $jreRoot "bin\javaw.exe"
    $jfx = Join-Path $jreRoot "lib\ext\jfxrt.jar"
    return ((Test-Path $javaExe) -or (Test-Path $javawExe)) -and (Test-Path $jfx)
}

function Copy-JreTree([string] $source, [string] $destination) {
    if (Test-Path $destination) {
        Remove-Item -Path $destination -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    Copy-Item -Path $source -Destination $destination -Recurse -Force
}

function Find-JavaFxJreRoot([string] $extractRoot) {
    # Prefer a nested jre/ with JavaFX (classic JDK 8 layout).
    $candidates = Get-ChildItem -Path $extractRoot -Recurse -Directory -Filter "jre" -ErrorAction SilentlyContinue |
        Where-Object { Test-JavaFxJre $_.FullName }
    $first = $candidates | Select-Object -First 1
    if ($null -ne $first) {
        return $first.FullName
    }

    # Liberica Full may keep javafx on the JDK root itself.
    $jdkRoots = Get-ChildItem -Path $extractRoot -Recurse -Directory -ErrorAction SilentlyContinue |
        Where-Object {
            (Test-Path (Join-Path $_.FullName "bin\java.exe")) -and
            (
                (Test-Path (Join-Path $_.FullName "lib\ext\jfxrt.jar")) -or
                (Test-Path (Join-Path $_.FullName "jre\lib\ext\jfxrt.jar"))
            )
        }
    foreach ($root in $jdkRoots) {
        $nested = Join-Path $root.FullName "jre"
        if (Test-JavaFxJre $nested) {
            return $nested
        }
        if (Test-Path (Join-Path $root.FullName "lib\ext\jfxrt.jar")) {
            return $root.FullName
        }
    }
    return $null
}

New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null

if (Test-JavaFxJre $cachedJreDir) {
    Write-Host "JavaFX JRE cache ready: $cachedJreDir"
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
    Write-Host "URL: $url"
    Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing -TimeoutSec 600
}

if ((Get-Item $zipPath).Length -lt 1000000) {
    throw "Download looks invalid (too small): $zipPath"
}

Write-Host "Extracting JRE to cache $cachedJreDir ..."
$extractRoot = Join-Path $cacheDir "extract-$Version"
if (Test-Path $extractRoot) {
    Remove-Item -Path $extractRoot -Recurse -Force
}
Expand-Archive -Path $zipPath -DestinationPath $extractRoot -Force

$jreSource = Find-JavaFxJreRoot $extractRoot
if ($null -eq $jreSource) {
    throw "Could not find a JavaFX JRE (java.exe + lib/ext/jfxrt.jar) inside $zipName"
}

Write-Host "Using JRE source: $jreSource"
Copy-JreTree $jreSource $cachedJreDir
Remove-Item -Path $extractRoot -Recurse -Force -ErrorAction SilentlyContinue

if (!(Test-JavaFxJre $cachedJreDir)) {
    throw "Cached JRE is missing JavaFX (jfxrt.jar): $cachedJreDir"
}

Write-Host "JavaFX JRE cache ready: $cachedJreDir"
Write-Host "Deploy step will copy this into the installed Docear\jre folder."
