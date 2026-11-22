"""全武器の GeckoLib 手持ち 3D モデル (型ごとの組み立て + 待機アニメ + 奥義で再生する「変形」アニメ)。

geo_transform.py の 6 種 (一点物の作り込み) 以外の武器 35 種をここで作る。型 (剣・刀・短剣・斧・槌・鎚矛・
連接棍・槍・杖・弓・大鎌・鞭・円月輪・魔導書・籠手・爪・角笛・銃・釣竿/短杖) ごとに組み立て関数があり、
SPEC の引数 (幅・長さ・鍔の型・刻印・浮遊片・棘…) で 1 本ずつ違う意匠にする。配色は武器の色と素材
(steel / void / ice / blood / bone) から作る。

アニメは 2 本 (Java の TransformWeaponItem が再生):
- idle (ループ 4 秒): 宝石の脈動・浮遊片の上下・輪の回転・布の揺れ・鞭のうねり など
- transform (1 回 1.2 秒): 奥義の瞬間の変形 (刃が伸びて光る・頭が膨らんで振り下ろす・表紙が開いて頁が舞う…)

座標は art/geo.py と同じ「見た目どおり」(x 右+, y 上+, z 手前+)。武器は +y に立て、握りを y≈-8 に置く。
"""
from __future__ import annotations

import math

from . import geo, palettes
from .geo import Material, Model

IDLE = 4.0
TF = 1.2

BLADES = {"steel": 0xd6dde8, "void": 0x4b3c7a, "ice": 0xcdeefa, "blood": 0x9c2438, "bone": 0xe6dac0}


HARMONY = {"steel": "split", "void": "triadic", "ice": "analogous", "blood": "complementary", "bone": "monochrome"}


def _mix(a: int, b: int, t: float) -> int:
    out = 0
    for sh in (16, 8, 0):
        x, y = (a >> sh) & 255, (b >> sh) & 255
        out |= int(x + (y - x) * t + 0.5) << sh
    return out


def palette(color: int, material: str = "steel") -> dict[str, Material]:
    blade = BLADES.get(material, BLADES["steel"])
    hm = palettes.harmony(color, HARMONY.get(material, "split"))  # Arcane Forge の配色調和で金具・布・光を揃える
    return {
        "blade": Material(blade, "metal"),
        "edge": Material(_mix(blade, 0xffffff, 0.35), "edge"),
        "rune": Material(_mix(blade, 0x202030, 0.15), "rune", accent=color),
        "core": Material(_mix(hm["gem2"], 0xffffff, 0.3), "glow", emissive=True),
        "glow": Material(color, "glow", emissive=True),
        "gem": Material(color, "gem", emissive=True),
        "crystal": Material(_mix(color, 0xffffff, 0.5), "crystal"),
        "gold": Material(_mix(0xd9b04c, hm["metal"], 0.25), "gold"),
        "metal": Material(_mix(blade, 0x6a7088, 0.35), "metal"),
        "grip": Material(_mix(color, 0x1c1420, 0.78), "leather", accent=_mix(color, 0xffffff, 0.3)),
        "wrap": Material(_mix(hm["particle"], 0x301828, 0.5), "cloth"),
        "wood": Material(_mix(0x6e4628, hm["shaft"], 0.3), "wood"),
        "dark": Material(_mix(color, 0x16121e, 0.85), "dark"),
        "bone": Material(0xe8dcc0, "bone"),
        "page": Material(0xefe4c8, "cloth"),
        "string": Material(_mix(color, 0xffffff, 0.7), "glow", emissive=True),
    }


# ================================================================ 共通の部品

def _grip(m: Model, bone: str, y0: float, y1: float, width: float = 1.6, pommel: str = "gem", rings: int = 3):
    """握り (革) + 巻き布の帯 + 柄頭。y0 が下端。"""
    m.box(bone, (0, (y0 + y1) / 2, 0), (width, y1 - y0, width), "grip")
    for k in range(rings):
        y = y0 + (y1 - y0) * (k + 0.5) / rings
        m.box(bone, (0, y, 0), (width + 0.4, 0.5, width + 0.4), "wrap")
    if pommel == "gem":
        m.box(bone, (0, y0 - 0.7, 0), (width + 0.6, 0.8, width + 0.6), "gold")
        m.box(bone, (0, y0 - 1.6, 0), (1.6, 1.6, 1.6), "gem", rot=(0, 45, 45))
    elif pommel == "ring":
        for a in range(6):
            t = a * math.pi / 3
            m.box(bone, (math.cos(t) * 1.2, y0 - 1.6 + math.sin(t) * 1.2, 0), (0.9, 0.9, 0.7), "gold", rot=(0, 0, math.degrees(t)))
    else:
        m.box(bone, (0, y0 - 0.9, 0), (width + 1.0, 1.4, width + 1.0), "metal", rot=(0, 45, 0))


def _guard(m: Model, bone: str, y: float, kind: str, span: float = 7.0):
    if kind == "cross":
        m.box(bone, (0, y, 0), (span, 1.2, 1.8), "gold")
        for s in (-1, 1):
            m.box(bone, (s * (span / 2 + 0.3), y + 0.5, 0), (1.0, 1.6, 1.4), "gold", rot=(0, 0, s * -25))
        m.box(bone, (0, y, 0), (1.8, 1.8, 2.2), "gem", rot=(0, 0, 45))
    elif kind == "wing":
        for s in (-1, 1):
            for k in range(3):
                m.bar(bone, (s * 0.8, y, 0), (s * (2.5 + k * 1.3), y + 1.5 + k * 0.9, 0), 0.9 - k * 0.15, 1.2, "gold")
        m.box(bone, (0, y, 0), (2.4, 1.4, 2.0), "metal")
    elif kind == "ring":
        for a in range(10):
            t = a * math.pi / 5
            m.box(bone, (math.cos(t) * 2.4, y + math.sin(t) * 1.2, 0), (1.2, 0.8, 1.6), "gold", rot=(0, 0, math.degrees(t) + 90))
        m.box(bone, (0, y, 0), (1.6, 1.6, 2.0), "gem", rot=(0, 0, 45))
    elif kind == "claw":
        m.box(bone, (0, y, 0), (4.0, 1.4, 1.8), "dark")
        for s in (-1, 1):
            m.bar(bone, (s * 1.6, y, 0), (s * 3.6, y + 2.6, 0), 0.8, 1.2, "metal")
            m.bar(bone, (s * 3.6, y + 2.6, 0), (s * 3.0, y + 4.0, 0), 0.6, 1.0, "edge")
        m.box(bone, (0, y, 0.2), (1.4, 1.4, 2.2), "gem", rot=(0, 0, 45))
    elif kind == "tsuba":
        m.box(bone, (0, y, 0), (3.6, 0.6, 3.6), "gold", rot=(0, 45, 0))
        m.box(bone, (0, y, 0), (3.0, 0.7, 3.0), "dark")
    else:  # bar
        m.box(bone, (0, y, 0), (span, 1.0, 1.6), "metal")


