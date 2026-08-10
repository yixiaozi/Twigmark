#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DISABLED — see scripts/convert_tags.py."""
import sys

print(
    "REFUSED: convert_tags.py disabled. It corrupted &#x...; NCRs into &[x...]; inside .mm files.",
    file=sys.stderr,
)
raise SystemExit(2)
