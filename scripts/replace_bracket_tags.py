#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DISABLED / SAFE STUB.

Old script replaced 【】 / &#x3010;&#x3011; with ASCII [] across whole .mm files.
Combined with convert_tags.py this produced illegal entities:

    &#x65e5;  →  &【x65e5】;  →  &[x65e5];

Do NOT run bulk bracket replacement on encoded .mm XML.
"""
from __future__ import print_function
import sys


def main():
    print(
        "REFUSED: scripts/replace_bracket_tags.py is disabled.\n"
        "It previously helped corrupt &#x...; into &[x...]; inside .mm files.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