def _floaters(m: Model, parent: str, n: int, y0: float, y1: float, radius: float, mat: str = "crystal", size=1.2):
    """武器の周りに浮かぶ破片 (骨を 1 つずつ。idle で上下、変形で外へ散る)。"""
    names = []
    for k in range(n):
        a = k * 2 * math.pi / n + 0.4
        y = y0 + (y1 - y0) * (k + 0.5) / n
        b = m.bone(f"float{k}", pivot=(0, y, 0), parent=parent)
        m.box(b, (math.cos(a) * radius, y, math.sin(a) * radius * 0.6), (size, size * 1.8, size), mat, rot=(0, 45, 20))
        names.append(b)
    return names


# ================================================================ 型ごとの組み立て
# 戻り値: (model, idle, transform)。idle / transform は {骨: {チャンネル: {時刻: [x,y,z]}}}

def sword(m: Model, w=3.0, L=16.0, guard="cross", rune=False, serrated=False, fuller=True, floaters=0,
          ribbons=False, pommel="gem"):
    m.bone("root")
    m.bone("hilt", parent="root")
    _grip(m, "hilt", -10.5, -4.0, pommel=pommel)
    _guard(m, "hilt", -3.4, guard, span=w + 4.5)
    m.bone("blade", pivot=(0, -3, 0), parent="root")
    body = "rune" if rune else "blade"
    m.box("blade", (0, -2.6 + L / 2, 0), (w, L, 0.7), body)
    for s in (-1, 1):  # 研いだ縁
        m.box("blade", (s * (w / 2 + 0.25), -2.6 + L / 2 - 0.5, 0), (0.5, L - 1.0, 0.45), "edge")
    if fuller:
        m.box("blade", (0, -2.6 + L * 0.45, 0), (max(0.6, w * 0.25), L * 0.7, 0.85), "core" if rune else "metal")
    tip = -2.6 + L
    m.box("blade", (0, tip, 0), (w * 0.72, w * 0.72, 0.6), "edge", rot=(0, 0, 45))
    if serrated:
        for k in range(int(L / 2.4)):
            y = 0.5 + k * 2.4
            m.box("blade", (w / 2 + 0.5, y, 0), (1.1, 1.1, 0.4), "edge", rot=(0, 0, 45))
    idle, tf = {}, {}
    fl = _floaters(m, "blade", floaters, 2.0, tip - 2, w + 1.8) if floaters else []
    for k, b in enumerate(fl):
        idle[b] = {"position": geo.wave(IDLE, (0, 0.5, 0), 8, phase=k * 2.1),
                   "rotation": geo.spin(IDLE, 1, 1.0, 8)}
        tf[b] = {"position": geo.ease(TF, [(0, [0, 0, 0]), (0.3, [0, 2.5, 0]), (0.75, [0, 2.5, 0]), (1, [0, 0, 0])]),
                 "scale": geo.ease(TF, [(0, [1, 1, 1]), (0.3, [1.6, 1.6, 1.6]), (1, [1, 1, 1])])}
    if ribbons:
        for s in (-1, 1):
            b = m.bone(f"ribbon{s + 1}", pivot=(s * 1.0, -10.0, 0), parent="hilt")
            m.box(b, (s * 1.0, -12.5, 0), (0.8, 5.0, 0.2), "wrap")
            idle[b] = {"rotation": geo.wave(IDLE, (10, 0, s * 14), 8, phase=s)}
    tf["blade"] = {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.25, [1.2, 1.25, 1.5]), (0.7, [1.12, 1.18, 1.3]), (1, [1, 1, 1])])}
    tf["root"] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.5, [0, 360, 0]), (1, [0, 360, 0])], 8)}
    return idle, tf


def katana(m: Model, L=18.0, curve=9.0, guard="tsuba", echo=0, shards=0):
    m.bone("root")
    m.bone("hilt", parent="root")
    _grip(m, "hilt", -11.5, -4.0, width=1.4, pommel="cap", rings=5)
    _guard(m, "hilt", -3.4, guard)
    m.bone("blade", pivot=(0, -3, 0), parent="root")
    m.box("blade", (0, -2.4, 0), (1.8, 1.2, 1.2), "gold")  # はばき
    n = 6
    x = y = 0.0
    y = -1.8
    for k in range(n):  # 反り: 区間ごとに少しずつ傾ける
        ang = curve * (k + 0.5) / n
        seg = L / n
        cx = x + math.sin(math.radians(ang)) * seg / 2
        cy = y + math.cos(math.radians(ang)) * seg / 2
        m.box("blade", (cx, cy, 0), (1.6, seg + 0.3, 0.5), "blade", rot=(0, 0, -ang))
        m.box("blade", (cx + 0.75 * math.cos(math.radians(ang)), cy - 0.75 * math.sin(math.radians(ang)), 0),
              (0.45, seg + 0.3, 0.35), "edge", rot=(0, 0, -ang))
        x += math.sin(math.radians(ang)) * seg
        y += math.cos(math.radians(ang)) * seg
    m.box("blade", (x + 0.2, y, 0), (1.3, 1.3, 0.45), "edge", rot=(0, 0, 45 - curve))
    idle, tf = {}, {}
    for k in range(echo):  # 残響の刃 (半透明ではなく発光の細い刃)
        b = m.bone(f"echo{k}", pivot=(0, -3, 0), parent="root")
        off = (k + 1) * 1.4 * (-1 if k % 2 else 1)
        m.box(b, (off, -2.6 + L / 2, -0.8), (0.5, L * 0.85, 0.3), "glow", rot=(0, 0, -curve * 0.5))
        idle[b] = {"position": geo.wave(IDLE, (0.4, 0, 0), 8, phase=k * 2.0)}
        tf[b] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.4, [0, 0, (k + 1) * 25 * (-1 if k % 2 else 1)]), (1, [0, 0, 0])])}
    for b in _floaters(m, "blade", shards, 1.0, L - 4, 2.6, "crystal", 1.0):
        idle[b] = {"position": geo.wave(IDLE, (0, 0.6, 0), 8, phase=len(idle))}
        tf[b] = {"position": geo.ease(TF, [(0, [0, 0, 0]), (0.35, [0, 3, 0]), (1, [0, 0, 0])])}
    # 抜刀: 鞘走りのように刃が一度沈み、閃いて伸びる
    tf["blade"] = {"position": geo.ease(TF, [(0, [0, 0, 0]), (0.15, [0, -3, 0]), (0.4, [0, 1.5, 0]), (1, [0, 0, 0])]),
                   "scale": geo.ease(TF, [(0, [1, 1, 1]), (0.4, [1.3, 1.15, 1.6]), (1, [1, 1, 1])])}
    tf["root"] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.4, [0, 0, -35]), (0.7, [0, 0, 10]), (1, [0, 0, 0])])}
    return idle, tf


