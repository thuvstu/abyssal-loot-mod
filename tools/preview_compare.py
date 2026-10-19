"""2D ドット絵 と 3D モデル (GUI 視点) を並べて比較する (開発用)。"""
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

TARGETS = ["abyss_sword", "abyss_axe", "abyss_staff", "abyss_spear", "abyss_bow",
           "abyss_hammer", "abyss_katana", "abyss_scythe", "abyss_dagger", "abyss_chakram",
           "abyss_tome", "abyss_gauntlet", "abyss_pickaxe", "abyss_shovel", "abyss_wand",
           "abyss_greatsword", "abyss_ember_flail", "abyss_war_horn", "abyss_depth_gun", "abyss_tide_trident"]


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    cell = 120
    cols = 5
    rows = (len(TARGETS) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell * 2, rows * cell), (34, 34, 46, 255))
    d = ImageDraw.Draw(sheet)
    for i, item_id in enumerate(TARGETS):
        with io.open(os.path.join(MODEL_DIR, f"{item_id}.json"), encoding="utf-8") as f:
            data = json.load(f)
        ref = data["textures"]["0"]
        tex = Image.open(os.path.join(TEX_DIR, ref.split(":")[-1].split("/")[-1] + ".png")).convert("RGBA")
        m = boxmodel.Model.from_json(data, tex)
        tr = data.get("display", {}).get("gui")
        img3d = boxmodel.render_preview(m, "gui", cell, transform=tr)
        icon_path = os.path.join(TEX_DIR, f"{item_id}_icon.png")
        img2d = Image.new("RGBA", (cell, cell), (34, 34, 46, 255))
        if os.path.exists(icon_path):
            ic = Image.open(icon_path).convert("RGBA").resize((cell, cell), Image.NEAREST)
            img2d.alpha_composite(ic)
        x = (i % cols) * cell * 2
        y = (i // cols) * cell
        sheet.alpha_composite(img2d, (x, y))
        sheet.alpha_composite(img3d, (x + cell, y))
        d.text((x + 2, y + 2), item_id.replace("abyss_", ""), fill=(255, 255, 255, 220))
    sheet.convert("RGB").save(os.path.join(OUT, "compare_2d_3d.png"))
    print("saved compare_2d_3d.png")


if __name__ == "__main__":
    main()
