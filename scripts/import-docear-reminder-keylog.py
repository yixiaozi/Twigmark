#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Import DocearReminder key.txt archives into TwigMark keylog SQLite DBs.

Layout: <src>/<year>/<month>/key.txt
Preserves key ORDER; synthesizes per-key timestamps by interpolating between
line start and next line start (approx=1).

Output: <out>/keylog-import-<year>.db (+ -p2 when >= --max-mb)
"""

from __future__ import annotations

import argparse
import re
import sqlite3
import struct
import sys
import zlib
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple

RE_MODERN = re.compile(
    r"^(?P<ts>\d{4}/\d{1,2}/\d{1,2} \d{1,2}:\d{2}:\d{2})\s*(?P<body>.*)$"
)
RE_US = re.compile(
    r"^(?P<ts>\d{1,2}/\d{1,2}/\d{4} \d{1,2}:\d{2}:\d{2} [AP]M)\s*(?P<body>.*)$",
    re.I,
)


def parse_ts_ms(ts: str) -> Optional[int]:
    ts = ts.strip()
    for fmt in ("%Y/%m/%d %H:%M:%S", "%Y/%m/%d %H:%M:%S", "%m/%d/%Y %I:%M:%S %p"):
        try:
            return int(datetime.strptime(ts, fmt).timestamp() * 1000)
        except ValueError:
            continue
    m = re.match(r"(\d{4})/(\d{1,2})/(\d{1,2}) (\d{1,2}):(\d{2}):(\d{2})", ts)
    if m:
        dt = datetime(int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5)), int(m.group(6)))
        return int(dt.timestamp() * 1000)
    m = re.match(r"(\d{1,2})/(\d{1,2})/(\d{4}) (\d{1,2}):(\d{2}):(\d{2}) ([AP]M)", ts, re.I)
    if m:
        month, day, year = int(m.group(1)), int(m.group(2)), int(m.group(3))
        hour, minute, sec = int(m.group(4)), int(m.group(5)), int(m.group(6))
        ampm = m.group(7).upper()
        if ampm == "PM" and hour != 12:
            hour += 12
        if ampm == "AM" and hour == 12:
            hour = 0
        return int(datetime(year, month, day, hour, minute, sec).timestamp() * 1000)
    return None


# Longest-first tokens for legacy lines without ';' (2021-style CamelCase glue).
_KNOWN_KEYS = sorted(
    [
        "LControlKey",
        "RControlKey",
        "LShiftKey",
        "RShiftKey",
        "ControlKey",
        "ShiftKey",
        "PrintScreen",
        "CapsLock",
        "NumLock",
        "Scroll",
        "PageUp",
        "Next",
        "Escape",
        "Return",
        "Insert",
        "Delete",
        "Space",
        "Back",
        "Tab",
        "Home",
        "End",
        "Left",
        "Right",
        "Up",
        "Down",
        "Pause",
        "Apps",
        "LWin",
        "RWin",
        "LMenu",
        "RMenu",
        "Menu",
        "Multiply",
        "Subtract",
        "Decimal",
        "Divide",
        "Add",
        "Separator",
        "Oemcomma",
        "OemPeriod",
        "OemMinus",
        "Oemplus",
        "Oem1",
        "Oem2",
        "Oem3",
        "Oem4",
        "Oem5",
        "Oem6",
        "Oem7",
        "Oem8",
    ]
    + [f"NumPad{i}" for i in range(10)]
    + [f"D{i}" for i in range(10)]
    + [f"F{i}" for i in range(24, 0, -1)]
    + [chr(c) for c in range(ord("A"), ord("Z") + 1)],
    key=len,
    reverse=True,
)


def split_keys(body: str) -> List[str]:
    if not body:
        return []
    if ";" in body:
        return [p for p in body.split(";") if p != ""]
    # 2021-style: DFBackBackLShiftKey...
    keys: List[str] = []
    i = 0
    n = len(body)
    while i < n:
        matched = None
        for name in _KNOWN_KEYS:
            if body.startswith(name, i):
                matched = name
                break
        if matched is None:
            # skip unknown char
            i += 1
            continue
        keys.append(matched)
        i += len(matched)
    return keys


def read_text(path: Path) -> str:
    raw = path.read_bytes()
    if raw.startswith(b"\xef\xbb\xbf"):
        return raw.decode("utf-8-sig")
    for enc in ("utf-8", "gb18030", "gbk"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def parse_key_file(path: Path) -> List[Tuple[int, List[str]]]:
    """Return list of (line_start_ts_ms, keys_in_order)."""
    text = read_text(path)
    rows: List[Tuple[int, List[str]]] = []
    for line in text.splitlines():
        if not line.strip():
            continue
        m = RE_MODERN.match(line) or RE_US.match(line)
        if not m:
            continue
        ts = parse_ts_ms(m.group("ts"))
        if ts is None:
            continue
        keys = split_keys(m.group("body") or "")
        if keys:
            rows.append((ts, keys))
    return rows


def encode_chunk(events: List[Tuple[int, int]]) -> bytes:
    raw = bytearray()
    for key_id, delta in events:
        raw += struct.pack(">HH", key_id & 0xFFFF, max(0, min(65535, delta)) & 0xFFFF)
    return zlib.compress(bytes(raw), level=1)


def floor_hour(ts: int) -> int:
    return (ts // 3600000) * 3600000


class YearWriter:
    def __init__(self, out_dir: Path, year: int, max_bytes: int, dry_run: bool):
        self.out_dir = out_dir
        self.year = year
        self.max_bytes = max_bytes
        self.dry_run = dry_run
        self.part = 1
        self.conn: Optional[sqlite3.Connection] = None
        self.path: Optional[Path] = None
        self.name_to_id: Dict[str, int] = {}
        self.sessions = 0
        self.keys = 0
        self.files: List[Path] = []
        self._since_check = 0

    def _name(self) -> str:
        if self.part == 1:
            return f"keylog-import-{self.year}.db"
        return f"keylog-import-{self.year}-p{self.part}.db"

    def open(self) -> None:
        self.close(True)
        self.name_to_id.clear()
        self.path = self.out_dir / self._name()
        self.files.append(self.path)
        if self.dry_run:
            print(f"  [dry-run] {self.path.name}")
            return
        if self.path.exists():
            self.path.unlink()
        for side in (f"{self.path.name}-wal", f"{self.path.name}-shm"):
            p = self.out_dir / side
            if p.exists():
                p.unlink()
        self.conn = sqlite3.connect(str(self.path))
        self.conn.execute("PRAGMA journal_mode=WAL")
        self.conn.execute("PRAGMA synchronous=NORMAL")
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS key_dict (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE
            );
            CREATE TABLE IF NOT EXISTS key_session (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_ts INTEGER NOT NULL,
                end_ts INTEGER NOT NULL,
                key_count INTEGER NOT NULL,
                approx INTEGER NOT NULL DEFAULT 0,
                source TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_key_session_ts ON key_session(start_ts, end_ts);
            CREATE TABLE IF NOT EXISTS key_chunk (
                session_id INTEGER PRIMARY KEY,
                blob BLOB NOT NULL
            );
            CREATE TABLE IF NOT EXISTS key_hour_stat (
                hour_ts INTEGER PRIMARY KEY,
                key_count INTEGER NOT NULL
            );
            """
        )
        print(f"  open {self.path.name}")
        self._since_check = 0

    def ensure_open(self) -> None:
        if self.path is None:
            self.open()

    def key_id(self, name: str) -> int:
        assert self.conn is not None
        if name in self.name_to_id:
            return self.name_to_id[name]
        cur = self.conn.execute("INSERT OR IGNORE INTO key_dict(name) VALUES (?)", (name,))
        row = self.conn.execute("SELECT id FROM key_dict WHERE name = ?", (name,)).fetchone()
        kid = int(row[0])
        self.name_to_id[name] = kid
        return kid

    def write_session(self, start_ts: int, end_ts: int, keys: List[str]) -> None:
        if not keys:
            return
        self.ensure_open()
        n = len(keys)
        span = max(0, end_ts - start_ts)
        step = 0 if n <= 1 else max(1, span // (n - 1))
        if self.dry_run:
            self.sessions += 1
            self.keys += n
            return
        assert self.conn is not None
        events: List[Tuple[int, int]] = []
        hour_map: Dict[int, int] = {}
        t = start_ts
        for i, name in enumerate(keys):
            kid = self.key_id(name)
            delta = 0 if i == 0 else min(65535, step)
            events.append((kid, delta))
            if i > 0:
                t += delta
            h = floor_hour(t)
            hour_map[h] = hour_map.get(h, 0) + 1
        blob = encode_chunk(events)
        cur = self.conn.execute(
            "INSERT INTO key_session(start_ts, end_ts, key_count, approx, source) VALUES (?,?,?,?,?)",
            (start_ts, t if t > start_ts else end_ts, n, 1, "import"),
        )
        sid = int(cur.lastrowid)
        self.conn.execute("INSERT INTO key_chunk(session_id, blob) VALUES (?,?)", (sid, blob))
        for h, c in hour_map.items():
            self.conn.execute("INSERT OR IGNORE INTO key_hour_stat(hour_ts, key_count) VALUES (?, 0)", (h,))
            self.conn.execute(
                "UPDATE key_hour_stat SET key_count = key_count + ? WHERE hour_ts = ?",
                (c, h),
            )
        self.sessions += 1
        self.keys += n
        self._since_check += 1
        if self._since_check >= 20:
            self._since_check = 0
            self.conn.commit()
            self.conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
            if self.path and self.path.stat().st_size >= self.max_bytes:
                print(f"  rotate {self.path.name} ({self.path.stat().st_size/1024/1024:.1f} MB)")
                self.part += 1
                self.open()

    def close(self, finalize: bool = False) -> None:
        if self.conn is not None:
            self.conn.commit()
            if finalize:
                try:
                    self.conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
                    self.conn.execute("VACUUM")
                except sqlite3.Error:
                    pass
            self.conn.close()
            self.conn = None
            if self.path and self.path.exists():
                print(f"  close {self.path.name} ({self.path.stat().st_size/1024/1024:.1f} MB)")


def list_key_files(src: Path, years: Optional[List[int]]) -> List[Tuple[int, Path]]:
    out: List[Tuple[int, Path]] = []
    for ydir in sorted([p for p in src.iterdir() if p.is_dir() and re.fullmatch(r"\d{4}", p.name)], key=lambda p: int(p.name)):
        year = int(ydir.name)
        if years and year not in years:
            continue
        for path in sorted(ydir.rglob("key.txt")):
            out.append((year, path))
    return out


def import_all(src: Path, out: Path, years: Optional[List[int]], max_mb: float, dry_run: bool, force: bool) -> int:
    out.mkdir(parents=True, exist_ok=True)
    files = list_key_files(src, years)
    if not files:
        print("No key.txt found.", file=sys.stderr)
        return 1
    year_set = set(years) if years else {y for y, _ in files}
    existing = []
    for y in sorted(year_set):
        existing.extend(out.glob(f"keylog-import-{y}.db"))
        existing.extend(out.glob(f"keylog-import-{y}-p*.db"))
    if existing and not force and not dry_run:
        print("Import DBs exist; pass --force to overwrite:", file=sys.stderr)
        for p in existing:
            print(" ", p, file=sys.stderr)
        return 1
    if force and not dry_run:
        for y in year_set:
            for p in list(out.glob(f"keylog-import-{y}.db*")) + list(out.glob(f"keylog-import-{y}-p*.db*")):
                p.unlink()

    max_bytes = int(max_mb * 1024 * 1024)
    print(f"Found {len(files)} key.txt under {src}")
    writers: Dict[int, YearWriter] = {}
    for year, path in files:
        if year not in writers:
            if writers:
                # close previous years
                for y in list(writers.keys()):
                    if y != year:
                        writers[y].close(True)
            writers[year] = YearWriter(out, year, max_bytes, dry_run)
            writers[year].open()
            print(f"Year {year}")
        rel = path.relative_to(src)
        rows = parse_key_file(path)
        key_total = 0
        for i, (start_ts, keys) in enumerate(rows):
            if i + 1 < len(rows):
                end_ts = rows[i + 1][0]
            else:
                end_ts = start_ts + max(1000, len(keys) * 80)
            if end_ts <= start_ts:
                end_ts = start_ts + max(1000, len(keys) * 80)
            writers[year].write_session(start_ts, end_ts, keys)
            key_total += len(keys)
        print(f"  {rel}: {len(rows)} lines, {key_total} keys")

    for w in writers.values():
        w.close(True)

    print("---")
    total_keys = sum(w.keys for w in writers.values())
    total_sess = sum(w.sessions for w in writers.values())
    print(f"sessions={total_sess} keys={total_keys}")
    for w in writers.values():
        for p in w.files:
            if p.exists():
                print(f"  {p} ({p.stat().st_size/1024/1024:.1f} MB)")
            else:
                print(f"  {p} (dry-run)")
    return 0


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Import DocearReminder key.txt → keylog SQLite")
    p.add_argument("--src", type=Path, default=Path(r"D:\Dropbox\Software\DocearReminder"))
    p.add_argument("--out", type=Path, default=Path(r"E:\yixiaozi\_data"))
    p.add_argument("--years", type=int, nargs="*")
    p.add_argument("--max-mb", type=float, default=30.0)
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--force", action="store_true")
    args = p.parse_args(argv)
    if not args.src.is_dir():
        print("Source missing:", args.src, file=sys.stderr)
        return 1
    return import_all(args.src, args.out, args.years, args.max_mb, args.dry_run, args.force)


if __name__ == "__main__":
    raise SystemExit(main())
