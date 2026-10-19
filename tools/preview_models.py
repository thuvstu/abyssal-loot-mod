"""開発用: モデルのプレビュー画像を作る。

    python tools/preview_models.py
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

from art import boxmodel, items, shapes

OUT = os.path.join("build", "model_preview")

TESTS = [
    ("abyss_sword", shapes.sword, 0xe9e3d5),
    ("abyss_axe", shapes.axe, 0xff8a2b),
    ("abyss_spear", shapes.spear, 0x5cc8ff),
    ("abyss_staff", shapes.staff, 0xc58bff),
    ("abyss_bow", shapes.bow, 0x7cffb2),
    ("abyss_hammer", shapes.hammer, 0xff6a7d),
    ("abyss_katana", shapes.katana, 0xff4d5e),
    ("abyss_scythe", shapes.scythe, 0xa0e87c),
    ("abyss_tome", shapes.tome, 0xe34bff),
    ("abyss_chakram", shapes.chakram, 0x7ce8ff),
    ("abyss_helm", shapes.helm, 0xb28ce0),
    ("abyss_chest", shapes.chest, 0xb28ce0),
]


def build(name, fn, color):
    p = shapes.make_palette(color)
    m = boxmodel.Model(name)
    fn(m, p)
    m.build_texture()
    return m


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    models = [(n, build(n, f, c)) for n, f, c in TESTS]

    # GUI 見た目 (傾きあり)
    tr = items.display("weapon")["gui"]
    cell = 200
    cols = 4
    rows = (len(models) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (34, 34, 46, 255))
    for i, (name, m) in enumerate(models):
        img = boxmodel.render_preview(m, "gui", cell, transform=tr)
        sheet.alpha_composite(img, ((i % cols) * cell, (i // cols) * cell))
    sheet.convert("RGB").save(os.path.join(OUT, "all_gui.png"))
    print("saved all_gui.png")

    # 正面 (傾きなし)
    flat = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (34, 34, 46, 255))
    for i, (name, m) in enumerate(models):
        img = boxmodel.render_preview(m, "gui", cell, transform=flat)
        sheet.alpha_composite(img, ((i % cols) * cell, (i // cols) * cell))
    sheet.convert("RGB").save(os.path.join(OUT, "all_flat.png"))
    print("saved all_flat.png")

    # テクスチャ (拡大)
    tex_sheet = Image.new("RGBA", (cols * 128, rows * 128), (34, 34, 46, 255))
    for i, (name, m) in enumerate(models):
        t = m.texture.resize((128, 128), Image.NEAREST)
        tex_sheet.alpha_composite(t, ((i % cols) * 128, (i // cols) * 128))
    tex_sheet.convert("RGB").save(os.path.join(OUT, "all_tex.png"))
    print("saved all_tex.png")

    # 一人称
    fptr = items.display("weapon")["firstperson_righthand"]
    for name, m in models[:4]:
        img = boxmodel.render_first_person(m, fptr, (720, 405))
        img.convert("RGB").save(os.path.join(OUT, f"fp_{name}.png"))
    print("saved fp_*.png")


if __name__ == "__main__":
    main()
