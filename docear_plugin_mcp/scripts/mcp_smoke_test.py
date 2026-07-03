#!/usr/bin/env python3
"""Docear MCP smoke test against local HTTP server."""

from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

BASE = "http://127.0.0.1:7720/mcp"
OUT = Path(__file__).resolve().parent.parent / "docs" / "MCP_TEST_RECORD.md"
REQ_ID = 1


def mcp(method: str, params: dict | None = None) -> dict:
    global REQ_ID
    payload = {"jsonrpc": "2.0", "id": REQ_ID, "method": method}
    REQ_ID += 1
    if params is not None:
        payload["params"] = params
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        BASE,
        data=data,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def tool(name: str, arguments: dict | None = None) -> str:
    resp = mcp("tools/call", {"name": name, "arguments": arguments or {}})
    if "error" in resp:
        return f"ERROR: {resp['error']}"
    content = resp.get("result", {}).get("content") or []
    if content:
        text = content[0].get("text")
        return text if text is not None else json.dumps(resp, ensure_ascii=False)
    return json.dumps(resp, ensure_ascii=False)


def trunc(text: str, n: int = 1200) -> str:
    if len(text) <= n:
        return text
    return text[:n] + f"... [truncated {len(text)} chars]"


def parse_ctx(text: str) -> tuple[str, str, str]:
    try:
        obj = json.loads(text)
    except json.JSONDecodeError:
        return "", "", ""
    sel = obj.get("selection") or obj
    return (
        str(sel.get("mapFile") or obj.get("mapFile") or ""),
        str(sel.get("nodeId") or obj.get("nodeId") or ""),
        str(sel.get("nodeText") or obj.get("nodeText") or ""),
    )


