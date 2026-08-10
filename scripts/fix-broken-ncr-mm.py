# -*- coding: utf-8 -*-
"""One-shot / reusable repair for broken Freeplane NCR corruptions in .mm files."""
from __future__ import print_function
import argparse
import re
import shutil
from pathlib import Path
from xml.etree import ElementTree as ET


def repair_text(text):
    original = text
    text = text.replace("&[x", "&#x").replace("&[X", "&#x")
    # Orphan ']' left after 【…】 → […] → broken-entity migration (']' after NCR ';')
    text = re.sub(r";]([\", \t])", r";\1", text)
    text = re.sub(r"&#x0;", "", text, flags=re.IGNORECASE)
    text = re.sub(r"&#0;", "", text)
    return text, text != original


def repair_file(path, backup=True):
    path = Path(path)
    raw = path.read_bytes()
    text = raw.decode("utf-8")
    fixed, changed = repair_text(text)
    if not changed:
        return False, "unchanged"
    ET.fromstring(fixed)  # validate
    if backup:
        bak = path.with_suffix(path.suffix + ".bak-ncr-repair")
        if not bak.exists():
            shutil.copy2(str(path), str(bak))
    path.write_bytes(fixed.encode("utf-8"))
    return True, "repaired"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+")
    args = ap.parse_args()
    for p in args.paths:
        changed, msg = repair_file(p)
        print(msg, p)


if __name__ == "__main__":
    main()
