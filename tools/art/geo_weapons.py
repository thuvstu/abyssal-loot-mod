"""エンド武器の手持ち 3D モデル (GeckoLib)。

ノクターン: 夜色の杖。柄に螺旋の帯が巻きつき、頭は三日月の環。環の中で四芒星が回り、
            カウント (0〜4) の数 +1 個の音符が頭のまわりを周回する。配色はカウントごと。
相棒の剣:   段階 (0〜5) で育つ長剣。段階 2 でルーンが灯り、3 で鍔に翼が生え、
            4 で刃が蒼く光り欠片が 2 つ周回、5 で欠片 4 つと刃全体の光輝。

骨の表示/非表示は Java 側 (EndgameGeoRenderer) と同じ規則を hidden_bones() に持つ。
"""
from __future__ import annotations

import math

from . import geo
from .endgame import AIBOU_THEMES, NOCTURNE_THEMES
from .geo import Material, Model

ANIM_LEN = 4.0


# ================================================================ ノクターン

NOCTURNE_NOTES = 5
NOCT_HEAD = 19.5  # 三日月の環の中心 (y)
NOCT_R = 6.4


def nocturne_model() -> Model:
    m = Model("abyss_nocturne")
    m.bone("root")
    # 柄: 夜色の芯 + 金の留め環 + 握り
    m.bone("shaft", parent="root")
    m.box("shaft", (0, 0, 0), (2.0, 24.0, 2.0), "shaft")
    for y in (-10.2, 10.6):
        m.box("shaft", (0, y, 0), (3.0, 1.2, 3.0), "gold")
    for y in (-8.0, -6.4, -4.8):
        m.box("shaft", (0, y, 0), (2.4, 1.0, 2.4), "grip")
    # 石突の宝珠
    m.bone("pommel", pivot=(0, -13.4, 0), parent="root")
    m.box("pommel", (0, -13.4, 0), (2.6, 2.6, 2.6), "gem", rot=(0, 45, 45))
    m.box("pommel", (0, -11.6, 0), (2.0, 0.8, 2.0), "gold")
    # 螺旋の帯 (回転アニメで流れる)
    m.bone("ribbon", pivot=(0, 0, 0), parent="root")
    n = 44
    for i in range(n):
        t = i / (n - 1)
        a = t * 3.2 * 2 * math.pi
        y = -8.5 + 17.5 * t
        r = 1.8
        m.box("ribbon", (r * math.cos(a), y, r * math.sin(a)), (0.6, 1.1, 1.4), "ribbon",
              rot=(0, -math.degrees(a), 0))
    # 頭: 首金具と接ぎの宝石
    m.bone("head", pivot=(0, 12, 0), parent="root")
    m.box("head", (0, 12.4, 0), (3.4, 1.6, 3.4), "gold")
    m.box("head", (0, 13.8, 0), (2.4, 2.4, 2.4), "gem", rot=(0, 0, 45))
    # 三日月の環 (下が太く、上端が開く)
    a0, a1 = 115.0, 425.0
    segs = 18
    for i in range(segs):
        ta, tb = i / segs, (i + 1) / segs
        aa = math.radians(a0 + (a1 - a0) * ta)
        ab = math.radians(a0 + (a1 - a0) * tb)
        mid = (ta + tb) / 2
        f = 1 - abs(mid - 0.5) * 2  # 0 (先端) → 1 (下)
        thick = 0.9 + 2.1 * f ** 1.2
        depth = 1.0 + 0.8 * f
        rr = NOCT_R + thick * 0.15
        pa = (rr * math.cos(aa), NOCT_HEAD + rr * math.sin(aa), 0)
        pb = (rr * math.cos(ab), NOCT_HEAD + rr * math.sin(ab), 0)
        ext = 0.35  # 継ぎ目の隙間を埋める
        dx, dy = pb[0] - pa[0], pb[1] - pa[1]
        ln = math.hypot(dx, dy)
        pa2 = (pa[0] - dx / ln * ext, pa[1] - dy / ln * ext, 0)
        pb2 = (pb[0] + dx / ln * ext, pb[1] + dy / ln * ext, 0)
        m.bar("head", pa2, pb2, thick, depth, "metal" if i % 6 else "metal_hi")
    # 環の両端の飾り玉
    for a in (a0, a1):
        ar = math.radians(a)
        m.box("head", (NOCT_R * math.cos(ar), NOCT_HEAD + NOCT_R * math.sin(ar), 0), (1.6, 1.6, 1.6),
              "gem", rot=(0, 0, 45))
    # 四芒星 (環の中で回る)
    m.bone("star", pivot=(0, NOCT_HEAD, 0), parent="head")
    c = (0, NOCT_HEAD, 0)
    m.box("star", c, (1.1, 9.0, 0.7), "star")
    m.box("star", c, (9.0, 1.1, 0.7), "star")
    m.box("star", c, (2.6, 2.6, 1.1), "star_core", rot=(0, 0, 45))
    m.box("star", c, (0.7, 5.2, 0.5), "star", rot=(0, 0, 45))
    m.box("star", c, (0.7, 5.2, 0.5), "star", rot=(0, 0, -45))
    # 周回する音符 (カウント +1 個だけ表示)
    m.bone("orbit", pivot=(0, NOCT_HEAD, 0), parent="root")
    for k in range(NOCTURNE_NOTES):
        a = 2 * math.pi * k / NOCTURNE_NOTES
        x, z = 10.0 * math.cos(a), 10.0 * math.sin(a)
        y = NOCT_HEAD - 2.0 + (k % 2) * 3.0
        b = m.bone(f"note{k}", pivot=(x, y, z), parent="orbit")
        m.box(b, (x, y, z), (1.7, 1.3, 0.8), "note", rot=(0, 0, 20))
        m.box(b, (x + 0.7, y + 1.8, z), (0.45, 3.2, 0.45), "note")
        m.box(b, (x + 1.25, y + 3.0, z), (1.1, 0.5, 0.45), "note", rot=(0, 0, -30))
    m.layout(128)
    return m


