#!/usr/bin/env python3
"""Validate .mm fixtures are well-formed Freeplane/Twigmark XML (no GUI)."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURE_DIR = ROOT / "testdata" / "mm-fixtures"

REQUIRED_ATTRS_HINT = ("CREATED", "ID", "MODIFIED", "TEXT")


def check_map(path: Path) -> None:
    tree = ET.parse(path)
    root = tree.getroot()
    if root.tag != "map":
        raise AssertionError(f"{path}: root tag is <{root.tag}>, expected <map>")
    nodes = list(root.iter("node"))
    if not nodes:
        raise AssertionError(f"{path}: no <node> elements")
    # Root node should usually carry identity attrs used by Twigmark MCP/search
    first = nodes[0]
    missing = [a for a in ("ID",) if a not in first.attrib]
    if missing:
        raise AssertionError(f"{path}: first node missing {missing}")
    # Ensure round-trip serialization does not drop node count
    xml_out = ET.tostring(root, encoding="utf-8")
    again = ET.fromstring(xml_out)
    if len(list(again.iter("node"))) != len(nodes):
        raise AssertionError(f"{path}: node count changed after serialize")


def main() -> int:
    if not FIXTURE_DIR.is_dir():
        print(f"Missing fixture dir: {FIXTURE_DIR}", file=sys.stderr)
        return 1
    maps = sorted(FIXTURE_DIR.glob("*.mm"))
    if not maps:
        print(f"No .mm fixtures in {FIXTURE_DIR}", file=sys.stderr)
        return 1
    for path in maps:
        check_map(path)
        print(f"OK {path.relative_to(ROOT)}")
    print(f"Validated {len(maps)} mindmap fixture(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
