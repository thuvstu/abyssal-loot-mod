"""マテリアル定義と「面」を塗る処理。

(モデル, 素材) の組合せで 1 つの面がどう塗られるかを決める。
すべて「面のローカル座標」で描き、隣接面同士の継ぎ目が目立たないよう
グラデーションは面内の向きに合わせる。
"""
from __future__ import annotations

import random
from dataclasses import dataclass, field

from PIL import Image, ImageDraw

from . import core
from .core import RGBA, mix, shade

# 面の方向 (MC の Direction に対応)
DOWN, UP, NORTH, SOUTH, WEST, EAST = "down", "up", "north", "south", "west", "east"

# 面ごとの基本明暗 (MC のライティングが主役なので here は控えめに)
FACE_TONE = {
    UP: 1.06,
    DOWN: 0.82,
    NORTH: 0.97,
    SOUTH: 0.93,
    WEST: 0.88,
    EAST: 0.91,
}


@dataclass
class Material:
    kind: str
    base: RGBA
    accent: RGBA | None = None
    emissive: bool = False
    metal: float = 0.0
    rough: float = 0.6
    tags: tuple = field(default_factory=tuple)

    def toned(self, face: str, amount: float = 1.0) -> RGBA:
        return shade(self.base, FACE_TONE.get(face, 1.0) ** amount)


def _rnd(seed: int) -> random.Random:
    return random.Random(seed)


# ------------------------------------------------------------------ 材質の描画

def _paint_metal(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    """金属は面の上下に反射が移る (上側が明るい)"""
    top = shade(base, 1.12)
    bot = shade(base, 0.46)
    core.vgradient(img, box, top, bot)
    # ハイライト帯
    if w >= 3 and h >= 3:
        hl = shade(base, 1.34)
        yh = y0 + max(0, int(h * 0.22))
        d.line([(x0 + 1, yh), (x1 - 2, yh)], fill=hl)
        if h > 12:
            d.line([(x0 + 1, yh + 1), (x1 - 2, yh + 1)], fill=mix(base, hl, 0.45))
    # 研磨傷
    rnd = _rnd(seed)
    for _ in range(max(1, int(w * h * 0.025))):
        x = rnd.randrange(x0, x1)
        y = rnd.randrange(y0, y1)
        c = shade(base, rnd.choice([0.72, 0.86, 1.18]))
        d.point((x, y), fill=c)
    if mat.accent:
        # 装飾のライン (端に 1 ピクセル)
        d.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=mat.accent)


def _paint_wood(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.16), shade(base, 0.72))
    rnd = _rnd(seed)
    for _ in range(max(1, int(w * 0.8))):
        x = rnd.randrange(x0, x1)
        c = shade(base, rnd.choice([0.78, 0.88, 1.12]))
        d.line([(x, y0), (x, y1 - 1)], fill=c)
    # 節
    if w > 4 and h > 4 and rnd.random() < 0.5:
        kx = rnd.randrange(x0 + 1, x1 - 1)
        ky = rnd.randrange(y0 + 1, y1 - 1)
        d.ellipse([kx - 1, ky - 1, kx + 1, ky + 1], outline=shade(base, 0.6))


def _paint_leather(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.12), shade(base, 0.74))
    rnd = _rnd(seed)
    core.noise(img, box, rnd, [shade(base, 0.85), shade(base, 1.14)], density=0.16)
    if h > 6:
        d.line([(x0, y0 + 1), (x1 - 1, y0 + 1)], fill=shade(base, 0.6))


def _paint_cloth(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.14), shade(base, 0.76))
    # 織り目
    for y in range(y0, y1):
        if (y - y0) % 2 == 0:
            d.line([(x0, y), (x1 - 1, y)], fill=shade(base, 0.9))
    for x in range(x0, x1):
        if (x - x0) % 3 == 0:
            d.line([(x, y0), (x, y1 - 1)], fill=shade(base, 1.08))
    if mat.accent:
        d.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=mat.accent)


def _paint_crystal(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.35), shade(base, 0.68))
    # 内部の芯 (発光)
    if w >= 2 and h >= 2:
        cx0, cy0 = x0 + w // 4, y0 + h // 4
        cx1, cy1 = x0 + (w * 3) // 4, y0 + (h * 3) // 4
        core.vgradient(img, (cx0, cy0, cx1, cy1), shade(base, 1.55), shade(base, 0.95))
    # ファセットの斜線
    rnd = _rnd(seed)
    for _ in range(max(1, int(w * h * 0.03))):
        x = rnd.randrange(x0, x1)
        y = rnd.randrange(y0, y1)
        d.line([(x, y), (min(x1 - 1, x + 2), max(y0, y - 2))], fill=shade(base, 1.3))
    if w >= 3 and h >= 3:
        d.point((x0 + 1, y0 + 1), fill=(255, 255, 255, 230))


