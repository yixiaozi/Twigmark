#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Import DocearReminder daily clipboard .txt archives into Docear SQLite DBs.

Source layout:
  <src>/<year>/<month>/<day>.txt   (year folders named with 4 digits)

Entry formats (mixed across years):
  1) Modern:  yyyy/M/d H:mm:ss<spaces>content
     Multi-line content continues until the next timestamp line.
  2) Legacy:  M/d/yyyy h:mm:ss AM|PM  on its own line, content on following lines
     Optional "****..." separator lines between records (not clipboard content).

Output (Docear already aggregates all clipboard_history-*.db in the data dir):
  <out>/clipboard_history-import-<year>.db
  <out>/clipboard_history-import-<year>-p2.db   when a shard exceeds --max-mb

Usage:
  python scripts/import-docear-reminder-clipboard.py
  python scripts/import-docear-reminder-clipboard.py --years 2026 --dry-run
  python scripts/import-docear-reminder-clipboard.py --force
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sqlite3
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Dict, Iterator, List, Optional, Tuple

# Timestamp at start of line (modern, optional same-line content after 2+ spaces)
RE_MODERN = re.compile(
    r"^(?P<ts>\d{4}/\d{1,2}/\d{1,2} \d{1,2}:\d{2}:\d{2})"
    r"(?:(?P<sep>\s{2,})(?P<body>.*))?$"
)
# US-style with AM/PM (legacy DocearReminder)
RE_US = re.compile(
    r"^(?P<ts>\d{1,2}/\d{1,2}/\d{4} \d{1,2}:\d{2}:\d{2} [AP]M)"
    r"(?:(?P<sep>\s{2,})(?P<body>.*))?$",
    re.IGNORECASE,
)
RE_STARS = re.compile(r"^\*{8,}\s*$")


@dataclass
class ClipEvent:
    ts_ms: int
    text: str
    source: str


def sha1_hex(text: str) -> str:
    return hashlib.sha1(text.encode("utf-8")).hexdigest()


def normalize_text(raw: str, max_len: int) -> str:
    if raw is None:
        return ""
    text = raw.replace("\r\n", "\n").replace("\r", "\n")
    # Trim edge whitespace like Java ClipboardHistoryDatabase.trimEdges
    start = 0
    end = len(text)
    while start < end and text[start].isspace():
        start += 1
    while end > start and text[end - 1].isspace():
        end -= 1
    text = text[start:end]
    if max_len > 0 and len(text) > max_len:
        text = text[:max_len]
    return text


def parse_ts_ms(ts: str, kind: str) -> Optional[int]:
    ts = ts.strip()
    formats = (
        ["%Y/%m/%d %H:%M:%S", "%Y/%m/%d %H:%M:%S"]
        if kind == "modern"
        else ["%m/%d/%Y %I:%M:%S %p", "%m/%d/%Y %H:%M:%S %p"]
    )
    # Also allow single-digit without zero-pad via flexible parse
    for fmt in formats:
        try:
            dt = datetime.strptime(ts, fmt)
            return int(dt.timestamp() * 1000)
        except ValueError:
            continue
    # Fallback: normalize spaces
    try:
        if kind == "modern":
            parts = ts.split()
            date_p = parts[0].split("/")
            time_p = parts[1].split(":")
            dt = datetime(
                int(date_p[0]),
                int(date_p[1]),
                int(date_p[2]),
                int(time_p[0]),
                int(time_p[1]),
                int(time_p[2]),
            )
            return int(dt.timestamp() * 1000)
        m = re.match(
            r"(\d{1,2})/(\d{1,2})/(\d{4}) (\d{1,2}):(\d{2}):(\d{2}) ([AP]M)",
            ts,
            re.I,
        )
        if m:
            month, day, year = int(m.group(1)), int(m.group(2)), int(m.group(3))
            hour, minute, sec = int(m.group(4)), int(m.group(5)), int(m.group(6))
            ampm = m.group(7).upper()
            if ampm == "PM" and hour != 12:
                hour += 12
            if ampm == "AM" and hour == 12:
                hour = 0
            dt = datetime(year, month, day, hour, minute, sec)
            return int(dt.timestamp() * 1000)
    except Exception:
        return None
    return None


