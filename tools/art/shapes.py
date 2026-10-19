"""武器・防具・素材の立体形状 (箱の集合) を定義する。

モデル空間: 1 単位 = 1/16 ブロック。中心は (8, 8, 8)。
武器は原則として +Y 方向 (刃先を上) に持ち、Z 方向は薄く (掴む向きに flat)。
"""
from __future__ import annotations

from .boxmodel import Model, box
from .materials import Material
from .core import hexcolor, mix, shade

# ------------------------------------------------------------------ 素材セット


def make_palette(accent: int, style: str = "steel") -> dict:
    """accent 色から一組の素材を作る。"""
    acc = hexcolor(accent)
    pal = {
        "steel": Material("metal", (166, 174, 190, 255), metal=0.95, rough=0.25),
        "dark": Material("metal", (74, 80, 96, 255), metal=0.85, rough=0.35),
        "black": Material("metal", (38, 40, 52, 255), metal=0.7, rough=0.5),
        "gold": Material("metal", (214, 168, 66, 255), metal=1.0, rough=0.18),
        "bronze": Material("metal", (172, 116, 62, 255), metal=0.9, rough=0.3),
        "wood": Material("wood", (118, 80, 48, 255)),
        "darkwood": Material("wood", (74, 48, 30, 255)),
        "leather": Material("leather", (70, 48, 38, 255)),
        "bone": Material("bone", (232, 226, 208, 255)),
        "stone": Material("stone", (116, 112, 104, 255)),
        "obsidian": Material("obsidian", (26, 18, 40, 255), accent=acc),
        "cloth": Material("cloth", shade(acc, 0.55)),
        "accent": Material("crystal", acc, emissive=True),
        "accent_dim": Material("crystal", shade(acc, 0.7)),
        "rune": Material("rune", (38, 30, 54, 255), accent=acc),
        "white": Material("flat", (246, 248, 252, 255)),
        "bonewhite": Material("bone", (236, 230, 212, 255)),
    }
    if style == "blood":
        pal["steel"] = Material("metal", (176, 168, 176, 255), metal=0.95, rough=0.25)
        pal["accent"] = Material("crystal", hexcolor(0xff2a55), emissive=True)
    elif style == "void":
        pal["steel"] = Material("metal", (96, 88, 124, 255), metal=0.9, rough=0.3)
        pal["accent"] = Material("crystal", hexcolor(0x8a5cff), emissive=True)
    elif style == "ice":
        pal["steel"] = Material("ice", (176, 222, 244, 255))
        pal["accent"] = Material("crystal", hexcolor(0xaee9ff), emissive=True)
    elif style == "bone":
        pal["steel"] = Material("bone", (234, 228, 210, 255))
    return pal


# ------------------------------------------------------------------ 小道具


def _center_x(w: float) -> float:
    return 8.0 - w / 2.0


def ridge(m: Model, name, x0, x1, z0, z1, y0, y1, mat):
    """刃の稜線 (中央の盛り上がり)。"""
    m.box(name, (x0, y0, z0), (x1, y1, z1), mat, hide=("up", "down"))


def blade(m: Model, name, y0, y1, w, z0, z1, mat, ridge_mat=None, tip=True):
    """剣身。width を段階的に落として先端を尖らせる。"""
    m.box(name, (8 - w / 2, y0, z0), (8 + w / 2, y1 - (1.6 if tip else 0), z1), mat)
    if tip:
        m.box(name + "_tip", (8 - w / 2 + 0.3, y1 - 1.6, z0 + 0.16),
              (8 + w / 2 - 0.3, y1, z1 - 0.16), mat)
        m.box(name + "_point", (8 - w / 2 + 0.68, y1, (z0 + z1) / 2 - 0.18),
              (8 + w / 2 - 0.68, y1 + 0.8, (z0 + z1) / 2 + 0.18), mat)
    if ridge_mat is not None:
        ridge(m, name + "_ridge", 8 - 0.24, 8 + 0.24, z0 - 0.16, z1 + 0.16, y0 + 0.6, y1 - 1.4, ridge_mat)


def guard(m: Model, name, y, w, h, z0, z1, mat, accent=None):
    """鍔。"""
    m.box(name, (8 - w / 2, y, z0), (8 + w / 2, y + h, z1), mat)
    if accent is not None:
        m.box(name + "_gem", (8 - 0.6, y + h * 0.2, z0 - 0.3), (8 + 0.6, y + h * 0.8, z1 + 0.3), accent)


