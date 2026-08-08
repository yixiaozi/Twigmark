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

function Test-JavaFxJreRoot {
    param([string] $JreRoot)
    if ([string]::IsNullOrWhiteSpace($JreRoot)) {
        return $false
    }
    $javaExe = Join-Path $JreRoot "bin\javaw.exe"
    if (!(Test-Path $javaExe)) {
        $javaExe = Join-Path $JreRoot "bin\java.exe"
    }
    $jfx = Join-Path $JreRoot "lib\ext\jfxrt.jar"
    return (Test-Path $javaExe) -and (Test-Path $jfx)
}

function Start-DocearFromInstallDir {
    param(
        [string] $InstallDir
    )

    if ([string]::IsNullOrWhiteSpace($InstallDir)) {
        return $false
    }

    $launcherJar = Join-Path $InstallDir "freeplanelauncher.jar"
    $bundledJre = Join-Path $InstallDir "jre"
    if ((Test-Path $launcherJar) -and (Test-JavaFxJreRoot $bundledJre)) {
        $javaExe = Join-Path $bundledJre "bin\javaw.exe"
        if (!(Test-Path $javaExe)) {
            $javaExe = Join-Path $bundledJre "bin\java.exe"
        }
        $resourceDir = Join-Path $InstallDir "resources"
        Write-Output "Launching Docear with bundled JavaFX JRE: $javaExe"
        Start-Process -FilePath $javaExe -ArgumentList @(
            "-Xmx512m",
            "-Xss2m",
            "-Dorg.freeplane.globalresourcedir=$resourceDir",
            "-jar",
            $launcherJar
        ) -WorkingDirectory $InstallDir
        return $true
    }

    $batLauncher = Join-Path $InstallDir "docear-javafx.bat"
    if (Test-Path $batLauncher) {
        Write-Output "Launching Docear from $batLauncher ..."
        Start-Process -FilePath $batLauncher -WorkingDirectory $InstallDir
        return $true
    }

    $batLauncher = Join-Path $InstallDir "docear.bat"
    if (Test-Path $batLauncher) {
        Write-Output "Launching Docear from $batLauncher ..."
        Start-Process -FilePath $batLauncher -WorkingDirectory $InstallDir
        return $true
    }

    $launcherPath = Join-Path $InstallDir "twigmark.exe"
    if (!(Test-Path $launcherPath)) {
        $launcherPath = Join-Path $InstallDir "docear.exe"
    }
    if (!(Test-Path $launcherPath)) {
        $launcherPath = Join-Path $InstallDir "Docear.exe"
    }
    if (!(Test-Path $launcherPath)) {
        Write-Warning "Docear launcher not found in $InstallDir"
        return $false
    }

    Write-Warning "Bundled JavaFX JRE not found; launching $launcherPath (Draw.io embed may fail)."
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
  and the left relationship-graph tab stays blank / load-failed.
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

<#
.SYNOPSIS
  Ensure scheduling-hub calendar classes shipped after calendar work.
#>
function Assert-CalendarHubLayout {
    param(
        [Parameter(Mandatory = $true)][string] $InstallDir,
        [string] $Context = "install"
    )

    if (!(Test-Path $InstallDir)) {
        throw "Calendar hub check ($Context): install dir not found: $InstallDir"
    }

    $coreCandidates = @(
        (Join-Path $InstallDir "core\org.freeplane.core\lib\freeplaneeditor.jar"),
        (Join-Path $InstallDir "core\org.freeplane.core\freeplaneeditor.jar")
    )
    $editorJar = $null
    foreach ($candidate in $coreCandidates) {
        if (Test-Path $candidate) {
            $editorJar = $candidate
            break
        }
    }
    if ($null -eq $editorJar) {
        $found = Get-ChildItem -Path (Join-Path $InstallDir "core") -Recurse -Filter "freeplaneeditor.jar" -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($null -ne $found) {
            $editorJar = $found.FullName
        }
    }
    if ($null -eq $editorJar) {
        throw "Calendar hub check ($Context): freeplaneeditor.jar not found under $InstallDir\core"
    }

    $bridgeEntry = "org/freeplane/view/swing/features/time/mindmapmode/ReminderCalendarBridge.class"
    if (-not (Test-JarContainsEntry -JarPath $editorJar -EntryPath $bridgeEntry)) {
        throw @"
Calendar hub check ($Context) FAILED:
  $editorJar is missing ReminderCalendarBridge

  Scheduling hub cannot load reminders (calendar will stay empty).
  Pull latest master and run a full rebuild.
"@
    }

    $coreJar = Join-Path $InstallDir "plugins\org.docear.plugin.core\lib\plugin.jar"
    if (!(Test-Path $coreJar)) {
        $foundCore = Get-ChildItem -Path (Join-Path $InstallDir "plugins") -Recurse -Filter "plugin.jar" -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match 'org\.docear\.plugin\.core' } |
            Select-Object -First 1
        if ($null -ne $foundCore) {
            $coreJar = $foundCore.FullName
        }
    }
    if (!(Test-Path $coreJar)) {
        throw "Calendar hub check ($Context): org.docear.plugin.core plugin.jar not found"
    }

    $requiredCore = @(
        "org/docear/plugin/core/calendar/CalendarViewportPanel.class",
        "org/docear/plugin/core/calendar/CalendarTaskService.class",
        "org/docear/plugin/core/calendar/CalendarViewportService.class"
    )
    foreach ($entry in $requiredCore) {
        if (-not (Test-JarContainsEntry -JarPath $coreJar -EntryPath $entry)) {
            throw "Calendar hub check ($Context): $coreJar is missing $entry"
        }
    }

	Write-Output "Calendar hub check OK ($Context): ReminderCalendarBridge + CalendarTaskService present."
}

