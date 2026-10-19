"""一人称視点のプレビューを複数まとめて作る (開発用)。"""
from __future__ import annotations

import io
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

from art import boxmodel

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot")
MODEL_DIR = os.path.join(ASSETS, "models", "item")
OUT = os.path.join(ROOT, "build", "model_preview")

TARGETS = ["abyss_sword", "abyss_axe", "abyss_spear", "abyss_staff", "abyss_bow",
           "abyss_hammer", "abyss_katana", "abyss_scythe", "abyss_tome", "abyss_chakram",
           "abyss_gauntlet", "abyss_greatsword"]


def load(item_id: str):
    with io.open(os.path.join(MODEL_DIR, f"{item_id}.json"), encoding="utf-8") as f:
        data = json.load(f)
    ref = data.get("textures", {}).get("0")
    tex = None
    if ref:
        rel = ref.split(":", 1)[-1]
        p = os.path.join(ASSETS, "textures", rel.replace("/", os.sep) + ".png")
        if os.path.exists(p):
            tex = Image.open(p).convert("RGBA")
    return boxmodel.Model.from_json(data, tex), data


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    cell = (360, 202)
    cols = 3
    ids = [t for t in TARGETS if os.path.exists(os.path.join(MODEL_DIR, f"{t}.json"))]
    rows = (len(ids) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell[0], rows * cell[1]), (34, 34, 46, 255))
    for i, item_id in enumerate(ids):
        m, data = load(item_id)
        tr = data.get("display", {}).get("firstperson_righthand")
        img = boxmodel.render_first_person(m, tr, cell)
        sheet.alpha_composite(img, ((i % cols) * cell[0], (i // cols) * cell[1]))
    sheet.convert("RGB").save(os.path.join(OUT, "contact_firstperson.png"))
    print("saved contact_firstperson.png", len(ids))


if __name__ == "__main__":
    main()