def grip(m: Model, name, y0, y1, w, z0, z1, mat, wrap=None):
    """柄。"""
    m.box(name, (8 - w / 2, y0, z0), (8 + w / 2, y1, z1), mat)
    if wrap is not None:
        y = int(y0) + 1
        i = 0
        while y + 0.45 < y1:
            m.box(f"{name}_wrap{i}", (8 - w / 2 - 0.08, y, z0 - 0.08),
                  (8 + w / 2 + 0.08, y + 0.45, z1 + 0.08), wrap)
            y += 1.15
            i += 1


def pommel(m: Model, name, y0, y1, w, z0, z1, mat):
    m.box(name, (8 - w / 2, y0, z0), (8 + w / 2, y1, z1), mat)


# ------------------------------------------------------------------ 武器

def sword(m: Model, p: dict, length: float = 13.0, w: float = 2.4, thick: float = 1.0,
          style: str = "steel", gem: bool = True) -> None:
    """剣。刃先 y=14.6 付近。"""
    z0, z1 = 8 - thick / 2, 8 + thick / 2
    top = 8 + length / 2
    bot = 8 - length / 2
    y_guard = bot + 5.0
    blade(m, "blade", y_guard, top, w, z0, z1, p["steel"])
    guard(m, "guard", y_guard - 0.9, 6.4, 0.9, z0 - 0.8, z1 + 0.8, p["gold"],
          p["accent"] if gem else None)
    grip(m, "grip", bot + 1.6, y_guard - 0.9, 1.7, z0 - 0.3, z1 + 0.3, p["leather"], p["gold"])
    pommel(m, "pommel", bot, bot + 1.7, 2.8, z0 - 0.7, z1 + 0.7, p["gold"])


def greatsword(m: Model, p: dict) -> None:
    z0, z1 = 7.4, 8.6
    blade(m, "blade", 8.0, 15.0, 3.0, z0, z1, p["steel"], p["accent_dim"])
    guard(m, "guard", 7.0, 9.0, 1.0, z0 - 1.0, z1 + 1.0, p["gold"], p["accent"])
    grip(m, "grip", 3.4, 7.0, 2.0, z0 - 0.4, z1 + 0.4, p["leather"], p["gold"])
    pommel(m, "pommel", 2.2, 3.5, 3.4, z0 - 0.9, z1 + 0.9, p["gold"])


def katana(m: Model, p: dict, style: str = "steel") -> None:
    """刀。反り刃 (刃先側が細くなる)。"""
    z0, z1 = 7.6, 8.4
    m.box("blade", (7.2, 7.6, z0), (8.6, 14.2, z1), p["steel"])
    m.box("blade_tip", (7.4, 14.2, z0 + 0.15), (8.4, 15.4, z1 - 0.15), p["steel"])
    m.box("ridge", (7.7, 7.8, z0 - 0.3), (8.1, 13.8, z1 + 0.3), p["accent_dim"], hide=("up", "down"))
    m.box("tsuba", (6.4, 7.0, 6.6), (9.6, 7.8, 9.4), p["gold"], hide=("up", "down"))
    m.box("tsuba_gem", (7.5, 7.1, 6.3), (8.5, 7.7, 6.7), p["accent"])
    grip(m, "grip", 3.6, 7.0, 1.5, 7.5, 8.5, p["cloth"], p["gold"])
    pommel(m, "pommel", 2.6, 3.7, 2.2, 7.2, 8.8, p["gold"])


def dagger(m: Model, p: dict, twin: bool = False) -> None:
    z0, z1 = 7.6, 8.4
    offs = [-1.6, 1.6] if twin else [0.0]
    for i, ox in enumerate(offs):
        x = 8 + ox
        m.box(f"blade{i}", (x - 0.8, 6.6, z0), (x + 0.8, 12.4, z1), p["steel"])
        m.box(f"tip{i}", (x - 0.55, 12.4, z0 + 0.2), (x + 0.55, 13.6, z1 - 0.2), p["steel"])
        m.box(f"ridge{i}", (x - 0.22, 6.9, z0 - 0.25), (x + 0.22, 12.0, z1 + 0.25), p["accent_dim"],
              hide=("up", "down"))
        m.box(f"guard{i}", (x - 1.9, 5.8, z0 - 0.4), (x + 1.9, 6.6, z1 + 0.4), p["gold"])
        m.box(f"grip{i}", (x - 0.65, 3.2, 7.6), (x + 0.65, 5.8, 8.4), p["leather"])
        m.box(f"pommel{i}", (x - 0.9, 2.4, 7.4), (x + 0.9, 3.3, 8.6), p["gold"])


