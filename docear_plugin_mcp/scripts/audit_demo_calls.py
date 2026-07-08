#!/usr/bin/env python3
"""Call several Docear MCP tools with _audit metadata to populate audit.db."""

from __future__ import annotations

import json
import sqlite3
import time
import urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:7720/mcp"
REQ_ID = 1
TRACE = f"cursor-audit-demo-{int(time.time())}"
AUDIT = {
    "caller": "cursor-agent",
    "traceId": TRACE,
    "tenant": "default",
    "questionSummary": "用户想测试 MCP 审计是否写入数据库",
    "operationGoal": "占位",
}


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
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Mcp-Session-Id": "cursor-audit-demo",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def tool(name: str, arguments: dict | None = None, goal: str = "") -> dict:
    args = dict(arguments or {})
    audit = dict(AUDIT)
    audit["operationGoal"] = goal
    args["_audit"] = audit
    resp = mcp("tools/call", {"name": name, "arguments": args})
    if "error" in resp:
        return {"error": resp["error"]}
    content = resp.get("result", {}).get("content") or []
    text = content[0].get("text") if content else json.dumps(resp, ensure_ascii=False)
    return {"ok": True, "preview": (text or "")[:400], "len": len(text or "")}


def count_events(db_path: Path) -> int:
    if not db_path.is_file():
        return -1
    conn = sqlite3.connect(str(db_path))
    try:
        return conn.execute("SELECT COUNT(*) FROM audit_event").fetchone()[0]
    finally:
        conn.close()


def main() -> None:
    print("initialize...")
    init = mcp(
        "initialize",
        {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "cursor-agent", "version": "1.0"},
        },
    )
    print(json.dumps(init.get("result", {}).get("serverInfo"), ensure_ascii=False))

    calls = [
        ("get_selection_context", {}, "读取当前打开导图与选中节点"),
        ("list_projects", {}, "列出工作区项目"),
        ("list_recently_modified", {"limit": 3, "modifiedWithinDays": 30}, "查最近修改节点"),
        ("search_nodes", {"query": "MCP", "limit": 3, "modifiedWithinDays": 365}, "搜索 MCP 相关节点"),
    ]

    for name, args, goal in calls:
        print(f"\ncalling {name}...")
        print(json.dumps(tool(name, args, goal), ensure_ascii=False))

    print("\nwaiting for async audit writer...")
    time.sleep(2.5)

    log = tool("list_audit_log", {"traceId": TRACE, "limit": 10}, "查询刚写入的审计明细")
    print("\n=== list_audit_log ===")
    print(json.dumps(log, ensure_ascii=False, indent=2))

    stats = tool("get_audit_stats", {"granularity": "minute", "limit": 5}, "查询分钟级聚合")
    print("\n=== get_audit_stats ===")
    print(json.dumps(stats, ensure_ascii=False, indent=2))

    db_paths = [
        Path(r"E:\yixiaozi\_data\_data\audit.db"),
        Path(r"E:\yixiaozi\_data\audit.db"),
    ]
    for p in db_paths:
        print(f"\n=== sqlite {p} event count = {count_events(p)} ===")


if __name__ == "__main__":
    main()
