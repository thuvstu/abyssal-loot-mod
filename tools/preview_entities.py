"""深淵MOB のテクスチャのコンタクトシートを作る (開発用)。

    python tools/preview_entities.py
"""
from __future__ import annotations

import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ENT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot", "textures", "entity")
OUT = os.path.join(ROOT, "build", "model_preview")


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    names = sorted(f[:-4] for f in os.listdir(ENT_DIR) if f.endswith(".png"))
    cell = 160
    cols = 7
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (34, 34, 46, 255))
    for i, name in enumerate(names):
        img = Image.open(os.path.join(ENT_DIR, f"{name}.png")).convert("RGBA")
        img = img.resize((cell, cell), Image.NEAREST)
        sheet.alpha_composite(img, ((i % cols) * cell, (i // cols) * cell))
    sheet.convert("RGB").save(os.path.join(OUT, "contact_entities.png"))
    print("saved contact_entities.png", len(names))


if __name__ == "__main__":
    main()