def axe(m: Model, p: dict, haft_len: float = 11.0, head_w: float = 6.0, style: str = "steel") -> None:
    """斧。柄 + 曲面をもった刃。"""
    top = 8 + haft_len / 2
    bot = 8 - haft_len / 2
    z0, z1 = 7.3, 8.7
    m.box("haft", (7.42, bot, z0), (8.58, top - 1.4, z1), p["wood"])
    m.box("haft_wrap", (7.35, bot + 0.6, z0 - 0.15), (8.65, bot + 3.2, z1 + 0.15), p["leather"])
    m.box("haft_cap", (7.3, bot, z0 - 0.2), (8.7, bot + 1.0, z1 + 0.2), p["gold"])
    # 刃: 付け根 →  widest 部分 → 刃先
    m.box("head_root", (7.4, top - 3.2, z0 - 0.3), (9.4, top - 0.4, z1 + 0.3), p["dark"])
    m.box("head_mid", (9.0, top - 4.0, z0 - 0.5), (11.6, top - 0.1, z1 + 0.5), p["steel"])
    m.box("head_edge", (11.2, top - 4.4, z0 - 0.8), (12.4, top + 0.3, z1 + 0.8), p["steel"])
    m.box("head_top", (9.0, top - 0.1, z0 - 0.5), (11.6, top + 0.5, z1 + 0.5), p["steel"])
    m.box("head_bot", (9.0, top - 4.5, z0 - 0.5), (11.6, top - 3.9, z1 + 0.5), p["steel"])
    m.box("rune", (9.6, top - 2.6, z0 - 0.75), (10.6, top - 1.2, z0 - 0.45), p["accent"])


def hammer(m: Model, p: dict, size: float = 5.0) -> None:
    """鎚。"""
    top = 8 + 5.0
    bot = 8 - 6.5
    m.box("haft", (7.42, bot, 7.5), (8.58, top - 2.4, 8.6), p["wood"])
    m.box("haft_wrap", (7.45, bot + 0.8, 7.35), (8.55, bot + 3.4, 8.65), p["leather"])
    m.box("head", (8 - size / 2, top - 2.6, 6.4), (8 + size / 2, top, 9.6), p["dark"])
    m.box("head_face", (8 + size / 2 - 0.7, top - 2.7, 6.3), (8 + size / 2 + 0.2, top + 0.1, 9.7), p["steel"])
    m.box("core", (8 - 0.9, top - 2.0, 6.2), (8 + 0.9, top - 0.6, 6.5), p["accent"])
    m.box("collar", (7.4, top - 2.9, 6.9), (8.6, top - 2.3, 9.1), p["gold"])


def mace(m: Model, p: dict) -> None:
    """メイス (球状の頭)。"""
    top = 8 + 5.5
    bot = 8 - 6.0
    m.box("haft", (7.42, bot, 7.6), (8.58, top - 2.0, 8.6), p["wood"])
    m.box("head", (6.2, top - 2.4, 6.2), (9.8, top + 1.4, 9.8), p["steel"])
    m.box("head_top", (6.9, top + 1.4, 6.9), (9.1, top + 2.3, 9.1), p["steel"])
    m.box("head_bot", (6.9, top - 3.3, 6.9), (9.1, top - 2.4, 9.1), p["steel"])
    m.box("gem", (7.5, top - 0.7, 6.0), (8.5, top + 0.7, 6.4), p["accent"])
    m.box("collar", (7.4, top - 2.6, 7.1), (8.6, top - 1.9, 8.9), p["gold"])


def spear(m: Model, p: dict, length: float = 15.0, head: str = "leaf") -> None:
    """槍。"""
    top = 8 + length / 2
    bot = 8 - length / 2
    m.box("shaft", (7.42, bot + 2.0, 7.6), (8.58, top - 2.2, 8.6), p["wood"])
    m.box("shaft_wrap", (7.55, bot + 2.4, 7.45), (8.45, bot + 5.0, 8.55), p["leather"])
    m.box("collar", (7.4, top - 2.6, 7.2), (8.6, top - 1.9, 8.8), p["gold"])
    if head == "leaf":
        m.box("head", (7.1, top - 2.2, 7.5), (8.9, top + 0.6, 8.5), p["steel"])
        m.box("tip", (7.6, top + 0.6, 7.8), (8.4, top + 2.4, 8.2), p["steel"])
    elif head == "trident":
        m.box("head", (7.5, top - 2.2, 7.5), (8.5, top + 0.4, 8.5), p["steel"])
        m.box("tip", (7.7, top + 0.4, 7.8), (8.3, top + 2.2, 8.2), p["steel"])
        for ox in (-2.6, 2.6):
            m.box(f"prong{ox}", (8 + ox - 0.55, top - 2.6, 7.6), (8 + ox + 0.55, top - 0.2, 8.4), p["steel"])
            m.box(f"prong_tip{ox}", (8 + ox - 0.4, top - 0.2, 7.7), (8 + ox + 0.4, top + 1.4, 8.3), p["steel"])
    elif head == "astral":
        m.box("head", (7.2, top - 2.0, 7.5), (8.8, top + 0.4, 8.5), p["steel"])
        m.box("ring", (6.9, top - 0.4, 6.9), (9.1, top + 1.6, 9.1), p["accent"], hide=("up", "down"))
    m.box("gem", (7.6, top - 1.4, 7.2), (8.4, top - 0.4, 7.5), p["accent"])


