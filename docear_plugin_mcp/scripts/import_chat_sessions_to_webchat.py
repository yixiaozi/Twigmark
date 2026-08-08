#!/usr/bin/env python3
"""Import legacy desktop AiChatSessionStore *.chat files into webchat SQLite.

Legacy files live under interaction_history/chat_sessions/ and store Windows
mapKey paths (E:\\yixiaozi\\...). New storage is conversations.map_key + messages
with source='desktop'.

Example:
  python3 import_chat_sessions_to_webchat.py \\
    --sessions-dir /Users/wangyang/Develop/yixiaozi/_data/.../chat_sessions \\
    --db /Users/wangyang/Develop/yixiaozi/_data/webchat-1628ec0c67d2.db \\
    --workspace /Users/wangyang/Develop/yixiaozi
"""

from __future__ import annotations

import argparse
import os
import re
import sqlite3
import sys
import uuid
from pathlib import Path
from typing import List, Optional, Tuple


DEFAULT_WIN_ROOTS = (
    r"E:\yixiaozi",
    r"E:/yixiaozi",
    r"e:\yixiaozi",
    r"e:/yixiaozi",
)

# Known path moves after Windows → Mac sync.
KNOWN_RELOCS = {
    "00统领全局/test.mm": "09记录存档/00思维导图/test.mm",
}


def new_id() -> str:
    return uuid.uuid4().hex


def parse_chat(path: Path) -> Tuple[str, int, List[Tuple[str, int, str]]]:
    text = path.read_text(encoding="utf-8", errors="replace")
    map_key = ""
    last_updated = 0
    m = re.search(r"^mapKey=(.*)$", text, re.M)
    if m:
        map_key = m.group(1).strip()
    m = re.search(r"^lastUpdated=(\d+)$", text, re.M)
    if m:
        last_updated = int(m.group(1))

    messages: List[Tuple[str, int, str]] = []
    for part in text.split("---MESSAGE---")[1:]:
        role = None
        ts = 0
        content_lines: List[str] = []
        in_content = False
        for line in part.splitlines():
            if line.startswith("role="):
                role = line[5:].strip()
                in_content = False
            elif line.startswith("time="):
                try:
                    ts = int(line[5:].strip())
                except ValueError:
                    ts = 0
            elif line == "content=":
                in_content = True
            elif in_content:
                content_lines.append(line.replace("\\\\", "\\"))
        if role:
            role_norm = "assistant" if role.upper() == "ASSISTANT" else "user"
            messages.append((role_norm, ts, "\n".join(content_lines)))
    return map_key, last_updated, messages


def remap_map_key(map_key: str, workspace: Path) -> str:
    if not map_key:
        return map_key
    normalized = map_key.replace("\\", "/")
    for root in DEFAULT_WIN_ROOTS:
        root_n = root.replace("\\", "/")
        if normalized.lower().startswith(root_n.lower()):
            rel = normalized[len(root_n) :].lstrip("/")
            rel = KNOWN_RELOCS.get(rel, rel)
            return str((workspace / rel).resolve())
    # Already a Unix path under workspace, or unknown.
    p = Path(map_key)
    if p.is_absolute():
        return str(p)
    return str((workspace / map_key).resolve())


def ensure_schema(conn: sqlite3.Connection) -> None:
    conn.execute(
        "CREATE TABLE IF NOT EXISTS conversations ("
        "id TEXT PRIMARY KEY,"
        "username TEXT NOT NULL,"
        "title TEXT NOT NULL DEFAULT '',"
        "created_at INTEGER NOT NULL,"
        "updated_at INTEGER NOT NULL,"
        "machine_id TEXT NOT NULL DEFAULT '',"
        "machine_name TEXT NOT NULL DEFAULT '',"
        "source TEXT NOT NULL DEFAULT 'web',"
        "map_key TEXT NOT NULL DEFAULT ''"
        ")"
    )
    for col, decl in (
        ("source", "TEXT NOT NULL DEFAULT 'web'"),
        ("map_key", "TEXT NOT NULL DEFAULT ''"),
        ("machine_id", "TEXT NOT NULL DEFAULT ''"),
        ("machine_name", "TEXT NOT NULL DEFAULT ''"),
    ):
        try:
            conn.execute(f"ALTER TABLE conversations ADD COLUMN {col} {decl}")
        except sqlite3.OperationalError:
            pass
    conn.execute(
        "CREATE INDEX IF NOT EXISTS idx_webchat_conv_map "
        "ON conversations(username, source, map_key)"
    )
    conn.commit()


def find_existing(
    conn: sqlite3.Connection, username: str, map_key: str
) -> Optional[Tuple[str, int]]:
    row = conn.execute(
        "SELECT id, (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id) AS n "
        "FROM conversations c WHERE username = ? AND source = 'desktop' AND map_key = ? "
        "ORDER BY updated_at DESC LIMIT 1",
        (username, map_key),
    ).fetchone()
    if not row:
        return None
    return row[0], int(row[1] or 0)


