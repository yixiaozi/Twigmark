@echo off
chcp 65001 >nul
title Docear Build and Launch
cd /d "%~dp0"

echo.
echo ========================================
echo   Twigmark one-click build and launch
echo   Build -^> E:\SoftWare\Twigmark -^> start
echo   Your mind-map data is not modified
echo   Existing working-directory.txt is kept
echo ========================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-docear-to-dist.ps1"
set ERR=%ERRORLEVEL%

echo.
if %ERR% neq 0 (
  echo [FAILED] exit code %ERR%. Scroll up for errors.
  echo Common causes: JDK 8 not installed, or ant build error.
) else (
  echo [OK] If Twigmark did not open, run docear.exe under E:\SoftWare\Twigmark
)
echo.
pause
exit /b %ERR%
