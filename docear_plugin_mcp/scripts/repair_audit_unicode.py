#!/usr/bin/env python3
"""Repair audit rows where \\uXXXX was stored as literal uXXXX (JsonParser bug)."""

from __future__ import annotations

import re
import sqlite3
import sys
from pathlib import Path

PATTERN = re.compile(r"u([0-9a-fA-F]{4})")


def repair_text(text: str) -> str:
    if not text or not PATTERN.search(text):
        return text
    return PATTERN.sub(lambda m: chr(int(m.group(1), 16)), text)


def main() -> None:
    db_path = Path(sys.argv[1] if len(sys.argv) > 1 else r"E:\yixiaozi\_data\_data\audit.db")
    if not db_path.is_file():
        print(f"DB not found: {db_path}")
        sys.exit(1)

    conn = sqlite3.connect(str(db_path))
    try:
        rows = conn.execute(
            "SELECT id, question_summary, operation_goal FROM audit_event"
        ).fetchall()
        updated = 0
        for row_id, summary, goal in rows:
            new_summary = repair_text(summary or "")
            new_goal = repair_text(goal or "")
            if new_summary != (summary or "") or new_goal != (goal or ""):
                conn.execute(
                    "UPDATE audit_event SET question_summary=?, operation_goal=? WHERE id=?",
                    (new_summary, new_goal, row_id),
                )
                updated += 1
        conn.commit()
        print(f"Repaired {updated} of {len(rows)} rows in {db_path}")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
