#!/usr/bin/env bash
# Safe mindmap backup — copy only. Does not stop Twigmark/Docear or deploy builds.
set -euo pipefail

SRC="${1:-}"
DST="${2:-}"

usage() {
  cat <<EOF
Usage: $0 <source-dir> [destination-dir]

Copies *.mm (and optional *.bak) from a Twigmark working directory into a
timestamped backup folder. Never launches or stops the application.

Examples:
  $0 "\$HOME/TwigmarkMaps"
  $0 "/path/to/maps" "/path/to/backups"
EOF
}

if [[ -z "$SRC" || "$SRC" == "-h" || "$SRC" == "--help" ]]; then
  usage
  exit 0
fi

if [[ ! -d "$SRC" ]]; then
  echo "Source directory not found: $SRC" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
if [[ -z "$DST" ]]; then
  DST="${SRC%/}/.twigmark-backups/backup-$STAMP"
else
  DST="${DST%/}/backup-$STAMP"
fi

mkdir -p "$DST"
count=0
while IFS= read -r -d '' f; do
  rel="${f#"$SRC"/}"
  mkdir -p "$DST/$(dirname "$rel")"
  cp -p "$f" "$DST/$rel"
  count=$((count+1))
done < <(find "$SRC" -type f \( -name '*.mm' -o -name '*.mm.bak' \) -print0 2>/dev/null)

# Write a small manifest for restore confidence
{
  echo "created=$STAMP"
  echo "source=$SRC"
  echo "files=$count"
} > "$DST/MANIFEST.txt"

echo "Backed up $count map file(s) -> $DST"
exit 0