def try_timestamp_line(line: str) -> Optional[Tuple[int, str]]:
    """Return (ts_ms, same_line_body) if line starts a clipboard entry."""
    m = RE_MODERN.match(line)
    if m:
        ts_ms = parse_ts_ms(m.group("ts"), "modern")
        if ts_ms is None:
            return None
        body = m.group("body") if m.group("sep") else ""
        if body is None:
            body = ""
        return ts_ms, body
    m = RE_US.match(line)
    if m:
        ts_ms = parse_ts_ms(m.group("ts"), "us")
        if ts_ms is None:
            return None
        body = m.group("body") if m.group("sep") else ""
        if body is None:
            body = ""
        return ts_ms, body
    return None


def flush_entry(
    ts_ms: Optional[int],
    parts: List[str],
    source: str,
    max_len: int,
) -> Optional[ClipEvent]:
    if ts_ms is None:
        return None
    # Drop trailing blank / star-separator lines
    while parts:
        last = parts[-1]
        if last.strip() == "" or RE_STARS.match(last):
            parts.pop()
            continue
        break
    # Drop leading blanks / stars
    while parts:
        first = parts[0]
        if first.strip() == "" or RE_STARS.match(first):
            parts.pop(0)
            continue
        break
    # Remove pure star separators in the middle (legacy layout)
    cleaned = [p for p in parts if not RE_STARS.match(p)]
    text = normalize_text("\n".join(cleaned), max_len)
    if not text:
        return None
    return ClipEvent(ts_ms=ts_ms, text=text, source=source)


def iter_events_from_text(text: str, source: str, max_len: int) -> Iterator[ClipEvent]:
    ts_ms: Optional[int] = None
    parts: List[str] = []
    for line in text.splitlines():
        # Star-only lines are legacy separators; skip while collecting
        if RE_STARS.match(line):
            continue
        hit = try_timestamp_line(line)
        if hit is not None:
            ev = flush_entry(ts_ms, parts, source, max_len)
            if ev is not None:
                yield ev
            ts_ms, body = hit
            parts = [body] if body != "" else []
            continue
        if ts_ms is not None:
            parts.append(line)
    ev = flush_entry(ts_ms, parts, source, max_len)
    if ev is not None:
        yield ev


