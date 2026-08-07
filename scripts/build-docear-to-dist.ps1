# Build Twigmark/Docear -> publish to a local dist folder -> extract -> launch docear.exe
# Does not modify user mind-map libraries.
#
# For a reproducible zip WITHOUT deploy/launch, prefer:
#   scripts\package-twigmark.ps1
#
# Usage:
#   Double-click: build-docear.bat
#   Or: powershell -ExecutionPolicy Bypass -File .\scripts\build-docear-to-dist.ps1
#
# Options:
#   -SkipBuild          reuse existing windows zip
#   -TargetDir <path>   install dir (default: <repo>\dist\TwigmarkDist, or $env:DOCEAR_DIST_DIR)
#   -NoLaunch           do not start after deploy
#
# Optional env:
#   DOCEAR_DIST_DIR, DOCEAR_DAILY_INSTALL, DOCEAR_WORK_DIR

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
        $TargetDir = Join-Path ((Resolve-Path (Join-Path $PSScriptRoot "..")).Path) "dist\TwigmarkDist"
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
Write-Host "Twigmark build and deploy" -ForegroundColor Green
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
        Write-Step "Verify build output (relationship graph + map tag filter)"
        Assert-RelationshipGraphPluginLayout -PluginsRoot $frameworkBuildPlugins -Context "docear_framework/build/plugins"
        Assert-MapTagFilterLayout -PluginsRoot $frameworkBuildPlugins -Context "docear_framework/build/plugins"
    }
}
else {
    Write-Host "Skipped build (-SkipBuild); deploying existing zip."
}

New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null

$patterns = @("twigmark_windows.zip", "docear_windows.zip", "twigmark_windows.zip.MD5", "docear_windows.zip.MD5", "gitinfo-*.txt", "history_en.txt")
foreach ($pat in $patterns) {
    Get-ChildItem -Path $distDir -Filter $pat -ErrorAction SilentlyContinue | ForEach-Object {
        Copy-Item -Path $_.FullName -Destination (Join-Path $TargetDir $_.Name) -Force
    }
}

$windowsZip = $null
foreach ($name in @("twigmark_windows.zip", "docear_windows.zip")) {
    $candidate = Join-Path $distDir $name
    if (Test-Path $candidate) {
        $windowsZip = $candidate
        break
    }
}
if ($null -eq $windowsZip) {
    throw "Package not found under $distDir (expected twigmark_windows.zip or docear_windows.zip)"
}

$extractDir = Join-Path $TargetDir "twigmark_windows"
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

Write-Step "Write default working-directory.txt"
# Prefer env; otherwise leave unset so first-run wizard chooses the home directory.
$defaultWorkDir = $env:DOCEAR_WORK_DIR
function Write-WorkingDirectoryFile([string] $FilePath, [string] $Value) {
    # UTF-8 without BOM — a BOM breaks Java path reading.
    [System.IO.File]::WriteAllText($FilePath, ($Value.Trim() + "`n"), [System.Text.UTF8Encoding]::new($false))
}
$wdFile = Join-Path $installDir "working-directory.txt"
if (-not [string]::IsNullOrWhiteSpace($defaultWorkDir)) {
    Write-WorkingDirectoryFile -FilePath $wdFile -Value $defaultWorkDir
    Write-Host "working-directory.txt -> $defaultWorkDir"
}
else {
    Write-Host "DOCEAR_WORK_DIR not set; skipping working-directory.txt (first-run wizard will ask)."
}

# Optional sync into a daily install path when DOCEAR_DAILY_INSTALL is set (or legacy path exists).
$dailyInstall = $env:DOCEAR_DAILY_INSTALL
if ([string]::IsNullOrWhiteSpace($dailyInstall) -and (Test-Path "E:\SoftWare\Docear")) {
    $dailyInstall = "E:\SoftWare\Docear"
}
$launchDir = $installDir
if ((-not [string]::IsNullOrWhiteSpace($dailyInstall)) -and (Test-Path $dailyInstall) -and ($installDir -ne $dailyInstall)) {
    Write-Step "Sync core + plugins into $dailyInstall"
    $srcCoreLib = Join-Path $installDir "core\org.freeplane.core\lib"
    $dstCoreLib = Join-Path $dailyInstall "core\org.freeplane.core\lib"
    if ((Test-Path $srcCoreLib) -and (Test-Path $dstCoreLib)) {
        Copy-Item -Path (Join-Path $srcCoreLib "*") -Destination $dstCoreLib -Force
        Write-Host "Updated core lib jars (ribbon / pomodoro / git UI)"
    }
    $srcPlugins = Join-Path $installDir "plugins"
    $dstPlugins = Join-Path $dailyInstall "plugins"
    if ((Test-Path $srcPlugins) -and (Test-Path $dstPlugins)) {
        Copy-Item -Path (Join-Path $srcPlugins "*") -Destination $dstPlugins -Recurse -Force
        Write-Host "Updated plugins"
    }
    if (-not [string]::IsNullOrWhiteSpace($defaultWorkDir)) {
        Write-WorkingDirectoryFile -FilePath (Join-Path $dailyInstall "working-directory.txt") -Value $defaultWorkDir
    }
    # Ribbon XML was write-once-cached under resource-cache (bundle:// URL hash).
    # Stale copies keep the old "帮助" tab even after freeplaneeditor.jar is updated.
    $cacheDirs = @(
        (Join-Path $dailyInstall "workspace\data\resource-cache"),
        (Join-Path $installDir "workspace\data\resource-cache"),
        (Join-Path $env:APPDATA "Docear\resource-cache"),
        (Join-Path $env:APPDATA "Twigmark\resource-cache")
    )
    foreach ($cacheDir in $cacheDirs) {
        if (Test-Path $cacheDir) {
            Get-ChildItem $cacheDir -File -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match 'mindmapmoderibbon|ribbons\.xml|workspace_ribbon|docear_core_ribbon' } |
                ForEach-Object {
                    Remove-Item -LiteralPath $_.FullName -Force -ErrorAction SilentlyContinue
                    Write-Host "Cleared stale ribbon cache: $($_.FullName)"
                }
        }
    }
    $userRibbon = Join-Path $dailyInstall "workspace\data\mindmapmoderibbon.xml"
    if (Test-Path $userRibbon) {
        Write-Host "NOTE: user override exists (takes precedence over jar): $userRibbon"
    }
    $launchDir = $dailyInstall
}

Write-Step "Verify installed layout"
$installPlugins = Join-Path $installDir "plugins"
Assert-RelationshipGraphPluginLayout -PluginsRoot $installPlugins -Context "installed $installPlugins"
Assert-CalendarHubLayout -InstallDir $installDir -Context "installed $installDir"
Assert-MapTagFilterLayout -PluginsRoot $installPlugins -Context "installed $installPlugins"

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
Write-Host "  Launch:   $launchDir"
Write-Host "  Scheduling hub shortcut: Ctrl+Shift+D"
Write-Host "  Draw.io: open a .drawio file from the workspace"
Write-Host "  Prefer docear.bat (uses bundled jre)"
Write-Host ""

if (-not $NoLaunch) {
    Write-Step "Launch Docear"
    Start-DocearFromInstallDir -InstallDir $launchDir | Out-Null
}
else {
    Write-Host "Skipped launch (-NoLaunch). Run: $launchDir\docear.exe"
}
