@echo off
chcp 65001 >nul
title Docear Windows 构建
cd /d "%~dp0"

echo.
echo 调用 scripts\build-docear-to-dist.ps1 ...
echo （编完会部署到 E:\Temp\DocearDist 并启动；加参数 -NoLaunch 可只编译）
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
  echo [失败] 退出码 %ERR%
) else (
  echo [完成]
)
pause
exit /b %ERR%