def dagger(m: Model, L=8.0, twin=False, fang=False):
    idle, tf = {}, {}
    for side in ((-1, 1) if twin else (0,)):
        off = side * 2.4
        r = m.bone(f"root{side + 1}", pivot=(off, -6, 0), rot=(0, 0, side * -14))
        m.box(r, (off, -8.5, 0), (1.4, 4.5, 1.4), "grip")
        for y in (-9.6, -7.4):
            m.box(r, (off, y, 0), (1.7, 0.4, 1.7), "wrap")
        m.box(r, (off, -11.4, 0), (1.5, 1.5, 1.5), "gem", rot=(0, 45, 45))
        m.box(r, (off, -6.0, 0), (5.0, 1.0, 1.6), "gold")
        for s_ in (-1, 1):
            m.box(r, (off + s_ * 2.7, -5.6, 0), (0.9, 1.4, 1.2), "gold", rot=(0, 0, s_ * -30))
        if fang:
            for k in range(4):
                m.box(r, (off + 0.35 * k, -4.6 + k * 2.0, 0), (2.4 - k * 0.45, 2.2, 0.8), "bone", rot=(0, 0, -9 * k))
            m.box(r, (off + 1.6, 3.3, 0), (0.9, 1.6, 0.6), "edge", rot=(0, 0, -35))
        else:
            m.box(r, (off, -5.4 + L / 2, 0), (2.2, L, 0.6), "blade")
            m.box(r, (off + 1.1, -5.4 + L / 2, 0), (0.45, L - 0.6, 0.4), "edge")
            m.box(r, (off, -5.4 + L / 2, 0), (0.5, L - 2, 0.75), "core")
            m.box(r, (off, -5.4 + L, 0), (1.6, 1.6, 0.5), "edge", rot=(0, 0, 45))
        d = 1 if side >= 0 else -1
        tf[r] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.5, [0, 0, 360 * d]), (1, [0, 0, 360 * d])], 8)}
        idle[r] = {"position": geo.wave(IDLE, (0, 0.2, 0), 8, phase=side)}
    return idle, tf


def axe(m: Model, double=False, bearded=False, haft=20.0):
    m.bone("root")
    m.bone("haft", parent="root")
    m.box("haft", (0, -12 + haft / 2, 0), (1.4, haft, 1.4), "wood")
    for y in (-11.0, -8.5, -6.0):
        m.box("haft", (0, y, 0), (1.8, 0.6, 1.8), "wrap")
    top = -12 + haft
    m.box("haft", (0, top + 0.6, 0), (1.8, 1.2, 1.8), "gold")
    m.bone("head", pivot=(0, top - 2, 0), parent="root")
    for s in ((-1, 1) if double else (1,)):
        m.box("head", (s * 2.2, top - 2, 0), (3.2, 3.0, 1.0), "metal")
        for k in range(5):  # 三日月の刃
            a = math.radians(-60 + k * 30)
            m.box("head", (s * (4.2 + math.cos(a) * 1.4), top - 2 + math.sin(a) * 3.4, 0), (1.6, 2.0, 0.7),
                  "blade", rot=(0, 0, s * math.degrees(a) * 0.6))
            m.box("head", (s * (5.1 + math.cos(a) * 1.5), top - 2 + math.sin(a) * 3.6, 0), (0.5, 2.0, 0.5),
                  "edge", rot=(0, 0, s * math.degrees(a) * 0.6))
        if bearded:
            m.box("head", (s * 3.4, top - 5.4, 0), (1.4, 2.4, 0.6), "blade", rot=(0, 0, s * 25))
    if not double:
        m.box("head", (-1.8, top - 2, 0), (1.6, 1.4, 1.0), "metal")
        m.box("head", (-2.9, top - 2, 0), (1.4, 1.4, 0.8), "edge", rot=(0, 0, 45))
    m.box("head", (0, top - 2, 0.55), (1.4, 1.4, 0.4), "gem", rot=(0, 0, 45))
    idle = {"head": {"scale": geo.wave(IDLE, (0.02, 0.02, 0.02), 8, base=(1, 1, 1))}}
    tf = {"head": {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.3, [1.45, 1.45, 1.45]), (0.8, [1.3, 1.3, 1.3]), (1, [1, 1, 1])])},
          "root": {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.3, [0, 0, 40]), (0.5, [0, 0, -30]), (1, [0, 0, 0])])}}
    return idle, tf


def hammer(m: Model, size=5.0, haft=19.0, spikes=False):
    m.bone("root")
    m.bone("haft", parent="root")
    m.box("haft", (0, -12 + haft / 2, 0), (1.4, haft, 1.4), "wood")
    for y in (-11.0, -9.0):
        m.box("haft", (0, y, 0), (1.8, 0.6, 1.8), "wrap")
    top = -12 + haft
    m.bone("head", pivot=(0, top, 0), parent="root")
    m.box("head", (0, top, 0), (size * 1.7, size, size), "metal")
    for s in (-1, 1):
        m.box("head", (s * size * 0.9, top, 0), (0.7, size + 0.6, size + 0.6), "gold")
        m.box("head", (s * (size * 0.85 + 0.5), top, 0), (0.6, size - 1.2, size - 1.2), "edge")
    m.box("head", (0, top, size / 2 + 0.1), (size * 0.6, size * 0.6, 0.4), "rune")
    m.box("head", (0, top, size / 2 + 0.3), (1.6, 1.6, 0.5), "gem", rot=(0, 0, 45))
    if spikes:
        for s in (-1, 1):
            m.box("head", (0, top + s * (size / 2 + 0.7), 0), (1.2, 1.4, 1.2), "edge", rot=(0, 45, 0))
    idle = {"head": {"rotation": geo.wave(IDLE, (0, 4, 0), 8)}}
    tf = {"head": {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.25, [1.5, 1.5, 1.5]), (0.45, [0.9, 0.9, 0.9]), (0.6, [1.2, 1.2, 1.2]), (1, [1, 1, 1])])},
          "root": {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.25, [-35, 0, 0]), (0.45, [40, 0, 0]), (1, [0, 0, 0])])}}
    return idle, tf


