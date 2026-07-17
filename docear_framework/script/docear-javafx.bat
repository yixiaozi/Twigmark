@echo off
setlocal
set "freeplanedir=%~dp0"
set "java_exe=%freeplanedir%jre\bin\javaw.exe"
if not exist "%java_exe%" set "java_exe=%freeplanedir%jre\bin\java.exe"
if not exist "%java_exe%" (
  echo [Docear] Missing JavaFX runtime in "%freeplanedir%jre".
  echo Draw.io embedded editor requires JavaFX. Run scripts\setup-drawio-javafx.ps1 and rebuild,
  echo or copy the jre folder from docear_framework\cache\javafx\jre into this directory.
  pause
  exit /b 1
)
if not exist "%freeplanedir%jre\lib\ext\jfxrt.jar" (
  echo [Docear] Bundled JRE is missing JavaFX ^(jfxrt.jar^).
  echo Re-run scripts\setup-drawio-javafx.ps1 with Liberica JDK 8 Full, then rebuild.
  pause
  exit /b 1
)
start "" "%java_exe%" -Xmx512m -Xss2m ^
  "-Dorg.freeplane.globalresourcedir=%freeplanedir%resources/" ^
  -jar "%freeplanedir%freeplanelauncher.jar"
endlocal
