"""装備テクスチャをヒューマノイド模型に貼って確認する (開発用)。

    python tools/preview_equipment_mockup.py
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

from art import boxmodel
from art.boxmodel import Box, Model

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
EQP_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot",
                       "textures", "entity", "equipment", "humanoid")
OUT = os.path.join(ROOT, "build", "model_preview")


def _net(u, v, w, h, d):
    """MC の箱ネット UV (texel 単位)。"""
    return {
        "down": (u + d, v, u + d + w, v + d),
        "up": (u + d + w, v, u + d + 2 * w, v + d),
        "east": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "west": (u + d + w, v + d, u + d + w + d, v + d + h),
        "south": (u + d + w + d, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def build_humanoid(texture: Image.Image, leggings: bool = False) -> Model:
    """装備の上半身 (頭・胴・腕) または脚做成り。"""
    m = Model("humanoid")
    parts = []
    if not leggings:
        # 絶対座標 (MC の HumanoidModel.createMesh と同じ組合せ)
        parts.append(("head", (0, 0), (-4, -8, -4), (8, 8, 8)))
        parts.append(("body", (16, 16), (-4, 0, -2), (8, 12, 4)))
        parts.append(("arm_r", (40, 16), (-8, 0, -2), (4, 12, 4)))
        parts.append(("arm_l", (48, 16), (2, 0, -2), (4, 12, 4)))
    else:
        parts.append(("leg_r", (0, 16), (-4, 12, -2), (4, 12, 4)))
        parts.append(("leg_l", (16, 16), (0, 12, -2), (4, 12, 4)))
    for name, (u, v), org, size in parts:
        w, h, d = size
        b = Box(name=name, frm=org, to=(org[0] + w, org[1] + h, org[2] + d),
                mat=None)
        m.boxes.append(b)
        for face, rect in _net(u, v, w, h, d).items():
            m.uv[(len(m.boxes) - 1, face)] = rect
    m.texture = texture
    m.texture_size = texture.width
    return m


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    sets = ["abyssal", "blood", "frost", "shadow"]
    cell = (200, 300)
    sheet = Image.new("RGBA", (len(sets) * cell[0], 2 * cell[1]), (34, 34, 46, 255))
    for i, name in enumerate(sets):
        tex = Image.open(os.path.join(EQP_DIR, f"{name}.png")).convert("RGBA")
        m = build_humanoid(tex, leggings=False)
        img = boxmodel.render_preview(m, "front", cell[1], transform={
            "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]})
        sheet.alpha_composite(img, (i * cell[0], 0))
        tex2 = Image.open(os.path.join(EQP_DIR, f"{name}_leggings.png")).convert("RGBA")
        m2 = build_humanoid(tex2, leggings=True)
        img2 = boxmodel.render_preview(m2, "front", cell[1], transform={
            "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]})
        sheet.alpha_composite(img2, (i * cell[0], cell[1]))
    sheet.convert("RGB").save(os.path.join(OUT, "equipment_mockup.png"))
    print("saved equipment_mockup.png")


if __name__ == "__main__":
    main()
