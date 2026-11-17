"""エンドコンテンツ武器 (夜想曲ノクターン・相棒の剣) の見た目。

元絵は ユーザー作の 64x64 ドット絵 (mm/life-pve-aibou/resourcepack) を tools/art/src/ に置いたもの。
- ノクターン: 左クリックカウント 0〜4 で 5 段階。カウント 1〜4 は光が脈打つ 5 コマのアニメーション
- 相棒の剣: 段階 0-1 / 2-3 / 4-5 の 3 段階
どちらも CUSTOM_MODEL_DATA の floats[0] (Java 側の Endgame.putInts) で切り替える。
"""
from __future__ import annotations

import colorsys
import math
import os

from PIL import Image, ImageDraw

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "src")

NOCTURNE_STAGES = 5
AIBOU_LOOKS = 3
ANIM_FRAMES = 5


def source(name: str) -> Image.Image:
    return Image.open(os.path.join(SRC, f"{name}.png")).convert("RGBA")


def _is_glow(r: int, g: int, b: int) -> bool:
    """光る部分 (宝石・紋・星屑) = 彩度か明度が高い画素。柄の木目・暗い縁は除く。"""
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return v > 0.55 and (s > 0.35 or v > 0.9)


def pulse_frames(img: Image.Image, frames: int = ANIM_FRAMES) -> Image.Image:
    """光る画素の明るさと色相を周期的に揺らした縦並びのアニメーション帯を作る。

    位相は画素の位置 (対角方向) でずらし、光が柄から先端へ流れるように見せる。
    """
    w, h = img.size
    strip = Image.new("RGBA", (w, h * frames), (0, 0, 0, 0))
    src = img.load()
    for f in range(frames):
        frame = img.copy()
        px = frame.load()
        for y in range(h):
            for x in range(w):
                r, g, b, a = src[x, y]
                if a == 0 or not _is_glow(r, g, b):
                    continue
                phase = 2 * math.pi * (f / frames - (x - y) / (w + h) * 2)
                k = 0.72 + 0.42 * (0.5 + 0.5 * math.sin(phase))
                hh, ss, vv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                hh = (hh + 0.02 * math.sin(phase)) % 1.0
                vv = min(1.0, vv * k)
                rr, gg, bb = colorsys.hsv_to_rgb(hh, ss, vv)
                px[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
        strip.paste(frame, (0, h * f))
    return strip


def anim_meta(frametime: int = 3) -> dict:
    return {"animation": {"frametime": frametime, "interpolate": True}}


def limit_stone() -> Image.Image:
    """限界突破の楔 (16x16): 蒼く光る楔形の結晶に金の帯。"""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    outline = (18, 22, 40, 255)
    body = (70, 200, 255, 255)
    light = (190, 248, 255, 255)
    dark = (30, 110, 170, 255)
    gold = (240, 196, 84, 255)
    pts = [(8, 1), (13, 6), (10, 15), (6, 15), (3, 6)]
    d.polygon(pts, fill=outline)
    d.polygon([(8, 2), (12, 6), (9, 14), (7, 14), (4, 6)], fill=body)
    d.polygon([(8, 2), (8, 14), (7, 14), (4, 6)], fill=dark)
    d.line([(8, 3), (6, 7)], fill=light)
    d.line([(9, 4), (11, 6)], fill=light)
    d.line([(4, 9), (12, 9)], fill=outline)
    d.line([(5, 10), (11, 10)], fill=gold)
    d.point((8, 10), fill=(255, 250, 220, 255))
    for p in ((2, 2), (14, 3), (13, 12)):
        d.point(p, fill=light)
    return img
