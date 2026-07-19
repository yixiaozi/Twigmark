# Build Docear -> publish to a local dist folder -> extract -> launch docear.exe
# Does not modify user mind-map libraries.
#
# Usage:
#   Double-click: build-docear.bat
#   Or: powershell -ExecutionPolicy Bypass -File .\scripts\build-docear-to-dist.ps1
#
# Options:
#   -SkipBuild          reuse existing docear_windows.zip
#   -TargetDir <path>   install dir (default: E:\Temp\DocearDist, or $env:DOCEAR_DIST_DIR)
#   -NoLaunch           do not start Docear after deploy

param(
    [switch] $SkipBuild,
    [string] $TargetDir = "",
    [switch] $NoLaunch
)

if ([string]::IsNullOrWhiteSpace($TargetDir)) {
    if (-not [string]::IsNullOrWhiteSpace($env:DOCEAR_DIST_DIR)) {
        $TargetDir = $env:DOCEAR_DIST_DIR
    }
    else {
        $TargetDir = "E:\Temp\DocearDist"
    }
}

$ErrorActionPreference = "Stop"

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
Write-Host "Docear build and deploy" -ForegroundColor Green
Write-Host "Repo:   $repoRoot"
Write-Host "Target: $TargetDir"
Write-Host ""

& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "ensure-build-metadata.ps1") | Out-Null

Write-Step "Check relationship-graph source manifest"
Assert-RelationshipGraphSourceManifest -RepoRoot $repoRoot

Write-Step "Locate JDK 8"
$jdkHome = Find-Jdk8Home
if ($null -eq $jdkHome) {
    throw @"
JDK 8 not found (need full JDK with javac, not JRE only).

Install Eclipse Temurin / Adoptium JDK 8, e.g.:
  C:\Program Files\Eclipse Adoptium\jdk-8.0.xxx-hotspot

Or set JAVA_HOME to your JDK 8 root and run again.
"@
}
$env:JAVA_HOME = $jdkHome
$env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
Write-Host "JAVA_HOME = $env:JAVA_HOME"

Write-Step "Prepare JavaFX JRE cache (Draw.io embed)"
$setupJavaFx = Join-Path $PSScriptRoot "setup-drawio-javafx.ps1"
$javaFxCacheJre = Join-Path $repoRoot "docear_framework\cache\javafx\jre"
if (Test-Path $setupJavaFx) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $setupJavaFx
    if ($LASTEXITCODE -ne 0) {
        throw "setup-drawio-javafx.ps1 failed with exit code $LASTEXITCODE"
    }
}
else {
    Write-Warning "Missing $setupJavaFx — Draw.io embed JRE will not be bundled."
}

if (!(Test-Path $antPath)) {
    throw "Ant not found: $antPath (repo should include tools\apache-ant-1.10.14)"
}
if (!(Test-Path $buildFile)) {
    throw "Build file not found: $buildFile"
}

# Avoid Windows file-lock failures: never leave a JRE under build\ before ant clean.
$staleBuildJre = Join-Path $repoRoot "docear_framework\build\jre"
if (Test-Path $staleBuildJre) {
    Write-Step "Remove stale build\jre before ant clean"
    try {
        Remove-Item -Path $staleBuildJre -Recurse -Force -ErrorAction Stop
    }
    catch {
        Write-Warning "Could not delete $staleBuildJre fully: $_. Ant clean may still succeed."
    }
}

