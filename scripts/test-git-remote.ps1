# Test Git pull/push using the same GitCommand code as Docear Git panel.
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\test-git-remote.ps1 <repo-path>
#   $env:DOCEAR_GIT_TEST_REPO = "<repo-path>"; powershell -ExecutionPolicy Bypass -File .\scripts\test-git-remote.ps1

param(
    [string] $RepoPath = ""
)

if ([string]::IsNullOrWhiteSpace($RepoPath)) {
    $RepoPath = $env:DOCEAR_GIT_TEST_REPO
}
if ([string]::IsNullOrWhiteSpace($RepoPath)) {
    throw "Repo path required. Pass -RepoPath or set DOCEAR_GIT_TEST_REPO."
}

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$antPath = Join-Path $repoRoot "tools\apache-ant-1.10.14\bin\ant.bat"
$buildFile = Join-Path $repoRoot "freeplane\ant\build.xml"
$classesDir = Join-Path $repoRoot "freeplane\build"
$frameworkJar = Join-Path $repoRoot "freeplane_framework\dist\freeplane_framework.jar"

$candidates = @(
    "C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot",
    "C:\Program Files\Eclipse Adoptium\jdk-8.0.412.8-hotspot",
    "C:\Program Files\Eclipse Adoptium\jdk-8.0.392.8-hotspot"
)
foreach ($jdk in $candidates) {
    if (Test-Path $jdk) {
        $env:JAVA_HOME = $jdk
        $env:Path = "$($env:JAVA_HOME)\bin;$($env:Path)"
        break
    }
}

if (!(Test-Path $antPath)) { throw "Ant not found: $antPath" }

Write-Output "Building freeplane (includes GitCommand)..."
Push-Location $repoRoot
try {
    & $antPath -f $buildFile build
    if ($LASTEXITCODE -ne 0) { throw "freeplane build failed with exit code $LASTEXITCODE" }

    $classpath = $classesDir
    if (Test-Path $frameworkJar) {
        $classpath = "$classesDir;$frameworkJar"
    }

    Write-Output ""
    Write-Output "Running GitRemoteTest on $RepoPath ..."
    Write-Output "Classpath: $classpath"
    Write-Output ""
    & java -cp $classpath org.freeplane.view.swing.features.git.GitRemoteTest $RepoPath
    if ($LASTEXITCODE -ne 0) {
        throw "GitRemoteTest exited with code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
