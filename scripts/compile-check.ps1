# Compile-only check for agents / local verification.
# NEVER stops Docear, NEVER deploys to E:\Temp\DocearDist or E:\SoftWare\Docear,
# NEVER launches the app. Safe to run while you are using Docear.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\compile-check.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\compile-check.ps1 -Modules freeplane,docear_plugin_core
#   compile-check.bat
#
# Default modules: freeplane (core editor jar). Pass -Modules to compile more.

param(
    [string[]] $Modules = @("freeplane")
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$antPath = Join-Path $repoRoot "tools\apache-ant-1.10.14\bin\ant.bat"
$runtimeScript = Join-Path $PSScriptRoot "docear-runtime.ps1"

. $runtimeScript

function Write-Step([string] $Message) {
    Write-Host ""
    Write-Host "==== $Message ====" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Docear compile-check (no stop / no deploy / no launch)" -ForegroundColor Green
Write-Host "Repo: $repoRoot"
Write-Host "Modules: $($Modules -join ', ')"
Write-Host ""

if (!(Test-Path $antPath)) {
    throw "Ant not found: $antPath"
}

Write-Step "Locate JDK (prefer 8; allow 11+ for modernization compile with source/target 1.8)"
$jdkHome = Find-Jdk8Home
if ($null -eq $jdkHome) {
    $jdkHome = Find-JdkModernHome
}
if ($null -eq $jdkHome) {
    throw "No suitable JDK found. Install JDK 8 (preferred) or JDK 11+ with javac, or set JAVA_HOME."
}
$env:JAVA_HOME = $jdkHome
$env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
Write-Host "JAVA_HOME = $env:JAVA_HOME"
& java -version 2>&1 | ForEach-Object { Write-Host $_ }

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "ensure-build-metadata.ps1") | Out-Null

$moduleMap = @{
    "freeplane"            = @{ BuildFile = "freeplane\ant\build.xml";            Target = "dist" }
    "docear_plugin_core"   = @{ BuildFile = "docear_plugin_core\ant\build.xml";   Target = "build" }
    "docear_plugin_mcp"    = @{ BuildFile = "docear_plugin_mcp\ant\build.xml";    Target = "build" }
    "docear_plugin_ai"     = @{ BuildFile = "docear_plugin_ai\ant\build.xml";     Target = "build" }
    "docear_plugin_drawio" = @{ BuildFile = "docear_plugin_drawio\ant\build.xml"; Target = "build" }
    "freeplane_plugin_workspace" = @{ BuildFile = "freeplane_plugin_workspace\ant\build.xml"; Target = "build" }
}

$failed = @()
foreach ($name in $Modules) {
    $key = $name.Trim()
    if (-not $moduleMap.ContainsKey($key)) {
        Write-Host "Unknown module '$key'. Known: $($moduleMap.Keys -join ', ')" -ForegroundColor Yellow
        $failed += $key
        continue
    }
    $spec = $moduleMap[$key]
    $buildFile = Join-Path $repoRoot $spec.BuildFile
    if (!(Test-Path $buildFile)) {
        Write-Host "Missing build file: $buildFile" -ForegroundColor Red
        $failed += $key
        continue
    }
    Write-Step "Compile $key ($($spec.Target))"
    Push-Location $repoRoot
    try {
        & $antPath -f $buildFile $spec.Target
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[FAIL] $key exit $($LASTEXITCODE)" -ForegroundColor Red
            $failed += $key
        }
        else {
            Write-Host "[OK] $key" -ForegroundColor Green
        }
    }
    finally {
        Pop-Location
    }
}

Write-Host ""
if ($failed.Count -gt 0) {
    Write-Host "Compile-check FAILED: $($failed -join ', ')" -ForegroundColor Red
    Write-Host "Jars were written under module dist/build folders only — your running Docear was not touched."
    exit 1
}

Write-Host "Compile-check OK. Running Docear was not stopped or replaced." -ForegroundColor Green
Write-Host "To install/restart for real use, run build-docear.bat yourself when convenient."
exit 0
