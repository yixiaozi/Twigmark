# -*- coding: utf-8 -*-
"""
Repair Freeplane .mm files that embed UTF-16 surrogate NCRs.

Freeplane/HTML sometimes stores emoji as &#55356;&#57119; (or &#xD83C;&#xDF1F;)
or HTML-escaped &amp;#55356;&amp;#57119;. Strict SAX (HtmlUtils.isWellformedXml)
rejects surrogates → map open looks broken / blank.

Convert valid high+low pairs to a single &#x1Fxxx; (or &amp;#x1Fxxx; inside HTML).
Drop illegal lone surrogates / &#x0; / &#xb; etc.
"""
from __future__ import print_function
import argparse
import os
import re
import shutil
from pathlib import Path

DEC_PAIR = re.compile(r"&#(\d+);&#(\d+);")
HEX_PAIR = re.compile(r"&#x([0-9a-fA-F]+);&#x([0-9a-fA-F]+);", re.I)
AMP_DEC_PAIR = re.compile(r"&amp;#(\d+);&amp;#(\d+);")
AMP_HEX_PAIR = re.compile(r"&amp;#x([0-9a-fA-F]+);&amp;#x([0-9a-fA-F]+);", re.I)

DEC_ONE = re.compile(r"&#(\d+);")
HEX_ONE = re.compile(r"&#x([0-9a-fA-F]+);", re.I)
AMP_DEC_ONE = re.compile(r"&amp;#(\d+);")
AMP_HEX_ONE = re.compile(r"&amp;#x([0-9a-fA-F]+);", re.I)


def is_xml10(cp):
    return (
        cp in (0x9, 0xA, 0xD)
        or (0x20 <= cp <= 0xD7FF)
        or (0xE000 <= cp <= 0xFFFD)
        or (0x10000 <= cp <= 0x10FFFF)
    )


def to_codepoint(hi, lo):
    if 0xD800 <= hi <= 0xDBFF and 0xDC00 <= lo <= 0xDFFF:
        return 0x10000 + ((hi - 0xD800) << 10) + (lo - 0xDC00)
    return None


def repl_dec_pair(m, amp=False):
    cp = to_codepoint(int(m.group(1)), int(m.group(2)))
    if cp is None or not is_xml10(cp):
        return m.group(0)
    body = "&#x%x;" % cp
    return ("&amp;#x%x;" % cp) if amp else body


def repl_hex_pair(m, amp=False):
    cp = to_codepoint(int(m.group(1), 16), int(m.group(2), 16))
    if cp is None or not is_xml10(cp):
        return m.group(0)
    return ("&amp;#x%x;" % cp) if amp else ("&#x%x;" % cp)


def drop_illegal_one(m, amp=False, hexmode=False):
    raw = m.group(1)
    cp = int(raw, 16) if hexmode else int(raw, 10)
    if is_xml10(cp):
        return m.group(0)
    return ""


def repair_text(text):
    original = text
    text = text.replace("&[x", "&#x").replace("&[X", "&#x")

    text = AMP_DEC_PAIR.sub(lambda m: repl_dec_pair(m, True), text)
    text = AMP_HEX_PAIR.sub(lambda m: repl_hex_pair(m, True), text)
    text = DEC_PAIR.sub(lambda m: repl_dec_pair(m, False), text)
    text = HEX_PAIR.sub(lambda m: repl_hex_pair(m, False), text)

    # drop remaining illegal singles (surrogates, NUL, C0, etc.)
    text = AMP_DEC_ONE.sub(lambda m: drop_illegal_one(m, True, False), text)
    text = AMP_HEX_ONE.sub(lambda m: drop_illegal_one(m, True, True), text)
    text = DEC_ONE.sub(lambda m: drop_illegal_one(m, False, False), text)
    text = HEX_ONE.sub(lambda m: drop_illegal_one(m, False, True), text)

    return text, text != original


def needs_repair(text):
    if "&[x" in text or "&[X" in text:
        return True
    if AMP_DEC_PAIR.search(text) or AMP_HEX_PAIR.search(text):
        return True
    if DEC_PAIR.search(text) or HEX_PAIR.search(text):
        # only if actually surrogates
        for m in DEC_PAIR.finditer(text):
            if to_codepoint(int(m.group(1)), int(m.group(2))) is not None:
                return True
        for m in HEX_PAIR.finditer(text):
            if to_codepoint(int(m.group(1), 16), int(m.group(2), 16)) is not None:
                return True
    for m in DEC_ONE.finditer(text):
        if not is_xml10(int(m.group(1))):
            return True
    for m in HEX_ONE.finditer(text):
        if not is_xml10(int(m.group(1), 16)):
            return True
    for m in AMP_DEC_ONE.finditer(text):
        if not is_xml10(int(m.group(1))):
            return True
    for m in AMP_HEX_ONE.finditer(text):
        if not is_xml10(int(m.group(1), 16)):
            return True
    return False


def repair_file(path, backup=True):
    path = Path(path)
    raw = path.read_bytes()
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        text = raw.decode("gbk")
    if not needs_repair(text):
        return False, "unchanged"
    fixed, _ = repair_text(text)
    # validate XML still parses
    from xml.parsers import expat

    try:
        expat.ParserCreate().Parse(fixed.encode("utf-8"), True)
    except Exception as e:
        return False, "expat-fail-after-repair: " + str(e)
    if backup:
        bak = path.with_name(path.name + ".bak-surrogate-ncr")
        if not bak.exists():
            shutil.copy2(str(path), str(bak))
    path.write_bytes(fixed.encode("utf-8"))
    return True, "repaired"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("root", nargs="?", default=r"E:\yixiaozi")
    args = ap.parse_args()
    root = Path(args.root)
    changed = []
    failed = []
    scanned = 0
    for dp, dns, fns in os.walk(str(root)):
        dns[:] = [d for d in dns if not d.startswith(".") and d not in ("bin", "_data")]
        for fn in fns:
            if not fn.lower().endswith(".mm"):
                continue
            if fn.startswith("~") or "冲突" in fn or ".bak" in fn.lower():
                continue
            scanned += 1
            p = os.path.join(dp, fn)
            try:
                ok, msg = repair_file(p)
            except Exception as e:
                failed.append((p, str(e)))
                continue
            if ok:
                changed.append(p)
                print("REPAIRED", p)
            elif msg.startswith("expat-fail"):
                failed.append((p, msg))
                print("FAIL", msg, p)
    print("scanned", scanned, "repaired", len(changed), "failed", len(failed))
    for p, msg in failed[:20]:
        print("FAIL", msg, p)


if __name__ == "__main__":
    main()
