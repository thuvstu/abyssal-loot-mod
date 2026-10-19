"""装備テクスチャ (64x32) のコンタクトシートを作る (開発用)。"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ENT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot",
                        "textures", "entity", "equipment", "humanoid")
OUT = os.path.join(ROOT, "build", "model_preview")


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    names = sorted(f[:-4] for f in os.listdir(ENT_DIR) if f.endswith(".png"))
    cell = (256, 128)
    cols = 4
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell[0], rows * cell[1]), (34, 34, 46, 255))
    for i, name in enumerate(names):
        img = Image.open(os.path.join(ENT_DIR, f"{name}.png")).convert("RGBA")
        img = img.resize((cell[0], cell[1]), Image.NEAREST)
        sheet.alpha_composite(img, ((i % cols) * cell[0], (i // cols) * cell[1]))
    sheet.convert("RGB").save(os.path.join(OUT, "contact_equipment.png"))
    print("saved contact_equipment.png", len(names))


if __name__ == "__main__":
    main()
