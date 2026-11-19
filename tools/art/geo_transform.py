"""変形する武器の GeckoLib モデル (追加武器 6 種)。

手持ち・地面・額縁では 3D モデルで描き、2 つのアニメを持つ:
- idle (ループ): 待機中のゆっくりした動き (歯車が回る・三日月が巡る・提灯が揺れる…)
- transform (1 回): 奥義を撃った瞬間にサーバーから再生させる変形 (結晶が伸びる・花弁がほどける・
  竜の棘が開く・三日月が割れる・砂時計が反転する・提灯が開いて炎が育つ)

座標は art/geo.py と同じ「見た目どおり」(x 右+, y 上+, z 手前+)。武器は +y に立てて作る。
インベントリの絵は従来どおり 64px の 2D (new_weapons.py) で、強化段階・特性の見た目も 2D 側が持つ。
"""
from __future__ import annotations

import math

from . import geo
from .geo import Material, Model

LEN_IDLE = 4.0
LEN_TF = 1.2


def _anims(item_id: str, idle: dict, transform: dict) -> dict:
    a = geo.anim_json(f"animation.{item_id}.idle", LEN_IDLE, idle)
    b = geo.anim_json(f"animation.{item_id}.transform", LEN_TF, transform)
    t = b["animations"][f"animation.{item_id}.transform"]
    t["loop"] = False
    a["animations"].update(b["animations"])
    return a


def _pulse(peak, base=(1.0, 1.0, 1.0)):
    """変形の山: 0 → 0.35 秒で peak、1.2 秒で戻る (scale / position 用)。"""
    return {0.0: list(base), 0.35: list(peak), 0.8: list(peak), LEN_TF: list(base)}


# ================================================================ 晶剣

def crystal_sword():
    m = Model("abyss_crystal_sword")
    m.bone("root")
    m.bone("hilt", parent="root")
    m.box("hilt", (0, -7.0, 0), (1.6, 6.0, 1.6), "grip")
    m.box("hilt", (0, -10.4, 0), (2.4, 2.4, 2.4), "gem", rot=(0, 45, 45))
    m.box("hilt", (0, -3.6, 0), (7.0, 1.4, 2.0), "metal")
    m.box("hilt", (0, -3.6, 0), (2.0, 2.0, 2.2), "gem", rot=(0, 0, 45))
    m.bone("blade", pivot=(0, -3, 0), parent="root")
    m.box("blade", (0, 5.0, 0), (3.4, 14.0, 0.8), "crystal")
    m.box("blade", (0, 13.4, 0), (2.2, 3.0, 0.8), "crystal", rot=(0, 0, 45))
    m.box("blade", (0, 5.0, 0), (0.8, 14.0, 1.0), "core")
    for k, (y, side) in enumerate(((2.0, -1), (6.0, 1), (10.0, -1))):
        b = m.bone(f"shard{k}", pivot=(side * 1.6, y, 0), parent="blade")
        m.box(b, (side * 2.6, y + 0.6, 0), (1.6, 3.2, 1.0), "crystal", rot=(0, 0, side * 30))
    m.layout(128)
    pal = {"grip": Material(0x2a3450, "cloth", accent=0x7ce8ff), "metal": Material(0x8fa6e8, "metal"),
           "gem": Material(0xa9e8ff, "gem", emissive=True), "crystal": Material(0xbfe6f6, "gem"),
           "core": Material(0xeafcff, "glow", emissive=True)}
    idle = {f"shard{k}": {"position": geo.wave(LEN_IDLE, (0, 0.3, 0), 8, phase=k * 2.0)} for k in range(3)}
    tf = {"blade": {"scale": _pulse((1.15, 1.08, 1.4))}}
    for k, side in enumerate((-1, 1, -1)):
        tf[f"shard{k}"] = {"position": _pulse((side * 3.0, 1.0, 0), (0, 0, 0)),
                           "rotation": {0.0: [0, 0, 0], 0.35: [0, 0, side * 40], LEN_TF: [0, 0, 0]}}
    return m, pal, _anims("abyss_crystal_sword", idle, tf)


# ================================================================ 桜刀

