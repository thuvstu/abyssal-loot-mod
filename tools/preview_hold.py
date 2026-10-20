"""所持位置 (グリップ) の確認用プレビュー。

指定アイテムを一人称視点で並べ、手の位置 (display 変換の原点) に十字を描く。
グリップが十字に来ていれば「どのアイテムも同じ位置で握れている」ことになる。

使い方:
    python tools/preview_hold.py                     # 既定の代表アイテム
    python tools/preview_hold.py abyss_sword abyss_axe
"""
from __future__ import annotations

import io
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw  # noqa: E402

from art import boxmodel  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot", "models", "item")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot", "textures", "item")
OUT = os.path.join(ROOT, "build", "model_preview")

# MC の ItemInHandRenderer と同じ手の位置 (blocks)。
# 既定では画面の下端ぎりぎりになるので、確認しやすいよう少し上へずらす。
HAND_POS = (0.56, -0.42, -0.72)
FOV = 70.0

DEFAULT_IDS = [
    "abyss_sword", "abyss_greatsword", "abyss_katana", "abyss_dagger",
    "abyss_axe", "abyss_spear", "abyss_staff", "abyss_bow",
    "abyss_scythe", "abyss_tome", "abyss_gauntlet", "abyss_chakram",
    "abyss_venom_whip", "abyss_hammer", "abyss_shears", "abyss_war_horn",
    "abyss_howling_claw", "abyss_fishing_rod",
]


def load(item_id: str):
    with io.open(os.path.join(MODEL_DIR, f"{item_id}.json"), encoding="utf-8") as f:
        data = json.load(f)
    ref = data.get("textures", {}).get("0")
    tex = Image.open(os.path.join(TEX_DIR, ref.split("/")[-1] + ".png")).convert("RGBA")
    return boxmodel.Model.from_json(data, tex), data


def render(item_id: str, w: int, h: int) -> Image.Image:
    model, data = load(item_id)
    transform = data["display"]["firstperson_righthand"]
    img = boxmodel.render_first_person(model, transform, (w, h), fov=FOV, item_pos=HAND_POS)
    draw = ImageDraw.Draw(img)
    f = 1.0 / math.tan(math.radians(FOV / 2))
    sx = w / 2 + (HAND_POS[0] * f / (-HAND_POS[2])) / (w / h) * (w / 2)
    sy = h / 2 - (HAND_POS[1] * f / (-HAND_POS[2])) * (h / 2)
    draw.line([(sx - 10, sy), (sx + 10, sy)], fill=(255, 64, 64, 255))
    draw.line([(sx, sy - 10), (sx, sy + 10)], fill=(255, 64, 64, 255))
    draw.text((6, 4), item_id.replace("abyss_", ""), fill=(255, 255, 255, 230))
    return img


def main(ids: list[str]) -> None:
    if not ids:
        ids = DEFAULT_IDS
    w, h, cols = 480, 270, 3
    rows = (len(ids) + cols - 1) // cols
    sheet = Image.new("RGBA", (w * cols, h * rows), (34, 34, 46, 255))
    for i, item_id in enumerate(ids):
        img = render(item_id, w, h)
        sheet.alpha_composite(img, ((i % cols) * w, (i // cols) * h))
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "hold_preview.png")
    sheet.convert("RGB").save(path)
    print(path)


if __name__ == "__main__":
    main(sys.argv[1:])
