#!/usr/bin/env python3
"""Generate Twigmark icons with macOS safe-zone padding (~84% scale, centered)."""
from __future__ import print_function

import os
import struct
import sys

try:
    from PIL import Image
except ImportError:
    sys.stderr.write("Pillow required: pip install pillow\n")
    sys.exit(1)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "docear_plugin_core", "resources", "images", "twigmark_app_icon_source.png")
FALLBACK_SRC = os.path.join(ROOT, "docear_plugin_core", "resources", "images", "twigmark_app_icon.png")
# Apple HIG: keep artwork inside ~80–86% of 1024 canvas so Dock/Launchpad matches peers.
SAFE_SCALE = 0.84
CANVAS = 1024

OUTPUTS = {
    "master1024": os.path.join(ROOT, "docear_plugin_core", "resources", "images", "twigmark_app_icon.png"),
    "script256": os.path.join(ROOT, "docear_framework", "script", "twigmark.png"),
    "docs": os.path.join(ROOT, "docs", "assets", "twigmark.png"),
    "tw16a": os.path.join(ROOT, "docear_plugin_core", "resources", "images", "twigmark16.png"),
    "tw32a": os.path.join(ROOT, "docear_plugin_core", "resources", "images", "twigmark32.png"),
    "tw16b": os.path.join(ROOT, "freeplane", "resources", "images", "twigmark16.png"),
    "tw32b": os.path.join(ROOT, "freeplane", "resources", "images", "twigmark32.png"),
    "icns": os.path.join(ROOT, "docear_framework", "mac-jarbundler", "twigmark.icns"),
    "ico": os.path.join(ROOT, "docear_framework", "docear_launch4j", "twigmark.ico"),
}


def padded_icon(source_path, canvas_size):
    src = Image.open(source_path).convert("RGBA")
    inner = max(1, int(round(canvas_size * SAFE_SCALE)))
    scaled = src.resize((inner, inner), Image.LANCZOS)
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    offset = (canvas_size - inner) // 2
    canvas.paste(scaled, (offset, offset), scaled)
    return canvas


def save_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG")
    print("wrote", path)


def save_ico(img1024, path):
    sizes = [16, 24, 32, 48, 64, 128, 256]
    frames = [img1024.resize((s, s), Image.LANCZOS) for s in sizes]
    os.makedirs(os.path.dirname(path), exist_ok=True)
    frames[0].save(
        path,
        format="ICO",
        sizes=[(s, s) for s in sizes],
        append_images=frames[1:],
    )
    print("wrote", path)


def save_icns(img1024, path):
    try:
        import icnsutil
    except ImportError:
        save_icns_manual(img1024, path)
        return
    icns = icnsutil.IcnsFile()
    pairs = [
        ("icp4", 16),
        ("icp5", 32),
        ("icp6", 64),
        ("ic07", 128),
        ("ic08", 256),
        ("ic09", 512),
        ("ic10", 1024),
    ]
    for key, size in pairs:
        frame = img1024.resize((size, size), Image.LANCZOS)
        icns.add_media(key, data=_png_bytes(frame))
    icns.write(path)
    print("wrote", path)


def _png_bytes(img):
    import io
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def save_icns_manual(img1024, path):
    """Minimal icns writer (PNG in ic09/ic10) when icnsutil unavailable."""
    entries = []
    for size, key in [(512, b"ic09"), (1024, b"ic10")]:
        frame = img1024.resize((size, size), Image.LANCZOS)
        data = _png_bytes(frame)
        entries.append((key, data))
    body = b""
    for key, data in entries:
        length = 8 + len(data)
        body += key + struct.pack(">I", length) + data
    header = b"icns" + struct.pack(">I", 8 + len(body))
    with open(path, "wb") as f:
        f.write(header + body)
    print("wrote", path, "(minimal icns)")


def main():
    source = SRC if os.path.isfile(SRC) else FALLBACK_SRC
    if not os.path.isfile(source):
        sys.stderr.write("missing source: " + source + "\n")
        sys.exit(1)
    master = padded_icon(source, CANVAS)
    save_png(master, OUTPUTS["master1024"])
    save_png(master.resize((256, 256), Image.LANCZOS), OUTPUTS["script256"])
    save_png(master.resize((256, 256), Image.LANCZOS), OUTPUTS["docs"])
    save_png(master.resize((16, 16), Image.LANCZOS), OUTPUTS["tw16a"])
    save_png(master.resize((32, 32), Image.LANCZOS), OUTPUTS["tw32a"])
    save_png(master.resize((16, 16), Image.LANCZOS), OUTPUTS["tw16b"])
    save_png(master.resize((32, 32), Image.LANCZOS), OUTPUTS["tw32b"])
    save_icns(master, OUTPUTS["icns"])
    save_ico(master, OUTPUTS["ico"])
    # Legacy alias used by some docs/scripts
    legacy = os.path.join(ROOT, "docear_framework", "mac-jarbundler", "docear.icns")
    if os.path.isfile(OUTPUTS["icns"]):
        import shutil
        shutil.copy2(OUTPUTS["icns"], legacy)
        print("wrote", legacy, "(copy of twigmark.icns)")


if __name__ == "__main__":
    main()
