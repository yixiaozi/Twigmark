#!/usr/bin/env python3
"""Standalone verification for Docear MCP SQLite audit layer."""

from __future__ import annotations

import json
import os
import sqlite3
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:7720/mcp"
REQ_ID = 1


def default_db_path() -> Path:
    appdata = os.environ.get("APPDATA")
    if appdata:
        return Path(appdata) / "Docear" / "_data" / "audit.db"
    return Path.home() / "Docear" / "_data" / "audit.db"


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
            "Mcp-Session-Id": "audit-test-session",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def tool(name: str, arguments: dict | None = None) -> str:
    resp = mcp("tools/call", {"name": name, "arguments": arguments or {}})
    if "error" in resp:
        raise RuntimeError(json.dumps(resp["error"], ensure_ascii=False))
    content = resp.get("result", {}).get("content") or []
    if content:
        text = content[0].get("text")
        return text if text is not None else json.dumps(resp, ensure_ascii=False)
    return json.dumps(resp, ensure_ascii=False)


def verify_sqlite(db_path: Path, trace_id: str) -> dict:
    if not db_path.is_file():
        raise FileNotFoundError(f"audit db not found: {db_path}")
    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    try:
        tables = {
            row[0]
            for row in conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table'"
            ).fetchall()
        }
        required = {
            "audit_event",
            "audit_trace",
            "audit_agg_minute",
            "audit_agg_hour",
            "audit_agg_day",
        }
        missing = required - tables
        if missing:
            raise AssertionError(f"missing tables: {sorted(missing)}")

        event = conn.execute(
            "SELECT action, question_summary, operation_goal, request_json, response_json, trace_id "
            "FROM audit_event WHERE trace_id = ? ORDER BY id DESC LIMIT 1",
            (trace_id,),
        ).fetchone()
        if event is None:
            raise AssertionError(f"no audit_event for trace_id={trace_id}")

        trace = conn.execute(
            "SELECT question_summary, call_count, actions FROM audit_trace WHERE trace_id = ?",
            (trace_id,),
        ).fetchone()
        if trace is None:
            raise AssertionError(f"no audit_trace for trace_id={trace_id}")

        agg = conn.execute(
            "SELECT call_count FROM audit_agg_minute WHERE action = ? ORDER BY bucket_ts DESC LIMIT 1",
            (event["action"],),
        ).fetchone()
        if agg is None:
            raise AssertionError("no minute aggregate row")

        return {
            "tables": sorted(tables),
            "event_action": event["action"],
            "question_summary": event["question_summary"],
            "operation_goal": event["operation_goal"],
            "request_json": event["request_json"],
            "response_json_preview": (event["response_json"] or "")[:200],
            "trace_call_count": trace["call_count"],
            "trace_actions": trace["actions"],
            "minute_call_count": agg["call_count"],
        }
    finally:
        conn.close()


def main() -> int:
    trace_id = f"audit-test-{int(time.time())}"
    audit = {
        "caller": "audit-integration-test",
        "traceId": trace_id,
        "tenant": "default",
        "questionSummary": "验证 MCP SQLite 审计层",
        "operationGoal": "调用 get_selection_context 并写入审计库",
    }

    print("1) initialize + audited tool call")
    mcp(
        "initialize",
        {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "audit-integration-test", "version": "1.0"},
        },
    )
    result_text = tool("get_selection_context", {"_audit": audit})
    print(f"   tool response length: {len(result_text)}")

    print("2) wait for async batch writer")
    time.sleep(2.0)

    print("3) query via MCP list_audit_log / list_audit_traces / get_audit_stats")
    log_json = json.loads(
        tool(
            "list_audit_log",
            {
                "_audit": {
                    **audit,
                    "operationGoal": "查询审计明细",
                },
                "traceId": trace_id,
                "limit": 5,
            },
        )
    )
    traces_json = json.loads(
        tool(
            "list_audit_traces",
            {
                "_audit": {
                    **audit,
                    "operationGoal": "查询 trace 汇总",
                },
                "questionQuery": "SQLite",
                "limit": 5,
            },
        )
    )
    stats_json = json.loads(
        tool(
            "get_audit_stats",
            {
                "_audit": {
                    **audit,
                    "operationGoal": "查询分钟级聚合",
                },
                "granularity": "minute",
                "action": "get_selection_context",
                "limit": 5,
            },
        )
    )

    if log_json.get("count", 0) < 1:
        raise AssertionError("list_audit_log returned no rows")
    if traces_json.get("count", 0) < 1:
        raise AssertionError("list_audit_traces returned no rows")
    if stats_json.get("count", 0) < 1:
        raise AssertionError("get_audit_stats returned no rows")

    db_path = Path(log_json.get("dbPath") or default_db_path())
    print(f"4) verify sqlite file: {db_path}")
    sqlite_report = verify_sqlite(db_path, trace_id)

    print("\nPASS audit integration test")
    print(json.dumps({"mcp_log": log_json, "sqlite": sqlite_report}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except urllib.error.URLError as e:
        print(f"MCP not reachable at {BASE}: {e}", file=sys.stderr)
        print("Start Docear with rebuilt MCP plugin, then rerun this script.", file=sys.stderr)
        raise SystemExit(2)
    except Exception as e:
        print(f"FAIL: {e}", file=sys.stderr)
        raise SystemExit(1)