def staff(m: Model, p: dict, orb: str = "orb") -> None:
    """杖。"""
    top = 8 + 6.5
    bot = 8 - 7.0
    m.box("shaft", (7.42, bot, 7.6), (8.58, top - 2.4, 8.6), p["darkwood"])
    m.box("shaft_wrap", (7.45, bot + 1.0, 7.45), (8.55, bot + 3.4, 8.55), p["gold"])
    m.box("shaft_ring", (7.3, top - 3.0, 7.3), (8.7, top - 2.2, 8.7), p["gold"])
    if orb == "orb":
        m.box("orb", (6.9, top - 2.4, 6.9), (9.1, top + 1.2, 9.1), p["accent"])
        m.box("orb_core", (7.5, top - 1.4, 7.5), (8.5, top - 0.2, 8.5), p["white"])
        m.box("cage", (6.5, top - 2.8, 6.5), (9.5, top - 2.2, 9.5), p["gold"], hide=("up", "down"))
        m.box("cage2", (6.5, top + 1.2, 6.5), (9.5, top + 1.8, 9.5), p["gold"], hide=("up", "down"))
    elif orb == "void":
        m.box("orb", (6.6, top - 2.8, 6.6), (9.4, top + 1.6, 9.4), p["obsidian"])
        m.box("orb_core", (7.6, top - 1.6, 7.2), (8.4, top - 0.4, 7.6), p["accent"])
    else:  # 杖先 (结晶)
        m.box("crystal", (7.0, top - 1.2, 7.0), (9.0, top + 2.4, 9.0), p["accent"],
              rotation={"origin": [8, top, 8], "axis": "y", "angle": 45})


def wand(m: Model, p: dict) -> None:
    top = 8 + 5.0
    bot = 8 - 5.0
    m.box("shaft", (7.42, bot, 7.6), (8.58, top - 1.6, 8.6), p["wood"])
    m.box("crystal", (7.2, top - 1.6, 7.2), (8.8, top + 1.6, 8.8), p["accent"])
    m.box("crystal2", (7.7, top + 1.2, 7.7), (8.3, top + 2.6, 8.3), p["accent_dim"])
    m.box("ring", (7.3, top - 1.9, 7.3), (8.7, top - 1.3, 8.7), p["gold"])


def bow(m: Model, p: dict) -> None:
    """弓。弯曲した 2 本の枝 + 弦。"""
    m.box("limb_top", (6.4, 8.4, 7.4), (8.4, 14.8, 8.6), p["wood"],
          rotation={"origin": [8, 8, 8], "axis": "z", "angle": -16})
    m.box("limb_bot", (6.4, 1.2, 7.4), (8.4, 7.6, 8.6), p["wood"],
          rotation={"origin": [8, 8, 8], "axis": "z", "angle": 16})
    m.box("grip", (6.8, 6.0, 7.3), (8.2, 10.0, 8.7), p["leather"])
    m.box("string", (7.8, 1.8, 7.8), (8.2, 14.2, 8.2), p["white"], hide=("up", "down"))
    m.box("tip_top", (6.3, 14.2, 7.3), (8.5, 15.4, 8.7), p["accent"])
    m.box("tip_bot", (6.3, 0.6, 7.3), (8.5, 1.8, 8.7), p["accent"])