def mace(m: Model, haft=17.0, flanges=8):
    m.bone("root")
    m.bone("haft", parent="root")
    m.box("haft", (0, -12 + haft / 2, 0), (1.4, haft, 1.4), "metal")
    _grip(m, "haft", -11.5, -6.0, width=1.6, pommel="cap")
    top = -12 + haft + 1.6
    m.bone("head", pivot=(0, top, 0), parent="root")
    m.box("head", (0, top, 0), (3.0, 3.6, 3.0), "gold")
    for k in range(flanges):
        a = k * 360 / flanges
        r = math.radians(a)
        m.box("head", (math.cos(r) * 1.9, top, math.sin(r) * 1.9), (1.4, 4.4, 0.5), "blade", rot=(0, -a, 0))
    m.box("head", (0, top + 2.8, 0), (2.2, 2.2, 2.2), "gem", rot=(45, 45, 0))
    b = m.bone("halo", pivot=(0, top, 0), parent="head")
    for k in range(10):
        r = k * math.pi / 5
        m.box(b, (math.cos(r) * 4.2, top, math.sin(r) * 4.2), (0.8, 0.4, 0.8), "core")
    idle = {"head": {"rotation": geo.spin(IDLE, 1, 0.5, 8)}, "halo": {"rotation": geo.spin(IDLE, 1, -1.5, 8)}}
    tf = {"halo": {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.4, [2.2, 1, 2.2]), (1, [1, 1, 1])])},
          "head": {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.3, [1.4, 1.4, 1.4]), (1, [1, 1, 1])])},
          "root": {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.3, [0, 0, 35]), (0.55, [0, 0, -25]), (1, [0, 0, 0])])}}
    return idle, tf


def flail(m: Model, links=5):
    m.bone("root")
    m.bone("haft", parent="root")
    _grip(m, "haft", -11.5, -2.0, width=1.6, pommel="cap", rings=4)
    m.box("haft", (0, -1.4, 0), (2.2, 1.2, 2.2), "gold")
    parent = "haft"
    y = -0.8
    for k in range(links):
        b = m.bone(f"link{k}", pivot=(0, y, 0), parent=parent)
        m.box(b, (0, y + 0.9, 0), (1.0, 1.8, 0.4), "metal", rot=(0, 90 * (k % 2), 0))
        parent, y = b, y + 1.8
    b = m.bone("ball", pivot=(0, y, 0), parent=parent)
    m.box(b, (0, y + 2.0, 0), (3.6, 3.6, 3.6), "dark")
    m.box(b, (0, y + 2.0, 0), (3.0, 3.0, 3.0), "glow", rot=(45, 45, 0))
    for v in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, 0, 1), (0, 0, -1), (0.7, 0.7, 0), (-0.7, 0.7, 0)):
        m.box(b, (v[0] * 2.4, y + 2.0 + v[1] * 2.4, v[2] * 2.4), (1.0, 1.0, 1.0), "edge", rot=(45, 45, 0))
    idle = {f"link{k}": {"rotation": geo.wave(IDLE, (0, 0, 6), 8, phase=k * 0.5)} for k in range(links)}
    idle["ball"] = {"rotation": geo.spin(IDLE, 1, 1, 8)}
    tf = {"link0": {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.5, [0, 360, 40]), (1, [0, 720, 0])], 8)},
          "ball": {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.4, [1.5, 1.5, 1.5]), (1, [1, 1, 1])])}}
    return idle, tf


def spear(m: Model, shaft=26.0, head="leaf", tassel=True, ring=False):
    m.bone("root")
    m.bone("shaft", parent="root")
    m.box("shaft", (0, -14 + shaft / 2, 0), (1.2, shaft, 1.2), "wood")
    for y in (-13.0, -4.0, 4.0):
        m.box("shaft", (0, y, 0), (1.6, 0.6, 1.6), "gold")
    top = -14 + shaft
    m.bone("head", pivot=(0, top, 0), parent="root")
    m.box("head", (0, top + 0.6, 0), (1.8, 1.6, 1.8), "gold")
    if head == "trident":
        m.box("head", (0, top + 1.8, 0), (6.0, 1.0, 1.2), "metal")
        for s in (-1, 0, 1):
            h = 6.0 if s == 0 else 4.5
            m.box("head", (s * 2.6, top + 2.2 + h / 2, 0), (0.9, h, 0.9), "blade")
            m.box("head", (s * 2.6, top + 2.4 + h, 0), (1.2, 1.2, 0.7), "edge", rot=(0, 0, 45))
            if s:
                m.box("head", (s * 3.3, top + 2.8 + h - 1.6, 0), (0.6, 1.4, 0.6), "edge", rot=(0, 0, s * 30))
    elif head == "astral":
        for k in range(5):
            w = 3.4 - k * 0.6
            m.box("head", (0, top + 2.4 + k * 1.5, 0), (w, 1.6, w), "blade", rot=(0, 45, 0))
        m.box("head", (0, top + 10.0, 0), (1.2, 2.4, 1.2), "edge", rot=(0, 45, 0))
        for s in (-1, 1):
            m.bar("head", (s * 1.2, top + 2.0, 0), (s * 3.6, top + 5.4, 0), 0.8, 0.8, "gold")
        m.box("head", (0, top + 2.4, 0), (2.0, 2.0, 2.0), "gem", rot=(45, 45, 0))
    else:  # leaf
        m.box("head", (0, top + 5.0, 0), (2.6, 7.0, 0.7), "blade")
        m.box("head", (0, top + 8.6, 0), (1.9, 1.9, 0.6), "edge", rot=(0, 0, 45))
        m.box("head", (0, top + 4.6, 0), (0.6, 6.0, 0.9), "core")
        for s in (-1, 1):
            m.box("head", (s * 1.4, top + 5.0, 0), (0.4, 6.0, 0.5), "edge")
    idle, tf = {}, {}
    if tassel:
        b = m.bone("tassel", pivot=(0, top - 0.2, 0), parent="shaft")
        for k in range(3):
            m.box(b, ((k - 1) * 0.5, top - 2.6, 0.7), (0.4, 4.4, 0.3), "wrap")
        idle[b] = {"rotation": geo.wave(IDLE, (14, 0, 8), 8)}
    if ring:
        b = m.bone("ring", pivot=(0, top + 3, 0), parent="head")
        for k in range(12):
            r = k * math.pi / 6
            m.box(b, (math.cos(r) * 3.6, top + 3 + math.sin(r) * 3.6, 0), (0.7, 0.7, 0.5), "core")
        idle[b] = {"rotation": geo.spin(IDLE, 2, 1, 8)}
        tf[b] = {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.35, [1.8, 1.8, 1.8]), (1, [1, 1, 1])])}
    tf["root"] = {"position": geo.ease(TF, [(0, [0, 0, 0]), (0.2, [0, -3, 0]), (0.4, [0, 6, 0]), (1, [0, 0, 0])])}
    tf["head"] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.6, [0, 720, 0]), (1, [0, 720, 0])], 8)}
    return idle, tf


