# -*- coding: utf-8 -*-
"""Scan .mm files with xml.parsers.expat (SAX-strict) and known corruption patterns."""
from __future__ import print_function
import os
import re
import sys
from xml.parsers import expat

ROOT = sys.argv[1] if len(sys.argv) > 1 else r"E:\yixiaozi"


def is_xml10_char(cp):
    return (
        cp in (0x9, 0xA, 0xD)
        or (0x20 <= cp <= 0xD7FF)
        or (0xE000 <= cp <= 0xFFFD)
        or (0x10000 <= cp <= 0x10FFFF)
    )


def scan_file(path):
    issues = []
    try:
        with open(path, "rb") as f:
            raw = f.read()
    except Exception as e:
        return ["read:" + str(e)]
    if len(raw) > 50 * 1024 * 1024:
        return ["too_large"]
    try:
        text = raw.decode("utf-8")
    except Exception:
        try:
            text = raw.decode("gbk")
            issues.append("encoding:gbk")
        except Exception as e:
            return ["decode:" + str(e)]

    if "&[x" in text or "&[X" in text:
        issues.append("&[x")
    if "&#x0;" in text or "&#X0;" in text or "&#0;" in text:
        issues.append("nullref")
    for m in re.finditer(r"&#x([0-9a-fA-F]+);", text):
        cp = int(m.group(1), 16)
        if not is_xml10_char(cp):
            issues.append("badncr:&#x%s;" % m.group(1))
            break
    for m in re.finditer(r"&#([0-9]+);", text):
        cp = int(m.group(1), 10)
        if not is_xml10_char(cp):
            issues.append("badncr:&#%s;" % m.group(1))
            break
    m = re.search(r"&(?!(#|[A-Za-z][A-Za-z0-9._:-]*;))", text)
    if m:
        snippet = text[m.start() : m.start() + 32].replace("\n", " ")
        issues.append("bare&:" + snippet)

    parser = expat.ParserCreate()
    parser.buffer_text = True
    try:
        parser.Parse(text.encode("utf-8"), True)
    except expat.ExpatError as e:
        issues.append("expat:" + str(e))

    return issues


def main():
    fail = []
    warn = []
    for dp, dns, fns in os.walk(ROOT):
        dns[:] = [d for d in dns if not d.startswith(".") and d not in ("bin", "_data")]
        for fn in fns:
            if not fn.lower().endswith(".mm"):
                continue
            if fn.startswith("~") or "冲突" in fn or ".bak" in fn.lower():
                continue
            path = os.path.join(dp, fn)
            issues = scan_file(path)
            if not issues:
                continue
            fatal = any(
                i.startswith("expat:")
                or i.startswith("&[x")
                or i.startswith("bare&")
                or i.startswith("read:")
                or i.startswith("decode:")
                for i in issues
            )
            row = (path, issues)
            if fatal:
                fail.append(row)
            else:
                warn.append(row)

    print("FATAL", len(fail))
    for path, issues in fail:
        print("FAIL\t%s\t%s" % (",".join(issues), path))
    print("WARN", len(warn))
    for path, issues in warn[:80]:
        print("WARN\t%s\t%s" % (",".join(issues), path))


if __name__ == "__main__":
    main()