<#
.SYNOPSIS
  Ensure map tag-filter overlay classes shipped (top-right 【标签】 filter).
#>
function Assert-MapTagFilterLayout {
    param(
        [Parameter(Mandatory = $true)][string] $PluginsRoot,
        [string] $Context = "plugins"
    )

    if (!(Test-Path $PluginsRoot)) {
        throw "Map tag filter check ($Context): plugins root not found: $PluginsRoot"
    }

    $workspaceJar = Join-Path $PluginsRoot "org.freeplane.plugin.workspace\lib\plugin.jar"
    $coreJar = Join-Path $PluginsRoot "org.docear.plugin.core\lib\plugin.jar"
    $workspaceManifest = Join-Path $PluginsRoot "org.freeplane.plugin.workspace\META-INF\MANIFEST.MF"

    if (!(Test-Path $workspaceJar)) {
        throw "Map tag filter check ($Context): missing $workspaceJar"
    }
    if (!(Test-Path $coreJar)) {
        throw "Map tag filter check ($Context): missing $coreJar"
    }
    if (!(Test-Path $workspaceManifest)) {
        throw "Map tag filter check ($Context): missing $workspaceManifest"
    }

    $manifestText = Get-Content -Path $workspaceManifest -Raw -ErrorAction Stop
    if ($manifestText -notmatch 'org\.freeplane\.plugin\.workspace\.components\.mapfilter') {
        throw @"
Map tag filter check ($Context) FAILED:
  workspace META-INF/MANIFEST.MF must Export-Package
  org.freeplane.plugin.workspace.components.mapfilter
"@
    }
    if ($manifestText -notmatch 'org\.freeplane\.plugin\.workspace\.features\.mapfilter') {
        throw @"
Map tag filter check ($Context) FAILED:
  workspace META-INF/MANIFEST.MF must Export-Package
  org.freeplane.plugin.workspace.features.mapfilter
"@
    }
    if ($manifestText -notmatch 'org\.freeplane\.plugin\.workspace\.components\.mapactivity') {
        throw @"
Map activity check ($Context) FAILED:
  workspace META-INF/MANIFEST.MF must Export-Package
  org.freeplane.plugin.workspace.components.mapactivity
"@
    }
    if ($manifestText -notmatch 'org\.freeplane\.plugin\.workspace\.features\.mapactivity') {
        throw @"
Map activity check ($Context) FAILED:
  workspace META-INF/MANIFEST.MF must Export-Package
  org.freeplane.plugin.workspace.features.mapactivity
"@
    }

    $requiredWorkspace = @(
        "org/freeplane/plugin/workspace/components/mapfilter/MapTagFilterPanel.class",
        "org/freeplane/plugin/workspace/features/mapfilter/MapTagFilterController.class",
        "org/freeplane/plugin/workspace/features/mapfilter/MapTagFilterService.class",
        "org/freeplane/plugin/workspace/features/mapfilter/TagFilterMapExtension.class",
        "org/freeplane/plugin/workspace/components/mapactivity/MapActivityOverlayPanel.class",
        "org/freeplane/plugin/workspace/features/mapactivity/MapActivityOverlayController.class",
        "org/freeplane/plugin/workspace/features/mapactivity/MapActivityCollector.class"
    )
    foreach ($entry in $requiredWorkspace) {
        if (-not (Test-JarContainsEntry -JarPath $workspaceJar -EntryPath $entry)) {
            throw "Map tag filter check ($Context): $workspaceJar is missing $entry"
        }
    }

    $requiredCore = @(
        "org/docear/plugin/core/ui/MapTagFilterOverlay.class",
        "org/docear/plugin/core/ui/MapActivityOverlay.class",
        "org/docear/plugin/core/ui/OverlayViewport.class"
    )
    foreach ($entry in $requiredCore) {
        if (-not (Test-JarContainsEntry -JarPath $coreJar -EntryPath $entry)) {
            throw "Map tag filter check ($Context): $coreJar is missing $entry"
        }
    }

    Write-Output "Map overlay check OK ($Context): tag filter + map activity packages present."
}

