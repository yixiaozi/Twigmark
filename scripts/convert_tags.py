#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DISABLED / SAFE STUB.

The old convert_tags.py matched #... inside TEXT=\"...\" attributes and rewrote
them as 【tags】. That also matched Freeplane numeric character references:

    &#x65e5;  →  treated as hashtag #x65e5  →  &【x65e5】;
then replace_bracket_tags.py turned 【】 into []:

    &#x65e5;  →  &[x65e5];

which breaks XML parsing and makes maps refuse to open.

Do NOT re-enable bulk #→【 conversion on raw .mm files.
Use in-app tag UI (【tag】) instead.
"""
from __future__ import print_function
import sys


def main():
    print(
        "REFUSED: scripts/convert_tags.py is disabled.\n"
        "It previously corrupted &#x...; NCRs into &[x...]; and broke .mm files.\n"
        "See MindMapEncodingRepair / scripts/fix-broken-ncr-mm.py for repair.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
