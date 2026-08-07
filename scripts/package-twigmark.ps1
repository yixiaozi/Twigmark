# Build a reproducible Twigmark Windows portable zip into the repo dist folder.
# Does NOT stop running apps, does NOT write E:\ paths, does NOT launch the app.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\package-twigmark.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\package-twigmark.ps1 -SkipBuild
#
# Output:
#   docear_framework/dist/twigmark_windows.zip  (preferred name; also copies alias)

param(
    [switch] $SkipBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$antPath = Join-Path $repoRoot "tools\apache-ant-1.10.14\bin\ant.bat"
$buildFile = Join-Path $repoRoot "docear_framework\ant\build.xml"
$distDir = Join-Path $repoRoot "docear_framework\dist"
$runtimeScript = Join-Path $PSScriptRoot "docear-runtime.ps1"

. $runtimeScript

function Write-Step([string] $Message) {
    Write-Host ""
    Write-Host "==== $Message ====" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Twigmark portable package (no deploy)" -ForegroundColor Green
Write-Host "Repo: $repoRoot"
Write-Host ""

& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "ensure-build-metadata.ps1") | Out-Null

Write-Step "Locate JDK 8"
$jdkHome = Find-Jdk8Home
if ($null -eq $jdkHome) {
    throw "JDK 8 not found. Set JAVA_HOME to a full JDK 8 and retry."
}
$env:JAVA_HOME = $jdkHome
$env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
Write-Host "JAVA_HOME = $env:JAVA_HOME"

Write-Step "Prepare JavaFX JRE cache (optional Draw.io)"
$setupJavaFx = Join-Path $PSScriptRoot "setup-drawio-javafx.ps1"
if (Test-Path $setupJavaFx) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $setupJavaFx
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "setup-drawio-javafx.ps1 failed; continuing without bundled JavaFX"
    }
}

if (!(Test-Path $antPath)) {
    throw "Ant not found: $antPath"
}
if (!(Test-Path $buildFile)) {
    throw "Build file not found: $buildFile"
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null

if (-not $SkipBuild) {
    Write-Step "Ant docear-dist (writes only under repo dist/build)"
    $antLog = Join-Path $distDir "ant-package-twigmark.log"
    Push-Location $repoRoot
    try {
        # Force stable status so packaging does not rewrite version.properties to devel.
        & cmd /c "`"$antPath`" -f `"$buildFile`" -Ddocear.version.status=stable docear-dist > `"$antLog`" 2>&1"
        $antExit = $LASTEXITCODE
        if (Test-Path $antLog) {
            Write-Host "---- ant log (tail) ----"
            Get-Content -Path $antLog -Tail 40 | ForEach-Object { Write-Host $_ }
            Write-Host "---- end ant log ----"
        }
        if ($antExit -ne 0) {
            throw "Build failed with exit code $antExit. Log: $antLog"
        }
    }
    finally {
        Pop-Location
    }
}
else {
    Write-Host "Skipped ant (-SkipBuild)"
}

function Find-WindowsZip([string] $Dir) {
    foreach ($name in @("twigmark_windows.zip", "docear_windows.zip")) {
        $p = Join-Path $Dir $name
        if (Test-Path $p) { return $p }
    }
    return $null
}

$windowsZip = Find-WindowsZip $distDir
if ($null -eq $windowsZip) {
    throw "Windows zip not found under $distDir"
}

$aliasZip = Join-Path $distDir "twigmark_windows.zip"
if ($windowsZip -ne $aliasZip) {
    Copy-Item -Path $windowsZip -Destination $aliasZip -Force
}

# Copy release docs next to the zip for distribution folders
foreach ($doc in @("README.md", "LICENSE", "CHANGELOG.md", "RELEASE_NOTES.md", "THIRD_PARTY_NOTICES.md")) {
    $src = Join-Path $repoRoot $doc
    if (Test-Path $src) {
        Copy-Item -Path $src -Destination (Join-Path $distDir $doc) -Force
    }
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host "Portable zip: $aliasZip"
Write-Host "Unzip and run docear.exe (launcher filename kept for compatibility)."
Write-Host ""
