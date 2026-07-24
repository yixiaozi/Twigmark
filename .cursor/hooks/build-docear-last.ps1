# DISABLED: Do not auto-build / deploy / restart Docear after agent turns.
# Full deploy closes the running app and overwrites E:\Temp\DocearDist.
# Run build-docear.bat manually when you want to install.
#
# Previous behavior (removed):
#   ant docear-dist → copy zip → scripts\build-docear-to-dist.ps1 -SkipBuild
Write-Output "build-docear-last.ps1: skipped (auto-deploy disabled). Use build-docear.bat when ready."
exit 0
