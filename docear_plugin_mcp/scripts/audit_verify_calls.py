#!/usr/bin/env python3
"""Call a batch of Docear MCP tools with Chinese _audit metadata for audit verification."""

from __future__ import annotations

import json
import sqlite3
import time
import urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:7720/mcp"
REQ_ID = 1
TRACE = f"cursor-audit-verify-{int(time.time())}"
AUDIT = {
    "caller": "cursor-agent",
    "traceId": TRACE,
    "tenant": "default",
    "questionSummary": "验证 MCP 审计中文是否正常写入",
    "operationGoal": "占位",
}


def mcp(method: str, params: dict | None = None) -> dict:
    global REQ_ID
    payload = {"jsonrpc": "2.0", "id": REQ_ID, "method": method}
    REQ_ID += 1
    if params is not None:
        payload["params"] = params
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        BASE,
        data=data,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Mcp-Session-Id": "cursor-audit-verify",
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
        return {"tool": name, "error": resp["error"]}
    content = resp.get("result", {}).get("content") or []
    text = content[0].get("text") if content else json.dumps(resp, ensure_ascii=False)
    return {"tool": name, "ok": True, "preview": (text or "")[:200], "len": len(text or "")}


def main() -> None:
    print(f"traceId = {TRACE}\n")

    mcp(
        "initialize",
        {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "cursor-agent", "version": "1.0"},
        },
    )

    calls = [
        ("get_selection_context", {}, "读取当前打开导图与选中节点"),
        ("list_projects", {}, "列出工作区项目"),
        ("list_recently_modified", {"limit": 5, "modifiedWithinDays": 7}, "查最近 7 天修改的节点"),
        ("search_nodes", {"query": "审计", "limit": 5, "modifiedWithinDays": 365}, "搜索审计相关节点"),
        ("list_todos", {"limit": 5}, "列出待办事项"),
        ("list_reminders", {"limit": 5}, "列出提醒事项"),
        ("get_workspace_plan", {}, "获取工作区计划概览"),
    ]

    for name, args, goal in calls:
        result = tool(name, args, goal)
        status = "OK" if result.get("ok") else "ERR"
        print(f"[{status}] {name}: {result.get('preview', result.get('error', ''))[:120]}")

    print("\nwaiting for audit writer...")
    time.sleep(2.5)

    log_result = tool("list_audit_log", {"traceId": TRACE, "limit": 20}, "查询本次 trace 的审计明细")
    if log_result.get("ok"):
        full = mcp("tools/call", {
            "name": "list_audit_log",
            "arguments": {
                "traceId": TRACE,
                "limit": 20,
                "_audit": {**AUDIT, "operationGoal": "查询审计明细"},
            },
        })
        text = full["result"]["content"][0]["text"]
        data = json.loads(text)
        print(f"\n=== audit log: {data.get('count', 0)} entries ===")
        for entry in data.get("entries", []):
            print(
                f"  #{entry['id']} {entry['action']:28} | "
                f"问题: {entry['questionSummary'][:30]} | "
                f"意图: {entry['operationGoal'][:24]}"
            )
        print(f"\ndbPath: {data.get('dbPath')}")

    db_path = Path(r"E:\yixiaozi\_data\_data\audit.db")
    if db_path.is_file():
        conn = sqlite3.connect(str(db_path))
        try:
            rows = conn.execute(
                """
                SELECT id, action, question_summary, operation_goal
                FROM audit_event
                WHERE trace_id = ?
                ORDER BY id
                """,
                (TRACE,),
            ).fetchall()
            print(f"\n=== sqlite direct ({len(rows)} rows for trace) ===")
            for row in rows:
                print(f"  #{row[0]} {row[1]}")
                print(f"    问题: {row[2]}")
                print(f"    意图: {row[3]}")
        finally:
            conn.close()


if __name__ == "__main__":
    main()