def _paint_ice(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.28), shade(base, 0.82))
    rnd = _rnd(seed)
    for _ in range(max(1, int(w * h * 0.04))):
        x = rnd.randrange(x0, x1)
        y = rnd.randrange(y0, y1)
        d.line([(x, y), (min(x1 - 1, x + 3), min(y1 - 1, y + 3))], fill=shade(base, 1.35))
    if w >= 3 and h >= 3:
        d.line([(x0 + 1, y0 + 1), (x1 - 2, y0 + 1)], fill=(255, 255, 255, 190))


def _paint_bone(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.1), shade(base, 0.74))
    rnd = _rnd(seed)
    core.noise(img, box, rnd, [shade(base, 0.84), shade(base, 1.1)], density=0.1)


def _paint_stone(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.08), shade(base, 0.72))
    rnd = _rnd(seed)
    core.noise(img, box, rnd, [shade(base, 0.78), shade(base, 1.14), shade(base, 0.92)], density=0.2)
    if w > 5 and h > 5 and rnd.random() < 0.6:
        x = rnd.randrange(x0 + 1, x1 - 2)
        y = rnd.randrange(y0 + 1, y1 - 2)
        d.line([(x, y), (x + 2, y + 3)], fill=shade(base, 0.6))


def _paint_obsidian(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.9), shade(base, 0.7))
    rnd = _rnd(seed)
    for _ in range(max(1, int(w * h * 0.02))):
        x = rnd.randrange(x0, x1)
        y = rnd.randrange(y0, y1)
        d.point((x, y), fill=mix(base, mat.accent or (120, 90, 200, 255), 0.6))


def _paint_paper(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.06), shade(base, 0.86))
    rnd = _rnd(seed)
    core.noise(img, box, rnd, [shade(base, 0.94), shade(base, 1.04)], density=0.14)


def _paint_liquid(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.2), shade(base, 0.75))
    # 液面
    if h > 4:
        d.line([(x0, y0 + 1), (x1 - 1, y0 + 1)], fill=shade(base, 1.55))
    if w > 3 and h > 3:
        d.ellipse([x0 + 1, y0 + 1, x0 + 3, y0 + 3], fill=(255, 255, 255, 200))


def _paint_scale(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.12), shade(base, 0.74))
    step = max(2, min(w, h) // 2)
    for row, y in enumerate(range(y0, y1, step)):
        off = (row % 2) * (step // 2)
        for x in range(x0 - step, x1 + step, step):
            d.ellipse([x + off, y, x + off + step - 1, y + step - 1],
                      outline=shade(base, 0.65))
            d.line([(x + off, y), (x + off + step - 1, y)], fill=shade(base, 1.22))


def _paint_rune(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.05), shade(base, 0.62))
    glow = mat.accent or (150, 110, 255, 255)
    rnd = _rnd(seed)
    for _ in range(max(1, int(w * h * 0.06))):
        x = rnd.randrange(x0, x1)
        y = rnd.randrange(y0, y1)
        d.point((x, y), fill=glow)


def _paint_flat(img, box, mat, face, seed, w, h):
    d = ImageDraw.Draw(img)
    base = mat.toned(face)
    core.vgradient(img, box, shade(base, 1.08), shade(base, 0.82))


PAINTERS = {
    "metal": _paint_metal,
    "wood": _paint_wood,
    "leather": _paint_leather,
    "cloth": _paint_cloth,
    "crystal": _paint_crystal,
    "ice": _paint_ice,
    "bone": _paint_bone,
    "stone": _paint_stone,
    "obsidian": _paint_obsidian,
    "paper": _paint_paper,
    "liquid": _paint_liquid,
    "scale": _paint_scale,
    "rune": _paint_rune,
    "flat": _paint_flat,
}


def paint_face(img: Image.Image, box, mat: Material, face: str, seed: int) -> None:
    """1 面を mat の質感で塗る。box は画像内のピクセル矩形。"""
    x0, y0, x1, y1 = box
    if x1 <= x0 or y1 <= y0:
        return
    painter = PAINTERS.get(mat.kind, _paint_flat)
    painter(img, box, mat, face, seed, x1 - x0, y1 - y0)