def staff(m: Model, orb="gem", prongs=4, shaft=24.0, rings=1, wings=False):
    m.bone("root")
    m.bone("shaft", parent="root")
    m.box("shaft", (0, -13 + shaft / 2, 0), (1.3, shaft, 1.3), "wood")
    for k in range(4):  # 巻き付く蔦 (金の帯を螺旋状に)
        m.box("shaft", (0, -8 + k * 3.5, 0), (1.7, 0.5, 1.7), "gold", rot=(0, k * 22, 8))
    m.box("shaft", (0, -13.6, 0), (1.8, 1.2, 1.8), "gold", rot=(0, 45, 0))
    top = -13 + shaft
    m.box("shaft", (0, top + 0.4, 0), (2.4, 1.2, 2.4), "gold", rot=(0, 45, 0))
    for k in range(prongs):  # 宝珠を抱く爪
        a = k * 360 / prongs
        r = math.radians(a)
        m.box("shaft", (math.cos(r) * 1.6, top + 2.8, math.sin(r) * 1.6), (0.6, 4.0, 0.6), "gold", rot=(0, -a, math.degrees(0.25)))
        m.box("shaft", (math.cos(r) * 1.3, top + 5.0, math.sin(r) * 1.3), (0.5, 1.2, 0.5), "edge", rot=(0, -a, -15))
    m.bone("orb", pivot=(0, top + 3.2, 0), parent="root")
    if orb == "void":
        m.box("orb", (0, top + 3.2, 0), (3.0, 3.0, 3.0), "dark", rot=(45, 45, 0))
        m.box("orb", (0, top + 3.2, 0), (2.0, 2.0, 2.0), "glow", rot=(0, 45, 45))
    elif orb == "moon":
        for k in range(6):
            a = math.radians(-100 + k * 40)
            m.box("orb", (math.cos(a) * 1.8, top + 3.2 + math.sin(a) * 1.8, 0), (1.2, 1.2, 1.0), "core", rot=(0, 0, math.degrees(a)))
    else:
        m.box("orb", (0, top + 3.2, 0), (2.8, 2.8, 2.8), "gem", rot=(45, 45, 0))
    idle = {"orb": {"rotation": geo.spin(IDLE, 1, 1, 8), "position": geo.wave(IDLE, (0, 0.35, 0), 8)}}
    tf = {"orb": {"position": geo.ease(TF, [(0, [0, 0, 0]), (0.35, [0, 4, 0]), (0.8, [0, 4, 0]), (1, [0, 0, 0])]),
                  "scale": geo.ease(TF, [(0, [1, 1, 1]), (0.35, [1.8, 1.8, 1.8]), (1, [1, 1, 1])])}}
    for k in range(rings):
        b = m.bone(f"ring{k}", pivot=(0, top + 3.2, 0), parent="orb")
        rad = 3.6 + k * 1.4
        for j in range(14):
            r = j * 2 * math.pi / 14
            m.box(b, (math.cos(r) * rad, top + 3.2, math.sin(r) * rad), (0.6, 0.3, 0.6), "core")
        idle[b] = {"rotation": geo.spin(IDLE, 0 if k % 2 else 2, 1 if k % 2 else -1, 8)}
        tf[b] = {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.4, [2.4, 2.4, 2.4]), (1, [1, 1, 1])])}
    if wings:
        for s in (-1, 1):
            b = m.bone(f"wing{s + 1}", pivot=(s * 1.0, top, 0), parent="shaft")
            for k in range(4):
                m.box(b, (s * (2.0 + k * 1.2), top - 0.5 + k * 0.9, 0), (1.4, 3.6 - k * 0.6, 0.3), "page", rot=(0, 0, s * (-20 - k * 12)))
            idle[b] = {"rotation": geo.wave(IDLE, (0, s * 12, 0), 8)}
            tf[b] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.35, [0, 0, s * -35]), (1, [0, 0, 0])])}
    return idle, tf


def bow(m: Model, span=22.0, moon=False):
    m.bone("root")
    m.bone("grip", parent="root")
    m.box("grip", (0, 0, 0), (1.6, 4.0, 1.6), "grip")
    m.box("grip", (0, 0, 0.9), (1.2, 1.2, 0.4), "gem", rot=(0, 0, 45))
    idle, tf = {}, {}
    for s in (-1, 1):
        b = m.bone(f"limb{s + 1}", pivot=(0, s * 2.0, 0), parent="root")
        x, y = 0.0, s * 2.0
        for k in range(5):
            ang = 10 + k * 9
            seg = span / 10
            nx = x - math.sin(math.radians(ang)) * seg
            ny = y + s * math.cos(math.radians(ang)) * seg
            m.bar(b, (x, y, 0), (nx, ny, 0), 1.2 - k * 0.1, 1.0, "gold" if moon and k % 2 else "wood")
            x, y = nx, ny
        m.box(b, (x, y, 0), (1.4, 1.4, 1.0), "gem" if moon else "gold", rot=(0, 0, 45))
        if moon:
            m.box(b, (x - 1.2, y - s * 1.0, 0), (0.5, 2.6, 0.5), "core", rot=(0, 0, s * 30))
        idle[b] = {"rotation": geo.wave(IDLE, (0, 0, s * 1.5), 8)}
        tf[b] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.3, [0, 0, s * -12]), (0.45, [0, 0, s * 4]), (1, [0, 0, 0])])}
        tip = (x, y)
    m.bone("string", pivot=(tip[0], 0, 0), parent="root")
    m.box("string", (tip[0], 0, 0), (0.3, abs(tip[1]) * 2, 0.3), "string")
    m.bone("arrow", pivot=(tip[0], 0, 0), parent="root")
    m.box("arrow", (tip[0] + 6, 0, 0), (12.0, 0.4, 0.4), "glow", rot=(0, 0, 0))
    m.box("arrow", (tip[0] + 12.4, 0, 0), (1.2, 1.2, 0.4), "edge", rot=(0, 0, 45))
    tf["string"] = {"position": geo.ease(TF, [(0, [0, 0, 0]), (0.3, [-3, 0, 0]), (0.45, [0.5, 0, 0]), (1, [0, 0, 0])])}
    tf["arrow"] = {"position": geo.ease(TF, [(0, [0, 0, 0]), (0.3, [-3, 0, 0]), (0.45, [20, 0, 0]), (0.46, [0, 0, 0]), (1, [0, 0, 0])]),
                   "scale": geo.ease(TF, [(0, [0, 0, 0]), (0.05, [1, 1, 1]), (0.45, [1, 1, 1]), (0.46, [0, 0, 0]), (1, [0, 0, 0])])}
    idle["arrow"] = {"scale": {0.0: [0, 0, 0], IDLE: [0, 0, 0]}}
    return idle, tf


