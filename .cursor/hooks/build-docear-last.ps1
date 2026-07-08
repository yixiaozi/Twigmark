$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$antPath = Join-Path $repoRoot "tools\apache-ant-1.10.14\bin\ant.bat"
$buildFile = Join-Path $repoRoot "docear_framework\ant\build.xml"
$distDir = Join-Path $repoRoot "docear_framework\dist"
$targetDir = "E:\Develop\Docear\Last"
$deployScript = Join-Path $repoRoot "scripts\build-docear-to-dist.ps1"
$ensureMetadataScript = Join-Path $repoRoot "scripts\ensure-build-metadata.ps1"

$jdk8 = "C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot"
if (Test-Path $jdk8) {
  $env:JAVA_HOME = $jdk8
  $env:Path = "$($env:JAVA_HOME)\bin;$($env:Path)"
}

if (!(Test-Path $antPath)) {
  throw "Ant not found at $antPath"
}

if (!(Test-Path $buildFile)) {
  throw "Build file not found at $buildFile"
}

& powershell -ExecutionPolicy Bypass -File $ensureMetadataScript | Out-Null

& $antPath -f $buildFile docear-dist
if ($LASTEXITCODE -ne 0) {
  throw "Docear build failed with exit code $LASTEXITCODE"
}

New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

$windowsZip = Join-Path $distDir "docear_windows.zip"
$windowsZipMd5 = Join-Path $distDir "docear_windows.zip.MD5"
if (!(Test-Path $windowsZip)) {
  throw "Expected package not found: $windowsZip"
}

Copy-Item -Path $windowsZip -Destination $targetDir -Force
if (Test-Path $windowsZipMd5) {
  Copy-Item -Path $windowsZipMd5 -Destination $targetDir -Force
}

Write-Output "Updated Docear Windows package in $targetDir"

if (Test-Path $deployScript) {
  Write-Output "Deploying to E:\Temp\DocearDist and restarting Docear ..."
  & powershell -ExecutionPolicy Bypass -File $deployScript -SkipBuild
}