def read_text_file(path: Path) -> str:
    raw = path.read_bytes()
    if raw.startswith(b"\xef\xbb\xbf"):
        return raw.decode("utf-8-sig")
    for enc in ("utf-8", "gb18030", "gbk"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def list_day_files(src_root: Path, years: Optional[List[int]]) -> List[Tuple[int, Path]]:
    out: List[Tuple[int, Path]] = []
    year_dirs = sorted(
        [p for p in src_root.iterdir() if p.is_dir() and re.fullmatch(r"\d{4}", p.name)],
        key=lambda p: int(p.name),
    )
    for ydir in year_dirs:
        year = int(ydir.name)
        if years and year not in years:
            continue
        for path in sorted(ydir.rglob("*.txt")):
            # Only numeric day files like 5.txt / 12.txt; skip key.txt etc.
            if not path.stem.isdigit():
                continue
            out.append((year, path))
    return out


class YearShardWriter:
    def __init__(
        self,
        out_dir: Path,
        year: int,
        max_bytes: int,
        dry_run: bool,
    ) -> None:
        self.out_dir = out_dir
        self.year = year
        self.max_bytes = max_bytes
        self.dry_run = dry_run
        self.part = 1
        self.conn: Optional[sqlite3.Connection] = None
        self.path: Optional[Path] = None
        self.hash_to_id: Dict[str, int] = {}
        self.entries = 0
        self.hits = 0
        self.created_files: List[Path] = []
        self._pending_since_size_check = 0

    def _db_name(self) -> str:
        if self.part == 1:
            return f"clipboard_history-import-{self.year}.db"
        return f"clipboard_history-import-{self.year}-p{self.part}.db"

    def open_shard(self) -> None:
        self.close(finalize=True)
        self.hash_to_id.clear()
        name = self._db_name()
        self.path = self.out_dir / name
        if self.dry_run:
            self.conn = None
            self.created_files.append(self.path)
            print(f"  [dry-run] would create {self.path.name}")
            return
        if self.path.exists():
            self.path.unlink()
        for side in (f"{self.path.name}-wal", f"{self.path.name}-shm"):
            side_path = self.out_dir / side
            if side_path.exists():
                side_path.unlink()
        self.conn = sqlite3.connect(str(self.path))
        self.conn.execute("PRAGMA journal_mode=WAL")
        self.conn.execute("PRAGMA synchronous=NORMAL")
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS clipboard_entry (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content_hash TEXT NOT NULL UNIQUE,
                content TEXT NOT NULL,
                char_len INTEGER NOT NULL DEFAULT 0,
                first_ts INTEGER NOT NULL,
                last_ts INTEGER NOT NULL,
                hit_count INTEGER NOT NULL DEFAULT 1
            );
            CREATE INDEX IF NOT EXISTS idx_clip_last_ts ON clipboard_entry(last_ts DESC);
            CREATE INDEX IF NOT EXISTS idx_clip_hit ON clipboard_entry(hit_count DESC);
            CREATE TABLE IF NOT EXISTS clipboard_hit (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entry_id INTEGER NOT NULL,
                hit_ts INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_clip_hit_entry ON clipboard_hit(entry_id, hit_ts DESC);
            """
        )
        self.created_files.append(self.path)
        print(f"  open {self.path.name}")
        self._pending_since_size_check = 0

    def ensure_open(self) -> None:
        if self.path is None:
            self.open_shard()

    def maybe_rotate(self) -> None:
        if self.dry_run or self.conn is None or self.path is None:
            return
        self._pending_since_size_check += 1
        if self._pending_since_size_check < 200:
            return
        self._pending_since_size_check = 0
        self.conn.commit()
        self.conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        size = self.path.stat().st_size
        if size >= self.max_bytes:
            print(f"  rotate: {self.path.name} is {size / (1024 * 1024):.1f} MB >= limit")
            self.part += 1
            self.open_shard()

    def record(self, ev: ClipEvent) -> None:
        self.ensure_open()
        h = sha1_hex(ev.text)
        self.hits += 1
        if self.dry_run:
            if h not in self.hash_to_id:
                self.hash_to_id[h] = len(self.hash_to_id) + 1
                self.entries += 1
            return
        assert self.conn is not None
        entry_id = self.hash_to_id.get(h)
        if entry_id is None:
            cur = self.conn.execute(
                "INSERT INTO clipboard_entry "
                "(content_hash, content, char_len, first_ts, last_ts, hit_count) "
                "VALUES (?,?,?,?,?,1)",
                (h, ev.text, len(ev.text), ev.ts_ms, ev.ts_ms),
            )
            entry_id = int(cur.lastrowid)
            self.hash_to_id[h] = entry_id
            self.entries += 1
        else:
            self.conn.execute(
                "UPDATE clipboard_entry SET last_ts = CASE WHEN ? > last_ts THEN ? ELSE last_ts END, "
                "hit_count = hit_count + 1, "
                "first_ts = CASE WHEN ? < first_ts THEN ? ELSE first_ts END "
                "WHERE id = ?",
                (ev.ts_ms, ev.ts_ms, ev.ts_ms, ev.ts_ms, entry_id),
            )
        self.conn.execute(
            "INSERT INTO clipboard_hit (entry_id, hit_ts) VALUES (?,?)",
            (entry_id, ev.ts_ms),
        )
        if self.hits % 50 == 0:
            self.conn.commit()
        self.maybe_rotate()

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
            if self.path is not None and self.path.exists():
                mb = self.path.stat().st_size / (1024 * 1024)
                print(f"  close {self.path.name} ({mb:.1f} MB)")


def import_all(
    src: Path,
    out: Path,
    years: Optional[List[int]],
    max_mb: float,
    max_text_len: int,
    dry_run: bool,
    force: bool,
) -> int:
    out.mkdir(parents=True, exist_ok=True)
    max_bytes = int(max_mb * 1024 * 1024)
    files = list_day_files(src, years)
    if not files:
        print("No day .txt files found.", file=sys.stderr)
        return 1

    year_set = set(years) if years else {y for y, _ in files}
    existing = []
    for y in sorted(year_set):
        existing.extend(sorted(out.glob(f"clipboard_history-import-{y}.db")))
        existing.extend(sorted(out.glob(f"clipboard_history-import-{y}-p*.db")))
    if existing and not force and not dry_run:
        print("Import DBs already exist (pass --force to overwrite):", file=sys.stderr)
        for p in existing:
            print(f"  {p}", file=sys.stderr)
        return 1
    if force and not dry_run:
        for y in year_set:
            for p in list(out.glob(f"clipboard_history-import-{y}.db*")) + list(
                out.glob(f"clipboard_history-import-{y}-p*.db*")
            ):
                p.unlink()

    print(f"Found {len(files)} day files under {src}")
    writers: Dict[int, YearShardWriter] = {}
    total_hits = 0
    total_entries_est = 0
    file_errors = 0

    current_year = None
    for year, path in files:
        if year not in writers:
            if current_year is not None and current_year in writers:
                writers[current_year].close(finalize=True)
            writers[year] = YearShardWriter(out, year, max_bytes, dry_run)
            writers[year].open_shard()
            current_year = year
            print(f"Year {year}")
        rel = path.relative_to(src)
        try:
            text = read_text_file(path)
            n = 0
            for ev in iter_events_from_text(text, str(rel), max_text_len):
                writers[year].record(ev)
                n += 1
                total_hits += 1
            if n:
                print(f"  {rel}: {n} entries")
        except Exception as e:
            file_errors += 1
            print(f"  ERROR {rel}: {e}", file=sys.stderr)

    for w in writers.values():
        w.close(finalize=True)
        total_entries_est += w.entries

    print("---")
    print(f"hits recorded: {total_hits}")
    print(f"unique entries (per-shard sum): {total_entries_est}")
    print(f"file errors: {file_errors}")
    created = []
    for w in writers.values():
        created.extend(w.created_files)
    print("databases:")
    for p in created:
        if p.exists():
            print(f"  {p} ({p.stat().st_size / (1024 * 1024):.1f} MB)")
        else:
            print(f"  {p} (dry-run)")
    print(
        "Docear reads all clipboard_history-*.db in the data folder; "
        "restart or refresh the Clipboard tab to see imports."
    )
    return 0 if file_errors == 0 else 2


def self_check() -> None:
    sample = (
        "2026/07/05 11:08:13   Hermes Agent（\n"
        "2026/07/05 11:09:48   场景 1：调用云端 API\n"
        "2026/07/05 11:10:22   内存最低 8GB（跑 7B 小模型）；流畅 32B 模型需 16GB+\n"
        "GPU（可选但强烈推荐）：NVIDIA 显卡 ≥6G 显存\n"
        "磁盘：≥20GB（存放模型文件）\n"
        "CPU：4 核起步，8 核更佳\n"
        "2026/07/05 11:11:50   Ollama \n"
        "10/11/2017 1:10:11 AM\n"
        "O:\\PRODUCTION\\5074\\Quality Result \n"
        "\n"
        "************************************************************************************************************************\n"
        "\n"
        "10/11/2017 1:11:29 AM\n"
        "用人统计\n"
    )
    events = list(iter_events_from_text(sample, "self", 100000))
    assert len(events) == 6, (len(events), events)
    multi = events[2]
    assert "GPU" in multi.text and "CPU" in multi.text, multi.text
    assert multi.text.count("\n") == 3, repr(multi.text)
    legacy = events[4]
    assert "PRODUCTION" in legacy.text
    assert "*" not in legacy.text
    print("self-check OK:", len(events), "events; multiline lines=", multi.text.count("\n") + 1)


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Import DocearReminder clipboard txt → SQLite")
    parser.add_argument(
        "--src",
        type=Path,
        default=Path(r"D:\Dropbox\Software\DocearReminder"),
        help="DocearReminder root containing year folders",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path(r"E:\yixiaozi\_data"),
        help="Docear data dir for clipboard_history-import-*.db",
    )
    parser.add_argument("--years", type=int, nargs="*", help="Only these years (default: all)")
    parser.add_argument("--max-mb", type=float, default=30.0, help="Rotate shard when DB >= this size")
    parser.add_argument(
        "--max-text-length",
        type=int,
        default=100000,
        help="Truncate each entry (Docear app max is 100000)",
    )
    parser.add_argument("--dry-run", action="store_true", help="Parse only, do not write DBs")
    parser.add_argument(
        "--force",
        action="store_true",
        help="Delete existing clipboard_history-import-<year>*.db before writing",
    )
    parser.add_argument("--self-check", action="store_true", help="Run parser unit check and exit")
    args = parser.parse_args(argv)

    if args.self_check:
        self_check()
        return 0

    if not args.src.is_dir():
        print(f"Source not found: {args.src}", file=sys.stderr)
        return 1

    return import_all(
        src=args.src,
        out=args.out,
        years=args.years,
        max_mb=args.max_mb,
        max_text_len=args.max_text_length,
        dry_run=args.dry_run,
        force=args.force,
    )


if __name__ == "__main__":
    raise SystemExit(main())
