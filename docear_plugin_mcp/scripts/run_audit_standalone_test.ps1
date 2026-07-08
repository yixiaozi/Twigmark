$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root "build_audit_test"
$src = Join-Path $root "src"
$stubs = Join-Path $root "scripts/test-stubs"
$jar = Join-Path $root "lib/sqlite-jdbc-3.21.0.jar"

if (-not (Test-Path $jar)) {
    throw "Missing $jar"
}

Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $build | Out-Null

$sources = @(
    (Join-Path $src "org/docear/plugin/mcp/json/JsonValue.java"),
    (Join-Path $src "org/docear/plugin/mcp/json/JsonWriter.java"),
    (Join-Path $src "org/docear/plugin/mcp/json/JsonParser.java"),
    (Join-Path $src "org/docear/plugin/mcp/audit/McpOperationIntent.java"),
    (Join-Path $src "org/docear/plugin/mcp/audit/McpAuditEvent.java"),
    (Join-Path $src "org/docear/plugin/mcp/audit/McpAuditDatabase.java"),
    (Join-Path $src "org/docear/plugin/mcp/audit/McpAuditWriter.java"),
    (Join-Path $src "org/docear/plugin/mcp/audit/McpRequestContext.java"),
    (Join-Path $src "org/docear/plugin/mcp/audit/McpAuditService.java"),
    (Join-Path $src "org/docear/plugin/mcp/audit/McpAuditStandaloneTest.java"),
    (Join-Path $stubs "org/docear/plugin/mcp/DocearMcpConfig.java"),
    (Join-Path $stubs "org/freeplane/core/util/LogUtils.java")
)

javac -encoding UTF-8 -source 1.6 -target 1.6 -cp $jar -d $build $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Push-Location $root
java -cp "$build;$jar" org.docear.plugin.mcp.audit.McpAuditStandaloneTest
$code = $LASTEXITCODE
Pop-Location
exit $code
