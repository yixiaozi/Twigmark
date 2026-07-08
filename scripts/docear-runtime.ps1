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
