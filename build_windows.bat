@echo off
chcp 65001 >nul
title Docear Windows Build
cd /d "%~dp0"

echo.
echo Calling scripts\build-docear-to-dist.ps1 ...
echo Deploy target: E:\Temp\DocearDist (add -NoLaunch to build only)
echo.

if /I "%~1"=="-NoLaunch" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-docear-to-dist.ps1" -NoLaunch
) else if /I "%~1"=="nolaunch" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-docear-to-dist.ps1" -NoLaunch
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-docear-to-dist.ps1" %*
)

set ERR=%ERRORLEVEL%
echo.
if %ERR% neq 0 (
  echo [FAILED] exit code %ERR%
) else (
  echo [OK]
)
pause
exit /b %ERR%