if (-not $SkipBuild) {
    Write-Step "Ant full build (freeplane jars + plugins + Windows zip; may take several minutes)"
    $antLog = Join-Path $distDir "ant-docear-dist.log"
    New-Item -ItemType Directory -Force -Path $distDir | Out-Null
    Push-Location $repoRoot
    try {
        & cmd /c "`"$antPath`" -f `"$buildFile`" docear-dist > `"$antLog`" 2>&1"
        $antExit = $LASTEXITCODE
        if (Test-Path $antLog) {
            Write-Host "---- ant log (tail) ----"
            Get-Content -Path $antLog -Tail 80 | ForEach-Object { Write-Host $_ }
            Write-Host "---- end ant log ----"
        }
        if ($antExit -ne 0) {
            throw "Docear build failed with exit code $antExit. Full log: $antLog"
        }
    }
    finally {
        Pop-Location
    }

    if (Test-Path $frameworkBuildPlugins) {
        Write-Step "Verify build output (relationship graph)"
        Assert-RelationshipGraphPluginLayout -PluginsRoot $frameworkBuildPlugins -Context "docear_framework/build/plugins"
    }
}
else {
    Write-Host "Skipped build (-SkipBuild); deploying existing zip."
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
    throw "Package not found: $windowsZip (run without -SkipBuild for a full build)"
}

$extractDir = Join-Path $TargetDir "docear_windows"
Write-Step "Stop running Docear"
Stop-RunningDocear
Write-Host "Extracting to $extractDir ..."

if (Test-Path $extractDir) {
    try {
        Remove-Item -Path $extractDir -Recurse -Force -ErrorAction Stop
    }
    catch {
        $backupName = "docear_windows.old." + (Get-Date -Format "yyyyMMdd-HHmmss")
        Write-Warning "Could not delete $extractDir (files in use?). Renaming to $backupName"
        Write-Warning "Close Docear if it is running from the old install, then retry."
        Rename-Item -Path $extractDir -NewName $backupName -Force
    }
}

Expand-Archive -Path $windowsZip -DestinationPath $extractDir -Force
Write-Host "Extraction done."

$installDir = Find-DocearInstallDir -RootDir $extractDir
if ($null -eq $installDir) {
    throw "Docear install folder not found under $extractDir (missing freeplanelauncher.jar)"
}

Write-Step "Verify installed layout"
$installPlugins = Join-Path $installDir "plugins"
Assert-RelationshipGraphPluginLayout -PluginsRoot $installPlugins -Context "installed $installPlugins"
Assert-CalendarHubLayout -InstallDir $installDir -Context "installed $installDir"

$drawioPlugin = Join-Path $installPlugins "org.docear.plugin.drawio"
if (!(Test-Path $drawioPlugin)) {
    throw "Draw.io plugin missing from install: $drawioPlugin"
}

Write-Step "Inject JavaFX JRE for Draw.io"
$bundledJre = Join-Path $installDir "jre"
$jfxJar = Join-Path $bundledJre "lib\ext\jfxrt.jar"
if (!(Test-Path $jfxJar)) {
    if (!(Test-JavaFxJreRoot $javaFxCacheJre)) {
        throw "JavaFX JRE cache missing at $javaFxCacheJre. Re-run scripts\setup-drawio-javafx.ps1."
    }
    if (Test-Path $bundledJre) {
        Remove-Item -Path $bundledJre -Recurse -Force
    }
    Write-Host "Copying $javaFxCacheJre -> $bundledJre"
    Copy-Item -Path $javaFxCacheJre -Destination $bundledJre -Recurse -Force
}
if (!(Test-Path $jfxJar)) {
    throw "Bundled JavaFX JRE missing ($jfxJar) after inject."
}
Write-Host "Draw.io plugin + JavaFX JRE OK"

Write-Host ""
Write-Host "Deploy complete" -ForegroundColor Green
Write-Host "  Packages: $TargetDir"
Write-Host "  Install:  $installDir"
Write-Host "  Scheduling hub shortcut: Ctrl+Shift+D"
Write-Host "  Draw.io: open a .drawio file from the workspace"
Write-Host "  Launch: prefer docear.bat (uses bundled jre)"
Write-Host ""

if (-not $NoLaunch) {
    Write-Step "Launch Docear"
    Start-DocearFromInstallDir -InstallDir $installDir | Out-Null
}
else {
    Write-Host "Skipped launch (-NoLaunch). Run: $installDir\docear.exe"
}
