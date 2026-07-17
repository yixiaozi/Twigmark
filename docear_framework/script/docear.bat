@echo off
setlocal
set "freeplanedir=%~dp0"
set "xargs=init.xargs"
set defines= "-Dorg.freeplane.param1=%~1" "-Dorg.freeplane.param2=%~2" "-Dorg.freeplane.param3=%~3" "-Dorg.freeplane.param4=%~4"
set resdir="-Dorg.freeplane.globalresourcedir=%freeplanedir%resources/"

rem Prefer bundled Liberica/JavaFX JRE so Draw.io embed works.
set "java_exe=%freeplanedir%jre\bin\javaw.exe"
if not exist "%java_exe%" set "java_exe=%freeplanedir%jre\bin\java.exe"
if not exist "%java_exe%" set "java_exe=javaw"
if /I "%java_exe%"=="javaw" (
  where javaw >nul 2>nul
  if errorlevel 1 set "java_exe=java"
)

if exist "%freeplanedir%freeplanelauncher.jar" (
  start "" "%java_exe%" -Xmx512m -Xss2m %resdir% %defines% -jar "%freeplanedir%freeplanelauncher.jar"
) else (
  "%java_exe%" -Xmx512m -Xss2m "-Dorg.knopflerfish.framework.bundlestorage=memory" "-Dorg.knopflerfish.gosg.jars=reference:file:%freeplanedir%core/" %resdir% %defines% -jar "%freeplanedir%framework.jar" -xargs "%freeplanedir%props.xargs" -xargs "%freeplanedir%%xargs%"
)
endlocal
