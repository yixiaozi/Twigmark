@echo off
REM ASCII launcher: forwards to build-docear.bat (avoids encoding issues in this file name path)
cd /d "%~dp0"
call "%~dp0build-docear.bat" %*