def sakura_blade():
    m = Model("abyss_sakura_blade")
    m.bone("root")
    m.bone("hilt", parent="root")
    m.box("hilt", (0, -8.0, 0), (1.4, 7.0, 1.4), "grip")
    m.box("hilt", (0, -4.2, 0), (3.2, 0.6, 3.2), "tsuba")
    m.bone("blade", pivot=(0, -4, 0), parent="root")
    for k in range(6):  # 反りのある刀身を 6 片で (変形でほどける)
        y = -2.6 + k * 3.0
        x = 0.06 * (k ** 2)
        b = m.bone(f"seg{k}", pivot=(x, y, 0), parent="blade")
        m.box(b, (x, y + 1.5, 0), (1.3, 3.1, 0.5), "steel", rot=(0, 0, -2.5 * k))
        m.box(b, (x - 0.55, y + 1.5, 0), (0.3, 3.1, 0.55), "edge", rot=(0, 0, -2.5 * k))
    m.bone("petals", pivot=(0, 6, 0), parent="root")
    for k in range(5):
        a = k * 2 * math.pi / 5
        m.box("petals", (3.2 * math.cos(a), 6 + 3.2 * math.sin(a), 0), (1.4, 1.0, 0.3), "petal", rot=(0, 0, math.degrees(a)))
    m.layout(128)
    pal = {"grip": Material(0x8a2a52, "cloth", accent=0xef8fb2), "tsuba": Material(0x4a3044, "metal"),
           "steel": Material(0xc8d0dc, "metal"), "edge": Material(0xffe0ee, "glow", emissive=True),
           "petal": Material(0xef8fb2, "glow", emissive=True)}
    idle = {"petals": {"rotation": geo.spin(LEN_IDLE, 2, 1.0, 8), "scale": {0.0: [0, 0, 0], LEN_IDLE: [0, 0, 0]}}}
    tf = {"petals": {"rotation": geo.spin(LEN_TF, 2, 1.0, 6),
                     "scale": {0.0: [0, 0, 0], 0.2: [1.4, 1.4, 1.4], 0.9: [1.6, 1.6, 1.6], LEN_TF: [0, 0, 0]}}}
    for k in range(6):
        off = (k - 2.5) * 0.7
        tf[f"seg{k}"] = {"position": {0.0: [0, 0, 0], 0.3: [off, k * 0.5, 0], 0.8: [off, k * 0.5, 0], LEN_TF: [0, 0, 0]},
                         "rotation": {0.0: [0, 0, 0], 0.3: [0, 0, (k - 2.5) * 12], LEN_TF: [0, 0, 0]}}
    return m, pal, _anims("abyss_sakura_blade", idle, tf)


# ================================================================ 竜断

def dragon_cleaver():
    m = Model("abyss_dragon_cleaver")
    m.bone("root")
    m.bone("hilt", parent="root")
    m.box("hilt", (0, -7.6, 0), (2.0, 7.0, 2.0), "grip")
    m.box("hilt", (0, -11.6, 0), (2.4, 2.0, 2.4), "bone", rot=(0, 45, 0))
    m.box("hilt", (0, -3.8, 0), (6.0, 1.6, 2.4), "guard")
    m.bone("blade", pivot=(0, -3, 0), parent="root")
    m.box("blade", (0.8, 5.0, 0), (6.0, 14.0, 1.0), "scale")
    m.box("blade", (-2.0, 5.0, 0), (0.6, 14.0, 1.1), "edge")
    m.box("blade", (1.8, 12.6, 0), (4.0, 2.0, 1.0), "scale", rot=(0, 0, 20))
    for k in range(3):  # 背の竜の棘 (変形で開く)
        y = 1.0 + k * 4.5
        b = m.bone(f"spur{k}", pivot=(3.8, y, 0), parent="blade")
        m.box(b, (4.6, y + 1.4, 0), (1.0, 3.6, 0.8), "bone", rot=(0, 0, -25))
    m.bone("vein", pivot=(0, 5, 0), parent="blade")
    m.box("vein", (0.8, 5.0, 0.55), (1.0, 12.0, 0.1), "lava")
    m.layout(128)
    pal = {"grip": Material(0x3a2420, "cloth", accent=0xff7a3c), "bone": Material(0xe8dcc0, "metal"),
           "guard": Material(0x6a4630, "metal"), "scale": Material(0x7a6060, "dark"),
           "edge": Material(0xd0c8c8, "metal"), "lava": Material(0xff7a3c, "glow", emissive=True)}
    idle = {"vein": {"scale": geo.wave(LEN_IDLE, (0.15, 0.0, 0.0), 8, base=(1.0, 1.0, 1.0))}}
    tf = {"vein": {"scale": _pulse((2.5, 1.05, 4.0))}}
    for k in range(3):
        tf[f"spur{k}"] = {"rotation": {0.0: [0, 0, 0], 0.25: [0, 0, -55 - 10 * k], 0.9: [0, 0, -55 - 10 * k], LEN_TF: [0, 0, 0]}}
    return m, pal, _anims("abyss_dragon_cleaver", idle, tf)


