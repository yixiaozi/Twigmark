# -*- coding: utf-8 -*-
"""Sanitize illegal XML 1.0 numeric character references in .mm files."""
from __future__ import print_function

import re
import shutil
import sys
from datetime import datetime
from pathlib import Path
from xml.etree import ElementTree as ET

HEX_REF = re.compile(r"&#x([0-9a-fA-F]+);")
DEC_REF = re.compile(r"&#([0-9]+);")
SURR_PAIR = re.compile(
    r"&#x([dD][89A-Fa-f][0-9A-Fa-f]{2});&#x([dD][C-Fc-f][0-9A-Fa-f]{2});"
)


def is_xml10_char(cp):
    return (
        cp == 0x9
        or cp == 0xA
        or cp == 0xD
        or (0x20 <= cp <= 0xD7FF)
        or (0xE000 <= cp <= 0xFFFD)
        or (0x10000 <= cp <= 0x10FFFF)
    )


def sanitize(text):
    def merge_surr(m):
        hi = int(m.group(1), 16)
        lo = int(m.group(2), 16)
        if 0xD800 <= hi <= 0xDBFF and 0xDC00 <= lo <= 0xDFFF:
            cp = 0x10000 + ((hi - 0xD800) << 10) + (lo - 0xDC00)
            return "&#x%x;" % cp
        return m.group(0)

    text = SURR_PAIR.sub(merge_surr, text)

    def repl_hex(m):
        cp = int(m.group(1), 16)
        return m.group(0) if is_xml10_char(cp) else ""

    def repl_dec(m):
        cp = int(m.group(1), 10)
        return m.group(0) if is_xml10_char(cp) else ""

    text = HEX_REF.sub(repl_hex, text)
    text = DEC_REF.sub(repl_dec, text)
    return text


def needs_repair(text):
    return sanitize(text) != text


def repair_file(path):
    original = path.read_text(encoding="utf-8")
    fixed = sanitize(original)
    if fixed == original:
        return False
    bak = path.with_suffix(
        path.suffix + ".bak-illegal-ncr-" + datetime.now().strftime("%Y%m%d%H%M%S")
    )
    shutil.copy2(path, bak)
    # Preserve original newlines as much as possible: write UTF-8 text.
    path.write_text(fixed, encoding="utf-8")
    ET.parse(str(path))
    print("FIXED", path, "backup", bak.name)
    return True


def main(root):
    root = Path(root)
    count = 0
    for f in root.rglob("*.mm"):
        try:
            text = f.read_text(encoding="utf-8", errors="strict")
        except Exception:
            try:
                text = f.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
        if not needs_repair(text):
            continue
        try:
            if repair_file(f):
                count += 1
        except Exception as e:
            print("FAILED", f, e)
    print("repaired_files", count)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else r"E:\yixiaozi")
