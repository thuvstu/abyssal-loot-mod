"""配色の道具 (MCAssetGen の advanced-minecraft-asset-generator と Arcane Forge から移植・改良)。

- ramp(base): 7 段の色階 (0 = 輪郭の最暗 … 6 = 照り)。暗い段は色相を寒色側へ、明るい段は暖色側へ寄せる
  (手描きのドット絵の陰影)。彩度も暗部で上げ明部で下げる。
- MATERIALS: 29 素材 (木・石・鉄・金・各宝石・竜・ウィザー・聖・血・虚無・呪・獄炎・氷結・秘術・自然・虹・
  機械鋼・プラズマ・青銅・骨・ミダス・エンド・影) の 刃 / 金具 / 柄 / 宝石 の色階と光の色 (palettes_data.json)。
- harmony(color, scheme): 1 色から配色一式 (宝石・光・粒子・金属・柄・光輪・翼) を作る。
  scheme = complementary / analogous / triadic / split / tetradic / monochrome。明度の関係を保つので陰影が崩れない。
"""
from __future__ import annotations

import colorsys
import json
import os

_HERE = os.path.dirname(os.path.abspath(__file__))
MATERIALS: dict = json.load(open(os.path.join(_HERE, "palettes_data.json"), encoding="utf-8"))
SCHEMES = {"complementary": (0.5, 0.5), "analogous": (0.083, -0.083), "triadic": (1 / 3, 2 / 3),
           "split": (0.42, 0.58), "tetradic": (0.25, 0.5), "monochrome": (0.0, 0.0)}


def _hsl(c: int):
    r, g, b = ((c >> 16) & 255) / 255, ((c >> 8) & 255) / 255, (c & 255) / 255
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    return h, s, l


def _rgb(h, s, l) -> int:
    r, g, b = colorsys.hls_to_rgb(h % 1, max(0.0, min(1.0, l)), max(0.0, min(1.0, s)))
    return (round(r * 255) << 16) | (round(g * 255) << 8) | round(b * 255)


def ramp(base: int, hue_shift: float = 0.04, contrast: float = 1.0, sat_boost: float = 0.0) -> list[int]:
    h, s, l = _hsl(base)
    out = []
    for i, off in enumerate((-0.36, -0.24, -0.12, 0, 0.11, 0.22, 0.32)):
        k = (i - 3) / 3
        ns = s + sat_boost + (0.08 * (3 - i) if i < 3 else -0.05 * (i - 3))
        out.append(_rgb(h - k * hue_shift, ns, min(0.97, max(0.04, l + off * contrast))))
    return out


def material(name: str) -> dict:
    """素材の色階 (int のリスト) と光の色。"""
    m = MATERIALS[name]
    return {k: ([int(c, 16) for c in m[k]] if isinstance(m[k], list) and k != "tags" else m[k])
            for k in ("primary", "secondary", "grip", "gem")} | {"glow": int(m["glow"], 16), "ja": m["ja"]}


def harmony(color: int, scheme: str = "complementary") -> dict[str, int]:
    h, s, l = _hsl(color)
    o1, o2 = SCHEMES[scheme]
    mono = scheme == "monochrome"
    return {
        "gem": color,
        "gem2": _rgb(h + o1, s * (0.55 if mono else 0.9), min(0.88, l + 0.26)),
        "glow": _rgb(h + o1 * 0.4, min(1, s * 1.05), min(0.72, l + 0.1)),
        "particle": _rgb(h + o2 * 0.35, s * 0.75, min(0.9, l + 0.34)),
        "metal": _rgb(h + o2, s * (0.3 if mono else 0.62), 0.6),
        "shaft": _rgb(h + o2 * 0.8, s * (0.35 if mono else 0.45), 0.26),
        "shaft2": _rgb(h + o2 * 0.8, s * (0.3 if mono else 0.4), 0.13),
        "halo": _rgb(h + o1, s * 0.8, 0.7),
        "wing": _rgb(h + o1 * 0.6, s * 0.3, 0.86),
    }