def title_from_map_key(map_key: str) -> str:
    name = Path(map_key).name
    return name if name else map_key or "(未命名)"


def import_one(
    conn: sqlite3.Connection,
    *,
    username: str,
    machine_id: str,
    machine_name: str,
    map_key: str,
    last_updated: int,
    messages: List[Tuple[str, int, str]],
    dry_run: bool,
) -> str:
    existing = find_existing(conn, username, map_key)
    if existing and existing[1] > 0:
        return f"skip existing ({existing[1]} msgs) {map_key}"

    if not messages:
        return f"skip empty {map_key}"

    created = messages[0][1] or last_updated or 0
    updated = last_updated or (messages[-1][1] if messages else created)
    conv_id = new_id()
    title = title_from_map_key(map_key)

    if dry_run:
        return f"would import {len(messages)} msgs → {map_key}"

    if existing and existing[1] == 0:
        conv_id = existing[0]
        conn.execute(
            "UPDATE conversations SET title=?, created_at=?, updated_at=?, "
            "machine_id=?, machine_name=?, source='desktop', map_key=? WHERE id=?",
            (title, created, updated, machine_id, machine_name, map_key, conv_id),
        )
    else:
        conn.execute(
            "INSERT INTO conversations"
            "(id, username, title, created_at, updated_at, machine_id, machine_name, source, map_key)"
            " VALUES(?,?,?,?,?,?,?,'desktop',?)",
            (
                conv_id,
                username,
                title,
                created,
                updated,
                machine_id,
                machine_name,
                map_key,
            ),
        )

    for role, ts, content in messages:
        conn.execute(
            "INSERT INTO messages"
            "(id, conversation_id, role, content, tool_trace_json, model, created_at, machine_id)"
            " VALUES(?,?,?,?,?,?,?,?)",
            (new_id(), conv_id, role, content, "", "", ts or created, machine_id),
        )
    return f"imported {len(messages)} msgs → {map_key}"


def detect_machine(conn: sqlite3.Connection, db_path: Path) -> Tuple[str, str]:
    row = conn.execute(
        "SELECT machine_id, machine_name FROM conversations "
        "WHERE machine_id != '' ORDER BY updated_at DESC LIMIT 1"
    ).fetchone()
    if row and row[0]:
        return row[0], row[1] or row[0]
    # Infer from webchat-<mac>.db filename.
    m = re.match(r"webchat-([0-9a-f]+)\.db$", db_path.name, re.I)
    if m:
        hexv = m.group(1).lower()
        return f"mac-{hexv}", os.uname().nodename
    return "mac-imported", os.uname().nodename


def main(argv: List[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--sessions-dir", required=True, type=Path)
    ap.add_argument("--db", required=True, type=Path)
    ap.add_argument(
        "--workspace",
        type=Path,
        default=Path("/Users/wangyang/Develop/yixiaozi"),
        help="Mac root that replaces E:\\yixiaozi",
    )
    ap.add_argument("--username", default="yixiaozi")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)

    sessions_dir: Path = args.sessions_dir
    db_path: Path = args.db
    if not sessions_dir.is_dir():
        print(f"sessions dir not found: {sessions_dir}", file=sys.stderr)
        return 1
    if not db_path.is_file():
        print(f"db not found: {db_path}", file=sys.stderr)
        return 1

    files = sorted(sessions_dir.glob("*.chat"))
    if not files:
        print("no *.chat files")
        return 0

    conn = sqlite3.connect(str(db_path))
    try:
        ensure_schema(conn)
        machine_id, machine_name = detect_machine(conn, db_path)
        print(f"db={db_path}")
        print(f"machine={machine_id} ({machine_name}) username={args.username}")
        print(f"workspace={args.workspace}")
        print(f"files={len(files)} dry_run={args.dry_run}")

        imported = skipped = 0
        for f in files:
            old_key, last_updated, messages = parse_chat(f)
            map_key = remap_map_key(old_key, args.workspace)
            # Skip GBK-as-UTF8 mojibake duplicates (e.g. 有条不紊 → 鏈夋潯涓嶇磰).
            if "鏈夋潯" in old_key or "\ufffd" in old_key:
                print(f"  skip garbled path: {f.name}")
                skipped += 1
                continue
            msg = import_one(
                conn,
                username=args.username,
                machine_id=machine_id,
                machine_name=machine_name,
                map_key=map_key,
                last_updated=last_updated,
                messages=messages,
                dry_run=args.dry_run,
            )
            print(f"  {f.name}: {msg}")
            if msg.startswith("imported") or msg.startswith("would"):
                imported += 1
            else:
                skipped += 1
        if not args.dry_run:
            conn.commit()
        print(f"done imported={imported} skipped={skipped}")
    finally:
        conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