# ================================================================ 月杖

def _staff_base(m, wood: str = "wood"):
    m.bone("root")
    m.bone("shaft", parent="root")
    m.box("shaft", (0, -1.0, 0), (1.4, 24.0, 1.4), wood)
    m.box("shaft", (0, -12.4, 0), (2.0, 1.4, 2.0), "metal")
    for y in (-7.0, 4.0):
        m.box("shaft", (0, y, 0), (2.0, 0.8, 2.0), "metal")


def lunar_staff():
    m = Model("abyss_lunar_staff")
    _staff_base(m, "quartz")
    m.bone("core", pivot=(0, 14.5, 0), parent="root")
    m.box("core", (0, 14.5, 0), (2.6, 2.6, 2.6), "gem", rot=(45, 0, 45))
    for side in (-1, 1):  # 三日月の左右 (変形で割れて逆回り)
        b = m.bone(f"moon_{'l' if side < 0 else 'r'}", pivot=(0, 14.5, 0), parent="root")
        for k in range(6):
            a = math.radians(90 + side * (25 + k * 22))
            m.box(b, (4.6 * math.cos(a), 14.5 + 4.6 * math.sin(a), 0), (1.4, 1.8, 1.0), "moon",
                  rot=(0, 0, math.degrees(a)))
    m.layout(128)
    pal = {"quartz": Material(0xece6dc, "metal"), "metal": Material(0xd3d7e2, "metal"),
           "gem": Material(0x5fd2ff, "gem", emissive=True), "moon": Material(0xd8f6ff, "glow", emissive=True)}
    idle = {"moon_l": {"rotation": geo.wave(LEN_IDLE, (0, 0, 8), 8)},
            "moon_r": {"rotation": geo.wave(LEN_IDLE, (0, 0, 8), 8)},
            "core": {"rotation": geo.spin(LEN_IDLE, 1, 1.0, 8)}}
    tf = {"moon_l": {"rotation": {0.0: [0, 0, 0], 0.4: [0, 0, 90], 0.9: [0, 0, 180], LEN_TF: [0, 0, 360]}},
          "moon_r": {"rotation": {0.0: [0, 0, 0], 0.4: [0, 0, -90], 0.9: [0, 0, -180], LEN_TF: [0, 0, -360]}},
          "core": {"scale": _pulse((2.0, 2.0, 2.0))}}
    return m, pal, _anims("abyss_lunar_staff", idle, tf)


# ================================================================ 刻杖

