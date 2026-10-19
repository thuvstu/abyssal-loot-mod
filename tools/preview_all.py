"""生成済みアイテムモデルのコンタクトシートを作る (開発用)。

    python tools/preview_all.py
"""
from __future__ import annotations

import io
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

from art import boxmodel, items

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot")
MODEL_DIR = os.path.join(ASSETS, "models", "item")
TEX_DIR = os.path.join(ASSETS, "textures", "item")
OUT = os.path.join(ROOT, "build", "model_preview")


def load(item_id: str):
    with io.open(os.path.join(MODEL_DIR, f"{item_id}.json"), encoding="utf-8") as f:
        data = json.load(f)
    tex = None
    ref = data.get("textures", {}).get("0") or data.get("textures", {}).get("layer0")
    if ref:
        rel = ref.split(":", 1)[-1]
        tex_path = os.path.join(ROOT, "src", "main", "resources", "assets",
                                "abyssalloot", "textures", rel.replace("/", os.sep) + ".png")
        if os.path.exists(tex_path):
            tex = Image.open(tex_path).convert("RGBA")
    return boxmodel.Model.from_json(data, tex), data


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    ids = sorted(f[:-5] for f in os.listdir(MODEL_DIR) if f.endswith(".json"))
    cell = 120
    cols = 10
    rows = (len(ids) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (34, 34, 46, 255))
    flat = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
    for i, item_id in enumerate(ids):
        try:
            m, data = load(item_id)
        except Exception as exc:  # noqa: BLE001
            print("skip", item_id, exc)
            continue
        if m.texture is not None and not m.boxes:
            # 2D アイコン (item/generated)
            img = Image.new("RGBA", (cell, cell), (34, 34, 46, 255))
            t = m.texture.resize((cell, cell), Image.NEAREST)
            img.alpha_composite(t)
        else:
            tr = data.get("display", {}).get("gui", flat)
            img = boxmodel.render_preview(m, "gui", cell, transform=tr)
        sheet.alpha_composite(img, ((i % cols) * cell, (i // cols) * cell))
    sheet.convert("RGB").save(os.path.join(OUT, "contact_gui.png"))
    print("saved contact_gui.png", len(ids), "items")


if __name__ == "__main__":
    main()
