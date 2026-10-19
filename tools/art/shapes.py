"""武器・防具・素材の立体形状 (箱の集合) を定義する。

モデル空間: 1 単位 = 1/16 ブロック。中心は (8, 8, 8)。
武器は原則として +Y 方向 (刃先を上) に持ち、Z 方向は薄く (掴む向きに flat)。

造形方針:
  * 刃は「中央板 + 左右の薄い刃」の 2 層にして断面を菱形にする。
  * 大きな部品は面取り (1 段小さい箱を重ねる) で角を落とし、丸みを出す。
  * 曲面 (鎌・斧の刃) は角度を付けた断片を連ねて描く。
"""
from __future__ import annotations

import math

from .boxmodel import Model, box
from .materials import Material
from .core import hexcolor, mix, shade

# ------------------------------------------------------------------ 素材セット


def make_palette(accent: int, style: str = "steel") -> dict:
    """accent 色から一組の素材を作る。"""
    acc = hexcolor(accent)
    pal = {
        "steel": Material("metal", (118, 126, 148, 255), metal=0.95, rough=0.25),
        "bright": Material("metal", (206, 214, 234, 255), metal=1.0, rough=0.14),
        "dark": Material("metal", (66, 72, 90, 255), metal=0.85, rough=0.35),
        "black": Material("metal", (38, 40, 52, 255), metal=0.7, rough=0.5),
        "gold": Material("metal", (190, 152, 74, 255), metal=1.0, rough=0.2),
        "bronze": Material("metal", (152, 106, 64, 255), metal=0.9, rough=0.3),
        "copper": Material("metal", (190, 112, 76, 255), metal=0.9, rough=0.28),
        "silver": Material("metal", (208, 212, 224, 255), metal=1.0, rough=0.15),
        "wood": Material("wood", (122, 84, 52, 255)),
        "darkwood": Material("wood", (80, 52, 34, 255)),
        "leather": Material("leather", (76, 52, 40, 255)),
        "bone": Material("bone", (228, 222, 202, 255)),
        "stone": Material("stone", (132, 127, 118, 255)),
        "stone_dim": Material("stone", (96, 93, 87, 255)),
        "obsidian": Material("obsidian", (28, 20, 42, 255), accent=acc),
        "cloth": Material("cloth", shade(acc, 0.5)),
        "accent": Material("crystal", acc, emissive=True),
        "accent_dim": Material("crystal", shade(acc, 0.74)),
        "accent_deep": Material("crystal", shade(acc, 0.46)),
        "rune": Material("rune", (40, 32, 58, 255), accent=acc),
        "white": Material("flat", (244, 246, 252, 255)),
        "bonewhite": Material("bone", (236, 230, 212, 255)),
        # 刃 (中央板・エッジ共通)。エッジ用は明るい色にする。
        "blade": Material("blade", (88, 96, 122, 255)),
        "blade_mid": Material("blade", (150, 160, 186, 255)),
        "blade_edge": Material("blade_edge", (214, 222, 244, 255)),
        "blade_rune": Material("blade", (84, 92, 122, 255), accent=acc),
    }
    if style == "blood":
        pal["steel"] = Material("metal", (168, 156, 164, 255), metal=0.95, rough=0.25)
        pal["blade"] = Material("blade", (176, 164, 172, 255))
        pal["blade_edge"] = Material("blade_edge", (222, 208, 214, 255))
        pal["accent"] = Material("crystal", hexcolor(0xff2a55), emissive=True)
    elif style == "void":
        pal["steel"] = Material("metal", (104, 96, 132, 255), metal=0.9, rough=0.3)
        pal["blade"] = Material("blade", (116, 108, 148, 255), accent=acc)
        pal["blade_edge"] = Material("blade_edge", (186, 176, 228, 255))
        pal["accent"] = Material("crystal", hexcolor(0x8a5cff), emissive=True)
    elif style == "ice":
        pal["steel"] = Material("ice", (176, 222, 244, 255))
        pal["blade"] = Material("blade", (186, 226, 246, 255))
        pal["blade_edge"] = Material("blade_edge", (232, 248, 255, 255))
        pal["accent"] = Material("crystal", hexcolor(0xaee9ff), emissive=True)
    elif style == "bone":
        pal["steel"] = Material("bone", (230, 224, 206, 255))
        pal["blade"] = Material("blade", (228, 222, 204, 255))
        pal["blade_edge"] = Material("blade_edge", (250, 248, 240, 255))
    elif style == "rune":
        pal["blade"] = pal["blade_rune"]
        pal["blade_edge"] = Material("blade_edge", (206, 214, 238, 255))
    return pal


# ------------------------------------------------------------------ 小道具


def _center_x(w: float) -> float:
    return 8.0 - w / 2.0


def _seg(m: Model, name, center, size, angle, mat, axis="z", **kw):
    """中心指定 + 回転つきの箱。"""
    cx, cy, cz = center
    sx, sy, sz = size
    return m.box(name, (cx - sx / 2, cy - sy / 2, cz - sz / 2),
                 (cx + sx / 2, cy + sy / 2, cz + sz / 2), mat,
                 rotation={"origin": [round(cx, 4), round(cy, 4), round(cz, 4)],
                           "axis": axis, "angle": angle}, **kw)


