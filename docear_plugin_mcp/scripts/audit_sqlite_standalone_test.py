#!/usr/bin/env python3
"""Standalone SQLite audit schema test without running Docear."""

from __future__ import annotations

import sqlite3
import tempfile
from pathlib import Path


SCHEMA = """
PRAGMA journal_mode=WAL;
CREATE TABLE IF NOT EXISTS audit_event (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ts INTEGER NOT NULL,
  tenant TEXT NOT NULL DEFAULT 'default',
  actor TEXT NOT NULL DEFAULT '',
  action TEXT NOT NULL,
  kind TEXT NOT NULL,
  intent TEXT NOT NULL,
  trace_id TEXT NOT NULL DEFAULT '',
  session_id TEXT,
  client_name TEXT,
  os_user TEXT,
  remote_address TEXT,
  question_summary TEXT,
  operation_goal TEXT,
  request_json TEXT,
  response_json TEXT,
  response_bytes INTEGER NOT NULL DEFAULT 0,
  response_truncated INTEGER NOT NULL DEFAULT 0,
  success INTEGER NOT NULL DEFAULT 1,
  duration_ms INTEGER NOT NULL DEFAULT 0,
  error_message TEXT
);
CREATE INDEX IF NOT EXISTS idx_audit_ts ON audit_event(ts);
CREATE INDEX IF NOT EXISTS idx_audit_trace ON audit_event(trace_id);
CREATE TABLE IF NOT EXISTS audit_trace (
  trace_id TEXT PRIMARY KEY,
  tenant TEXT NOT NULL DEFAULT 'default',
  question_summary TEXT,
  actor TEXT,
  first_ts INTEGER NOT NULL,
  last_ts INTEGER NOT NULL,
  call_count INTEGER NOT NULL DEFAULT 0,
  actions TEXT
);
CREATE TABLE IF NOT EXISTS audit_agg_minute (
  bucket_ts INTEGER NOT NULL,
  tenant TEXT NOT NULL DEFAULT 'default',
  actor TEXT NOT NULL DEFAULT '',
  action TEXT NOT NULL,
  intent TEXT NOT NULL,
  call_count INTEGER NOT NULL DEFAULT 0,
  success_count INTEGER NOT NULL DEFAULT 0,
  fail_count INTEGER NOT NULL DEFAULT 0,
  total_duration_ms INTEGER NOT NULL DEFAULT 0,
  total_response_bytes INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (bucket_ts, tenant, actor, action, intent)
);
"""


def main() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        db_path = Path(tmp) / "_data" / "audit.db"
        db_path.parent.mkdir(parents=True, exist_ok=True)
        conn = sqlite3.connect(str(db_path))
        try:
            conn.executescript(SCHEMA)
            ts = 1_700_000_000_000
            bucket = (ts // 60000) * 60000
            conn.execute(
                "INSERT INTO audit_event (ts, tenant, actor, action, kind, intent, trace_id, question_summary,"
                " operation_goal, request_json, response_json, response_bytes, success, duration_ms)"
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (
                    ts,
                    "default",
                    "standalone-test",
                    "get_selection_context",
                    "tool",
                    "CONTEXT",
                    "trace-1",
                    "测试问题",
                    "测试目的",
                    '{"includeFolded": true}',
                    '{"mapFile":"demo.mm"}',
                    18,
                    1,
                    12,
                ),
            )
            conn.execute(
                "INSERT INTO audit_trace (trace_id, tenant, question_summary, actor, first_ts, last_ts, call_count, actions)"
                " VALUES (?,?,?,?,?,?,?,?)",
                ("trace-1", "default", "测试问题", "standalone-test", ts, ts, 1, "get_selection_context"),
            )
            conn.execute(
                "INSERT INTO audit_agg_minute (bucket_ts, tenant, actor, action, intent, call_count, success_count,"
                " fail_count, total_duration_ms, total_response_bytes) VALUES (?,?,?,?,?,?,?,?,?,?)",
                (bucket, "default", "standalone-test", "get_selection_context", "CONTEXT", 1, 1, 0, 12, 18),
            )
            conn.commit()

            row = conn.execute(
                "SELECT question_summary, request_json, response_json FROM audit_event WHERE trace_id='trace-1'"
            ).fetchone()
            assert row[0] == "测试问题"
            assert "includeFolded" in row[1]
            assert "demo.mm" in row[2]

            mode = conn.execute("PRAGMA journal_mode").fetchone()[0]
            assert mode.lower() == "wal"
        finally:
            conn.close()

    print("PASS standalone sqlite audit schema test")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
