$base = "http://127.0.0.1:7720/mcp"
function Invoke-Mcp($method, $paramsObj, $id) {
    $body = @{ jsonrpc = "2.0"; id = $id; method = $method; params = $paramsObj } | ConvertTo-Json -Depth 8 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    try {
        $resp = Invoke-WebRequest -Uri $base -Method POST -ContentType "application/json; charset=utf-8" -Body $bytes -TimeoutSec 45
        return @{ ok = $true; content = $resp.Content }
    } catch {
        $err = $_.Exception.Message
        if ($_.ErrorDetails) { $err = $_.ErrorDetails.Message }
        return @{ ok = $false; content = $err }
    }
}
function Invoke-Tool($name, $arguments, $id) {
    Invoke-Mcp "tools/call" @{ name = $name; arguments = $arguments } $id
}
function Invoke-Resource($uri, $id) {
    Invoke-Mcp "resources/read" @{ uri = $uri } $id
}

$results = @()

function Add-Result($name, $r) {
    $pass = $r.ok -and ($r.content -notmatch '"error"')
    if ($r.ok -and $r.content -match '"error"') { $pass = $false }
    $script:results += [pscustomobject]@{ Test = $name; Pass = $pass; Snippet = $r.content.Substring(0, [Math]::Min(180, $r.content.Length)) }
}

Add-Result "initialize" (Invoke-Mcp "initialize" @{ protocolVersion = "2024-11-05"; capabilities = @{}; clientInfo = @{ name = "test"; version = "1" } } 1)
Add-Result "tools/list" (Invoke-Mcp "tools/list" @{} 2)
Add-Result "resources/list" (Invoke-Mcp "resources/list" @{} 3)
Add-Result "prompts/list" (Invoke-Mcp "prompts/list" @{} 4)
Add-Result "resource/manifest" (Invoke-Resource "docear://manifest" 5)
Add-Result "resource/selection" (Invoke-Resource "docear://context/selection" 6)
Add-Result "resource/active-map" (Invoke-Resource "docear://context/active-map" 7)
Add-Result "resource/workspace-plan" (Invoke-Resource "docear://workspace/plan" 8)
Add-Result "resource/todos" (Invoke-Resource "docear://tasks/todos" 9)
Add-Result "resource/reminders" (Invoke-Resource "docear://tasks/reminders" 10)
Add-Result "resource/overdue" (Invoke-Resource "docear://tasks/overdue" 11)
Add-Result "resource/today" (Invoke-Resource "docear://tasks/today" 12)
Add-Result "resource/overview" (Invoke-Resource "docear://workspace/overview" 13)
Add-Result "tool/get_selection_context" (Invoke-Tool "get_selection_context" @{} 20)
Add-Result "tool/get_active_map_json" (Invoke-Tool "get_active_map_json" @{} 21)
Add-Result "tool/get_workspace_plan" (Invoke-Tool "get_workspace_plan" @{} 22)
Add-Result "tool/list_todos" (Invoke-Tool "list_todos" @{} 23)
Add-Result "tool/list_reminders" (Invoke-Tool "list_reminders" @{} 24)
Add-Result "tool/list_overdue" (Invoke-Tool "list_overdue" @{} 25)
Add-Result "tool/list_projects" (Invoke-Tool "list_projects" @{} 26)
Add-Result "tool/search_nodes" (Invoke-Tool "search_nodes" @{ query = "test"; limit = 3 } 27)
Add-Result "prompt/daily-review" (Invoke-Mcp "prompts/get" @{ name = "daily-review" } 30)

$ctx = Invoke-Tool "get_selection_context" @{} 40
$mapFile = $null
$rootId = $null
if ($ctx.ok -and $ctx.content -match 'ID_\d+') {
    if ($ctx.content -match '\\test\.mm') { $mapFile = "test.mm found in context" }
    if ($ctx.content -match '"nodeId":"(ID_\d+)"') { $rootId = $Matches[1] }
}
if ($rootId) {
    $poem = "MCP测试节点 " + (Get-Date -Format "HH:mm:ss")
    Add-Result "tool/add_node" (Invoke-Tool "add_node" @{ parentNodeId = $rootId; text = $poem } 41)
    if ($ctx.content -match '"mapFile":"([^"]+test\.mm)"') {
        $path = $Matches[1] -replace '\\\\','\'
        Add-Result "tool/change_node_text" (Invoke-Tool "change_node_text" @{ nodeId = $rootId; text = "test (MCP已测试)" } 42)
    }
}

$results | Format-Table -AutoSize
$failed = $results | Where-Object { -not $_.Pass }
"FAILED: $($failed.Count) / $($results.Count)"
$failed | ForEach-Object { "--- $($_.Test) ---`n$($_.Snippet)`n" }
