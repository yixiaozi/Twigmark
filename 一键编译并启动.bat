@echo off
chcp 65001 >nul
title Docear 一键编译并启动
cd /d "%~dp0"

echo.
echo ========================================
echo   Docear 一键编译并启动
echo   会编译 → 部署到 E:\Temp\DocearDist → 启动
echo   不会改动你的导图数据
echo ========================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-docear-to-dist.ps1"
set ERR=%ERRORLEVEL%

echo.
if %ERR% neq 0 (
  echo [失败] 退出码 %ERR%。请向上翻看红色报错。
  echo 常见原因: 未安装 JDK 8，或 ant 编译出错。
) else (
  echo [完成] 若未自动弹出，请打开 E:\Temp\DocearDist\docear_windows 下的 docear.exe
)
echo.
pause
exit /b %ERR%
