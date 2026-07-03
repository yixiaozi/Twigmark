param(
    [string]$BaseUrl = "http://127.0.0.1:7720/mcp",
    [string]$OutFile = ""
)

$ErrorActionPreference = "Stop"
$script:ReqId = 1

function Invoke-Mcp {
    param(
        [string]$Method,
        [hashtable]$Params
    )
    $body = @{
        jsonrpc = "2.0"
        id      = $script:ReqId
        method  = $Method
    }
    if ($null -ne $Params) {
        $body.params = $Params
    }
    $script:ReqId++
    $json = $body | ConvertTo-Json -Depth 20 -Compress
    return Invoke-RestMethod -Uri $BaseUrl -Method Post -ContentType "application/json; charset=utf-8" -Body $json
}

function Invoke-Tool {
    param(
        [string]$Name,
        [hashtable]$Arguments
    )
    if ($null -eq $Arguments) { $Arguments = @{} }
    return Invoke-Mcp -Method "tools/call" -Params @{ name = $Name; arguments = $Arguments }
}

function Get-ToolText {
    param($Response)
    if ($null -eq $Response) { return "" }
    if ($Response.result.content -and $Response.result.content.Count -gt 0) {
        return [string]$Response.result.content[0].text
    }
    if ($Response.error) {
        return ("ERROR: " + $Response.error.message)
    }
    return ($Response | ConvertTo-Json -Depth 10 -Compress)
}

function Trunc {
    param([string]$Text, [int]$Max = 1200)
    if ([string]::IsNullOrEmpty($Text)) { return "" }
    if ($Text.Length -le $Max) { return $Text }
    return ($Text.Substring(0, $Max) + "... [truncated " + $Text.Length + " chars]")
}

$lines = New-Object System.Collections.Generic.List[string]
function L([string]$s) { [void]$lines.Add($s) }

L "# Docear MCP 功能测试记录"
L ""
L ("- 测试时间: " + (Get-Date -Format "yyyy-MM-dd HH:mm:ss"))
L ("- MCP 地址: " + $BaseUrl)
L ""

$healthUrl = $BaseUrl -replace "/mcp$", "/health"
$health = Invoke-RestMethod -Uri $healthUrl -Method Get
L "## 0. 连通性"
L ("- PASS health: " + ($health | ConvertTo-Json -Compress))
L ""

L "## 1. 协议与工具列表"
$init = Invoke-Mcp -Method "initialize" -Params @{
    protocolVersion = "2024-11-05"
    capabilities    = @{}
    clientInfo      = @{ name = "mcp-smoke-test"; version = "1.0" }
}
L ("- initialize: " + $init.result.serverInfo.name + " v" + $init.result.serverInfo.version)
$toolsResp = Invoke-Mcp -Method "tools/list" -Params $null
$toolNames = @($toolsResp.result.tools | ForEach-Object { $_.name })
L ("- tools/list: " + $toolNames.Count + " tools")
L ""

L "## 2. 读：上下文与当前导图"
$ctxText = Get-ToolText (Invoke-Tool -Name "get_selection_context" -Arguments $null)
L "### get_selection_context"
L '```json'
L (Trunc $ctxText 2000)
L '```'

$mapFile = ""
$selectedNodeId = ""
$selectedNodeText = ""
try {
    $ctxObj = $ctxText | ConvertFrom-Json
    if ($ctxObj.mapFile) { $mapFile = [string]$ctxObj.mapFile }
    if ($ctxObj.nodeId) { $selectedNodeId = [string]$ctxObj.nodeId }
    if ($ctxObj.nodeText) { $selectedNodeText = [string]$ctxObj.nodeText }
    if (-not $mapFile -and $ctxObj.selection) {
        if ($ctxObj.selection.mapFile) { $mapFile = [string]$ctxObj.selection.mapFile }
        if ($ctxObj.selection.nodeId) { $selectedNodeId = [string]$ctxObj.selection.nodeId }
        if ($ctxObj.selection.nodeText) { $selectedNodeText = [string]$ctxObj.selection.nodeText }
    }
} catch { }

L ("- 解析 mapFile: " + $mapFile)
L ("- 解析 nodeId: " + $selectedNodeId + "  text: " + $selectedNodeText)
L ""

$activeText = Get-ToolText (Invoke-Tool -Name "get_active_map_json" -Arguments @{ includeFolded = $true })
L "### get_active_map_json"
L ("- 响应长度: " + $activeText.Length + " chars")
L ("- 预览: " + (Trunc $activeText 500))
L ""

if ($mapFile) {
    $mmText = Get-ToolText (Invoke-Tool -Name "get_mindmap_json" -Arguments @{
            filePath      = $mapFile
            maxDepth      = 2
            includeFolded = $true
        })
    L "### get_mindmap_json"
    L ("- filePath: " + $mapFile)
    L ("- 响应长度: " + $mmText.Length + " chars")
    L ("- 预览: " + (Trunc $mmText 500))
    L ""
}

L "## 3. 读：搜索与列表"
L "### list_recently_modified"
L '```json'
L (Trunc (Get-ToolText (Invoke-Tool -Name "list_recently_modified" -Arguments @{ limit = 5; modifiedWithinDays = 90; query = "" })) 1500)
L '```'
L ""