def scythe(m: Model, p: dict) -> None:
    """鎌。柄 + 湾曲した刃。"""
    m.box("haft", (7.42, 1.6, 7.4), (8.58, 12.6, 8.6), p["darkwood"])
    m.box("haft_wrap", (7.35, 2.2, 7.25), (8.65, 4.6, 8.75), p["leather"])
    m.box("collar", (7.2, 11.8, 7.1), (8.8, 12.6, 8.9), p["gold"])
    # 刃 (4 枚を階段状・角度付きにして湾曲を描く)
    m.box("blade0", (5.6, 12.4, 7.2), (9.6, 13.8, 8.8), p["steel"],
          rotation={"origin": [8, 12.6, 8], "axis": "z", "angle": 12})
    m.box("blade1", (8.0, 11.6, 7.2), (11.6, 13.0, 8.8), p["steel"],
          rotation={"origin": [8, 12.6, 8], "axis": "z", "angle": 4})
    m.box("blade2", (9.8, 9.8, 7.2), (13.0, 11.8, 8.8), p["steel"],
          rotation={"origin": [8, 12.6, 8], "axis": "z", "angle": -8})
    m.box("blade3", (10.4, 7.4, 7.2), (13.2, 9.8, 8.8), p["steel"],
          rotation={"origin": [8, 12.6, 8], "axis": "z", "angle": -22})
    m.box("edge", (9.4, 7.0, 7.0), (13.4, 9.4, 9.0), p["accent_dim"],
          rotation={"origin": [8, 12.6, 8], "axis": "z", "angle": -16})


def flail(m: Model, p: dict) -> None:
    """OBS: 鎖 + 玉。"""
    m.box("haft", (7.42, 4.0, 7.6), (8.58, 12.0, 8.6), p["darkwood"])
    for i in range(4):
        m.box(f"chain{i}", (7.7, 11.0 - i * 1.7, 7.7), (8.3, 12.0 - i * 1.7, 8.3), p["dark"])
    m.box("ball", (6.4, 2.0, 6.4), (9.6, 5.2, 9.6), p["dark"])
    m.box("ball_core", (7.4, 3.0, 6.2), (8.6, 4.2, 6.5), p["accent"])
    for a in (0, 45, 90, 135):
        m.box(f"spike{a}", (7.6, 2.6, 5.2), (8.4, 4.6, 6.4), p["steel"],
              rotation={"origin": [8, 3.6, 8], "axis": "y", "angle": a})


def chakram(m: Model, p: dict) -> None:
    """輪刃。8 個の刃片を円形に並べる。"""
    import math
    for i in range(8):
        a = i * 45.0
        rad = math.radians(a)
        ox = math.cos(rad) * 3.1
        oy = math.sin(rad) * 3.1
        m.box(f"blade{i}", (8 + ox - 0.85, 8 + oy - 0.85, 7.4), (8 + ox + 0.85, 8 + oy + 0.85, 8.6),
              p["steel"], rotation={"origin": [8 + ox, 8 + oy, 8], "axis": "z", "angle": a})
        m.box(f"tip{i}", (8 + ox - 0.3, 8 + oy - 0.3, 7.2), (8 + ox + 0.3, 8 + oy + 0.3, 8.8),
              p["steel"], rotation={"origin": [8 + ox, 8 + oy, 8], "axis": "z", "angle": a})
    m.box("core", (7.2, 7.2, 7.2), (8.8, 8.8, 8.8), p["accent"])
    m.box("core_glow", (7.6, 7.6, 7.0), (8.4, 8.4, 7.3), p["white"])


def tome(m: Model, p: dict) -> None:
    """魔道書 (表・裏・背の板 + 頁)。"""
    m.box("pages", (4.3, 3.4, 6.7), (12.4, 12.6, 9.3), p["bonewhite"])
    m.box("cover_front", (3.6, 3.0, 5.9), (12.4, 13.0, 6.7), p["cloth"])
    m.box("cover_back", (3.6, 3.0, 9.3), (12.4, 13.0, 10.1), p["cloth"])
    m.box("spine", (3.0, 3.0, 5.8), (4.4, 13.0, 10.2), p["darkwood"])
    m.box("clasp", (2.7, 6.0, 5.7), (3.7, 7.6, 10.3), p["gold"])
    m.box("gem", (5.4, 6.4, 5.5), (7.4, 8.4, 5.9), p["accent"])
    m.box("ribbon", (7.8, 3.0, 5.4), (8.6, 5.2, 5.9), p["accent_dim"])


