# Shared helpers: stop / start Docear during build-deploy scripts.

function Stop-RunningDocear {
    param(
        [int] $GraceSeconds = 2
    )

    $stopped = $false

    Get-CimInstance Win32_Process -Filter "Name='javaw.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'freeplanelauncher\.jar' } |
        ForEach-Object {
            Write-Output "Stopping Docear javaw pid $($_.ProcessId) ..."
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
            $stopped = $true
        }

    Get-Process -Name "docear", "Docear" -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Output "Stopping $($_.ProcessName) pid $($_.Id) ..."
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
        $stopped = $true
    }

    if ($stopped) {
        Start-Sleep -Seconds $GraceSeconds
    }

    Remove-Item "$env:APPDATA\Docear\single_instance.lock" -Force -ErrorAction SilentlyContinue
}

function Find-DocearInstallDir {
    param(
        [string] $RootDir
    )

    if ([string]::IsNullOrWhiteSpace($RootDir) -or !(Test-Path $RootDir)) {
        return $null
    }

    foreach ($subDir in Get-ChildItem -Path $RootDir -Directory -ErrorAction SilentlyContinue) {
        if (Test-Path (Join-Path $subDir.FullName "freeplanelauncher.jar")) {
            return $subDir.FullName
        }
    }
    return $null
}

function Start-DocearFromInstallDir {
    param(
        [string] $InstallDir
    )

    if ([string]::IsNullOrWhiteSpace($InstallDir)) {
        return $false
    }

    $launcherPath = Join-Path $InstallDir "docear.exe"
    if (!(Test-Path $launcherPath)) {
        $launcherPath = Join-Path $InstallDir "Docear.exe"
    }
    if (!(Test-Path $launcherPath)) {
        Write-Warning "Docear.exe not found in $InstallDir"
        return $false
    }

    Write-Output "Launching Docear from $launcherPath ..."
    Start-Process -FilePath $launcherPath
    return $true
}

function Test-JarContainsEntry {
    param(
        [Parameter(Mandatory = $true)][string] $JarPath,
        [Parameter(Mandatory = $true)][string] $EntryPath
    )

    if (!(Test-Path $JarPath)) {
        return $false
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $normalized = $EntryPath.Replace("\", "/")
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.Replace("\", "/") -eq $normalized) {
                return $true
            }
        }
        return $false
    }
    finally {
        $zip.Dispose()
    }
}

<#
.SYNOPSIS
  Guard against relationship-graph NoClassDefFoundError (TagGroupCascadeBar).

  OSGi must export ...components.tagfilter; core must use TagGroupFilterBarFactory
  from the exported ...components package (not import TagGroupCascadeBar directly).
#>
function Assert-RelationshipGraphPluginLayout {
    param(
        [Parameter(Mandatory = $true)][string] $PluginsRoot,
        [string] $Context = "plugins"
    )

    if (!(Test-Path $PluginsRoot)) {
        throw "Relationship graph check ($Context): plugins root not found: $PluginsRoot"
    }

    $workspacePlugin = Join-Path $PluginsRoot "org.freeplane.plugin.workspace"
    $corePlugin = Join-Path $PluginsRoot "org.docear.plugin.core"
    $workspaceManifest = Join-Path $workspacePlugin "META-INF\MANIFEST.MF"
    $workspaceJar = Join-Path $workspacePlugin "lib\plugin.jar"
    $coreJar = Join-Path $corePlugin "lib\plugin.jar"

    if (!(Test-Path $workspaceManifest)) {
        throw "Relationship graph check ($Context): missing $workspaceManifest"
    }
    if (!(Test-Path $workspaceJar)) {
        throw "Relationship graph check ($Context): missing $workspaceJar"
    }
    if (!(Test-Path $coreJar)) {
        throw "Relationship graph check ($Context): missing $coreJar"
    }

    $manifestText = Get-Content -Path $workspaceManifest -Raw -ErrorAction Stop
    if ($manifestText -notmatch 'org\.freeplane\.plugin\.workspace\.components\.tagfilter') {
        throw @"
Relationship graph check ($Context) FAILED:
  org.freeplane.plugin.workspace META-INF/MANIFEST.MF does not Export-Package
  org.freeplane.plugin.workspace.components.tagfilter

  Without that export, org.docear.plugin.core hits:
  NoClassDefFoundError: .../tagfilter/TagGroupCascadeBar
  and the left 「关系图」tab stays blank / load-failed.
"@
    }

    $requiredWorkspaceEntries = @(
        "org/freeplane/plugin/workspace/components/TagGroupFilterBarFactory.class",
        "org/freeplane/plugin/workspace/components/tagfilter/TagGroupCascadeBar.class",
        "org/freeplane/plugin/workspace/components/tagfilter/TagGroupCascadeBar`$Listener.class"
    )
    foreach ($entry in $requiredWorkspaceEntries) {
        if (-not (Test-JarContainsEntry -JarPath $workspaceJar -EntryPath $entry)) {
            throw "Relationship graph check ($Context): $workspaceJar is missing $entry"
        }
    }

    $requiredCoreEntries = @(
        "org/docear/plugin/core/graph/RelationshipGraphSideTabPanel.class",
        "org/docear/plugin/core/graph/RelationshipGraphIntegration.class",
        "org/docear/plugin/core/graph/RelationshipGraphService.class"
    )
    foreach ($entry in $requiredCoreEntries) {
        if (-not (Test-JarContainsEntry -JarPath $coreJar -EntryPath $entry)) {
            throw "Relationship graph check ($Context): $coreJar is missing $entry"
        }
    }

    Write-Output "Relationship graph check OK ($Context): tagfilter exported + factory/classes present."
}

function Assert-RelationshipGraphSourceManifest {
    param(
        [Parameter(Mandatory = $true)][string] $RepoRoot
    )

    $srcManifest = Join-Path $RepoRoot "freeplane_plugin_workspace\META-INF\MANIFEST.MF"
    if (!(Test-Path $srcManifest)) {
        throw "Relationship graph source check: missing $srcManifest"
    }
    $text = Get-Content -Path $srcManifest -Raw -ErrorAction Stop
    if ($text -notmatch 'org\.freeplane\.plugin\.workspace\.components\.tagfilter') {
        throw @"
Relationship graph source check FAILED:
  $srcManifest must Export-Package org.freeplane.plugin.workspace.components.tagfilter
  (prevents NoClassDefFoundError for TagGroupCascadeBar at runtime).
"@
    }

    $factorySrc = Join-Path $RepoRoot "freeplane_plugin_workspace\src\org\freeplane\plugin\workspace\components\TagGroupFilterBarFactory.java"
    if (!(Test-Path $factorySrc)) {
        throw "Relationship graph source check: missing TagGroupFilterBarFactory.java"
    }

    Write-Output "Relationship graph source check OK (MANIFEST Export-Package + factory)."
}