function Find-Jdk8Home {
    $explicit = @(
        $env:JAVA_HOME,
        "C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot",
        "C:\Program Files\Eclipse Adoptium\jdk-8.0.412.8-hotspot",
        "C:\Program Files\Eclipse Adoptium\jdk-8.0.392.8-hotspot",
        "C:\Program Files\Temurin\jdk-8*",
        "C:\Program Files\Java\jdk1.8*",
        "C:\Program Files\Microsoft\jdk-8*",
        "C:\Program Files\Zulu\zulu-8*"
    )
    foreach ($path in $explicit) {
        if ([string]::IsNullOrWhiteSpace($path)) { continue }
        if ($path -match '[\*\?]') {
            $globHits = @(Get-Item -Path $path -ErrorAction SilentlyContinue | Sort-Object FullName -Descending)
            foreach ($m in $globHits) {
                if (Test-Path (Join-Path $m.FullName "bin\javac.exe")) {
                    return $m.FullName
                }
            }
            continue
        }
        if (Test-Path (Join-Path $path "bin\javac.exe")) {
            return $path
        }
    }

    $whereJava = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $whereJava) {
        try {
            $ver = & java -version 2>&1 | Out-String
            if ($ver -match 'version "1\.8' -or $ver -match 'version "8') {
                $javaHomeProp = & java -XshowSettings:properties -version 2>&1 |
                    Select-String -Pattern 'java\.home\s*=\s*(.+)' |
                    Select-Object -First 1
                if ($null -ne $javaHomeProp) {
                    $home = $javaHomeProp.Matches[0].Groups[1].Value.Trim()
                    if ($home -match '\\jre$') {
                        $parent = Split-Path $home -Parent
                        if (Test-Path (Join-Path $parent "bin\javac.exe")) {
                            return $parent
                        }
                    }
                    if (Test-Path (Join-Path $home "bin\javac.exe")) {
                        return $home
                    }
                }
            }
        }
        catch { }
    }
    return $null
}

# Fallback for Cloud Agents / modernization: JDK 11+ can compile with source/target 1.8.
function Find-JdkModernHome {
    $candidates = @(
        $env:JAVA_HOME,
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Eclipse Adoptium\jdk-17*",
        "C:\Program Files\Eclipse Adoptium\jdk-11*",
        "C:\Program Files\Microsoft\jdk-21*",
        "C:\Program Files\Microsoft\jdk-17*",
        "C:\Program Files\Zulu\zulu-21*",
        "C:\Program Files\Zulu\zulu-17*"
    )
    foreach ($path in $candidates) {
        if ([string]::IsNullOrWhiteSpace($path)) { continue }
        if ($path -match '[\*\?]') {
            $globHits = @(Get-Item -Path $path -ErrorAction SilentlyContinue | Sort-Object FullName -Descending)
            foreach ($m in $globHits) {
                if (Test-Path (Join-Path $m.FullName "bin\javac.exe")) {
                    return $m.FullName
                }
            }
            continue
        }
        if (Test-Path (Join-Path $path "bin\javac.exe")) {
            return $path
        }
    }
    $whereJava = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $whereJava) {
        try {
            $ver = & java -version 2>&1 | Out-String
            if ($ver -match 'version "(1[1-9]|[2-9][0-9])') {
                $javaHomeProp = & java -XshowSettings:properties -version 2>&1 |
                    Select-String -Pattern 'java\.home\s*=\s*(.+)' |
                    Select-Object -First 1
                if ($null -ne $javaHomeProp) {
                    $home = $javaHomeProp.Matches[0].Groups[1].Value.Trim()
                    if ($home -match '[/\\]jre$') {
                        $parent = Split-Path $home -Parent
                        if (Test-Path (Join-Path $parent "bin\javac.exe")) {
                            return $parent
                        }
                    }
                    if (Test-Path (Join-Path $home "bin\javac.exe")) {
                        return $home
                    }
                    # Debian/Ubuntu: java.home may be a JRE; javac often lives in sibling paths.
                    $amd64 = "/usr/lib/jvm/java-21-openjdk-amd64"
                    if (Test-Path (Join-Path $amd64 "bin/javac")) { return $amd64 }
                }
            }
        }
        catch { }
    }
    return $null
}
