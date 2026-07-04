@echo off
setlocal
set "freeplanedir=%~dp0"
set "java_exe=%freeplanedir%jre\bin\javaw.exe"
if not exist "%java_exe%" (
  echo [Docear] Missing JavaFX runtime in "%freeplanedir%jre".
  echo Draw.io embedded editor requires JavaFX. Run scripts\setup-drawio-javafx.ps1 and rebuild,
  echo or copy the jre folder from docear_framework\build\jre into this directory.
  pause
  exit /b 1
)
start "" "%java_exe%" -Xmx512m -Xss2m ^
  "-Dorg.knopflerfish.framework.bundlestorage=memory" ^
  "-Dorg.knopflerfish.gosg.jars=reference:file:%freeplanedir%core/" ^
  "-Dorg.freeplane.globalresourcedir=%freeplanedir%resources/" ^
  "-Dorg.freeplane.userfpdir=%APPDATA%\Docear" ^
  -jar "%freeplanedir%freeplanelauncher.jar"