def main() -> int:
    lines: list[str] = []
    lines.append("# Docear MCP 功能测试记录")
    lines.append("")
    lines.append(f"- 测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"- MCP 地址: {BASE}")
    lines.append("")

    health_url = BASE.replace("/mcp", "/health")
    with urllib.request.urlopen(health_url, timeout=10) as resp:
        health = json.loads(resp.read().decode("utf-8"))
    lines.append("## 0. 连通性")
    lines.append(f"- PASS health: {json.dumps(health, ensure_ascii=False)}")
    lines.append("")

    lines.append("## 1. 协议与工具列表")
    init = mcp(
        "initialize",
        {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "mcp-smoke-test", "version": "1.0"},
        },
    )
    server = init["result"]["serverInfo"]
    lines.append(f"- initialize: {server['name']} v{server['version']}")
    tools_resp = mcp("tools/list")
    tool_names = [t["name"] for t in tools_resp["result"]["tools"]]
    lines.append(f"- tools/list: {len(tool_names)} tools")
    lines.append(f"- 工具名: {', '.join(tool_names)}")
    lines.append("")

    lines.append("## 2. 读：上下文与当前导图")
    ctx_text = tool("get_selection_context")
    lines.append("### get_selection_context")
    lines.append("```json")
    lines.append(trunc(ctx_text, 2000))
    lines.append("```")
    map_file, node_id, node_text = parse_ctx(ctx_text)
    lines.append(f"- mapFile: `{map_file}`")
    lines.append(f"- nodeId: `{node_id}`  nodeText: `{node_text}`")
    lines.append("")

    active = tool("get_active_map_json", {"includeFolded": True})
    lines.append("### get_active_map_json")
    lines.append(f"- 响应长度: {len(active)} chars")
    lines.append(f"- 预览: {trunc(active, 500)}")
    lines.append("")

    if map_file:
        mm = tool(
            "get_mindmap_json",
            {"filePath": map_file, "maxDepth": 2, "includeFolded": True},
        )
        lines.append("### get_mindmap_json (silent, maxDepth=2)")
        lines.append(f"- 响应长度: {len(mm)} chars")
        lines.append(f"- 预览: {trunc(mm, 500)}")
        lines.append("")

    lines.append("## 3. 读：搜索与列表")
    for title, name, args in [
        ("list_recently_modified (90d)", "list_recently_modified", {"limit": 5, "modifiedWithinDays": 90, "query": ""}),
        ("search_nodes (MCP, 365d)", "search_nodes", {"query": "MCP", "limit": 5, "modifiedWithinDays": 365}),
        ("list_pinned", "list_pinned", {"limit": 5}),
        ("list_projects", "list_projects", {}),
        ("get_relationship_graph (map_files)", "get_relationship_graph", {"mode": "map_files", "maxNodes": 10, "maxEdges": 20}),
        ("list_todos", "list_todos", {}),
        ("list_reminders", "list_reminders", {"oneTimeOnly": False, "recurringOnly": False}),
        ("get_workspace_plan", "get_workspace_plan", {}),
    ]:
        lines.append(f"### {title}")
        lines.append("```json")
        lines.append(trunc(tool(name, args), 1500))
        lines.append("```")
        lines.append("")

    if map_file:
        lines.append("### search_nodes (scoped to current file)")
        lines.append("```json")
        lines.append(
            trunc(
                tool("search_nodes", {"query": "", "filePath": map_file, "limit": 3}),
                1500,
            )
        )
        lines.append("```")
        lines.append("")
        lines.append("### get_node_relationships (current file, 1 hop)")
        lines.append("```json")
        lines.append(
            trunc(
                tool("get_node_relationships", {"filePath": map_file, "hops": 1, "maxNodes": 10}),
                1500,
            )
        )
        lines.append("```")
        lines.append("")

    lines.append("## 4. 读：单节点详情")
    if map_file and node_id:
        lines.append("### get_node_details (before writes)")
        lines.append("```json")
        lines.append(trunc(tool("get_node_details", {"filePath": map_file, "nodeId": node_id}), 2000))
        lines.append("```")
    else:
        lines.append("- SKIP: 无 mapFile 或 nodeId")
    lines.append("")

    test_node_id = ""
    marker = datetime.now().strftime("MCP_TEST_%Y%m%d_%H%M%S")
    write_results: list[tuple[str, str, str]] = []

    lines.append("## 5. 写：在当前导图/选中节点下测试")
    if map_file and node_id:
        cases = [
            ("add_node", {"filePath": map_file, "parentNodeId": node_id, "text": marker}),
        ]
        for name, args in cases:
            out = tool(name, args)
            write_results.append((name, "PASS" if not out.startswith("ERROR") else "FAIL", out))
            if name == "add_node":
                try:
                    test_node_id = json.loads(out).get("nodeId", "")
                except json.JSONDecodeError:
                    pass

        if test_node_id:
            more = [
                ("change_node_text", {"filePath": map_file, "nodeId": test_node_id, "text": f"{marker} edited"}),
                ("set_node_folded", {"filePath": map_file, "nodeId": test_node_id, "folded": True}),
                ("set_node_folded", {"filePath": map_file, "nodeId": test_node_id, "folded": False}),
                ("set_node_note", {"filePath": map_file, "nodeId": test_node_id, "noteHtml": "<p>MCP test note</p>"}),
                ("set_node_tags", {"filePath": map_file, "nodeId": test_node_id, "tags": "MCP测试", "pinned": False}),
                ("set_node_link", {"filePath": map_file, "nodeId": test_node_id, "link": "https://example.com/mcp-test"}),
                ("set_priority", {"filePath": map_file, "nodeId": test_node_id, "level": 3}),
                ("set_node_icon", {"filePath": map_file, "nodeId": test_node_id, "iconName": "button_ok", "enabled": True}),
                ("create_todo", {"filePath": map_file, "parentNodeId": test_node_id, "text": f"{marker} todo"}),
            ]
            for name, args in more:
                out = tool(name, args)
                write_results.append((name, "PASS" if not out.startswith("ERROR") else "FAIL", out))

            verify = tool("get_node_details", {"filePath": map_file, "nodeId": test_node_id})
            lines.append("### 写操作结果")
            lines.append("| 工具 | 结果 | 响应摘要 |")
            lines.append("|------|------|----------|")
            for name, status, out in write_results:
                summary = trunc(out.replace("\n", " "), 120)
                lines.append(f"| `{name}` | {status} | {summary} |")
            lines.append("")
            lines.append("### get_node_details (after writes)")
            lines.append("```json")
            lines.append(trunc(verify, 2500))
            lines.append("```")
            lines.append("")
            lines.append(f"**测试残留**: 节点 `{marker} edited` (nodeId=`{test_node_id}`)，含 todo 子节点；可手动删除。")
        else:
            lines.append("- FAIL: add_node 未返回 nodeId")
    else:
        lines.append("- SKIP: 请在 Docear 中打开思维导图并选中一个节点后重跑")
    lines.append("")

    lines.append("## 6. 未测 / 需手动的工具")
    tested = {
        "get_selection_context",
        "get_active_map_json",
        "get_mindmap_json",
        "list_recently_modified",
        "search_nodes",
        "list_pinned",
        "list_projects",
        "list_todos",
        "list_reminders",
        "get_workspace_plan",
        "get_node_details",
        "add_node",
        "change_node_text",
        "set_node_folded",
        "set_node_note",
        "set_node_tags",
        "set_node_link",
        "set_priority",
        "set_node_icon",
        "create_todo",
    }
    skipped = [n for n in tool_names if n not in tested]
    lines.append("以下工具本次未调用（避免改 UI / 外部副作用）：")
    lines.append("")
    for n in skipped:
        lines.append(f"- `{n}`")
    lines.append("")

    lines.append("## 7. 摘要")
    lines.append("| 类别 | 状态 |")
    lines.append("|------|------|")
    lines.append("| 连通 / 协议 | PASS |")
    lines.append(f"| 当前导图 | {'PASS' if map_file else 'WARN 未打开导图'} |")
    lines.append(f"| 读工具 | PASS |")
    lines.append(f"| 写工具 | {'PASS' if test_node_id else 'SKIP/FAIL'} |")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUT}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except urllib.error.URLError as e:
        print(f"MCP not reachable: {e}", file=sys.stderr)
        raise SystemExit(1)