def chrono_staff():
    m = Model("abyss_chrono_staff")
    _staff_base(m, "copper")
    m.bone("glass", pivot=(0, 15.5, 0), parent="root")
    m.box("glass", (0, 17.4, 0), (4.4, 0.8, 4.4), "gold")
    m.box("glass", (0, 13.6, 0), (4.4, 0.8, 4.4), "gold")
    m.box("glass", (0, 16.4, 0), (2.6, 1.6, 2.6), "sand")
    m.box("glass", (0, 14.6, 0), (2.6, 1.6, 2.6), "sandglass")
    m.box("glass", (0, 15.5, 0), (0.8, 1.0, 0.8), "sand")
    for x in (-1.9, 1.9):
        m.box("glass", (x, 15.5, 0), (0.5, 3.4, 0.5), "gold")
    for k, (x, y, r) in enumerate(((-3.6, 12.0, 2.0), (3.4, 10.4, 1.6))):
        b = m.bone(f"gear{k}", pivot=(x, y, 0), parent="root")
        m.box(b, (x, y, 0), (r * 2, r * 2, 0.6), "gold")
        for t in range(6):
            a = t * math.pi / 3
            m.box(b, (x + (r + 0.4) * math.cos(a), y + (r + 0.4) * math.sin(a), 0), (0.8, 0.8, 0.6), "gold",
                  rot=(0, 0, math.degrees(a)))
    m.layout(128)
    pal = {"copper": Material(0xc1743f, "metal"), "metal": Material(0xc69745, "metal"),
           "gold": Material(0xe0c46a, "metal"), "sand": Material(0xfff3c4, "glow", emissive=True),
           "sandglass": Material(0xe0c46a, "gem")}
    idle = {"gear0": {"rotation": geo.spin(LEN_IDLE, 2, 1.0, 8)}, "gear1": {"rotation": geo.spin(LEN_IDLE, 2, -1.3, 8)}}
    tf = {"glass": {"rotation": {0.0: [0, 0, 0], 0.6: [0, 0, 180], LEN_TF: [0, 0, 180]}},
          "gear0": {"rotation": geo.spin(LEN_TF, 2, 3.0, 8)}, "gear1": {"rotation": geo.spin(LEN_TF, 2, -4.0, 8)}}
    return m, pal, _anims("abyss_chrono_staff", idle, tf)


# ================================================================ 魂灯杖

def soul_lantern():
    m = Model("abyss_soul_lantern")
    _staff_base(m, "wood")
    m.box("shaft", (1.6, 11.6, 0), (3.6, 1.0, 1.0), "metal")
    m.bone("lantern", pivot=(3.2, 11.2, 0), parent="root")
    m.box("lantern", (3.2, 10.6, 0), (0.4, 1.2, 0.4), "metal")
    m.box("lantern", (3.2, 9.6, 0), (3.0, 0.6, 3.0), "metal")
    m.box("lantern", (3.2, 5.0, 0), (3.0, 0.6, 3.0), "metal")
    m.bone("flame", pivot=(3.2, 7.3, 0), parent="lantern")
    m.box("flame", (3.2, 7.3, 0), (1.6, 2.4, 1.6), "flame", rot=(0, 45, 0))
    for k, (dx, dz) in enumerate(((1.5, 0), (-1.5, 0), (0, 1.5), (0, -1.5))):
        b = m.bone(f"pane{k}", pivot=(3.2 + dx, 9.3, dz), parent="lantern")
        m.box(b, (3.2 + dx, 7.3, dz), (0.2 if dx else 2.6, 4.0, 2.6 if dx else 0.2), "glass")
    m.layout(128)
    pal = {"wood": Material(0x5a3a22, "dark"), "metal": Material(0x8a6a3a, "metal"),
           "flame": Material(0x5ef2d6, "glow", emissive=True), "glass": Material(0x9ff6e6, "gem")}
    idle = {"lantern": {"rotation": geo.wave(LEN_IDLE, (0, 0, 9), 8)},
            "flame": {"scale": geo.wave(LEN_IDLE, (0.12, 0.2, 0.12), 8, base=(1.0, 1.0, 1.0))}}
    tf = {"flame": {"scale": _pulse((2.0, 2.6, 2.0))}}
    for k, (ax, sgn) in enumerate(((2, 1), (2, -1), (0, -1), (0, 1))):
        v = [0, 0, 0]
        v[ax] = sgn * 70
        tf[f"pane{k}"] = {"rotation": {0.0: [0, 0, 0], 0.3: v, 0.9: v, LEN_TF: [0, 0, 0]}}
    return m, pal, _anims("abyss_soul_lantern", idle, tf)


MODELS = {"abyss_crystal_sword": crystal_sword, "abyss_sakura_blade": sakura_blade,
          "abyss_dragon_cleaver": dragon_cleaver, "abyss_lunar_staff": lunar_staff,
          "abyss_chrono_staff": chrono_staff, "abyss_soul_lantern": soul_lantern}