def scythe(m: Model, shaft=26.0, soul=False):
    m.bone("root")
    m.bone("shaft", parent="root")
    m.box("shaft", (0, -14 + shaft / 2, 0), (1.3, shaft, 1.3), "dark" if soul else "wood")
    for y in (-12.0, -2.0):
        m.box("shaft", (0, y, 0), (1.8, 1.4, 1.8), "wrap")
    top = -14 + shaft
    m.box("shaft", (0, top, 0), (2.2, 2.2, 2.2), "gold", rot=(0, 45, 0))
    m.bone("blade", pivot=(0, top, 0), parent="root")
    x, y = 0.5, top
    for k in range(7):
        ang = 175 - k * 14
        seg = 2.6
        nx = x + math.cos(math.radians(ang)) * seg * -1
        ny = y + math.sin(math.radians(ang)) * seg * 0.5 + 0.2
        w = 2.6 - k * 0.3
        m.bar("blade", (x, y, 0), (nx, ny, 0), w, 0.6, "blade")
        m.bar("blade", (x, y - w / 2, 0), (nx, ny - w / 2, 0), 0.45, 0.5, "edge")
        x, y = nx, ny
    m.box("blade", (0.4, top, 1.2), (1.4, 1.4, 0.5), "gem", rot=(0, 0, 45))
    idle, tf = {}, {}
    if soul:
        for k in range(3):
            b = m.bone(f"wisp{k}", pivot=(0, top, 0), parent="blade")
            m.box(b, (-3 - k * 3, top + 3 + (k % 2), 1.5), (1.0, 1.0, 1.0), "core", rot=(45, 45, 0))
            idle[b] = {"position": geo.wave(IDLE, (0.6, 0.8, 0.4), 8, phase=k * 2.1)}
            tf[b] = {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.4, [2.0, 2.0, 2.0]), (1, [1, 1, 1])])}
    tf["blade"] = {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.3, [1.35, 1.35, 1.35]), (1, [1, 1, 1])])}
    tf["root"] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.25, [0, 0, 30]), (0.55, [0, 0, -50]), (1, [0, 0, 0])])}
    return idle, tf


def whip(m: Model, segs=8):
    m.bone("root")
    m.bone("handle", parent="root")
    _grip(m, "handle", -11.0, -4.0, width=1.6, pommel="gem", rings=3)
    m.box("handle", (0, -3.4, 0), (2.4, 1.0, 2.4), "gold")
    parent, y = "handle", -3.0
    idle, tf = {}, {}
    for k in range(segs):
        b = m.bone(f"seg{k}", pivot=(0, y, 0), parent=parent)
        sz = 1.2 - k * 0.08
        m.box(b, (0, y + 1.1, 0), (sz, 2.2, sz), "bone" if k % 2 else "dark")
        if k % 2 == 0:
            m.box(b, (0.7, y + 1.1, 0), (0.6, 0.9, 0.4), "edge", rot=(0, 0, -30))
        idle[b] = {"rotation": geo.wave(IDLE, (6, 0, 9), 8, phase=k * 0.7)}
        tf[b] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.2, [0, 0, -25]), (0.45, [0, 0, 30 - k * 2]), (0.7, [0, 0, -10]), (1, [0, 0, 0])])}
        parent, y = b, y + 2.2
    b = m.bone("tip", pivot=(0, y, 0), parent=parent)
    m.box(b, (0, y + 0.8, 0), (1.4, 1.4, 1.4), "glow", rot=(45, 45, 0))
    return idle, tf


def chakram(m: Model, radius=7.0, eclipse=False):
    m.bone("root")
    m.bone("ring", pivot=(0, 0, 0), parent="root")
    n = 16
    for k in range(n):
        a = k * 2 * math.pi / n
        m.box("ring", (math.cos(a) * radius, math.sin(a) * radius, 0), (2.9, 1.6, 0.6), "blade", rot=(0, 0, math.degrees(a) + 90))
        if k % 2 == 0:
            m.box("ring", (math.cos(a) * (radius + 1.4), math.sin(a) * (radius + 1.4), 0), (1.3, 1.3, 0.4), "edge", rot=(0, 0, math.degrees(a) + 45))
    m.box("ring", (0, 0, 0), (2.4, 2.4, 0.8), "dark" if eclipse else "gold", rot=(0, 0, 45))
    m.box("ring", (0, 0, 0.3), (1.6, 1.6, 0.6), "gem", rot=(0, 0, 45))
    for k in range(4):  # 握りの十字
        a = k * math.pi / 2
        m.box("ring", (math.cos(a) * radius * 0.5, math.sin(a) * radius * 0.5, 0), (radius - 1.6, 0.6, 0.5), "grip", rot=(0, 0, math.degrees(a)))
    idle, tf = {"ring": {"rotation": geo.spin(IDLE, 2, 1, 8)}}, {}
    if eclipse:
        b = m.bone("corona", pivot=(0, 0, 0), parent="root")
        for k in range(12):
            a = k * math.pi / 6
            m.box(b, (math.cos(a) * (radius + 3.0), math.sin(a) * (radius + 3.0), -0.4), (1.6, 0.5, 0.3), "core", rot=(0, 0, math.degrees(a)))
        idle[b] = {"rotation": geo.spin(IDLE, 2, -0.5, 8)}
        tf[b] = {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.4, [1.6, 1.6, 1]), (1, [1, 1, 1])])}
    tf["ring"] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (1, [0, 0, 1080])], 12)}
    tf["root"] = {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.3, [1.3, 1.3, 1.3]), (1, [1, 1, 1])])}
    return idle, tf


