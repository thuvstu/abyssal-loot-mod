"""全武器の一人称ビューを並べて確認する (開発用)。"""
from __future__ import annotations

import io
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw

from art import boxmodel

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
MODEL_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot", "models", "item")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot", "textures", "item")
OUT = os.path.join(ROOT, "build", "model_preview")


def load(item_id: str):
    with io.open(os.path.join(MODEL_DIR, f"{item_id}.json"), encoding="utf-8") as f:
        data = json.load(f)
    ref = data.get("textures", {}).get("0") or data.get("textures", {}).get("layer0")
    tex = None
    if ref:
        p = os.path.join(TEX_DIR, ref.split(":")[-1].split("/")[-1] + ".png")
        if os.path.exists(p):
            tex = Image.open(p).convert("RGBA")
    return boxmodel.Model.from_json(data, tex), data


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    ids = sorted(f[:-5] for f in os.listdir(MODEL_DIR)
                 if f.endswith(".json") and not f.endswith("_icon.json"))
    cell = 200
    cols = 8
    rows = (len(ids) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (34, 34, 46, 255))
    d = ImageDraw.Draw(sheet)
    for i, item_id in enumerate(ids):
        m, data = load(item_id)
        tr = data.get("display", {}).get("firstperson_righthand") or {
            "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
        img = boxmodel.render_first_person(m, tr, (cell, cell))
        sheet.alpha_composite(img, ((i % cols) * cell, (i // cols) * cell))
        d.text(((i % cols) * cell + 3, (i // cols) * cell + 3), item_id.replace("abyss_", ""),
               fill=(255, 255, 255, 200))
    sheet.convert("RGB").save(os.path.join(OUT, "contact_firstperson_all.png"))
    print("saved contact_firstperson_all.png", len(ids))


if __name__ == "__main__":
    main()