def _bone(m: Model, name, p0, p1, width, z0, z1, mat, grow: float = 0.08, **kw):
    """2 点を結ぶ回転した箱 (骨格)。曲線は短い骨格の連鎖で描く。

    grow だけ両端を伸ばして、関節の隙間 (ノッチ) を埋める。
    """
    x0, y0 = p0
    x1, y1 = p1
    dx, dy = x1 - x0, y1 - y0
    length = max(0.05, math.hypot(dx, dy)) + grow * 2.0
    ang = math.degrees(math.atan2(dy, dx))
    cx, cy, cz = (x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0
    return m.box(name, (cx - length / 2, cy - width / 2, z0),
                 (cx + length / 2, cy + width / 2, z1), mat,
                 rotation={"origin": [round(cx, 4), round(cy, 4), round(cz, 4)],
                           "axis": "z", "angle": round(ang, 3)}, **kw)


def _arc(m: Model, prefix, cx, cy, z0, z1, r, a0, a1, count, length, thick, mat, spin=90.0):
    """円弧に沿って箱を並べる (鎌や斧の湾曲した刃)。"""
    for i in range(count):
        t = i / max(1, count - 1)
        a = a0 + (a1 - a0) * t
        rad = math.radians(a)
        px = cx + math.cos(rad) * r
        py = cy + math.sin(rad) * r
        _seg(m, f"{prefix}{i}", (px, py, (z0 + z1) / 2), (length, thick, z1 - z0), a + spin, mat)


def _orb(m: Model, prefix, cx, cy, cz, r, mat, band=None, waist=0.42):
    """面取りした球。上下 2 段のテーパー + 45 度回転の帯で丸く見せる。"""
    s = r * waist
    m.box(f"{prefix}_mid", (cx - r, cy - s, cz - r), (cx + r, cy + s, cz + r), mat)
    m.box(f"{prefix}_up1", (cx - r * 0.74, cy + s, cz - r * 0.74), (cx + r * 0.74, cy + s + r * 0.42, cz + r * 0.74), mat)
    m.box(f"{prefix}_up2", (cx - r * 0.38, cy + s + r * 0.42, cz - r * 0.38),
          (cx + r * 0.38, cy + s + r * 0.72, cz + r * 0.38), mat)
    m.box(f"{prefix}_dn1", (cx - r * 0.74, cy - s - r * 0.42, cz - r * 0.74),
          (cx + r * 0.74, cy - s, cz + r * 0.74), mat)
    m.box(f"{prefix}_dn2", (cx - r * 0.38, cy - s - r * 0.72, cz - r * 0.38),
          (cx + r * 0.38, cy - s - r * 0.42, cz + r * 0.38), mat)
    if band is not None:
        _seg(m, f"{prefix}_band", (cx, cy, cz), (r * 2.02, s * 2.02, r * 2.02), 45.0, band, axis="y")


def _bevel_box(m: Model, name, frm, to, mat, bevel=0.35, cap_mat=None):
    """角を落とした箱 (本体 + 上下の面取り)。"""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    cap_mat = cap_mat or mat
    m.box(name, (x0 + bevel, y0, z0 + bevel), (x1 - bevel, y1, z1 - bevel), mat)
    m.box(name + "_lo", (x0, y0, z0), (x1, y0 + (y1 - y0) * 0.16, z1), cap_mat)
    m.box(name + "_hi", (x0, y1 - (y1 - y0) * 0.16, z0), (x1, y1, z1), cap_mat)


def ridge(m: Model, name, x0, x1, z0, z1, y0, y1, mat):
    """刃の稜線 (中央の盛り上がり)。"""
    m.box(name, (x0, y0, z0), (x1, y1, z1), mat, hide=("up", "down"))


def blade(m: Model, name, y0, y1, w, z0, z1, mat, ridge_mat=None, tip=True, edge_mat=None):
    """剣身。中央板 + 左右の薄い刃 (断面が菱形) + 3 段テーパーの先端。"""
    hw = w / 2.0
    zc = (z0 + z1) / 2.0
    t = z1 - z0
    ecore = max(0.28, min(w * 0.17, 0.6))
    et = max(0.3, t * 0.46)
    edge_mat = edge_mat or mat
    body_y1 = y1 - (2.4 if tip else 0.0)
    m.box(name, (8 - hw + ecore, y0, z0), (8 + hw - ecore, body_y1, z1), mat)
    m.box(name + "_el", (8 - hw, y0, zc - et / 2), (8 - hw + ecore, body_y1, zc + et / 2), edge_mat)
    m.box(name + "_er", (8 + hw - ecore, y0, zc - et / 2), (8 + hw, body_y1, zc + et / 2), edge_mat)
    if ridge_mat is not None:
        ridge(m, name + "_ridge", 8 - 0.26, 8 + 0.26, z0 - 0.22, z1 + 0.22, y0 + 0.5, body_y1 - 0.5, ridge_mat)
    if tip:
        stages = ((0.82, 0.74, 0.66), (0.60, 0.56, 0.62), (0.38, 0.40, 0.58), (0.16, 0.26, 0.54))
        yy = body_y1
        for k, (kw_, kt, hh) in enumerate(stages):
            last = k == len(stages) - 1
            yy2 = y1 if last else yy + hh
            m.box(f"{name}_tip{k}", (8 - hw * kw_, yy, zc - t * kt / 2),
                  (8 + hw * kw_, yy2, zc + t * kt / 2), edge_mat if last else mat)
            yy = yy2


def guard(m: Model, name, y, w, h, z0, z1, mat, accent=None, sweep=0.9, trim=None):
    """鍔。中央の板 + 上下の金線 + 両端の跳ね上げ + 宝石。"""
    hw = w / 2.0
    m.box(name, (8 - hw, y, z0), (8 + hw, y + h, z1), mat)
    if trim is not None:
        m.box(name + "_trim_hi", (8 - hw, y + h - 0.2, z0 - 0.09), (8 + hw, y + h + 0.06, z1 + 0.09), trim)
        m.box(name + "_trim_lo", (8 - hw, y - 0.06, z0 - 0.09), (8 + hw, y + 0.18, z1 + 0.09), trim)
    for i, s in ((0, -1), (1, 1)):
        # 端の跳ね上がり (小さな飾り)
        m.box(f"{name}_cap{i}", (8 + s * hw - 0.36, y + h * 0.65, z0 - 0.12),
              (8 + s * hw + 0.36, y + h * 1.45, z1 + 0.12), trim or mat)
        m.box(f"{name}_cap2{i}", (8 + s * hw - 0.22, y + h * 1.45, z0 - 0.08),
              (8 + s * hw + 0.22, y + h * 1.85, z1 + 0.08), trim or mat)
    if accent is not None:
        m.box(name + "_gem_rim", (8 - 0.72, y + h * 0.02, z0 - 0.44), (8 + 0.72, y + h * 0.98, z1 + 0.44),
              trim or mat)
        m.box(name + "_gem", (8 - 0.5, y + h * 0.16, z0 - 0.54), (8 + 0.5, y + h * 0.84, z1 + 0.54), accent)


def grip(m: Model, name, y0, y1, w, z0, z1, mat, wrap=None):
    """柄。面取りした本体 + 巻きひも。"""
    hw = w / 2.0
    zc = (z0 + z1) / 2.0
    t = z1 - z0
    bevel = min(0.3, w * 0.22, t * 0.28)
    m.box(name, (8 - hw + bevel, y0, z0 + bevel), (8 + hw - bevel, y1, z1 - bevel), mat)
    m.box(name + "_l", (8 - hw, y0, zc - t / 2 + bevel), (8 - hw + bevel, y1, zc + t / 2 - bevel), mat)
    m.box(name + "_r", (8 + hw - bevel, y0, zc - t / 2 + bevel), (8 + hw, y1, zc + t / 2 - bevel), mat)
    m.box(name + "_f", (8 - hw + bevel, y0, z0), (8 + hw - bevel, y1, z0 + bevel), mat)
    m.box(name + "_b", (8 - hw + bevel, y0, z1 - bevel), (8 + hw - bevel, y1, z1), mat)
    if wrap is not None:
        # 巻きひもは柄の色に寄せてなじませる (金ピカの輪が並ぶのを避ける)
        wrap = Material(wrap.kind, mix(mat.base, wrap.base, 0.72),
                        metal=wrap.metal, rough=wrap.rough)
        y = y0 + 0.3
        i = 0
        while y + 0.5 < y1 - 0.2:
            m.box(f"{name}_wrap{i}", (8 - hw - 0.05, y, z0 - 0.05),
                  (8 + hw + 0.05, y + 0.52, z1 + 0.05), wrap)
            y += 0.82
            i += 1
    m.box(name + "_cuff0", (8 - hw - 0.15, y0 - 0.3, z0 - 0.15),
          (8 + hw + 0.15, y0 + 0.26, z1 + 0.15), wrap or mat)
    m.box(name + "_cuff1", (8 - hw - 0.15, y1 - 0.26, z0 - 0.15),
          (8 + hw + 0.15, y1 + 0.3, z1 + 0.15), wrap or mat)


def pommel(m: Model, name, y0, y1, w, z0, z1, mat, gem=None, trim=None):
    """柄頭 (2 段の玉)。"""
    hw = w / 2.0
    trim = trim or mat
    m.box(name, (8 - hw, y0 + 0.3, z0), (8 + hw, y1 - 0.3, z1), mat)
    m.box(name + "_b", (8 - hw * 0.72, y0, z0 + (z1 - z0) * 0.16),
          (8 + hw * 0.72, y0 + 0.5, z1 - (z1 - z0) * 0.16), trim)
    m.box(name + "_t", (8 - hw * 0.72, y1 - 0.5, z0 + (z1 - z0) * 0.16),
          (8 + hw * 0.72, y1, z1 - (z1 - z0) * 0.16), trim)
    if gem is not None:
        _orb(m, name + "_gem", 8, y0 + 0.9, (z0 + z1) / 2, 0.42, gem)


def collar(m: Model, name, y, w, h, z0, z1, mat, rivets=0.0):
    """柄と刃をつなぐ金具。"""
    _bevel_box(m, name, (8 - w / 2, y, z0), (8 + w / 2, y + h, z1), mat, bevel=0.22)
    if rivets > 0:
        for s in (-1, 1):
            m.box(f"{name}_rv{s}", (8 + s * (w / 2 - 0.2) - 0.14, y + h * 0.2, z0 - 0.12),
                  (8 + s * (w / 2 - 0.2) + 0.14, y + h * 0.8, z1 + 0.12), mat)


# ------------------------------------------------------------------ 武器


def sword(m: Model, p: dict, length: float = 13.0, w: float = 2.8, thick: float = 1.0,
          style: str = "steel", gem: bool = True) -> None:
    """剣。"""
    z0, z1 = 8 - thick / 2, 8 + thick / 2
    top = 8 + length / 2
    bot = 8 - length / 2
    y_guard = bot + 5.0
    blade(m, "blade", y_guard + 0.55, top, w, z0, z1, p["blade"], p["blade_mid"],
          edge_mat=p["blade_edge"])
    # 刃元の金具 (リカッソ)
    m.box("ricasso", (8 - w / 2 + 0.15, y_guard - 0.1, z0 - 0.1),
          (8 + w / 2 - 0.15, y_guard + 0.75, z1 + 0.1), p["dark"])
    m.box("ricasso_rim", (8 - w / 2 + 0.05, y_guard + 0.62, z0 - 0.17),
          (8 + w / 2 - 0.05, y_guard + 0.88, z1 + 0.17), p["gold"])
    if style == "rune":
        for i in range(3):
            yy = y_guard + 1.8 + i * 2.1
            m.box(f"rune{i}", (8 - 0.34, yy, z0 - 0.14), (8 + 0.34, yy + 0.7, z1 + 0.14),
                  p["accent"])
    guard(m, "guard", y_guard - 1.0, 6.4, 0.95, z0 - 0.75, z1 + 0.75, p["dark"],
          p["accent"] if gem else None, trim=p["gold"])
    grip(m, "grip", bot + 1.4, y_guard - 1.0, 2.0, z0 - 0.3, z1 + 0.3, p["leather"], p["bronze"])
    pommel(m, "pommel", bot, bot + 1.4, 2.9, z0 - 0.7, z1 + 0.7, p["dark"],
           p["accent_dim"] if gem else None, trim=p["gold"])


def greatsword(m: Model, p: dict) -> None:
    """大剣。"""
    z0, z1 = 7.3, 8.7
    blade(m, "blade", 8.2, 15.2, 3.4, z0, z1, p["blade"], p["blade_mid"],
          edge_mat=p["blade_edge"])
    for i in range(3):
        yy = 9.2 + i * 1.7
        m.box(f"gem{i}", (8 - 0.5, yy, z0 - 0.16), (8 + 0.5, yy + 0.9, z1 + 0.16), p["accent"])
    guard(m, "guard", 7.1, 7.8, 1.1, z0 - 1.05, z1 + 1.05, p["dark"], p["accent"], trim=p["gold"])
    grip(m, "grip", 3.2, 7.1, 2.4, z0 - 0.4, z1 + 0.4, p["leather"], p["bronze"])
    pommel(m, "pommel", 1.9, 3.2, 4.0, z0 - 0.95, z1 + 0.95, p["dark"], p["accent_dim"], trim=p["gold"])


def katana(m: Model, p: dict, style: str = "steel") -> None:
    """刀。反りと鎺・鍔・柄巻き。"""
    z0, z1 = 7.55, 8.45
    zc = 8.0
    # 刃 (反りは 3 段の角度で表現)
    _seg(m, "blade_lo", (7.75, 10.4, zc), (1.15, 5.8, 0.9), 2.5, p["blade"])
    _seg(m, "blade_hi", (7.55, 13.7, zc), (1.05, 4.4, 0.86), 8.0, p["blade"])
    _seg(m, "blade_tip", (7.5, 15.15, zc), (0.86, 1.9, 0.7), 26.0, p["blade_edge"])
    m.box("blade_el", (7.0, 8.0, zc - 0.22), (7.24, 15.0, zc + 0.22), p["blade_edge"])
    m.box("ridge", (7.62, 8.2, z0 - 0.12), (7.92, 13.2, z1 + 0.12), p["blade_mid"], hide=("up", "down"))
    if style == "rune":
        for i in range(3):
            yy = 9.4 + i * 1.9
            m.box(f"rune{i}", (7.68, yy, zc - 0.6), (7.92, yy + 0.8, zc + 0.6), p["accent"])
    # 鍔・切羽
    m.box("habaki", (7.0, 7.9, z0 + 0.05), (8.0, 8.9, z1 - 0.05), p["bronze"])
    m.box("tsuba", (6.3, 7.05, 6.5), (9.7, 7.95, 9.5), p["dark"])
    m.box("tsuba_rim", (6.2, 7.0, 6.4), (9.8, 7.25, 9.6), p["gold"])
    m.box("tsuba_gem", (7.5, 7.1, 6.3), (8.5, 7.9, 6.7), p["accent"])
    grip(m, "grip", 3.5, 7.05, 1.7, 7.45, 8.55, p["cloth"], p["gold"])
    pommel(m, "pommel", 2.5, 3.6, 2.4, 7.3, 8.7, p["gold"], p["accent_dim"])


def dagger(m: Model, p: dict, twin: bool = False) -> None:
    """短剣 (twin=True で二刀)。"""
    z0, z1 = 7.55, 8.45
    offs = [-2.4, 2.4] if twin else [0.0]
    for i, ox in enumerate(offs):
        x = 8 + ox
        gw = 1.4 if twin else 1.9
        m.box(f"blade{i}", (x - 0.66, 6.4, z0), (x + 0.66, 12.1, z1), p["blade"])
        m.box(f"edge_l{i}", (x - 0.86, 6.4, 7.86), (x - 0.66, 12.1, 8.14), p["blade_edge"])
        m.box(f"edge_r{i}", (x + 0.66, 6.4, 7.86), (x + 0.86, 12.1, 8.14), p["blade_edge"])
        m.box(f"tip1{i}", (x - 0.5, 12.1, z0 + 0.12), (x + 0.5, 13.1, z1 - 0.12), p["blade"])
        m.box(f"tip2{i}", (x - 0.22, 13.1, 7.82), (x + 0.22, 13.9, 8.18), p["blade_edge"])
        m.box(f"ridge{i}", (x - 0.18, 6.8, z0 - 0.14), (x + 0.18, 11.9, z1 + 0.14),
              p["blade_mid"], hide=("up", "down"))
        m.box(f"guard{i}", (x - gw, 5.6, z0 - 0.3), (x + gw, 6.4, z1 + 0.3), p["gold"])
        m.box(f"guard_gem{i}", (x - 0.42, 5.7, z0 - 0.45), (x + 0.42, 6.3, z1 + 0.45), p["accent"])
        grip(m, f"grip{i}", 3.0, 5.6, 1.5, 7.6, 8.4, p["leather"], p["gold"])
        pommel(m, f"pommel{i}", 2.2, 3.0, 2.0, 7.5, 8.5, p["gold"])


def axe(m: Model, p: dict, haft_len: float = 11.0, head_w: float = 6.0, style: str = "steel") -> None:
    """斧。柄 + 三日月の刃 (縦スラブの連なり) + 背面の刺。"""
    top = 8 + haft_len / 2
    bot = 8 - haft_len / 2
    z0, z1 = 7.2, 8.8
    m.box("haft", (7.5, bot, 7.7), (8.5, top - 0.5, 8.3), p["wood"])
    m.box("haft_wrap", (7.38, bot + 0.4, 7.55), (8.62, bot + 3.2, 8.45), p["leather"])
    m.box("haft_cap", (7.34, bot - 0.25, 7.5), (8.66, bot + 0.55, 8.5), p["bronze"])
    m.box("haft_ring", (7.4, top - 4.0, 7.6), (8.6, top - 3.4, 8.4), p["gold"])
    collar(m, "collar", top - 2.7, 1.9, 2.5, 7.25, 8.75, p["bronze"])
    # 刃: 横スラブで作った三日月 (楕円の外形) + 円弧に沿った薄い刃先
    yc = top - 1.85
    H = 2.95
    slabs = 13
    pts = []
    for i in range(slabs + 1):
        yy = yc - H + 2.0 * H * i / slabs
        k = max(0.0, 1.0 - ((yy - yc) / H) ** 2)
        pts.append((8.2 + 1.25 + 2.15 * math.sqrt(k), yy))
    for i in range(slabs):
        y0s, y1s = pts[i][1], pts[i + 1][1]
        reach = max(pts[i][0], pts[i + 1][0]) - 0.1
        m.box(f"head{i}", (8.18, y0s - 0.02, z0 + 0.16), (reach, y1s + 0.02, z1 - 0.16), p["blade"])
    # 刃先 (折れ線に沿って連続した帯)
    for i in range(slabs):
        _bone(m, f"edge{i}", pts[i], pts[i + 1], 0.62, z0, z1, p["blade_edge"], grow=0.16)
    # 刃の背 (柄との接合部)
    m.box("head_back", (8.15, yc - 1.55, z0 + 0.12), (9.3, yc + 1.55, z1 - 0.12), p["steel"])
    # 背面の刺 (beak)
    m.box("beak", (6.1, top - 1.3, 7.5), (7.6, top - 0.15, 8.5), p["dark"])
    m.box("beak_tip", (5.4, top - 1.0, 7.7), (6.2, top - 0.35, 8.3), p["steel"])
    if style == "rune":
        m.box("rune", (8.6, yc - 0.8, z0 + 0.02), (9.5, yc + 0.8, z0 + 0.2), p["accent"])
    else:
        m.box("gem", (8.9, yc - 0.6, z0 + 0.02), (9.7, yc + 0.6, z0 + 0.22), p["accent"])


def hammer(m: Model, p: dict, size: float = 5.0) -> None:
    """鎚。面取りした頭 + 打撃面の縁 + 帯 + 石突。"""
    top = 8 + 5.0
    bot = 8 - 6.5
    m.box("haft", (7.42, bot, 7.6), (8.58, top - 2.2, 8.6), p["wood"])
    m.box("haft_wrap", (7.34, bot + 0.8, 7.5), (8.66, bot + 3.6, 8.7), p["leather"])
    m.box("haft_cap", (7.3, bot - 0.3, 7.5), (8.7, bot + 0.8, 8.7), p["bronze"])
    hw = size / 2.0
    m.box("head", (8 - hw, top - 2.6, 6.5), (8 + hw, top, 9.5), p["dark"])
    m.box("head_top", (8 - hw + 0.3, top, 6.7), (8 + hw - 0.3, top + 0.5, 9.3), p["dark"])
    m.box("face_r", (8 + hw, top - 2.5, 6.6), (8 + hw + 0.5, top - 0.1, 9.4), p["steel"])
    m.box("rim_r", (8 + hw + 0.35, top - 2.6, 6.5), (8 + hw + 0.62, top, 9.5), p["bright"])
    m.box("face_l", (8 - hw - 0.5, top - 2.5, 6.6), (8 - hw, top - 0.1, 9.4), p["steel"])
    m.box("rim_l", (8 - hw - 0.62, top - 2.6, 6.5), (8 - hw - 0.35, top, 9.5), p["bright"])
    m.box("band0", (8 - hw + 0.2, top - 0.7, 6.4), (8 + hw - 0.2, top - 0.1, 9.6), p["gold"])
    m.box("gem", (8 - 0.55, top - 2.0, 6.35), (8 + 0.55, top - 1.0, 6.7), p["accent"])
    m.box("gem2", (8 - 0.55, top - 2.0, 9.3), (8 + 0.55, top - 1.0, 9.65), p["accent"])
    collar(m, "collar", top - 3.0, 1.7, 0.9, 7.0, 9.0, p["bronze"])


def mace(m: Model, p: dict) -> None:
    """メイス (面取りした球の頭 + 棘)。"""
    top = 8 + 5.4
    bot = 8 - 6.0
    m.box("haft", (7.42, bot, 7.6), (8.58, top - 2.6, 8.6), p["darkwood"])
    m.box("haft_wrap", (7.34, bot + 0.6, 7.5), (8.66, bot + 3.6, 8.7), p["leather"])
    m.box("haft_cap", (7.3, bot - 0.3, 7.5), (8.7, bot + 0.7, 8.7), p["bronze"])
    cy = top - 0.4
    _orb(m, "ball", 8, cy, 8, 1.9, p["steel"], band=p["dark"])
    for i in range(6):
        a = i * 60.0
        rad = math.radians(a)
        px, py = 8 + math.cos(rad) * 2.0, cy + math.sin(rad) * 2.0
        _seg(m, f"spike{i}", (px, py, 8), (0.85, 0.5, 0.5), a + 90, p["bright"])
    m.box("gem", (7.5, cy - 0.7, 6.0), (8.5, cy + 0.7, 6.35), p["accent"])
    m.box("gem2", (7.5, cy - 0.7, 9.65), (8.5, cy + 0.7, 10.0), p["accent"])
    collar(m, "collar", top - 3.0, 1.7, 0.8, 7.1, 8.9, p["gold"])


def spear(m: Model, p: dict, length: float = 15.0, head: str = "leaf") -> None:
    """槍。穂の種類 (leaf / trident / astral) を切り替える。"""
    top = 8 + length / 2
    bot = 8 - length / 2
    m.box("shaft", (7.45, bot + 1.6, 7.65), (8.55, top - 2.4, 8.35), p["wood"])
    m.box("shaft_wrap", (7.36, bot + 2.0, 7.55), (8.64, bot + 5.2, 8.45), p["leather"])
    m.box("butt", (7.3, bot, 7.5), (8.7, bot + 1.7, 8.5), p["bronze"])
    collar(m, "collar", top - 2.8, 1.7, 1.0, 7.2, 8.8, p["gold"])
    if head == "leaf":
        # 刃 (剣と同じ 3 段テーパー) + 根元の飾り
        blade(m, "spearhead", top - 2.4, top + 2.6, 2.7, 7.42, 8.58, p["blade"],
              p["bright"], edge_mat=p["blade_edge"])
        m.box("guard_ring", (7.15, top - 2.6, 7.15), (8.85, top - 2.0, 8.85), p["gold"])
        m.box("neck", (7.6, top - 2.1, 7.6), (8.4, top - 1.6, 8.4), p["dark"])
        m.box("gem", (7.35, top - 2.9, 7.3), (8.65, top - 2.1, 7.6), p["accent"])
    elif head == "trident":
        m.box("core", (7.6, top - 2.2, 7.7), (8.4, top + 0.4, 8.3), p["steel"])
        m.box("crossbar", (5.4, top - 1.9, 7.6), (10.6, top - 0.85, 8.4), p["steel"])
        m.box("crossbar_rim", (5.3, top - 2.05, 7.5), (10.7, top - 1.7, 8.5), p["gold"])
        m.box("cprong", (7.72, top + 0.4, 7.78), (8.28, top + 1.9, 8.22), p["steel"])
        m.box("cprong_tip", (7.82, top + 1.9, 7.84), (8.18, top + 2.9, 8.16), p["blade_edge"])
        for s in (-1, 1):
            bx = 8.0 + s * 2.4
            _bone(m, f"prong{s}", (bx, top - 1.2), (bx + s * 0.3, top + 1.3), 0.72, 7.7, 8.3, p["steel"])
            _bone(m, f"prong_tip{s}", (bx + s * 0.3, top + 1.3), (bx + s * 0.45, top + 2.5), 0.44,
                  7.8, 8.2, p["blade_edge"])
            _bone(m, f"prong_el{s}", (bx - s * 0.34, top - 0.9), (bx + s * 0.06, top + 1.2), 0.2,
                  7.72, 7.9, p["bright"])
    elif head == "astral":
        # 結晶の穂先 + 周囲を舞う欠片 (浮遊感)
        m.box("core", (7.65, top - 2.2, 7.65), (8.35, top + 0.3, 8.35), p["steel"])
        m.box("crystal", (7.25, top - 1.9, 7.25), (8.75, top + 2.3, 8.75), p["accent"],
              rotation={"origin": [8, top - 1.9, 8], "axis": "y", "angle": 45})
        m.box("crystal_low", (7.4, top - 2.9, 7.4), (8.6, top - 1.9, 8.6), p["accent_dim"],
              rotation={"origin": [8, top - 1.9, 8], "axis": "y", "angle": 45})
        m.box("crystal_tip", (7.72, top + 2.3, 7.72), (8.28, top + 3.3, 8.28), p["white"])
        for i, (dx, dy, ang) in enumerate(((-1.9, 1.2, 32), (1.9, 1.2, -32), (0.0, 3.0, 0))):
            m.box(f"shard{i}", (8 + dx - 0.34, top + dy - 0.7, 7.72),
                  (8 + dx + 0.34, top + dy + 0.7, 8.28), p["accent"],
                  rotation={"origin": [8 + dx, top + dy, 8], "axis": "z", "angle": ang})
        m.box("halo", (7.2, top - 1.5, 6.9), (7.45, top - 0.5, 9.1), p["accent_dim"])
    m.box("gem", (7.62, top - 1.6, 7.3), (8.38, top - 0.4, 7.6), p["accent"])


def staff(m: Model, p: dict, orb: str = "orb") -> None:
    """杖。籠に収めた宝珠 / 結晶。"""
    top = 8 + 6.5
    bot = 8 - 7.0
    m.box("shaft", (7.5, bot, 7.7), (8.5, top - 2.8, 8.3), p["darkwood"])
    m.box("grip", (7.36, bot + 0.4, 7.56), (8.64, bot + 4.2, 8.44), p["leather"])
    for i in range(3):
        yy = bot + 0.9 + i * 1.15
        m.box(f"grip_ring{i}", (7.32, yy, 7.52), (8.68, yy + 0.3, 8.48), p["gold"])
    m.box("shaft_ring", (7.3, top - 3.6, 7.5), (8.7, top - 3.1, 8.5), p["gold"])
    cy = top - 1.3
    if orb == "orb":
        _orb(m, "orb", 8, cy, 8, 1.65, p["accent"])
        for a in (45, 135, 225, 315):
            rad = math.radians(a)
            px, py = 8 + math.cos(rad) * 1.7, cy + math.sin(rad) * 1.7
            _seg(m, f"cage{a}", (px, py, 8), (0.22, 3.5, 0.22), a, p["bronze"], axis="y")
    elif orb == "void":
        _orb(m, "orb", 8, cy, 8, 1.6, p["obsidian"])
        m.box("orb_eye", (7.45, cy - 0.55, 6.2), (8.55, cy + 0.55, 6.8), p["accent"])
        m.box("orb_eye_b", (7.45, cy - 0.55, 9.2), (8.55, cy + 0.55, 9.8), p["accent"])
        m.box("orb_glow", (7.75, cy - 0.24, 6.05), (8.25, cy + 0.24, 6.32), p["white"])
        m.box("orb_glow_b", (7.75, cy - 0.24, 9.68), (8.25, cy + 0.24, 9.95), p["white"])
        for i in range(4):
            a = 45 + i * 90
            rad = math.radians(a)
            _seg(m, f"claw{i}", (8 + math.cos(rad) * 1.42, cy + 0.35, 8 + math.sin(rad) * 1.42),
                 (0.26, 2.3, 0.26), a, p["dark"], axis="y")
    else:
        # 結晶を戴く杖
        m.box("crystal", (7.3, cy - 0.6, 7.3), (8.7, cy + 2.4, 8.7), p["accent"],
              rotation={"origin": [8, cy - 0.6, 8], "axis": "y", "angle": 45})
        m.box("crystal_tip", (7.68, cy + 2.4, 7.68), (8.32, cy + 3.4, 8.32), p["accent_dim"])
        m.box("cradle", (7.1, cy - 1.0, 7.1), (8.9, cy - 0.4, 8.9), p["gold"])
        for i in range(4):
            a = 45 + i * 90
            rad = math.radians(a)
            _seg(m, f"prong{i}", (8 + math.cos(rad) * 1.0, cy + 0.9, 8 + math.sin(rad) * 1.0),
                 (0.3, 2.0, 0.3), a * 0.35, p["gold"])


def wand(m: Model, p: dict) -> None:
    """短杖。握り + 金の口金 + 結晶の穂先。"""
    m.box("shaft", (7.5, 2.6, 7.7), (8.5, 10.6, 8.3), p["darkwood"])
    m.box("grip", (7.34, 3.0, 7.52), (8.66, 5.6, 8.48), p["leather"])
    m.box("cap", (7.36, 2.0, 7.54), (8.64, 3.0, 8.46), p["bronze"])
    m.box("band", (7.42, 5.6, 7.6), (8.58, 6.2, 8.4), p["gold"])
    m.box("socket", (7.25, 10.2, 7.25), (8.75, 11.2, 8.75), p["gold"])
    m.box("socket2", (7.4, 11.2, 7.4), (8.6, 11.8, 8.6), p["bronze"])
    # 結晶 (柱 + 尖り + 側面の小片)
    _prism(m, "cryst", 8.0, 11.6, 15.9, 1.05, 8.0, p["accent"], p["accent_dim"])
    m.box("cryst_shine", (7.35, 12.6, 9.0), (7.85, 14.4, 9.3), p["white"])


def bow(m: Model, p: dict) -> None:
    """弓。円弧をなす上下の弓身 + 握り + 弦 + 照準の宝石。"""
    z0, z1 = 7.55, 8.45
    cx, cy, r = -3.0, 8.0, 12.2
    n = 8
    pts = []
    for i in range(n + 1):
        a = math.radians(-35.0 + 70.0 * i / n)
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    for i in range(n):
        # 中央 (握り付近) が太く、先端に向かって細い
        t = abs(i - (n - 1) / 2.0) / ((n - 1) / 2.0)
        w = 1.05 - 0.42 * t
        _bone(m, f"limb{i}", pts[i], pts[i + 1], w, z0, z1, p["wood"])
    # 握り (革巻き + 金の口金)
    m.box("grip", (8.95, 6.3, z0 - 0.18), (10.15, 9.7, z1 + 0.18), p["leather"])
    m.box("grip_ring0", (8.9, 6.0, z0 - 0.24), (10.2, 6.6, z1 + 0.24), p["gold"])
    m.box("grip_ring1", (8.9, 9.4, z0 - 0.24), (10.2, 10.0, z1 + 0.24), p["gold"])
    m.box("sight", (9.9, 7.4, z0 - 0.34), (10.45, 8.6, z0 - 0.05), p["accent"])
    # 弦
    m.box("string", (6.92, 0.9, 7.9), (7.08, 15.1, 8.1), p["white"], hide=("up", "down"))
    # 弦留め (先端の金具)
    for i, yy in ((0, 1.0), (1, 15.0)):
        m.box(f"nock{i}", (6.6, yy - 0.5, z0 + 0.05), (7.5, yy + 0.5, z1 - 0.05), p["gold"])


def scythe(m: Model, p: dict) -> None:
    """大鎌。刃は左向き + 滑らかな湾曲 (円弧に沿ったスラブ)。"""
    m.box("haft", (7.5, 1.4, 7.45), (8.5, 12.4, 8.55), p["darkwood"])
    m.box("haft_wrap", (7.34, 2.0, 7.3), (8.66, 4.8, 8.7), p["leather"])
    m.box("haft_cap", (7.35, 1.0, 7.3), (8.65, 1.9, 8.7), p["bronze"])
    collar(m, "collar", 11.6, 1.7, 1.0, 7.2, 8.8, p["gold"])
    # 刃: 中心 (8,8) 半径 4.6 の円弧。90 度 (柄の頂点) から 152 度 (左) へ。
    cx, cy, r = 8.0, 8.0, 4.6
    n = 7
    for i in range(n):
        t = i / (n - 1)
        a = 90.0 + 62.0 * t
        rad = math.radians(a)
        px, py = cx + math.cos(rad) * r, cy + math.sin(rad) * r
        thick = 1.6 - 0.75 * t
        length = 2.3 - 0.35 * t
        _seg(m, f"blade{i}", (px, py, 8), (length, thick, 1.5), a + 90, p["steel"])
        rr = r + thick * 0.5 + 0.22
        ex, ey = cx + math.cos(rad) * rr, cy + math.sin(rad) * rr
        _seg(m, f"edge{i}", (ex, ey, 8), (length * 0.92, 0.52, 1.75), a + 90, p["blade_edge"])
    m.box("blade_root", (7.1, 11.5, 7.2), (8.9, 13.0, 8.8), p["dark"])
    m.box("rune", (7.4, 11.9, 7.05), (8.6, 12.6, 7.25), p["accent"])


def flail(m: Model, p: dict) -> None:
    """鎖球: 柄 + 鎖 + 棘のある玉 (玉は上側)。"""
    m.box("haft", (7.4, 1.2, 7.55), (8.6, 6.8, 8.45), p["darkwood"])
    m.box("haft_cap", (7.25, 0.5, 7.4), (8.75, 1.5, 8.6), p["gold"])
    m.box("haft_wrap", (7.3, 2.4, 7.45), (8.7, 4.6, 8.55), p["leather"])
    m.box("haft_ring", (7.3, 6.0, 7.45), (8.7, 6.7, 8.55), p["gold"])
    for i in range(4):
        yy = 6.8 + i * 1.0
        ang = 0 if i % 2 == 0 else 90
        _seg(m, f"chain{i}", (8, yy + 0.5, 8), (1.05, 0.4, 0.4), ang, p["silver"], axis="y")
    _orb(m, "ball", 8, 12.6, 8, 1.85, p["steel"], band=p["dark"])
    for i in range(8):
        a = i * 45.0
        rad = math.radians(a)
        px, py = 8 + math.cos(rad) * 1.75, 12.6 + math.sin(rad) * 1.75
        _seg(m, f"spike{i}", (px, py, 8), (1.0, 0.5, 0.5), a + 90, p["gold"])
    m.box("ball_gem", (7.55, 12.15, 9.75), (8.45, 13.05, 10.15), p["accent"])


def chakram(m: Model, p: dict) -> None:
    """輪刃。内輪 + 8 枚の angled な刃 + 金の心金。"""
    z0, z1 = 7.3, 8.7
    zc = 8.0
    # 内輪 (8 本の弦で近似)
    for i in range(8):
        a0 = math.radians(i * 45.0 - 22.5)
        a1 = math.radians(i * 45.0 + 22.5)
        r = 1.62
        p0 = (8 + math.cos(a0) * r, 8 + math.sin(a0) * r)
        p1 = (8 + math.cos(a1) * r, 8 + math.sin(a1) * r)
        _bone(m, f"ring{i}", p0, p1, 0.86, z0 + 0.2, z1 - 0.2, p["dark"])
    # 刃 (接線方向に反った 8 枚)
    for i in range(8):
        a = i * 45.0
        a2 = a + 20.0
        p0 = (8 + math.cos(math.radians(a)) * 1.7, 8 + math.sin(math.radians(a)) * 1.7)
        p1 = (8 + math.cos(math.radians(a2)) * 3.6, 8 + math.sin(math.radians(a2)) * 3.6)
        p2 = (8 + math.cos(math.radians(a2 + 14.0)) * 4.9, 8 + math.sin(math.radians(a2 + 14.0)) * 4.9)
        _bone(m, f"blade{i}", p0, p1, 1.25, z0 + 0.22, z1 - 0.22, p["steel"])
        _bone(m, f"tip{i}", p1, p2, 0.66, z0 + 0.05, z1 - 0.05, p["blade_edge"])
    m.box("hub", (7.05, 7.05, 7.15), (8.95, 8.95, 8.85), p["gold"])
    m.box("core", (7.3, 7.3, 6.95), (8.7, 8.7, 9.05), p["accent"])
    m.box("core_glow", (7.72, 7.72, 6.8), (8.28, 8.28, 7.15), p["white"])
    m.box("core_glow2", (7.72, 7.72, 8.85), (8.28, 8.28, 9.2), p["white"])


def tome(m: Model, p: dict) -> None:
    """魔道書。頁 + 表裏の表紙 + 背 + 角金具 + 留め金 + 宝石 + 栞。"""
    # 頁 (小口が右へ張り出す)
    m.box("pages", (3.9, 3.4, 6.7), (12.5, 12.5, 9.3), p["bonewhite"])
    for i in range(4):
        x = 4.3 + i * 1.95
        m.box(f"page_edge{i}", (x, 3.5, 6.55), (x + 1.45, 12.4, 6.72), p["stone"])
    for i in range(4):
        z = 7.0 + i * 0.6
        m.box(f"page_line{i}", (4.0, 3.5, z), (12.4, 12.4, z + 0.07), p["stone"])
    # 背表紙
    m.box("spine", (2.9, 2.9, 5.8), (4.1, 13.0, 10.2), p["darkwood"])
    for i in range(3):
        y = 4.6 + i * 3.4
        m.box(f"spine_band{i}", (2.8, y, 5.7), (4.15, y + 0.75, 10.3), p["gold"])
    # 裏表紙 (奥)
    m.box("cover_back", (3.4, 2.9, 5.95), (12.25, 13.0, 6.8), p["cloth"])
    # 表紙 (手前 +Z)
    m.box("cover_front", (3.4, 2.9, 9.3), (12.25, 13.0, 10.15), p["cloth"])
    # 縁金
    m.box("frame_t", (3.55, 11.9, 10.1), (12.1, 12.8, 10.32), p["gold"])
    m.box("frame_b", (3.55, 3.05, 10.1), (12.1, 3.55, 10.32), p["gold"])
    m.box("frame_l", (3.55, 3.05, 10.1), (4.0, 12.8, 10.32), p["gold"])
    m.box("frame_r", (11.65, 3.05, 10.1), (12.1, 12.8, 10.32), p["gold"])
    # 角金具
    for sx, sy in ((0, 0), (1, 0), (0, 1), (1, 1)):
        m.box(f"corner{sx}{sy}", (3.5 + sx * 7.6, 2.95 + sy * 8.8, 10.1),
              (5.0 + sx * 7.6, 4.55 + sy * 8.8, 10.4), p["gold"])
    # 宝石
    m.box("gem_rim", (7.0, 6.6, 10.1), (9.0, 9.6, 10.36), p["gold"])
    m.box("gem", (7.3, 6.9, 10.15), (8.7, 9.3, 10.52), p["accent"])
    m.box("gem_core", (7.45, 8.0, 10.35), (7.85, 8.7, 10.56), p["white"])
    # 留め金 (右の小口)
    m.box("clasp", (12.05, 6.4, 9.85), (12.8, 8.0, 10.5), p["gold"])
    m.box("clasp2", (12.05, 6.0, 6.1), (12.8, 7.6, 6.75), p["gold"])
    # 栞
    m.box("ribbon", (7.85, 1.1, 7.6), (8.75, 3.5, 8.5), p["accent_dim"])
    m.box("ribbon_tip", (7.85, 1.1, 7.6), (8.75, 1.8, 8.5), p["accent"])


def gauntlet(m: Model, p: dict, big: bool = False) -> None:
    """拳套 (握り拳)。甲 + 手前に折り畳んだ指 4 本 + 親指 + 鋲付きの手首。"""
    s = 1.28 if big else 1.0
    # 拳の塊
    m.box("fist", (8 - 2.5 * s, 4.9, 6.4), (8 + 2.5 * s, 10.5, 10.2), p["steel"])
    # 甲の板 (手前 +Z へ張り出す)
    m.box("plate", (8 - 2.55 * s, 5.1, 10.1), (8 + 2.55 * s, 10.3, 10.85), p["steel"])
    m.box("plate_rim_b", (8 - 2.65 * s, 4.85, 10.05), (8 + 2.65 * s, 5.4, 10.8), p["gold"])
    m.box("plate_rim_t", (8 - 2.65 * s, 10.0, 10.05), (8 + 2.65 * s, 10.5, 10.8), p["gold"])
    # 甲の宝石
    m.box("gem_rim", (8 - 1.2 * s, 6.5, 10.8), (8 + 1.2 * s, 9.1, 11.02), p["gold"])
    m.box("gem", (8 - 0.9 * s, 6.8, 10.95), (8 + 0.9 * s, 8.8, 11.18), p["accent"])
    m.box("gem_core", (8 - 0.78 * s, 7.8, 11.12), (8 - 0.22 * s, 8.5, 11.28), p["white"])
    # 拳骨 4 つ (上端) + 奥へ巻いた指
    for i in range(4):
        cx = 8 + (i - 1.5) * 1.24 * s
        m.box(f"knuckle{i}", (cx - 0.58 * s, 10.25, 9.7), (cx + 0.58 * s, 11.4, 10.9), p["steel"])
        m.box(f"knuckle_cap{i}", (cx - 0.44 * s, 11.35, 9.95), (cx + 0.44 * s, 11.75, 10.7), p["dark"])
        m.box(f"finger{i}", (cx - 0.52 * s, 9.3, 6.2), (cx + 0.52 * s, 10.35, 9.7), p["dark"])
    # 親指 (左側)
    m.box("thumb", (8 - 3.4 * s, 6.4, 7.2), (8 - 2.2 * s, 8.6, 10.4), p["steel"],
          rotation={"origin": [8 - 2.8 * s, 7.5, 8.8], "axis": "x", "angle": -18})
    m.box("thumb_tip", (8 - 3.6 * s, 8.3, 8.4), (8 - 2.5 * s, 9.5, 10.0), p["dark"])
    # 手首 (外へ広がる + 金の縁 + 鋲)
    m.box("cuff", (8 - 2.85 * s, 2.2, 6.9), (8 + 2.85 * s, 5.1, 10.4), p["dark"])
    m.box("cuff_trim", (8 - 3.0 * s, 1.8, 6.75), (8 + 3.0 * s, 2.7, 10.55), p["gold"])
    m.box("cuff_rim", (8 - 2.95 * s, 4.85, 6.8), (8 + 2.95 * s, 5.45, 10.5), p["gold"])
    for i, ox in enumerate((-2.0, 2.0)):
        m.box(f"cuff_riv{i}", (8 + ox * s - 0.28, 3.2, 10.35), (8 + ox * s + 0.28, 4.0, 10.65), p["bright"])


def claw(m: Model, p: dict) -> None:
    """爪。付け根から湾曲して伸びる 3 本 + 金具 + 宝石。"""
    for i, ox in enumerate((-2.1, 0.0, 2.1)):
        x = 8.0 + ox
        pts = [(x, 6.6), (x - 0.12, 9.3), (x - 0.5, 11.6), (x - 1.1, 13.3), (x - 1.85, 14.3)]
        widths = [1.2, 1.0, 0.82, 0.6, 0.32]
        depths = [1.15, 1.05, 0.92, 0.78, 0.6]
        for k in range(len(pts) - 1):
            _bone(m, f"claw{i}_{k}", pts[k], pts[k + 1], widths[k],
                  8 - depths[k], 8 + depths[k], p["bone"] if k < 3 else p["bonewhite"])
        m.box(f"claw_base{i}", (x - 0.8, 5.4, 6.85), (x + 0.8, 7.0, 9.15), p["dark"])
    m.box("band", (4.7, 5.6, 6.6), (11.3, 7.2, 9.4), p["dark"])
    m.box("band_trim", (4.5, 5.3, 6.45), (11.5, 5.9, 9.55), p["gold"])
    m.box("band_rim", (4.6, 7.05, 6.5), (11.4, 7.5, 9.5), p["gold"])
    m.box("gem", (7.3, 5.9, 6.4), (8.7, 7.0, 6.75), p["accent"])
    for i, ox in enumerate((-3.2, 3.2)):
        m.box(f"stud{i}", (8 + ox - 0.3, 6.0, 6.45), (8 + ox + 0.3, 6.8, 6.75), p["bright"])


def horn(m: Model, p: dict) -> None:
    """戦角。吹き口から湾曲しながら広がる角 + 金の帯 + 喇叭。"""
    pts = [(9.7, 1.9), (10.4, 4.3), (10.55, 6.9), (9.8, 9.4), (8.1, 11.2), (6.0, 12.3), (4.3, 12.6)]
    widths = [0.8, 1.0, 1.35, 1.9, 2.55, 3.3, 4.1]
    depths = [0.55, 0.7, 0.95, 1.25, 1.6, 2.0, 2.4]
    for i in range(len(pts) - 1):
        _bone(m, f"horn{i}", pts[i], pts[i + 1], widths[i],
              8 - depths[i], 8 + depths[i], p["bone"])
    # 喇叭の縁
    _bone(m, "bell", pts[-1], (3.3, 12.2), 4.9, 8 - 3.0, 8 + 3.0, p["bone"])
    _bone(m, "bell_rim", (3.55, 12.45), (3.15, 12.1), 5.3, 8 - 3.2, 8 + 3.2, p["bonewhite"])
    # 吹き口
    m.box("mouth", (9.4, 1.3, 7.35), (10.15, 2.3, 8.65), p["gold"])
    m.box("mouth_rim", (9.25, 1.1, 7.2), (10.3, 1.7, 8.8), p["bronze"])
    # 金の帯 (角に沿わせる)
    for i, (k, w) in enumerate(((1, 1.2), (3, 2.1))):
        _bone(m, f"band{i}", pts[k], pts[k + 1], widths[k] + 0.32,
              8 - depths[k] - 0.12, 8 + depths[k] + 0.12, p["gold"])
    # 宝石 (喇叭の側面)
    m.box("gem", (4.0, 11.4, 5.1), (5.3, 12.6, 5.55), p["accent"])
    m.box("gem_rim", (3.8, 11.2, 5.15), (5.5, 12.8, 5.4), p["gold"])


def whip(m: Model, p: dict) -> None:
    """毒鞭。握り + うねる鞭 + 先端の毒棘。"""
    m.box("handle", (7.3, 2.4, 7.3), (8.7, 6.4, 8.7), p["leather"])
    m.box("handle_cap", (6.95, 1.7, 6.95), (9.05, 2.7, 9.05), p["gold"])
    m.box("pommel_gem", (7.55, 1.35, 7.55), (8.45, 2.0, 8.45), p["accent"])
    for i in range(3):
        m.box(f"wrap{i}", (7.22, 3.1 + i * 1.05, 7.22), (8.78, 3.4 + i * 1.05, 8.78), p["gold"])
    n = 10
    pts = []
    for i in range(n + 1):
        t = i / n
        pts.append((8.0 + math.sin(t * 5.6) * 1.9, 6.6 + t * 9.4))
    for i in range(n):
        w = 0.9 - 0.55 * (i / n)
        _bone(m, f"lash{i}", pts[i], pts[i + 1], w, 8 - w * 0.85, 8 + w * 0.85, p["accent_dim"])
    tipx, tipy = pts[-1]
    _bone(m, "tip", (tipx, tipy), (tipx - 0.1, tipy + 1.5), 0.5, 7.75, 8.25, p["accent"])


def fishing_rod(m: Model, p: dict) -> None:
    """釣り竿。3 段に細くなる竿 + リール + 糸 + 浮き。"""
    m.box("rod0", (7.45, 3.0, 7.6), (8.55, 10.0, 8.4), p["wood"])
    m.box("rod1", (7.6, 10.0, 7.7), (8.4, 13.4, 8.3), p["dark"])
    m.box("rod2", (7.72, 13.4, 7.8), (8.28, 15.8, 8.2), p["dark"])
    m.box("grip", (7.25, 2.2, 7.45), (8.75, 5.7, 8.55), p["leather"])
    m.box("grip_end", (7.15, 1.5, 7.35), (8.85, 2.4, 8.65), p["bronze"])
    m.box("grip_ring", (7.2, 5.4, 7.4), (8.8, 6.0, 8.6), p["gold"])
    # リール
    m.box("reel", (8.55, 4.0, 7.0), (9.95, 6.4, 8.2), p["bronze"])
    m.box("reel_ring", (9.85, 4.3, 6.9), (10.25, 6.1, 8.3), p["gold"])
    m.box("reel_handle", (9.9, 4.9, 6.2), (10.5, 5.5, 6.9), p["gold"])
    # 糸 (穂先から斜めに垂れる) + 浮き
    _bone(m, "line", (8.0, 15.7), (12.5, 9.2), 0.12, 7.94, 8.06, p["white"])
    m.box("float", (12.15, 6.8, 7.5), (13.05, 8.7, 8.5), p["accent"])
    m.box("float_top", (12.3, 8.7, 7.65), (12.9, 9.4, 8.35), p["white"])
    m.box("hook", (12.35, 5.8, 7.7), (12.8, 6.9, 8.3), p["silver"])


def shears(m: Model, p: dict) -> None:
    """鋏。交差する 2 枚の刃 + 支点 + 握り輪。"""
    z0, z1 = 7.45, 8.55
    for s in (-1, 1):
        # 刃 (上へ開く)
        _bone(m, f"blade{s}", (8.0 + s * 0.5, 8.6), (8.0 + s * 2.3, 13.6), 1.15, z0, z1, p["steel"])
        _bone(m, f"edge{s}", (8.0 + s * 2.3, 13.6), (8.0 + s * 2.75, 14.8), 0.6,
              z0 - 0.06, z1 + 0.06, p["blade_edge"])
        _bone(m, f"ridge{s}", (8.0 + s * 0.9, 9.4), (8.0 + s * 2.1, 12.9), 0.32,
              z0 - 0.09, z1 + 0.09, p["bright"])
        # 柄 (反対側へ)
        _bone(m, f"arm{s}", (8.0 - s * 0.45, 7.4), (8.0 - s * 2.2, 3.2), 0.85,
              7.6, 8.4, p["dark"])
        _bone(m, f"ring{s}", (8.0 - s * 2.2, 3.2), (8.0 - s * 2.9, 1.6), 2.05,
              7.6, 8.4, p["accent_dim"])
        _bone(m, f"ring_in{s}", (8.0 - s * 2.2, 3.2), (8.0 - s * 2.9, 1.6), 1.05,
              7.5, 8.5, p["dark"])
        rx0, rx1 = sorted((8.0 - s * 2.55, 8.0 - s * 2.05))
        m.box(f"rivet{s}", (rx0, 2.35, 7.45), (rx1, 2.95, 8.55), p["gold"])
    m.box("pivot", (7.25, 7.25, 7.2), (8.75, 8.75, 8.8), p["gold"])
    m.box("pivot_gem", (7.55, 7.55, 6.95), (8.45, 8.45, 7.25), p["accent"])
    m.box("pivot_gem2", (7.55, 7.55, 8.75), (8.45, 8.45, 9.05), p["accent"])


def pickaxe(m: Model, p: dict) -> None:
    """鶴嘴。湾曲した 2 本の爪 + 柄 + 金具。"""
    m.box("haft", (7.5, 2.0, 7.65), (8.5, 12.7, 8.35), p["wood"])
    m.box("haft_cap", (7.38, 1.5, 7.55), (8.62, 2.6, 8.45), p["bronze"])
    m.box("grip", (7.35, 3.0, 7.5), (8.65, 5.4, 8.5), p["leather"])
    collar(m, "socket", 11.5, 2.0, 2.0, 7.05, 8.95, p["bronze"], rivets=1.0)
    for s in (-1, 1):
        _bone(m, f"arm{s}", (8.0, 12.9), (8.0 + s * 3.4, 12.6), 1.05, 7.2, 8.8, p["steel"])
        _bone(m, f"tip{s}", (8.0 + s * 3.4, 12.6), (8.0 + s * 4.9, 11.5), 0.78, 7.32, 8.68, p["steel"])
        _bone(m, f"edge{s}", (8.0 + s * 4.9, 11.5), (8.0 + s * 5.25, 10.75), 0.36, 7.42, 8.58, p["blade_edge"])
    m.box("gem", (7.62, 12.4, 7.15), (8.38, 13.5, 7.5), p["accent"])


def depth_gun(m: Model, p: dict) -> None:
    """深淵の銃。銃身 + 機関部 + 銃床 + ドラム。"""
    m.box("barrel", (4.6, 9.5, 7.3), (12.8, 11.1, 8.7), p["dark"])
    m.box("barrel_top", (5.2, 11.1, 7.5), (11.6, 11.5, 8.5), p["steel"])
    m.box("muzzle", (12.5, 9.0, 6.9), (13.7, 11.6, 9.1), p["steel"])
    m.box("muzzle_ring", (12.6, 8.8, 6.7), (13.9, 9.2, 9.3), p["gold"])
    m.box("muzzle_ring2", (12.6, 11.4, 6.7), (13.9, 11.8, 9.3), p["gold"])
    m.box("core", (12.2, 9.7, 6.7), (13.1, 10.9, 7.1), p["accent"])
    m.box("grip", (6.3, 5.2, 7.25), (7.9, 9.6, 8.75), p["wood"],
          rotation={"origin": [7.1, 9.6, 8], "axis": "z", "angle": 14})
    m.box("grip_wrap", (6.35, 5.4, 7.5), (7.85, 7.2, 8.5), p["leather"])
    m.box("stock", (3.8, 7.4, 7.1), (6.6, 9.6, 8.9), p["wood"])
    m.box("stock_cap", (3.4, 7.2, 7.0), (4.0, 9.8, 9.0), p["bronze"])
    m.box("drum", (8.4, 8.8, 6.5), (10.5, 11.7, 9.5), p["bronze"])
    m.box("drum_band", (8.3, 8.6, 6.4), (10.6, 9.1, 9.6), p["gold"])
    m.box("drum_core", (9.0, 9.6, 6.2), (9.9, 10.8, 6.6), p["accent"])
    m.box("trigger", (7.4, 8.2, 7.6), (8.2, 9.4, 8.4), p["dark"])


def tool_axe(m: Model, p: dict) -> None:
    """採掘斧。片刃 + 面取り。"""
    m.box("haft", (7.42, 2.2, 7.6), (8.58, 13.2, 8.6), p["wood"])
    m.box("haft_cap", (7.36, 2.0, 7.5), (8.64, 2.8, 8.7), p["bronze"])
    collar(m, "collar", 11.6, 1.8, 1.8, 7.1, 8.9, p["bronze"])
    for i, (yy, sc) in enumerate(((13.4, 0.55), (12.7, 0.9), (12.0, 0.72), (11.4, 0.45))):
        _seg(m, f"head{i}", (8.0 + 1.4 + sc * 1.3, yy, 8), (1.2 + sc * 1.8, 0.95, 1.5), 0, p["steel"])
        _seg(m, f"edge{i}", (8.0 + 2.9 + sc * 1.5, yy, 8), (0.6, 1.0, 1.75), 0, p["blade_edge"])


def shovel(m: Model, p: dict) -> None:
    """シャベル。刃は上側 (2D ドット絵と合わせる)。"""
    m.box("haft", (7.5, 2.0, 7.65), (8.5, 11.4, 8.35), p["wood"])
    m.box("haft_cap", (7.38, 1.5, 7.55), (8.62, 2.6, 8.45), p["bronze"])
    m.box("grip", (7.36, 3.0, 7.5), (8.64, 5.2, 8.5), p["leather"])
    collar(m, "socket", 11.0, 2.0, 1.5, 7.1, 8.9, p["bronze"], rivets=1.0)
    # 刃 (幅広のスコップ: 凹んだ面 + 立ち上がった縁)
    m.box("blade", (5.7, 11.7, 7.45), (10.3, 14.5, 8.55), p["steel"])
    m.box("dish", (6.15, 12.05, 8.5), (9.85, 14.15, 8.66), p["dark"])
    m.box("lip_l", (5.55, 11.55, 7.4), (5.95, 14.7, 8.9), p["blade_edge"])
    m.box("lip_r", (10.05, 11.55, 7.4), (10.45, 14.7, 8.9), p["blade_edge"])
    m.box("lip_t", (5.55, 14.45, 7.4), (10.45, 14.95, 8.9), p["blade_edge"])
    m.box("tip", (7.15, 14.9, 7.6), (8.85, 15.6, 8.7), p["blade_edge"])
    m.box("ridge", (7.85, 12.1, 8.55), (8.15, 14.3, 8.82), p["bright"])
    m.box("gem", (7.6, 12.5, 8.55), (8.4, 13.4, 8.88), p["accent"])


def hoe(m: Model, p: dict) -> None:
    """クワ。首 + 前へ傾いた幅広の刃 + 金具。"""
    m.box("haft", (7.5, 2.2, 7.65), (8.5, 12.6, 8.35), p["wood"])
    m.box("haft_cap", (7.38, 1.7, 7.55), (8.62, 2.8, 8.45), p["bronze"])
    m.box("grip", (7.36, 3.2, 7.5), (8.64, 5.4, 8.5), p["leather"])
    collar(m, "socket", 12.1, 1.9, 1.5, 7.1, 8.9, p["bronze"], rivets=1.0)
    # 首 (前へ曲げる)
    m.box("neck", (7.8, 12.6, 7.8), (8.2, 13.5, 8.2), p["dark"])
    m.box("neck2", (7.7, 13.35, 7.2), (8.3, 13.95, 8.3), p["dark"])
    # 刃 (下へ垂れる幅広の板 + 刃先 + 稜線)
    m.box("blade", (5.2, 12.2, 7.55), (10.8, 14.25, 8.45), p["steel"])
    m.box("edge", (5.3, 11.85, 7.5), (10.7, 12.35, 8.5), p["blade_edge"])
    m.box("edge2", (5.45, 11.6, 7.6), (10.55, 11.9, 8.4), p["blade_edge"])
    m.box("ridge", (7.8, 12.4, 8.4), (8.2, 14.1, 8.7), p["bright"])
    m.box("gem", (7.55, 12.7, 8.4), (8.45, 13.5, 8.75), p["accent"])


# ------------------------------------------------------------------ 防具


def helm(m: Model, p: dict) -> None:
    """兜。面頬 + 眉庇 + 鉢 + 頬当て + 首垂れ + 頂の飾り。"""
    # 鉢 (下から上へ窄める)
    layers = (
        (5.2, 10.8, 5.6, 10.4, 7.6, 9.6),
        (5.2, 10.8, 5.6, 10.4, 9.6, 10.9),
        (5.4, 10.6, 5.8, 10.2, 10.9, 11.9),
        (5.9, 10.1, 6.3, 9.7, 11.9, 12.6),
        (6.6, 9.4, 7.0, 9.0, 12.6, 13.1),
        (7.4, 8.6, 7.6, 8.4, 13.1, 13.5),
    )
    for i, (x0, x1, z0, z1, y, y1) in enumerate(layers):
        m.box(f"dome{i}", (x0, y, z0), (x1, y1, z1), p["steel"])
    # 首垂れ (奥)
    m.box("neck_guard", (5.3, 6.4, 6.1), (10.7, 7.7, 10.7), p["dark"])
    m.box("neck_rim", (5.1, 6.2, 5.9), (10.9, 6.75, 10.9), p["gold"])
    # 面頬 (前に張り出す)
    m.box("face", (5.0, 5.4, 4.5), (11.0, 8.9, 5.85), p["steel"])
    m.box("face_edge", (4.85, 5.3, 4.4), (11.15, 5.95, 5.95), p["dark"])
    m.box("face_chin", (5.4, 5.5, 4.42), (10.6, 6.3, 4.62), p["gold"])
    # 目の窪み + 眼光
    m.box("eye_l", (5.65, 7.65, 4.32), (7.45, 8.55, 4.66), p["black"])
    m.box("eye_r", (8.55, 7.65, 4.32), (10.35, 8.55, 4.66), p["black"])
    m.box("glow_l", (5.85, 7.85, 4.22), (7.25, 8.35, 4.36), p["accent"])
    m.box("glow_r", (8.75, 7.85, 4.22), (10.15, 8.35, 4.36), p["accent"])
    # 鼻当て
    m.box("nose", (7.6, 5.9, 4.18), (8.4, 8.95, 4.74), p["gold"])
    m.box("nose_tip", (7.72, 5.7, 4.14), (8.28, 6.1, 4.66), p["bright"])
    # 眉庇 (細い帯 + 鋲)
    m.box("brow", (4.9, 8.85, 4.32), (11.1, 9.4, 5.9), p["gold"])
    for i, ox in enumerate((-2.4, -0.8, 0.8, 2.4)):
        m.box(f"rivet{i}", (8 + ox - 0.22, 8.98, 4.2), (8 + ox + 0.22, 9.28, 4.42), p["bright"])
    # 頬当て
    for i, s in ((0, -1), (1, 1)):
        cx = 8 + s * 3.05
        m.box(f"cheek{i}", (cx - 0.68, 4.6, 4.95), (cx + 0.68, 7.3, 9.0), p["dark"])
        m.box(f"cheek_rim{i}", (cx - 0.74, 4.35, 4.85), (cx + 0.74, 4.95, 9.1), p["gold"])
        m.box(f"cheek_riv{i}", (cx - 0.3, 6.0, 4.8), (cx + 0.3, 6.6, 5.05), p["bright"])
    # 頂の飾り (台 + 珠 + 先端)
    m.box("crest_base", (7.15, 13.4, 7.15), (8.85, 14.1, 8.85), p["gold"])
    m.box("crest_ball", (7.3, 14.1, 7.3), (8.7, 15.05, 8.7), p["accent"])
    m.box("crest_shine", (7.55, 14.35, 7.15), (8.0, 14.8, 7.35), p["white"])
    m.box("crest_tip", (7.68, 15.05, 7.68), (8.32, 15.7, 8.32), p["gold"])


def chest(m: Model, p: dict) -> None:
    """胸当て。胸板 + 腹部の札 + 3 枚の肩甲 + 襟 + 紋章 + 帯。"""
    # 胴 (肩幅は広く、腰は窄める)
    m.box("torso", (4.5, 7.0, 5.4), (11.5, 11.3, 10.6), p["steel"])
    m.box("torso_low", (5.0, 3.4, 5.6), (11.0, 7.2, 10.4), p["steel"])
    m.box("torso_waist", (5.2, 3.1, 5.8), (10.8, 3.6, 10.2), p["dark"])
    # 胸板 (前へ張り出す 3 段)
    m.box("plate0", (5.0, 8.3, 4.9), (11.0, 11.3, 5.65), p["steel"])
    m.box("plate1", (5.4, 6.5, 4.8), (10.6, 8.3, 5.55), p["steel"])
    m.box("plate2", (5.8, 4.8, 4.85), (10.2, 6.5, 5.5), p["dark"])
    m.box("plate_rim", (5.15, 11.1, 4.8), (10.85, 11.65, 5.75), p["gold"])
    m.box("plate_edge", (4.95, 8.1, 4.85), (11.05, 8.45, 5.6), p["gold"])
    # 襟
    m.box("neck", (6.5, 11.2, 6.5), (9.5, 12.7, 9.5), p["dark"])
    m.box("neck_rim", (6.3, 12.5, 6.3), (9.7, 13.15, 9.7), p["gold"])
    # 肩甲 (下地 + 外へ広がる 3 枚の札 + 縁 + 鋲)
    for i, s in ((0, -1), (1, 1)):
        cx = 8 + s * 3.4
        m.box(f"shoulder{i}", (cx - 1.62, 8.9, 5.55), (cx + 1.62, 12.1, 10.45), p["dark"])
        for k in range(3):
            w = 1.85 - k * 0.18
            y = 11.15 - k * 1.05
            # 上ほど窄めて肩を丸く見せる
            inset = (2 - k) * 0.16
            z0p, z1p = 5.05 + inset, 10.95 - inset
            m.box(f"pauldron_gap{i}_{k}", (cx - w - 0.05, y - 0.16, z0p - 0.05),
                  (cx + w + 0.05, y + 0.02, z1p + 0.05), p["black"])
            m.box(f"pauldron{i}_{k}", (cx - w, y, z0p), (cx + w, y + 0.95, z1p), p["steel"])
        m.box(f"pauldron_rim{i}", (cx - 1.4, 12.05, 6.5), (cx + 1.4, 12.75, 9.5), p["gold"])
        m.box(f"pauldron_riv{i}", (cx - 0.32, 11.95, 6.3), (cx + 0.32, 12.4, 6.6), p["bright"])
    # 紋章
    m.box("emblem_rim", (6.75, 6.8, 4.65), (9.25, 9.6, 5.0), p["gold"])
    m.box("emblem", (7.05, 7.1, 4.5), (8.95, 9.3, 4.8), p["accent"])
    m.box("emblem_core", (7.62, 7.75, 4.42), (7.95, 8.6, 4.58), p["white"])
    # 帯と留め金
    m.box("belt", (4.3, 2.9, 5.2), (11.7, 4.1, 10.8), p["gold"])
    m.box("buckle_rim", (7.0, 2.6, 5.1), (9.0, 2.9, 5.45), p["gold"])
    m.box("buckle", (7.2, 2.95, 4.95), (8.8, 4.15, 5.35), p["accent_dim"])
    # 背中の脊椎
    m.box("spine", (7.55, 5.6, 10.45), (8.45, 10.4, 10.95), p["dark"])
    m.box("spine_riv", (7.7, 6.4, 10.9), (8.3, 7.0, 11.15), p["bright"])


def legs(m: Model, p: dict) -> None:
    """脚当て。腰甲 + 草摺 + 腿甲 + 膝当て + 脛当て。"""
    m.box("hip", (4.3, 9.4, 5.4), (11.7, 12.2, 10.6), p["steel"])
    m.box("belt", (4.1, 11.8, 5.2), (11.9, 12.6, 10.8), p["gold"])
    m.box("buckle", (7.15, 11.6, 5.0), (8.85, 12.8, 5.45), p["accent"])
    # 草摺 (垂れ札)
    for i, ox in enumerate((-2.2, 0.0, 2.2)):
        m.box(f"tasset{i}", (8 + ox - 0.95, 6.9, 5.0), (8 + ox + 0.95, 9.4, 5.45), p["steel"])
        m.box(f"tasset_rim{i}", (8 + ox - 1.0, 6.75, 4.95), (8 + ox + 1.0, 7.1, 5.5), p["gold"])
        m.box(f"tasset_top{i}", (8 + ox - 1.0, 9.3, 5.0), (8 + ox + 1.0, 9.6, 5.45), p["dark"])
    # 脚
    for i, s in ((0, -1), (1, 1)):
        x = 8 + s * 2.65
        m.box(f"thigh{i}", (x - 1.95, 4.4, 5.75), (x + 1.95, 9.5, 10.35), p["steel"])
        m.box(f"thigh_lame{i}", (x - 1.75, 5.0, 5.4), (x + 1.75, 8.6, 5.85), p["steel"])
        m.box(f"thigh_gap{i}", (x - 1.8, 4.75, 5.45), (x + 1.8, 5.0, 5.8), p["black"])
        m.box(f"thigh_band{i}", (x - 2.0, 8.8, 5.7), (x + 2.0, 9.5, 10.4), p["gold"])
        m.box(f"knee{i}", (x - 2.1, 2.7, 5.15), (x + 2.1, 4.5, 10.85), p["dark"])
        m.box(f"knee_cop{i}", (x - 1.4, 2.85, 4.75), (x + 1.4, 4.45, 5.35), p["steel"])
        m.box(f"knee_gem{i}", (x - 0.55, 3.3, 4.58), (x + 0.55, 4.15, 4.8), p["accent"])
        m.box(f"shin{i}", (x - 1.75, 1.3, 5.95), (x + 1.75, 3.0, 10.15), p["dark"])
        m.box(f"shin_plate{i}", (x - 1.45, 1.5, 5.6), (x + 1.45, 2.95, 6.0), p["steel"])
        m.box(f"shin_rim{i}", (x - 1.85, 1.2, 5.9), (x + 1.85, 1.8, 10.2), p["gold"])


def boots(m: Model, p: dict) -> None:
    """靴。脚絆 + 足甲 + 爪先 + 踵 + 靴底 + 留め革 (幅と段差で足を表現)。"""
    for i, s in ((0, -1), (1, 1)):
        x = 8 + s * 2.65
        # 脚絆 (細め)
        m.box(f"shaft{i}", (x - 1.7, 4.4, 6.0), (x + 1.7, 9.2, 10.6), p["steel"])
        m.box(f"cuff{i}", (x - 1.95, 8.8, 5.8), (x + 1.95, 9.9, 10.8), p["gold"])
        m.box(f"shaft_plate{i}", (x - 1.35, 4.9, 5.55), (x + 1.35, 8.5, 6.0), p["dark"])
        # 足首
        m.box(f"ankle{i}", (x - 2.0, 3.0, 5.3), (x + 2.0, 4.7, 10.7), p["dark"])
        # 足 (脚絆より広い)
        m.box(f"foot{i}", (x - 2.35, 1.8, 3.4), (x + 2.35, 3.4, 10.6), p["steel"])
        m.box(f"toe{i}", (x - 2.4, 2.4, 2.95), (x + 2.4, 4.05, 4.5), p["steel"])
        m.box(f"toe_band{i}", (x - 2.42, 3.95, 3.6), (x + 2.42, 4.22, 10.2), p["gold"])
        m.box(f"heel{i}", (x - 2.2, 1.8, 9.7), (x + 2.2, 3.95, 10.9), p["dark"])
        m.box(f"sole{i}", (x - 2.5, 1.25, 2.85), (x + 2.5, 1.9, 11.0), p["black"])
        # 留め革と金具
        m.box(f"strap{i}", (x - 2.1, 4.7, 5.85), (x + 2.1, 5.6, 10.55), p["leather"])
        m.box(f"buckle{i}", (x - 0.55, 4.6, 5.6), (x + 0.55, 5.7, 5.95), p["gold"])
        m.box(f"buckle2{i}", (x - 0.5, 2.9, 3.2), (x + 0.5, 3.9, 3.6), p["gold"])


# ------------------------------------------------------------------ 素材


def gem(m: Model, p: dict, **kw) -> None:
    """宝石 (ブリリアントカット風: 冠 + パビリオン)。"""
    m.box("crown", (5.6, 8.6, 5.6), (10.4, 11.0, 10.4), p["accent"])
    m.box("table", (6.5, 11.0, 6.5), (9.5, 11.9, 9.5), p["accent"])
    m.box("girdle", (5.3, 8.2, 5.3), (10.7, 8.8, 10.7), p["accent_dim"])
    m.box("pav1", (6.0, 6.4, 6.0), (10.0, 8.2, 10.0), p["accent"])
    m.box("pav2", (6.9, 4.8, 6.9), (9.1, 6.4, 9.1), p["accent"])
    m.box("pav3", (7.5, 3.6, 7.5), (8.5, 4.8, 8.5), p["accent_dim"])
    m.box("core", (7.3, 8.0, 6.1), (8.7, 9.6, 6.5), p["white"])


def shard(m: Model, p: dict) -> None:
    """欠片。"""
    m.box("shard", (5.9, 4.6, 6.2), (10.1, 11.4, 9.8), p["accent"],
          rotation={"origin": [8, 8, 8], "axis": "z", "angle": 18})
    m.box("shard2", (6.6, 3.4, 6.6), (9.4, 5.4, 9.4), p["accent_dim"],
          rotation={"origin": [8, 8, 8], "axis": "z", "angle": 18})
    m.box("shard_core", (7.3, 6.8, 5.9), (8.7, 9.2, 6.4), p["white"])
    m.box("facet", (6.2, 9.6, 6.0), (9.8, 10.6, 6.5), p["white"])


def stone_chunk(m: Model, p: dict) -> None:
    """岩塊。不揃いな面 + 鉱脈 + 影。"""
    m.box("rock0", (5.0, 4.6, 5.2), (11.0, 10.4, 10.8), p["stone"])
    m.box("rock1", (4.2, 3.6, 6.2), (6.4, 6.2, 9.8), p["stone"])
    m.box("rock2", (9.4, 3.8, 5.6), (11.9, 7.0, 10.2), p["stone"])
    m.box("rock3", (6.2, 10.0, 6.0), (10.4, 12.0, 10.0), p["stone"])
    m.box("rock4", (7.0, 12.0, 7.0), (9.6, 13.0, 9.2), p["stone"])
    m.box("shade0", (4.2, 3.6, 5.2), (6.4, 5.2, 6.2), p["stone_dim"])
    m.box("shade1", (9.8, 4.6, 10.0), (11.9, 6.2, 10.8), p["stone_dim"])
    m.box("ore", (6.0, 6.4, 10.55), (9.8, 9.6, 11.15), p["accent"])
    m.box("ore2", (5.4, 8.8, 9.0), (7.2, 10.2, 10.4), p["accent"])
    m.box("ore3", (10.4, 7.0, 6.6), (11.4, 9.0, 9.0), p["accent"])
    m.box("chip", (9.6, 4.7, 9.7), (11.7, 6.5, 11.5), p["stone_dim"])


def _prism(m: Model, name, cx, y0, y1, half, z, mat_a, mat_b, tilt: float = 0.0) -> None:
    """柱。上へ窄まる 3 段 (結晶・角・牙などに使う)。"""
    h = y1 - y0
    s1 = y0 + h * 0.60
    s2 = y0 + h * 0.84
    rot = {"origin": [cx, y0, z], "axis": "z", "angle": tilt} if tilt else None
    m.box(f"{name}_b", (cx - half, y0, z - half), (cx + half, s1, z + half), mat_a, rotation=rot)
    m.box(f"{name}_m", (cx - half * 0.68, s1, z - half * 0.68), (cx + half * 0.68, s2, z + half * 0.68),
          mat_a, rotation=rot)
    m.box(f"{name}_t", (cx - half * 0.30, s2, z - half * 0.30), (cx + half * 0.30, y1, z + half * 0.30),
          mat_b, rotation=rot)


def crystal(m: Model, p: dict) -> None:
    """結晶クラスタ (大 1 本 + 小 2 本 + 台座)。"""
    _prism(m, "c0", 8.0, 3.0, 13.4, 1.45, 8.0, p["accent"], p["accent_dim"])
    _prism(m, "c1", 5.3, 3.0, 9.8, 0.92, 8.0, p["accent"], p["accent_dim"], tilt=-16)
    _prism(m, "c2", 10.7, 3.0, 8.6, 0.80, 8.0, p["accent"], p["accent_dim"], tilt=16)
    m.box("base", (4.7, 2.0, 6.2), (11.3, 3.2, 9.8), p["accent_dim"])


def drop_mat(m: Model, p: dict) -> None:
    """雫 / 実。丸い胴 + 尖った先端。"""
    _orb(m, "body", 8, 6.8, 8, 2.45, p["accent"])
    _prism(m, "tip", 8.0, 9.0, 13.2, 0.95, 8.0, p["accent"], p["accent_dim"])
    m.box("glow", (7.5, 7.4, 10.2), (8.0, 7.9, 10.6), p["white"])


def scale_mat(m: Model, p: dict) -> None:
    """鱗。根元が広く先が丸い板 + 中央の稜線。"""
    m.box("s0", (5.4, 9.6, 6.6), (10.6, 11.7, 9.4), p["accent"])
    m.box("s1", (5.9, 7.3, 6.4), (10.1, 9.6, 9.6), p["accent"])
    m.box("s2", (6.5, 5.1, 6.2), (9.5, 7.3, 9.8), p["accent"])
    m.box("s3", (7.2, 3.4, 6.5), (8.8, 5.1, 9.5), p["accent_dim"])
    m.box("ridge", (7.7, 3.6, 9.15), (8.3, 11.8, 10.15), p["accent_dim"])
    m.box("sheen", (5.9, 9.3, 9.0), (10.1, 9.9, 9.5), p["accent_dim"])


def bone_mat(m: Model, p: dict) -> None:
    """骨。軸 + 両端の瘤 + 飾り。"""
    m.box("shaft", (7.25, 4.4, 7.25), (8.75, 11.6, 8.75), p["bone"])
    for i, yy in ((0, 3.2), (1, 10.6)):
        m.box(f"knob_a{i}", (6.4, yy, 6.9), (7.6, yy + 2.0, 9.1), p["bone"])
        m.box(f"knob_b{i}", (8.4, yy, 6.9), (9.6, yy + 2.0, 9.1), p["bone"])
        m.box(f"knob_c{i}", (6.9, yy, 6.4), (9.1, yy + 2.0, 7.6), p["bone"])
        m.box(f"knob_d{i}", (6.9, yy, 8.4), (9.1, yy + 2.0, 9.6), p["bone"])
        m.box(f"cap{i}", (7.4, yy + 0.7, 7.4), (8.6, yy + 1.3, 8.6), p["accent_dim"])
    m.box("charm", (7.05, 7.1, 7.05), (8.95, 9.1, 8.95), p["accent"])
    m.box("ring", (7.6, 12.0, 7.6), (8.4, 12.8, 8.4), p["accent_dim"])


def thread_mat(m: Model, p: dict) -> None:
    """糸。うねる糸 + 巻き取り。"""
    pts = [(8.0, 3.4), (6.9, 5.8), (9.1, 8.2), (6.9, 10.6), (9.1, 12.8)]
    for i in range(len(pts) - 1):
        _bone(m, f"th{i}", pts[i], pts[i + 1], 0.72, 7.55, 8.45, p["accent"])
    m.box("spool", (6.1, 2.0, 6.1), (9.9, 3.4, 9.9), p["accent_dim"])
    m.box("spool_rim", (5.9, 1.6, 5.9), (10.1, 2.2, 10.1), p["accent_dim"])
    m.box("glow", (7.55, 7.5, 9.55), (8.0, 8.2, 9.9), p["white"])


def rose_mat(m: Model, p: dict) -> None:
    """花。花びらの層 + 中心 + 茎。"""
    m.box("pet0", (5.7, 5.2, 6.3), (10.3, 7.4, 9.7), p["accent"])
    m.box("pet1", (6.2, 7.2, 6.0), (9.8, 9.6, 10.0), p["accent"])
    m.box("pet2", (6.9, 9.4, 6.6), (9.1, 11.2, 9.4), p["accent_dim"])
    m.box("heart", (7.15, 7.4, 7.15), (8.85, 9.2, 8.85), p["accent_dim"])
    m.box("stem", (7.55, 2.0, 7.55), (8.45, 5.4, 8.45), p["leather"])
    m.box("leaf", (8.4, 3.0, 7.3), (10.5, 4.2, 8.7), p["accent_dim"])
    m.box("leaf2", (5.5, 3.8, 7.4), (7.6, 5.0, 8.6), p["accent_dim"])


def wisp_mat(m: Model, p: dict) -> None:
    """霧の塊。芯の珠 + 漂う帯 + 尾。"""
    _orb(m, "core", 8, 8.6, 8, 2.15, p["accent"])
    m.box("band", (5.45, 8.0, 5.45), (10.55, 9.2, 10.55), p["accent_dim"])
    m.box("tail", (7.25, 3.4, 7.25), (8.75, 6.8, 8.75), p["accent_dim"])
    m.box("tail2", (6.5, 1.8, 6.5), (9.5, 3.6, 9.5), p["accent_dim"])
    m.box("glow", (7.45, 8.2, 10.2), (7.95, 8.8, 10.55), p["white"])


def splinter(m: Model, p: dict) -> None:
    """破片。尖った 3 本。"""
    _prism(m, "sp0", 8.0, 3.0, 13.0, 1.05, 8.0, p["accent"], p["accent_dim"])
    _prism(m, "sp1", 5.9, 3.0, 9.2, 0.62, 8.3, p["accent"], p["accent_dim"], tilt=-24)
    _prism(m, "sp2", 10.1, 3.0, 8.6, 0.58, 7.7, p["accent"], p["accent_dim"], tilt=22)
    m.box("core", (7.55, 5.2, 9.0), (7.95, 6.2, 9.35), p["white"])


def pouch(m: Model, p: dict) -> None:
    """袋。"""
    m.box("bag", (4.8, 4.4, 5.2), (11.2, 11.2, 10.8), p["cloth"])
    m.box("bag_low", (5.4, 3.6, 5.8), (10.6, 4.6, 10.2), p["cloth"])
    m.box("neck", (6.3, 10.6, 6.3), (9.7, 12.4, 9.7), p["leather"])
    m.box("tie", (6.0, 11.9, 6.0), (10.0, 12.7, 10.0), p["gold"])
    m.box("ring", (7.2, 12.7, 7.2), (8.8, 13.5, 8.8), p["gold"])
    m.box("gem", (7.3, 6.8, 4.9), (8.7, 8.8, 5.3), p["accent"])
    m.box("patch", (5.2, 7.0, 5.1), (6.6, 8.4, 5.35), p["leather"])