def tome(m: Model, clasp="gem", flames=False, chaos=False):
    m.bone("root")
    m.bone("spine", parent="root")
    m.box("spine", (-4.2, 0, 0), (0.9, 11.0, 2.6), "dark")
    m.box("spine", (0, 0, 0), (7.6, 10.4, 1.8), "page")
    idle, tf = {}, {}
    for s in (-1, 1):
        b = m.bone(f"cover{s + 1}", pivot=(-4.2, 0, s * 1.2), parent="root")
        m.box(b, (0, 0, s * 1.2), (8.6, 11.2, 0.6), "dark")
        m.box(b, (0, 0, s * 1.55), (6.4, 9.0, 0.2), "gold")
        m.box(b, (0, 0, s * 1.7), (2.6, 2.6, 0.4), "gem", rot=(0, 0, 45))
        for c in ((-1, -1), (1, -1), (-1, 1), (1, 1)):  # 角金具
            m.box(b, (c[0] * 3.8, c[1] * 5.1, s * 1.5), (1.2, 1.2, 0.3), "gold", rot=(0, 0, 45))
        tf[b] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.3, [0, s * 70, 0]), (0.8, [0, s * 70, 0]), (1, [0, 0, 0])])}
    for k in range(4):  # 舞う頁
        b = m.bone(f"page{k}", pivot=(0, 0, 0), parent="root")
        m.box(b, ((k - 1.5) * 2.4, 7.5 + (k % 2) * 1.2, 0), (2.0, 2.6, 0.2), "page", rot=(10, 0, (k - 1.5) * 15))
        idle[b] = {"position": geo.wave(IDLE, (0, 0.6, 0), 8, phase=k * 1.6), "scale": {0.0: [0.7, 0.7, 0.7], IDLE: [0.7, 0.7, 0.7]}}
        tf[b] = {"position": geo.ease(TF, [(0, [0, 0, 0]), (0.4, [(k - 1.5) * 2, 5, 0]), (1, [0, 0, 0])]),
                 "rotation": geo.ease(TF, [(0, [0, 0, 0]), (1, [0, 360 * (1 if k % 2 else -1), 0])], 8),
                 "scale": geo.ease(TF, [(0, [0.7, 0.7, 0.7]), (0.4, [1.3, 1.3, 1.3]), (1, [0.7, 0.7, 0.7])])}
    if flames or chaos:
        for k in range(3):
            b = m.bone(f"fx{k}", pivot=(0, 6, 0), parent="root")
            m.box(b, ((k - 1) * 2.4, 6.8 + (1 - abs(k - 1)) * 1.4, 0), (1.2, 2.4, 1.2), "glow", rot=(0, 45, 0))
            idle[b] = {"scale": geo.wave(IDLE, (0.25, 0.4, 0.25), 8, phase=k * 2.0, base=(1, 1, 1))}
            tf[b] = {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.35, [1.8, 2.6, 1.8]), (1, [1, 1, 1])])}
    idle["root"] = {"position": geo.wave(IDLE, (0, 0.3, 0), 8)}
    return idle, tf


def gauntlet(m: Model, big=False, claws=False):
    k = 1.25 if big else 1.0
    m.bone("root")
    m.bone("cuff", parent="root")
    m.box("cuff", (0, -6 * k, 0), (5.0 * k, 6.0 * k, 5.0 * k), "metal")
    for y in (-8.5, -4.0):
        m.box("cuff", (0, y * k, 0), (5.6 * k, 0.8 * k, 5.6 * k), "gold")
    m.box("cuff", (0, -6 * k, 2.6 * k), (2.0, 2.0, 0.5), "gem", rot=(0, 0, 45))
    m.bone("hand", pivot=(0, -2.5 * k, 0), parent="root")
    m.box("hand", (0, -1.0 * k, 0), (5.2 * k, 3.4 * k, 4.4 * k), "metal")
    idle, tf = {}, {}
    for f in range(4):
        b = m.bone(f"finger{f}", pivot=((f - 1.5) * 1.3 * k, 0.8 * k, 0.8), parent="hand")
        m.box(b, ((f - 1.5) * 1.3 * k, 2.2 * k, 0.8), (1.1 * k, 2.8 * k, 1.2 * k), "blade")
        if claws:
            m.box(b, ((f - 1.5) * 1.3 * k, 5.0 * k, 0.8), (0.6, 3.6 * k, 0.5), "edge", rot=(0, 0, (f - 1.5) * -6))
        tf[b] = {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.25, [-80, 0, 0]), (0.7, [-80, 0, 0]), (1, [0, 0, 0])])}
        idle[b] = {"rotation": geo.wave(IDLE, (4, 0, 0), 8, phase=f * 0.6)}
    b = m.bone("thumb", pivot=(2.6 * k, -1.0 * k, 0.5), parent="hand")
    m.box(b, (3.0 * k, 0.2, 0.6), (1.2 * k, 2.4 * k, 1.2 * k), "blade", rot=(0, 0, -25))
    tf["hand"] = {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.3, [1.3, 1.3, 1.3]), (1, [1, 1, 1])])}
    tf["root"] = {"position": geo.ease(TF, [(0, [0, 0, 0]), (0.3, [0, -2, 0]), (0.45, [0, 4, 0]), (1, [0, 0, 0])])}
    return idle, tf


def horn(m: Model):
    m.bone("root")
    m.bone("horn", parent="root")
    x, y, w = 0.0, -8.0, 1.6
    for k in range(9):
        ang = 10 + k * 13
        nx, ny = x + math.sin(math.radians(ang)) * 2.0, y + math.cos(math.radians(ang)) * 2.0
        m.bar("horn", (x, y, 0), (nx, ny, 0), w, w, "bone")
        if k % 3 == 1:
            m.bar("horn", (x, y, 0), (nx, ny, 0), w + 0.4, w + 0.4, "gold")
        x, y, w = nx, ny, w + 0.35
    m.bone("bell", pivot=(x, y, 0), parent="root")
    m.box("bell", (x + 0.6, y, 0), (1.4, w + 1.4, w + 1.4), "gold")
    m.box("bell", (x + 1.2, y, 0), (0.4, w, w), "glow")
    m.box("horn", (0.2, -7.0, 1.0), (1.4, 1.4, 0.5), "gem", rot=(0, 0, 45))
    b = m.bone("strap", pivot=(0, -8, 0), parent="root")
    for k in range(5):
        m.box(b, (1.2 + k * 1.4, -9.5 - math.sin(k / 4 * math.pi) * 1.6, 0), (1.5, 0.5, 0.5), "grip", rot=(0, 0, -20 + k * 10))
    idle = {"strap": {"rotation": geo.wave(IDLE, (0, 0, 5), 8)}}
    tf = {"bell": {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.2, [1.6, 1.6, 1.6]), (0.4, [1.2, 1.2, 1.2]), (0.6, [1.5, 1.5, 1.5]), (1, [1, 1, 1])])},
          "root": {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.3, [0, 0, 15]), (1, [0, 0, 0])])}}
    return idle, tf


def gun(m: Model):
    m.bone("root")
    m.bone("body", parent="root")
    m.box("body", (0, -6.0, 0), (1.8, 5.0, 1.8), "grip", rot=(0, 0, -15))
    m.box("body", (0, -2.0, 0), (2.4, 4.0, 2.4), "metal")
    m.box("body", (0, -2.0, 1.3), (1.2, 1.2, 0.4), "gem", rot=(0, 0, 45))
    m.bone("drum", pivot=(0, -1.5, 0), parent="body")
    for k in range(6):
        a = k * math.pi / 3
        m.box("drum", (math.cos(a) * 1.3, -1.5, math.sin(a) * 1.3), (0.9, 2.4, 0.9), "glow")
    m.bone("barrel", pivot=(0, 0, 0), parent="body")
    m.box("barrel", (0, 5.0, 0), (1.6, 10.0, 1.6), "metal")
    m.box("barrel", (0, 9.6, 0), (2.4, 1.2, 2.4), "gold")
    for y in (2.0, 4.5, 7.0):
        m.box("barrel", (0, y, 0), (2.0, 0.5, 2.0), "dark")
    m.box("barrel", (0, 4.0, 1.4), (0.8, 5.0, 0.8), "core")  # 照準の光る線
    idle = {"drum": {"rotation": geo.spin(IDLE, 1, 0.5, 8)}}
    tf = {"barrel": {"position": geo.ease(TF, [(0, [0, 0, 0]), (0.1, [0, -2.5, 0]), (0.35, [0, 0, 0]), (0.45, [0, -2.5, 0]), (0.7, [0, 0, 0]), (1, [0, 0, 0])])},
          "drum": {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (1, [0, 720, 0])], 8)}}
    return idle, tf


