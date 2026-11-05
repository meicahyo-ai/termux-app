#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SRC="$HERE/ai_bridge.py"
DEST_DIR="$HOME/.termux-lab"
DEST="$DEST_DIR/ai_bridge.py"

if [ ! -f "$SRC" ]; then
    echo "ERROR: Source AI Bridge tidak ditemukan."
    exit 1
fi

python - "$SRC" <<'PYCODE'
import ast
import sys
from pathlib import Path

source = Path(sys.argv[1]).read_text(encoding="utf-8")
ast.parse(source)

print("Python syntax: VALID")
PYCODE

mkdir -p "$DEST_DIR"

if [ -f "$DEST" ] && cmp -s "$SRC" "$DEST"; then
    echo "AI Bridge sudah menggunakan versi yang sama."
    exit 0
fi

if [ -f "$DEST" ]; then
    BACKUP="$DEST.bak-$(date +%Y%m%d-%H%M%S)-$$"
    cp -p "$DEST" "$BACKUP"
    echo "Backup: $BACKUP"
fi

TMP="$(mktemp "$DEST_DIR/.ai_bridge.tmp.XXXXXX")"
trap 'rm -f "$TMP"' EXIT

cp "$SRC" "$TMP"
chmod 600 "$TMP"

mv -f "$TMP" "$DEST"

echo "AI Bridge berhasil diperbarui."
echo "STATUS=BRIDGE_INSTALLED"