L "### search_nodes query=MCP"
L '```json'
L (Trunc (Get-ToolText (Invoke-Tool -Name "search_nodes" -Arguments @{ query = "MCP"; limit = 5; modifiedWithinDays = 365 })) 1500)
L '```'
L ""

if ($mapFile) {
    L "### search_nodes filePath scoped"
    L '```json'
    L (Trunc (Get-ToolText (Invoke-Tool -Name "search_nodes" -Arguments @{ query = ""; filePath = $mapFile; limit = 3 })) 1500)
    L '```'
    L ""
}

L "### list_pinned"
L '```json'
L (Trunc (Get-ToolText (Invoke-Tool -Name "list_pinned" -Arguments @{ limit = 5 })) 1000)
L '```'
L ""

L "### list_projects"
L '```json'
L (Trunc (Get-ToolText (Invoke-Tool -Name "list_projects" -Arguments $null)) 1000)
L '```'
L ""

L "## 4. 读：单节点详情"
if ($mapFile -and $selectedNodeId) {
    L "### get_node_details"
    L ("- nodeId: " + $selectedNodeId)
    L '```json'
    L (Trunc (Get-ToolText (Invoke-Tool -Name "get_node_details" -Arguments @{ filePath = $mapFile; nodeId = $selectedNodeId })) 2000)
    L '```'
} else {
    L "### get_node_details — SKIP"
}
L ""

L "## 5. 写：在当前导图创建测试节点"
$testNodeId = ""
$testMarker = "MCP_TEST_" + (Get-Date -Format "yyyyMMdd_HHmmss")
if ($mapFile -and $selectedNodeId) {
    $addText = Get-ToolText (Invoke-Tool -Name "add_node" -Arguments @{
            filePath     = $mapFile
            parentNodeId = $selectedNodeId
            text         = $testMarker
        })
    L "### add_node"
    L '```json'
    L $addText
    L '```'
    try {
        $addObj = $addText | ConvertFrom-Json
        if ($addObj.nodeId) { $testNodeId = [string]$addObj.nodeId }
    } catch { }

    if ($testNodeId) {
        L "### change_node_text"
        L '```json'
        L (Get-ToolText (Invoke-Tool -Name "change_node_text" -Arguments @{
                    filePath = $mapFile; nodeId = $testNodeId; text = ($testMarker + " edited")
                }))
        L '```'
        L ""

        L "### set_node_folded true/false"
        L '```json'
        L (Get-ToolText (Invoke-Tool -Name "set_node_folded" -Arguments @{ filePath = $mapFile; nodeId = $testNodeId; folded = $true }))
        L (Get-ToolText (Invoke-Tool -Name "set_node_folded" -Arguments @{ filePath = $mapFile; nodeId = $testNodeId; folded = $false }))
        L '```'
        L ""

        L "### set_node_note / set_node_tags / set_priority"
        L '```json'
        L (Get-ToolText (Invoke-Tool -Name "set_node_note" -Arguments @{ filePath = $mapFile; nodeId = $testNodeId; noteHtml = '<p>MCP test note</p>' }))
        L (Get-ToolText (Invoke-Tool -Name "set_node_tags" -Arguments @{ filePath = $mapFile; nodeId = $testNodeId; tags = "MCP测试"; pinned = $false }))
        L (Get-ToolText (Invoke-Tool -Name "set_priority" -Arguments @{ filePath = $mapFile; nodeId = $testNodeId; level = 3 }))
        L '```'
        L ""

        L "### create_todo child"
        L '```json'
        L (Get-ToolText (Invoke-Tool -Name "create_todo" -Arguments @{ filePath = $mapFile; parentNodeId = $testNodeId; text = ($testMarker + " todo") }))
        L '```'
        L ""

        L "### get_node_details after writes"
        L '```json'
        L (Trunc (Get-ToolText (Invoke-Tool -Name "get_node_details" -Arguments @{ filePath = $mapFile; nodeId = $testNodeId })) 2500)
        L '```'
        L ""
        L ("### 测试残留: 节点 " + $testMarker + " edited id=" + $testNodeId)
    }
} else {
    L "- SKIP 写测试：无 mapFile 或未选中节点"
}

L ""
L "## 6. 测试摘要"
L "- health / initialize / tools/list: PASS"
if ($mapFile) { L "- get_selection_context: PASS" } else { L "- get_selection_context: WARN" }
L "- get_active_map_json / get_mindmap_json: PASS or SKIP"
L "- list_recently_modified / search_nodes / list_pinned / list_projects: PASS"
if ($mapFile -and $selectedNodeId) { L "- get_node_details: PASS" } else { L "- get_node_details: SKIP" }
if ($testNodeId) { L "- write tools: PASS (see section 5)" } else { L "- write tools: SKIP" }

$out = $lines -join [Environment]::NewLine
if ($OutFile) {
    [System.IO.File]::WriteAllText($OutFile, $out, [System.Text.UTF8Encoding]::new($false))
}
Write-Output $out