def nocturne_palette(count: int) -> dict[str, Material]:
    main, glow = NOCTURNE_THEMES[count]
    metal = geo.glow_ramp_color(_mix_int(0x8c86b8, main, 0.32), 0.0)
    return {
        "shaft": Material(0x2d2652, "dark"),
        "grip": Material(0x3a2f66, "cloth"),
        "gold": Material(0xe8be54, "metal"),
        "gem": Material(main, "gem", emissive=count > 0),
        "ribbon": Material(main, "glow", emissive=True),
        "metal": Material(metal, "metal"),
        "metal_hi": Material(geo.glow_ramp_color(metal, 0.35), "metal"),
        "star": Material(_mix_int(main, glow, 0.5), "glow", emissive=True),
        "star_core": Material(geo.glow_ramp_color(glow, 0.55), "glow", emissive=True),
        "note": Material(glow, "glow", emissive=True),
    }


def nocturne_hidden(count: int) -> set[str]:
    return {f"note{k}" for k in range(count + 1, NOCTURNE_NOTES)}


def nocturne_anim() -> dict:
    L = ANIM_LEN
    return geo.anim_json("animation.abyss_nocturne.idle", L, {
        "ribbon": {"rotation": geo.spin(L, 1, -1.0, 8)},
        "star": {"rotation": geo.spin(L, 1, 1.0, 8),
                 "scale": {0.0: [1, 1, 1], L / 4: [1.18, 1.18, 1.18], L / 2: [1, 1, 1],
                           3 * L / 4: [1.18, 1.18, 1.18], L: [1, 1, 1]}},
        "orbit": {"rotation": geo.spin(L, 1, 1.0, 8),
                  "position": geo.wave(L, (0, 0.8, 0), 8)},
        "head": {"rotation": geo.wave(L, (0, 0, 2.5), 8, phase=math.pi / 2)},
    })


# ================================================================ 相棒の剣

AIBOU_SHARDS = 4


def aibou_model() -> Model:
    m = Model("abyss_aibou")
    m.bone("root")
    # 握り
    m.bone("hilt", parent="root")
    m.box("hilt", (0, -7.6, 0), (1.8, 7.2, 1.8), "grip")
    for y in (-10.0, -8.2, -6.4, -4.8):
        m.box("hilt", (0, y, 0), (2.1, 0.6, 2.1), "wrap")
    m.box("hilt", (0, -12.2, 0), (2.8, 2.0, 2.8), "guard")
    m.box("hilt", (0, -12.2, 0), (1.5, 1.5, 3.1), "gem", rot=(0, 0, 45))
    # 鍔
    m.bone("guard", pivot=(0, -3.2, 0), parent="root")
    m.box("guard", (0, -3.2, 0), (9.0, 1.6, 2.4), "guard")
    m.box("guard", (0, -3.0, 0), (3.2, 2.8, 3.0), "guard")
    m.box("guard", (0, -3.0, 0), (1.7, 1.7, 3.4), "gem", rot=(0, 0, 45))
    for s in (-1, 1):
        m.box("guard", (s * 4.9, -3.2, 0), (1.6, 1.6, 2.6), "guard", rot=(0, 0, 45))
    # 刃: 芯 + 両側の刃先 + 切っ先の段 + 中央の樋
    m.bone("blade", pivot=(0, 0, 0), parent="root")
    m.box("blade", (0, 4.3, 0), (3.2, 13.4, 1.0), "blade")
    for s in (-1, 1):
        m.box("blade", (s * 1.8, 4.3, 0), (0.4, 13.4, 0.6), "edge")
    for (w, y0, y1) in ((3.4, 11.0, 13.0), (2.6, 13.0, 14.6), (1.7, 14.6, 16.0), (0.8, 16.0, 17.2)):
        m.box("blade", (0, (y0 + y1) / 2, 0), (w, y1 - y0, 0.9), "blade")
    m.box("blade", (0, 4.6, 0), (1.0, 12.4, 1.1), "fuller")
    # 翼 (段階 3 から)
    for side, name in ((-1, "wing_l"), (1, "wing_r")):
        px = side * 2.4
        m.bone(name, pivot=(px, -3.0, 0), parent="guard")
        for k, (ang, ln) in enumerate(((30, 6.4), (52, 5.4), (74, 4.0))):
            a = math.radians(180 - ang if side < 0 else ang)
            base = (px, -3.0 + 0.4 * k, 0)
            tip = (base[0] + math.cos(a) * ln, base[1] + math.sin(a) * ln, 0)
            m.bar(name, base, tip, 1.3 - 0.2 * k, 0.8, "wing" if k else "wing_hi")
    # 周回する欠片 (段階 4 で 2 つ、5 で 4 つ)
    m.bone("halo", pivot=(0, 6, 0), parent="root")
    for k in range(AIBOU_SHARDS):
        a = 2 * math.pi * k / AIBOU_SHARDS
        x, z = 5.2 * math.cos(a), 5.2 * math.sin(a)
        y = 1.0 + 4.0 * k
        b = m.bone(f"shard{k}", pivot=(x, y, z), parent="halo")
        m.box(b, (x, y, z), (1.0, 2.2, 1.0), "shard", rot=(0, 45, 0))
    m.layout(128)
    return m


