# Seed Ant build metadata from *.template when missing (local-only files, gitignored).

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$pairs = @(
    @("docear_plugin_core\resources\build.number", "docear_plugin_core\resources\build.number.template"),
    @("docear_plugin_core\resources\version.properties", "docear_plugin_core\resources\version.properties.template"),
    @("docear_framework\docear.version.properties", "docear_framework\docear.version.properties.template"),
    @("Jabref_Beta_2_7_Docear\build.number", "Jabref_Beta_2_7_Docear\build.number.template"),
    @("Jabref_Beta_2_7_Docear\src\resource\build.properties", "Jabref_Beta_2_7_Docear\src\resource\build.properties.template")
)

foreach ($pair in $pairs) {
    $target = Join-Path $repoRoot $pair[0]
    $template = Join-Path $repoRoot $pair[1]
    if (!(Test-Path $target) -and (Test-Path $template)) {
        Copy-Item -Path $template -Destination $target -Force
        Write-Output "Created $($pair[0]) from template"
    }
}