def gauntlet(m: Model, p: dict, big: bool = False) -> None:
    """拳套。"""
    s = 1.35 if big else 1.0
    m.box("hand", (8 - 2.6 * s, 4.2, 6.2), (8 + 2.6 * s, 8.2, 9.8), p["steel"])
    m.box("cuff", (8 - 3.0 * s, 2.6, 5.8), (8 + 3.0 * s, 4.4, 10.2), p["dark"])
    m.box("cuff_trim", (8 - 3.1 * s, 2.4, 5.7), (8 + 3.1 * s, 3.0, 10.3), p["gold"])
    for i, ox in enumerate((-1.9, -0.65, 0.65, 1.9)):
        m.box(f"finger{i}", (8 + ox * s - 0.5 * s, 7.6, 6.8), (8 + ox * s + 0.5 * s, 11.0 * s + 2.0, 9.2),
              p["steel"])
    m.box("core", (8 - 0.8 * s, 5.0, 5.9), (8 + 0.8 * s, 7.0, 6.3), p["accent"])


def claw(m: Model, p: dict) -> None:
    """爪。"""
    for i, ox in enumerate((-2.0, 0.0, 2.0)):
        m.box(f"claw{i}", (8 + ox - 0.55, 7.0, 7.2), (8 + ox + 0.55, 12.6, 8.8), p["bone"])
        m.box(f"claw_tip{i}", (8 + ox - 0.35, 12.6, 7.5), (8 + ox + 0.35, 14.2, 8.5), p["bone"])
    m.box("band", (5.0, 6.2, 6.6), (11.0, 7.4, 9.4), p["dark"])
    m.box("gem", (7.4, 6.3, 6.3), (8.6, 7.3, 6.7), p["accent"])


def horn(m: Model, p: dict) -> None:
    """戦角。"""
    m.box("horn", (4.6, 4.0, 6.6), (11.4, 8.6, 9.4), p["bone"],
          rotation={"origin": [8, 6.4, 8], "axis": "z", "angle": 0})
    m.box("mouth", (4.2, 4.6, 6.2), (11.8, 6.4, 9.8), p["dark"])
    m.box("band", (9.0, 4.2, 6.3), (10.2, 8.4, 9.7), p["gold"])
    m.box("gem", (9.1, 5.4, 6.0), (10.1, 7.2, 6.4), p["accent"])


def depth_gun(m: Model, p: dict) -> None:
    """深淵の銃。"""
    m.box("barrel", (4.2, 9.4, 7.2), (13.0, 11.2, 8.8), p["dark"])
    m.box("barrel_end", (12.4, 9.0, 6.8), (13.8, 11.6, 9.2), p["steel"])
    m.box("core", (12.2, 9.6, 6.6), (13.0, 11.0, 7.0), p["accent"])
    m.box("grip", (6.4, 5.4, 7.2), (8.0, 9.6, 8.8), p["wood"],
          rotation={"origin": [7.2, 9.6, 8], "axis": "z", "angle": 12})
    m.box("stock", (4.0, 7.6, 7.1), (6.6, 9.6, 8.9), p["wood"])
    m.box("drum", (8.4, 9.0, 6.6), (10.4, 11.6, 9.4), p["bronze"])
    m.box("drum_core", (9.0, 9.8, 6.3), (9.8, 10.8, 6.7), p["accent"])


def whip(m: Model, p: dict) -> None:
    """毒鞭。"""
    m.box("handle", (7.3, 3.0, 7.3), (8.7, 6.4, 8.7), p["leather"])
    m.box("handle_cap", (7.0, 2.4, 7.0), (9.0, 3.2, 9.0), p["gold"])
    for i in range(5):
        t = i / 4.0
        m.box(f"whip{i}", (8 - 1.0 + t * 1.2, 6.0 + i * 2.0, 7.6),
              (8 + 1.0 - t * 0.6, 8.0 + i * 2.0, 8.4), p["accent_dim"],
              rotation={"origin": [8, 7.0 + i * 2.0, 8], "axis": "z", "angle": 10 - t * 22})
    m.box("tip", (8.4, 15.6, 7.7), (9.2, 17.0, 8.3), p["accent"])


def fishing_rod(m: Model, p: dict) -> None:
    """釣り竿。"""
    m.box("rod", (7.45, 2.0, 7.6), (8.58, 12.6, 8.6), p["wood"])
    m.box("grip", (7.4, 2.0, 7.4), (8.6, 5.0, 8.6), p["leather"])
    m.box("reel", (8.6, 3.2, 6.8), (9.8, 5.2, 8.0), p["bronze"])
    m.box("tip", (7.9, 12.6, 7.9), (8.1, 15.4, 8.1), p["dark"])
    m.box("float", (7.7, 14.4, 7.7), (8.3, 15.6, 8.3), p["accent"])