def aibou_palette(stage: int) -> dict[str, Material]:
    blade_hex, glow_hex, guard_hex = AIBOU_THEMES[stage]
    glow = glow_hex or 0x9aa4b8
    return {
        "grip": Material(0x34405e, "cloth"),
        "wrap": Material(guard_hex, "metal"),
        "guard": Material(guard_hex, "metal"),
        "gem": Material(glow if glow_hex else 0x6f7fa8, "gem", emissive=stage >= 2),
        "blade": Material(blade_hex, "glow" if stage >= 5 else "metal", emissive=stage >= 5),
        "edge": Material(geo.glow_ramp_color(blade_hex, 0.55) if stage < 4 else glow,
                         "glow" if stage >= 4 else "metal", emissive=stage >= 4),
        "fuller": Material(geo.shift(blade_hex, -0.3), "rune", emissive=stage >= 2,
                           accent=glow if stage >= 2 else None),
        "wing": Material(geo.glow_ramp_color(guard_hex, 0.35), "metal", emissive=stage >= 5),
        "wing_hi": Material(geo.glow_ramp_color(guard_hex, 0.6), "metal", emissive=stage >= 4),
        "shard": Material(geo.glow_ramp_color(glow, 0.4), "glow", emissive=True),
    }


def aibou_hidden(stage: int) -> set[str]:
    hidden = set()
    if stage < 3:
        hidden |= {"wing_l", "wing_r"}
    shards = 0 if stage < 4 else (2 if stage == 4 else 4)
    hidden |= {f"shard{k}" for k in range(shards, AIBOU_SHARDS)}
    return hidden


def aibou_anim() -> dict:
    L = ANIM_LEN
    return geo.anim_json("animation.abyss_aibou.idle", L, {
        "wing_l": {"rotation": geo.wave(L, (0, 0, 9), 8)},
        "wing_r": {"rotation": geo.wave(L, (0, 0, -9), 8)},
        "halo": {"rotation": geo.spin(L, 1, 1.0, 8),
                 "position": geo.wave(L, (0, 0.7, 0), 8)},
    })


# ================================================================ 共通

def _mix_int(a: int, b: int, t: float) -> int:
    ca = ((a >> 16) & 255, (a >> 8) & 255, a & 255)
    cb = ((b >> 16) & 255, (b >> 8) & 255, b & 255)
    r = [int(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)]
    return (r[0] << 16) | (r[1] << 8) | r[2]


# 手持ち表示: バニラ handheld を基準に、立てて作ったモデルを z -45° 寝かせる
# (display の回転は Rz が最内側なので z 成分へ足せばよい)
def display(scale_tp: float, scale_fp: float, gui_scale: float = 0.62) -> dict:
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 4.0, 0.5],
                                  "scale": [scale_tp] * 3},
        "thirdperson_lefthand": {"rotation": [0, 90, -100], "translation": [0, 4.0, 0.5],
                                 "scale": [scale_tp] * 3},
        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.2, 1.13],
                                  "scale": [scale_fp] * 3},
        "firstperson_lefthand": {"rotation": [0, 90, -70], "translation": [1.13, 3.2, 1.13],
                                 "scale": [scale_fp] * 3},
        "ground": {"rotation": [0, 0, -45], "translation": [0, 2, 0], "scale": [0.45] * 3},
        "fixed": {"rotation": [0, 180, -45], "translation": [0, 0, 0], "scale": [gui_scale] * 3},
        "head": {"rotation": [0, 180, -45], "translation": [0, 13, 7], "scale": [0.8] * 3},
        "gui": {"rotation": [30, 225, -45], "translation": [0, 0, 0], "scale": [gui_scale] * 3},
    }
