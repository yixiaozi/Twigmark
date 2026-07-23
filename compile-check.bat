@echo off
chcp 65001 >nul
title Docear compile-check (safe while Docear is running)
cd /d "%~dp0"

echo.
echo ========================================
echo   Compile only — does NOT close Docear
echo   Does NOT deploy / launch
echo ========================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\compile-check.ps1" %*
set ERR=%ERRORLEVEL%

echo.
if %ERR% neq 0 (
  echo [FAILED] exit code %ERR%
) else (
  echo [OK] Compile succeeded. Your running Docear was left alone.
)
echo.
exit /b %ERR%