def shears(m: Model, p: dict) -> None:
    """鋏。"""
    m.box("blade1", (5.2, 8.6, 7.4), (10.8, 9.5, 8.6), p["steel"],
          rotation={"origin": [8, 8, 8], "axis": "z", "angle": 12})
    m.box("blade2", (5.2, 6.5, 7.4), (10.8, 7.4, 8.6), p["steel"],
          rotation={"origin": [8, 8, 8], "axis": "z", "angle": -12})
    m.box("pivot", (7.4, 7.4, 6.9), (8.6, 8.6, 8.9), p["gold"])
    m.box("handle1", (4.0, 5.4, 7.3), (7.0, 7.2, 8.7), p["accent_dim"],
          rotation={"origin": [8, 8, 8], "axis": "z", "angle": 12})
    m.box("handle2", (4.0, 8.8, 7.3), (7.0, 10.6, 8.7), p["accent_dim"],
          rotation={"origin": [8, 8, 8], "axis": "z", "angle": -12})


def pickaxe(m: Model, p: dict) -> None:
    """鶴嘴。"""
    m.box("haft", (7.42, 2.4, 7.6), (8.58, 13.0, 8.6), p["wood"])
    m.box("head", (4.2, 12.2, 7.5), (11.8, 13.4, 8.5), p["steel"])
    m.box("tip_l", (3.0, 11.2, 7.4), (4.6, 12.8, 8.6), p["steel"])
    m.box("tip_r", (11.4, 11.2, 7.4), (13.0, 12.8, 8.6), p["steel"])
    m.box("collar", (7.4, 12.0, 7.2), (8.6, 13.6, 8.8), p["gold"])


def tool_axe(m: Model, p: dict) -> None:
    """採掘斧。"""
    m.box("haft", (7.42, 2.2, 7.6), (8.58, 13.2, 8.6), p["wood"])
    m.box("head", (8.4, 11.6, 6.8), (12.2, 13.6, 9.2), p["steel"])
    m.box("edge", (11.8, 11.2, 6.6), (13.0, 14.0, 9.4), p["steel"])
    m.box("collar", (7.4, 11.8, 7.2), (8.6, 13.4, 8.8), p["bronze"])


def shovel(m: Model, p: dict) -> None:
    """シャベル。"""
    m.box("haft", (7.42, 4.0, 7.6), (8.58, 14.0, 8.6), p["wood"])
    m.box("blade", (6.4, 1.2, 6.8), (9.6, 4.4, 9.2), p["steel"])
    m.box("edge", (6.6, 1.0, 6.9), (9.4, 1.4, 9.1), p["dark"])
    m.box("collar", (7.4, 3.8, 7.3), (8.6, 4.6, 8.7), p["bronze"])


def hoe(m: Model, p: dict) -> None:
    """クワ。"""
    m.box("haft", (7.42, 2.6, 7.6), (8.58, 13.4, 8.6), p["wood"])
    m.box("neck", (7.9, 12.6, 7.9), (8.1, 14.2, 8.1), p["dark"])
    m.box("blade", (5.0, 13.0, 7.3), (11.0, 14.4, 8.7), p["steel"])
    m.box("edge", (5.0, 12.8, 7.2), (11.0, 13.2, 8.8), p["dark"])


# ------------------------------------------------------------------ 防具

def helm(m: Model, p: dict) -> None:
    """兜。"""
    m.box("dome", (4.8, 7.0, 5.4), (11.2, 12.6, 10.6), p["steel"])
    m.box("dome_top", (5.8, 12.6, 6.4), (10.2, 13.6, 9.6), p["steel"])
    m.box("back", (4.8, 5.6, 6.0), (11.2, 7.2, 11.0), p["dark"])
    m.box("visor", (4.6, 6.8, 4.4), (11.4, 8.8, 5.6), p["dark"])
    m.box("visor_glow", (5.4, 7.2, 4.1), (10.6, 7.9, 4.5), p["accent"])
    m.box("crest", (7.4, 13.4, 3.8), (8.6, 15.2, 5.2), p["accent"])
    m.box("crest_base", (6.9, 13.0, 4.2), (9.1, 13.8, 5.0), p["gold"])
    for i, ox in enumerate((-2.2, 2.2)):
        m.box(f"cheek{i}", (8 + ox - 0.9, 4.6, 4.6), (8 + ox + 0.9, 7.4, 8.2), p["steel"])


