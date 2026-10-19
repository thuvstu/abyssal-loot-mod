"""2D ドット絵 (インベントリ表示用アイコン) のコンタクトシート (開発用)。"""
from __future__ import annotations

import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot", "textures", "item")
OUT = os.path.join(ROOT, "build", "model_preview")


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    names = sorted(f[:-4] for f in os.listdir(TEX_DIR) if f.endswith("_icon.png"))
    cell = 96
    cols = 12
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (34, 34, 46, 255))
    for i, name in enumerate(names):
        img = Image.open(os.path.join(TEX_DIR, f"{name}.png")).convert("RGBA")
        img = img.resize((cell, cell), Image.NEAREST)
        sheet.alpha_composite(img, ((i % cols) * cell, (i // cols) * cell))
    sheet.convert("RGB").save(os.path.join(OUT, "contact_icons.png"))
    print("saved contact_icons.png", len(names))


if __name__ == "__main__":
    main()
