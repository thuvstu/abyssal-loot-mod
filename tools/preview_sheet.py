"""指定カテゴリのモデルを GUI 表示で一覧シートに描く (目視確認用)。

使い方:
    python tools/preview_sheet.py weapons [start] [count]
    python tools/preview_sheet.py armor
    python tools/preview_sheet.py materials
"""
from __future__ import annotations

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw

from art import boxmodel
from generate_textures import WEAPON_MODELS, ARMOR_MODELS, MATERIAL_MODELS

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, "src/main/resources/assets/abyssalloot/models/item")
TEX = os.path.join(ROOT, "src/main/resources/assets/abyssalloot/textures/item")
OUT = os.path.join(ROOT, "build/model_preview")


def render(name: str, size: int = 200) -> Image.Image:
    with open(os.path.join(MODELS, name + ".json"), encoding="utf-8") as fh:
        d = json.load(fh)
    ref = d["textures"]["0"]
    tex = Image.open(os.path.join(TEX, ref.split(":")[-1].split("/")[-1] + ".png")).convert("RGBA")
    m = boxmodel.Model.from_json(d, tex)
    im = boxmodel.render_preview(m, "gui", size, transform=d["display"]["gui"])
    dr = ImageDraw.Draw(im)
    dr.text((3, 3), name.replace("abyss_", ""), fill=(235, 235, 245, 220))
    return im


def sheet(names, cols: int, cell: int, path: str) -> None:
    rows = (len(names) + cols - 1) // cols
    img = Image.new("RGBA", (cols * cell, rows * cell), (34, 34, 46, 255))
    for i, n in enumerate(names):
        img.alpha_composite(render(n, cell), ((i % cols) * cell, (i // cols) * cell))
    img.convert("RGB").save(path)
    print(path, f"{cols * cell}x{rows * cell}", len(names), "models")


def main() -> None:
    cat = sys.argv[1] if len(sys.argv) > 1 else "weapons"
    table = {"weapons": WEAPON_MODELS, "armor": ARMOR_MODELS, "materials": MATERIAL_MODELS}[cat]
    names = list(table)
    start = int(sys.argv[2]) if len(sys.argv) > 2 else 0
    count = int(sys.argv[3]) if len(sys.argv) > 3 else len(names)
    names = names[start:start + count]
    cols = 6
    cell = 200
    sheet(names, cols, cell, os.path.join(OUT, f"sheet_{cat}_{start}.png"))


if __name__ == "__main__":
    main()