def chest(m: Model, p: dict) -> None:
    """胸当て。"""
    m.box("body", (4.2, 4.4, 5.0), (11.8, 12.2, 11.0), p["steel"])
    m.box("body_low", (4.6, 3.4, 5.4), (11.4, 4.6, 10.6), p["dark"])
    m.box("neck", (6.0, 12.0, 6.4), (10.0, 13.2, 9.6), p["dark"])
    m.box("shoulder_l", (2.0, 8.8, 5.0), (4.8, 12.4, 11.2), p["steel"])
    m.box("shoulder_r", (11.2, 8.8, 5.0), (14.0, 12.4, 11.2), p["steel"])
    m.box("pauldron_l", (1.6, 11.6, 4.6), (5.0, 12.8, 11.6), p["gold"])
    m.box("pauldron_r", (11.0, 11.6, 4.6), (14.4, 12.8, 11.6), p["gold"])
    m.box("emblem", (6.8, 7.4, 4.5), (9.2, 10.0, 5.1), p["accent"])
    m.box("belt", (4.0, 3.0, 4.8), (12.0, 4.0, 11.2), p["gold"])
    m.box("belt_gem", (7.2, 2.8, 4.5), (8.8, 4.2, 4.9), p["accent"])


def legs(m: Model, p: dict) -> None:
    """脚当て。"""
    m.box("hip", (4.0, 8.4, 5.0), (12.0, 12.2, 11.0), p["steel"])
    m.box("leg_l", (4.2, 1.8, 5.2), (7.4, 8.8, 10.8), p["steel"])
    m.box("leg_r", (8.6, 1.8, 5.2), (11.8, 8.8, 10.8), p["steel"])
    m.box("knee_l", (3.8, 4.2, 4.6), (7.8, 6.2, 11.4), p["dark"])
    m.box("knee_r", (8.2, 4.2, 4.6), (12.2, 6.2, 11.4), p["dark"])
    m.box("belt", (3.8, 11.2, 4.8), (12.2, 12.4, 11.2), p["gold"])


def boots(m: Model, p: dict) -> None:
    """靴。"""
    for i, ox in enumerate((-2.8, 2.8)):
        m.box(f"boot{i}", (8 + ox - 2.2, 3.4, 5.2), (8 + ox + 2.2, 9.6, 10.4), p["steel"])
        m.box(f"foot{i}", (8 + ox - 2.4, 2.0, 4.8), (8 + ox + 2.8, 3.6, 10.8), p["dark"])
        m.box(f"cuff{i}", (8 + ox - 2.4, 8.8, 4.8), (8 + ox + 2.4, 10.0, 10.8), p["gold"])


# ------------------------------------------------------------------ 素材

def gem(m: Model, p: dict) -> None:
    """宝石 (結晶)。"""
    m.box("gem", (5.4, 4.6, 5.4), (10.6, 11.4, 10.6), p["accent"])
    m.box("gem_top", (6.4, 11.4, 6.4), (9.6, 13.0, 9.6), p["accent"])
    m.box("gem_top2", (7.3, 13.0, 7.3), (8.7, 14.2, 8.7), p["accent_dim"])
    m.box("gem_bot", (6.4, 3.0, 6.4), (9.6, 4.6, 9.6), p["accent"])
    m.box("gem_bot2", (7.3, 2.0, 7.3), (8.7, 3.0, 8.7), p["accent_dim"])
    m.box("gem_core", (7.4, 6.8, 6.2), (8.6, 9.2, 6.6), p["white"])


def shard(m: Model, p: dict) -> None:
    """欠片。"""
    m.box("shard", (5.6, 4.4, 6.0), (10.4, 11.6, 10.0), p["accent"],
          rotation={"origin": [8, 8, 8], "axis": "z", "angle": 18})
    m.box("shard_core", (7.2, 6.4, 5.6), (8.8, 9.6, 6.2), p["white"])


def stone_chunk(m: Model, p: dict) -> None:
    m.box("stone", (4.6, 4.0, 4.6), (11.4, 12.0, 11.4), p["stone"],
          rotation={"origin": [8, 8, 8], "axis": "y", "angle": 22})
    m.box("rune", (6.8, 6.4, 4.0), (9.2, 9.6, 4.6), p["accent"])


def pouch(m: Model, p: dict) -> None:
    """袋。"""
    m.box("bag", (4.6, 4.2, 5.0), (11.4, 11.4, 11.0), p["cloth"])
    m.box("neck", (6.2, 10.6, 6.2), (9.8, 12.6, 9.8), p["leather"])
    m.box("tie", (5.8, 12.0, 5.8), (10.2, 12.8, 10.2), p["gold"])
    m.box("gem", (7.2, 6.6, 4.7), (8.8, 9.0, 5.1), p["accent"])
