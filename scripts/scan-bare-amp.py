# -*- coding: utf-8 -*-
import re
from pathlib import Path

PAT = re.compile(r"&(?!(?:amp|lt|gt|quot|apos|#(?:x?[0-9a-fA-F]+|[0-9]+));)")
root = Path(r"E:\yixiaozi")
bad = []
for f in root.rglob("*.mm"):
    if "_data" in f.parts:
        continue
    try:
        s = f.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        continue
    if PAT.search(s):
        bad.append(f)
print("files_with_bare_amp", len(bad))
for b in bad[:40]:
    print(b)