def wand(m: Model, rod=False):
    m.bone("root")
    m.bone("shaft", parent="root")
    L = 18.0 if rod else 13.0
    m.box("shaft", (0, -10 + L / 2, 0), (1.0, L, 1.0), "wood")
    _grip(m, "shaft", -10.0, -5.5, width=1.3, pommel="cap", rings=2)
    top = -10 + L
    m.bone("tip", pivot=(0, top + 1.2, 0), parent="root")
    m.box("tip", (0, top + 1.2, 0), (2.0, 2.0, 2.0), "gem", rot=(45, 45, 0))
    for k in range(3):
        a = k * 120
        r = math.radians(a)
        m.box("tip", (math.cos(r) * 1.4, top + 0.6, math.sin(r) * 1.4), (0.4, 2.6, 0.4), "gold", rot=(0, -a, 15))
    b = m.bone("motes", pivot=(0, top + 1.2, 0), parent="tip")
    for k in range(5):
        a = k * 2 * math.pi / 5
        m.box(b, (math.cos(a) * 2.6, top + 1.2 + math.sin(a * 2) * 0.8, math.sin(a) * 2.6), (0.5, 0.5, 0.5), "core")
    idle = {"motes": {"rotation": geo.spin(IDLE, 1, 1, 8)}, "tip": {"scale": geo.wave(IDLE, (0.08, 0.08, 0.08), 8, base=(1, 1, 1))}}
    tf = {"motes": {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.35, [2.6, 2.6, 2.6]), (1, [1, 1, 1])])},
          "tip": {"scale": geo.ease(TF, [(0, [1, 1, 1]), (0.3, [1.7, 1.7, 1.7]), (1, [1, 1, 1])])},
          "root": {"rotation": geo.ease(TF, [(0, [0, 0, 0]), (0.25, [0, 0, 25]), (0.5, [0, 0, -15]), (1, [0, 0, 0])])}}
    return idle, tf


# ================================================================ 武器ごとの意匠

SPEC = {
    "abyss_sword": (sword, {}),
    "abyss_greatsword": (sword, {"w": 4.6, "L": 21.0, "guard": "wing", "pommel": "cap"}),
    "abyss_rune_blade": (sword, {"rune": True, "guard": "ring", "floaters": 2}),
    "abyss_rift_blade": (sword, {"w": 3.2, "L": 17.0, "rune": True, "serrated": True, "guard": "claw", "floaters": 3}),
    "abyss_wind_blade": (sword, {"w": 2.0, "L": 19.0, "guard": "wing", "ribbons": True, "fuller": False}),
    "abyss_katana": (katana, {}),
    "abyss_echo_katana": (katana, {"echo": 2}),
    "abyss_frost_katana": (katana, {"shards": 3, "curve": 6.0}),
    "abyss_dagger": (dagger, {}),
    "abyss_fang_dagger": (dagger, {"fang": True}),
    "abyss_twin_dagger": (dagger, {"twin": True}),
    "abyss_axe": (axe, {}),
    "abyss_blood_axe": (axe, {"double": True, "bearded": True}),
    "abyss_hammer": (hammer, {}),
    "abyss_quake_hammer": (hammer, {"size": 6.4, "haft": 20.0, "spikes": True}),
    "abyss_moon_mace": (mace, {}),
    "abyss_ember_flail": (flail, {}),
    "abyss_spear": (spear, {}),
    "abyss_storm_spear": (spear, {"ring": True}),
    "abyss_astral_lance": (spear, {"head": "astral", "tassel": False, "ring": True}),
    "abyss_meteor_lance": (spear, {"head": "astral"}),
    "abyss_tide_trident": (spear, {"head": "trident"}),
    "abyss_staff": (staff, {}),
    "abyss_void_staff": (staff, {"orb": "void", "rings": 2}),
    "abyss_rod": (wand, {"rod": True}),
    "abyss_wand": (wand, {}),
    "abyss_bow": (bow, {}),
    "abyss_moon_bow": (bow, {"moon": True}),
    "abyss_scythe": (scythe, {}),
    "abyss_soul_scythe": (scythe, {"soul": True}),
    "abyss_venom_whip": (whip, {}),
    "abyss_chakram": (chakram, {}),
    "abyss_eclipse_chakram": (chakram, {"eclipse": True}),
    "abyss_tome": (tome, {}),
    "abyss_chaos_tome": (tome, {"chaos": True}),
    "abyss_flame_grimoire": (tome, {"flames": True}),
    "abyss_gauntlet": (gauntlet, {}),
    "abyss_titan_gauntlet": (gauntlet, {"big": True}),
    "abyss_howling_claw": (gauntlet, {"claws": True}),
    "abyss_war_horn": (horn, {}),
    "abyss_depth_gun": (gun, {}),
}

# 手持ちの大きさ (三人称, 一人称)。長物は小さめ
SCALES = {sword: (0.62, 0.5), katana: (0.6, 0.48), dagger: (0.7, 0.6), axe: (0.6, 0.5), hammer: (0.6, 0.5),
          mace: (0.62, 0.5), flail: (0.62, 0.5), spear: (0.52, 0.42), staff: (0.55, 0.45), bow: (0.6, 0.5),
          scythe: (0.52, 0.42), whip: (0.62, 0.5), chakram: (0.55, 0.45), tome: (0.6, 0.5), gauntlet: (0.62, 0.5),
          horn: (0.65, 0.55), gun: (0.65, 0.55), wand: (0.65, 0.55)}


def build(item_id: str, color: int, material: str = "steel"):
    """(model, palette, anim, (scale_tp, scale_fp))"""
    fn, kw = SPEC[item_id]
    m = Model(item_id)
    idle, tf = fn(m, **kw)
    m.layout(128)
    anim_i = geo.anim_json(f"animation.{item_id}.idle", IDLE, idle or {"root": {"rotation": {0.0: [0, 0, 0]}}})
    anim_t = geo.anim_json(f"animation.{item_id}.transform", TF, tf)
    anim_t["animations"][f"animation.{item_id}.transform"]["loop"] = False
    anim_i["animations"].update(anim_t["animations"])
    return m, palette(color, material), anim_i, SCALES[fn]
