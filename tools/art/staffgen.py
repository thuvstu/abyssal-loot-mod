"""杖ジェネレータ (MCAssetGen の Spellforge Atelier = minecraft-magic-staff-generator を移植・改良したもの)。

杖を「部品の組み合わせ」で描く。データ (`staffgen_data.json`) は元アプリから焼き出したもの:
- 属性 28 種 (炎・氷・雷・自然・水・光・闇・虚空・秘術・血・大地・風・星辰・時間・音・鋼・毒・嵐・魂・水晶・重力・
  プラズマ・桜・夢・極光・墨・幸運・祭) … 核の色・魔力の色・浮遊物
- 素材 43 種 … 柄と金具の色 (金属なら艶を強く)
- 杖の型 45 種 (魔導師・ドルイド・月・死霊術・熾天使・死神・蛇杖・時術師・嵐呼び…) … 柄の形・模様・握り・柄頭・
  台座 (爪・王冠・三日月・翼・鹿角…)・核 (結晶・宝珠・星・髑髏・魔眼・霊炎・月・心臓…)・浮遊物の既定値

元実装 (Canvas に 3200 行の手描き処理) からの改良点
- 描画はこの mod の HD 描画基盤 (art/hd.py) の部品 (法線で塗る円柱・球・三日月・星) に置き換えた。
  光源・色ランプ・selout 輪郭・発光の光暈が既存の 64px 武器アイコンと揃う
- 柄は折れ線に沿った「管」として 1 画素ずつ軸方向の位置 t と断面の位置 s を持たせ、模様 (木目・螺旋・ルーン・
  溶岩脈・金線…) をその座標で描く。曲がった柄でも模様が柄に沿う
- 浮遊物と粒子は 8 コマのアニメーション (浮遊物は軌道に沿って回り、粒子は魔力の色で漂う)
- 乱数は名前由来で決定的。未知の型・属性・部品は一覧付きの ValueError

使い方:
    img = staffgen.render(staffgen.config("lunar", element="ice"))          # 1 コマ
    frames = staffgen.frames(cfg)                                          # 8 コマ
CLI: `python tools/abyss.py staffgen list | render | sheet`
"""
from __future__ import annotations

import io
import json
import math
import os
import random
import zlib

from PIL import Image

from . import hd
from .hd import hexc, mix, ramp

_HERE = os.path.dirname(os.path.abspath(__file__))
with io.open(os.path.join(_HERE, "staffgen_data.json"), encoding="utf-8") as _f:
    DATA = json.load(_f)
ELEMENTS: dict = DATA["elements"]
MATERIALS: dict = DATA["materials"]
STAFF_TYPES: dict = DATA["staff_types"]
SIGNATURES: dict = DATA["type_signatures"]
FRAMES = 8
WHITE = (255, 255, 255, 255)


def _c(hexstr: str):
    return hexc(int(hexstr.lstrip("#"), 16))


def _mat(mid: str):
    if mid not in MATERIALS:
        raise ValueError(f"未知の素材 '{mid}'。使えるもの: {', '.join(MATERIALS)}")
    m = MATERIALS[mid]
    return _c(m["color"]), bool(m.get("metal")), bool(m.get("glow"))


def config(staff_type: str = "wizard", element: str | None = None, **over) -> dict:
    """型の既定値 + 型の特徴 (浮遊物など) + 属性の色。over で個別に上書き。"""
    if staff_type not in STAFF_TYPES:
        raise ValueError(f"未知の杖の型 '{staff_type}'。使えるもの: {', '.join(STAFF_TYPES)}")
    cfg = {"type": staff_type, "length": 1.0, "thickness": 2, "taper": True, "shape": "straight",
           "shaftMaterial": "oak", "pattern": "plain", "grip": False, "gripStyle": "wrap", "bands": 1,
           "trimMaterial": "gold", "pommel": "cap", "mount": "none", "core": "orb", "coreSize": 1.0,
           "floatingCore": False, "floater": "none", "floaterPath": "orbit", "floaterCount": 3,
           "ornament": "none", "element": element or "arcane", "seed": staff_type, "particles": True}
    cfg.update(STAFF_TYPES[staff_type]["preset"])
    sig = SIGNATURES.get(staff_type, {})
    for k in ("floater", "floaterPath", "floaterCount", "ornament", "gemCut"):
        if k in sig:
            cfg[k] = sig[k]
    if element:
        cfg["element"] = element
    cfg.update(over)
    if cfg["element"] not in ELEMENTS:
        raise ValueError(f"未知の属性 '{cfg['element']}'。使えるもの: {', '.join(ELEMENTS)}")
    el = ELEMENTS[cfg["element"]]
    cfg.setdefault("coreColor", el["core"])
    cfg.setdefault("energyColor", el["energy"])
    if cfg["floater"] == "none" and over.get("floater") is None and not sig:
        cfg["floater"] = el["floater"]
    return cfg


# ---------------------------------------------------------------- 柄 (管)

class Tube:
    """折れ線に沿った太さ付きの管。画素ごとに (t: 柄尻 0 → 先端 1, s: 断面 -1..1) を持つ。"""

    def __init__(self, pts, r0: float, r1: float):
        self.pts, self.r0, self.r1 = pts, r0, r1
        self.seg_len = [math.dist(a, b) for a, b in zip(pts, pts[1:])]
        self.total = sum(self.seg_len) or 1.0

    def at(self, t: float):
        """管の中心線上の点と接線。"""
        dist = max(0.0, min(1.0, t)) * self.total
        for (a, b), L in zip(zip(self.pts, self.pts[1:]), self.seg_len):
            if dist <= L or L == 0:
                k = dist / L if L else 0
                return (a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k), ((b[0] - a[0]) / (L or 1), (b[1] - a[1]) / (L or 1))
            dist -= L
        a, b = self.pts[-2], self.pts[-1]
        L = self.seg_len[-1] or 1
        return b, ((b[0] - a[0]) / L, (b[1] - a[1]) / L)

    def normal(self, t):
        _p, (ux, uy) = self.at(t)
        nx, ny = -uy, ux
        if nx + ny < 0:
            nx, ny = -nx, -ny
        return nx, ny

    def locate(self, x: float, y: float):
        """点 → (t, s, 半径)。管の外なら None。"""
        best = None
        acc = 0.0
        for (a, b), L in zip(zip(self.pts, self.pts[1:]), self.seg_len):
            dx, dy = b[0] - a[0], b[1] - a[1]
            L2 = dx * dx + dy * dy or 1e-9
            k = max(0.0, min(1.0, ((x - a[0]) * dx + (y - a[1]) * dy) / L2))
            qx, qy = a[0] + dx * k, a[1] + dy * k
            d = math.hypot(x - qx, y - qy)
            t = (acc + k * L) / self.total
            r = self.r0 + (self.r1 - self.r0) * t
            if d <= r and (best is None or d / r < best[3]):
                side = (x - qx) * (-dy) + (y - qy) * dx
                s = (d / r) * (1 if side >= 0 else -1)
                best = (t, s, r, d / r)
            acc += L
        return None if best is None else best[:3]


def _shaft_points(shape: str, length: float, rnd: random.Random):
    """柄の中心線 (柄尻→先端)。先端の位置は頭の部品を置く場所になる。"""
    p0 = (8.0, 58.0)
    tip = (8 + 36 * length, 58 - 36 * length)
    n = 18
    pts = []
    for i in range(n + 1):
        t = i / n
        x = p0[0] + (tip[0] - p0[0]) * t
        y = p0[1] + (tip[1] - p0[1]) * t
        # 軸に直交 (右下向き) の振れ
        off = 0.0
        if shape == "curved":
            off = 4.0 * math.sin(math.pi * t)
        elif shape == "gnarled":
            off = 0.9 * math.sin(t * 11 + rnd.uniform(0, 3)) + 0.45 * math.sin(t * 27)
        elif shape == "swan":
            off = -5.0 * math.sin(math.pi * min(1, t * 1.15)) * (t ** 1.5) * 2.2
        pts.append((x + off * 0.7071, y + off * 0.7071))
    if shape == "hooked":  # 先端が峰側へ巻き込む
        hx, hy = pts[-1]
        for i in range(1, 7):
            a = -math.pi / 4 + i * 0.42
            pts.append((hx + 5 * math.sin(a) - 3.5, hy - 5 * math.cos(a) + 3.5))
    return pts


def _pattern(pattern: str, t: float, s: float, base, trim, energy, rnd_seed: int):
    """柄の模様: (t, s) から色を変える (None なら素の材質)。戻り値は (色, 発光)。"""
    h = lambda a, b: (zlib.crc32(f"{a},{b},{rnd_seed}".encode()) % 1000) / 1000  # noqa: E731
    if pattern in ("plain", "microEdge", "vanillaGrain") or pattern is None:
        if pattern == "vanillaGrain" and math.sin(t * 90 + s * 2) > 0.85:
            return hd._hsv_shift(base, -0.12, 0.7, 0.02, 0.0), 0
        return None, 0
    if pattern == "grain" or pattern == "bark":
        k = math.sin(t * 60 + math.sin(t * 9) * 3 + s * 2)
        if k > (0.6 if pattern == "grain" else 0.2):
            return hd._hsv_shift(base, -0.18 if pattern == "bark" else -0.12, 0.7, 0.03, 0.03), 0
        return None, 0
    if pattern == "spiral":
        return (trim, 0) if math.sin(t * 70 + s * 3.2) > 0.55 else (None, 0)
    if pattern == "twisted":
        return (hd._hsv_shift(base, -0.15, 0.7, 0.03, 0.0), 0) if math.sin(t * 70 + s * 3.2) > 0.2 else (None, 0)
    if pattern in ("ribbed", "notch", "bamboo"):
        return (hd._hsv_shift(base, -0.22, 0.7, 0.04, 0.0), 0) if (t * 14) % 1 < (0.18 if pattern != "notch" else 0.1) else (None, 0)
    if pattern in ("runes", "etched", "engraved"):
        on = (t * 11) % 1 < 0.45 and abs(s) < 0.45 and h(int(t * 33), int((s + 1) * 2)) > 0.45 and 0.2 < t < 0.85
        if on:
            return (energy, 0.6) if pattern == "runes" else (hd._hsv_shift(base, -0.25, 0.7, 0.03, 0.0), 0)
        return None, 0
    if pattern == "vine" or pattern == "mossy" or pattern == "woven":
        k = math.sin(t * 40 + s * 4 + math.sin(t * 7) * 2)
        if k > 0.62 or (pattern == "mossy" and h(int(t * 40), int(s * 3)) > 0.82):
            return hexc(0x4f9a3a), 0
        return None, 0
    if pattern in ("lavavein", "cracked", "crystalSeam", "kintsugi"):
        k = abs(math.sin(t * 25 + math.sin(t * 7 + s * 3) * 2.5))
        if k > 0.94:
            if pattern == "cracked":
                return hd._hsv_shift(base, -0.35, 0.7, 0.05, 0.0), 0
            col = {"lavavein": energy, "crystalSeam": energy, "kintsugi": hexc(0xe8c060)}[pattern]
            return col, 0.8 if pattern != "kintsugi" else 0
        return None, 0
    if pattern in ("filigree", "gemInlay", "arabesque"):
        if abs(s) < 0.25 and math.sin(t * 50) > 0.3:
            return trim, 0
        if pattern == "gemInlay" and (t * 6) % 1 < 0.08:
            return energy, 0.5
        return None, 0
    if pattern in ("thinBand", "tipTint"):
        if pattern == "tipTint" and t > 0.78:
            return mix(base, energy, (t - 0.78) / 0.22 * 0.7), 0.2
        return (trim, 0) if (t * 5) % 1 < 0.06 else (None, 0)
    if pattern in ("candy", "chevron"):
        return (hexc(0xf4f0f0) if pattern == "candy" else trim, 0) if math.sin(t * 40 + s * 2.5) > 0.3 else (None, 0)
    if pattern == "chainlink":
        return (hd._hsv_shift(base, -0.25, 0.7, 0.03, 0.0), 0) if abs(math.sin(t * 45)) < 0.25 else (None, 0)
    if pattern in ("scales", "feathered", "barnacle"):
        row = int(t * 26)
        k = ((s + 1) * 1.5 + (0.5 if row % 2 else 0)) % 1
        return (hd._hsv_shift(base, -0.2, 0.7, 0.03, 0.0), 0) if k < 0.22 or (t * 26) % 1 < 0.15 else (None, 0)
    if pattern in ("speckle", "frosted"):
        if h(int(t * 60), int(s * 4)) > 0.86:
            return (WHITE if pattern == "frosted" else hd._hsv_shift(base, -0.25, 0.7, 0.03, 0.0)), 0
        return None, 0
    if pattern in ("gradient", "wave", "circuit", "gearwork", "riveted", "marquetry"):
        if pattern == "gradient":
            return mix(base, energy, t * 0.55), 0.1 * t
        if pattern == "riveted" and (t * 9) % 1 < 0.1 and abs(s) < 0.3:
            return trim, 0
        if pattern == "circuit" and abs(s) < 0.15 and math.sin(t * 30) > 0:
            return energy, 0.4
        if pattern in ("wave", "gearwork", "marquetry") and math.sin(t * 36 + s * 5) > 0.75:
            return hd._hsv_shift(base, 0.08, 0.15, 0.03, 0.0), 0
        return None, 0
    return None, 0


def _draw_shaft(c: hd.Canvas, tube: Tube, base, metal: bool, glow_mat: bool, pattern: str, trim, energy, seed):
    pid = c._new_part()
    rmp = ramp(base, spread=0.72 if metal else 0.6)
    xs = [p[0] for p in tube.pts]
    ys = [p[1] for p in tube.pts]
    pad = max(tube.r0, tube.r1) + 1
    for y in range(int(min(ys) - pad), int(max(ys) + pad) + 1):
        for x in range(int(min(xs) - pad), int(max(xs) + pad) + 1):
            loc = tube.locate(x + 0.5, y + 0.5)
            if loc is None:
                continue
            t, s, _r = loc
            nx, ny = tube.normal(t)
            nz = math.sqrt(max(0.0, 1 - s * s))
            col, glow = _pattern(pattern, t, s, base, trim, energy, seed)
            local = rmp if col is None else ramp(col, spread=0.55)
            c.shade_put(x, y, (nx * s, ny * s, nz), local, pid, spec=0.5 if metal else 0.15)
            if glow or glow_mat:
                c.add_glow(x, y, max(glow, 0.35 if glow_mat else 0))
    return pid


# ---------------------------------------------------------------- 頭の部品

def _ring(c, center, r, width, color, spec=0.6, a0=0.0, a1=2 * math.pi, n=40):
    pts = [(center[0] + math.cos(a0 + (a1 - a0) * i / n) * r, center[1] + math.sin(a0 + (a1 - a0) * i / n) * r)
           for i in range(n + 1)]
    for p, q in zip(pts, pts[1:]):
        c.capsule(p, q, width, ramp(color), spec=spec)


def _claw(c, base, tip, ctrl, r, color):
    """2 次曲線の爪。"""
    pts = []
    for i in range(9):
        t = i / 8
        x = (1 - t) ** 2 * base[0] + 2 * (1 - t) * t * ctrl[0] + t * t * tip[0]
        y = (1 - t) ** 2 * base[1] + 2 * (1 - t) * t * ctrl[1] + t * t * tip[1]
        pts.append((x, y))
    for i, (p, q) in enumerate(zip(pts, pts[1:])):
        c.capsule(p, q, r * (1 - i / 10), ramp(color), spec=0.6)


def _draw_mount(c, mount, head, u, v, trim, energy, ph):
    hx, hy = head
    R = 7.5

    def P(a, b):  # 頭の座標系: a = 先端方向, b = 直交方向
        return (hx + u[0] * a + v[0] * b, hy + u[1] * a + v[1] * b)
    if mount in ("none", None):
        return
    if mount in ("collar", "tinyCollar", "guard", "microRing", "tipCap"):
        w = {"collar": 4.0, "tinyCollar": 2.6, "guard": 6.5, "microRing": 3.0, "tipCap": 2.4}[mount]
        c.capsule(P(-R + 1, -w), P(-R + 1, w), 1.3, ramp(trim), spec=0.7)
        if mount == "guard":
            c.capsule(P(-R + 1, -w), P(-R + 3, -w - 1.5), 1.0, ramp(trim), spec=0.7)
            c.capsule(P(-R + 1, w), P(-R + 3, w + 1.5), 1.0, ramp(trim), spec=0.7)
        return
    if mount in ("prongs", "claw", "cage"):
        n = 4 if mount == "cage" else 3
        for i in range(n):
            b = (-1 + 2 * i / (n - 1)) * 6.5
            bend = 1.2 if mount == "claw" else 0.0
            _claw(c, P(-R + 1, b * 0.25), P(R * 0.75, b * 0.55 - bend * math.copysign(1, b or 1)), P(0, b * 1.25), 1.0, trim)
        if mount == "cage":
            c.capsule(P(R * 0.75, -3.6), P(R * 0.75, 3.6), 0.9, ramp(trim), spec=0.6)
        return
    if mount in ("crown", "tiara"):
        c.capsule(P(-R + 2, -5.5), P(-R + 2, 5.5), 1.2, ramp(trim), spec=0.7)
        for b in (-5, -2.5, 0, 2.5, 5):
            h = 3.5 if b == 0 else 2.5 if abs(b) < 3 else 1.8
            c.capsule(P(-R + 2, b), P(-R + 2 + h * (1.4 if mount == "crown" else 1.0), b * 1.15), 0.8, ramp(trim), spec=0.7, r1=0.3)
        return
    if mount in ("crescent", "scythe"):
        rr = 9.0 if mount == "crescent" else 12.0
        cut = (u[0] * 3.5 + v[0] * 2, u[1] * 3.5 + v[1] * 2) if mount == "crescent" else (u[0] * 3 - v[0] * 4, u[1] * 3 - v[1] * 4)
        cen = P(0, 0) if mount == "crescent" else P(2, -4)
        c.crescent(cen, rr, cut, rr * 0.86, ramp(trim if mount == "crescent" else hexc(0xd0d8e8), spread=0.7))
        return
    if mount in ("ring", "astrolabe", "lyre", "arch"):
        if mount == "arch":
            _ring(c, P(-1, 0), 8.5, 1.0, trim, a0=0, a1=math.pi * 2)
            return
        _ring(c, P(0, 0), 8.0, 0.9, trim)
        if mount == "astrolabe":
            pts = [P(math.cos(a) * 3.5, math.sin(a) * 8.5) for a in [i * math.pi / 20 for i in range(41)]]
            for p, q in zip(pts, pts[1:]):
                c.capsule(p, q, 0.7, ramp(trim), spec=0.6)
        if mount == "lyre":
            for b in (-2, 0, 2):
                c.line([P(-6, b), P(6, b)], mix(trim, WHITE, 0.4))
        return
    if mount in ("fork", "trident"):
        c.capsule(P(-R, -6), P(-R, 6), 1.2, ramp(trim), spec=0.7)
        for b in (-6, 0, 6):
            ln = 15 if b == 0 else 11
            c.capsule(P(-R, b), P(-R + ln, b * 0.85), 1.1, ramp(trim), spec=0.7, r1=0.35)
        return
    if mount == "curl":
        pts = []
        for i in range(36):
            a = i * 0.2
            r = 9 - i * 0.2
            pts.append(P(math.sin(a) * r, math.cos(a) * r - 1))
        for p, q in zip(pts, pts[1:]):
            c.capsule(p, q, 1.2, ramp(trim), spec=0.5)
        return
    if mount == "wings":
        for side in (-1, 1):
            for k in range(4):
                base = P(-2 + k * 1.2, side * 3)
                tip = P(4 + k * 2.5, side * (12 - k * 1.5))
                c.capsule(base, tip, 1.6 - k * 0.25, ramp(hexc(0xf4f0e8)), spec=0.3, r1=0.5)
        return
    if mount == "antler":
        for side in (-1, 1):
            c.capsule(P(-5, side * 1.5), P(6, side * 9), 1.1, ramp(trim), spec=0.4, r1=0.6)
            c.capsule(P(0, side * 4.5), P(7, side * 3.5), 0.9, ramp(trim), spec=0.4, r1=0.4)
            c.capsule(P(3, side * 7), P(9, side * 7.5), 0.8, ramp(trim), spec=0.4, r1=0.3)
        return
    if mount == "serpent":
        pts = [P(-14 + i * 1.2, math.sin(i * 0.7) * 4) for i in range(20)]
        for p, q in zip(pts, pts[1:]):
            c.capsule(p, q, 1.3, ramp(hexc(0x3fae5a)), spec=0.4)
        return
    if mount in ("lantern", "hourglass", "chain"):
        c.capsule(P(-R, -5), P(-R, 5), 1.0, ramp(trim), spec=0.6)
        c.capsule(P(R, -5), P(R, 5), 1.0, ramp(trim), spec=0.6)
        if mount != "chain":
            for b in (-5, 5):
                c.capsule(P(-R, b), P(R, b * (0.4 if mount == "hourglass" else 1)), 0.8, ramp(trim), spec=0.6)
        return
    if mount in ("lotus", "coralCrown"):
        for k in range(5):
            b = (k - 2) * 3.2
            c.poly([P(-R + 1, b * 0.5), P(1, b * 1.25 - 1.6), P(4, b * 1.1), P(1, b * 1.25 + 1.6)],
                   ramp(hexc(0xf0a8c8) if mount == "lotus" else hexc(0xe77868)))
        return
    # 未対応の台座は小さな襟で代用
    c.capsule(P(-R + 1, -4), P(-R + 1, 4), 1.2, ramp(trim), spec=0.7)


def _draw_core(c, core, center, size, core_col, energy, ph, u, v):
    cx, cy = center
    r = 5.5 * size

    def P(a, b):
        return (cx + u[0] * a + v[0] * b, cy + u[1] * a + v[1] * b)
    if core in ("none", None):
        return
    if core in ("crystal", "prism", "diamond", "ruby", "sapphire", "emeraldGem", "cluster"):
        col = {"ruby": hexc(0xe0314f), "sapphire": hexc(0x2f6fe4), "emeraldGem": hexc(0x3ad46a)}.get(core, core_col)
        ln = r * (1.6 if core in ("crystal", "cluster") else 1.15)
        wd = r * (0.75 if core == "crystal" else 0.9)
        cen = P(1.0, 0)
        pts = [P(ln, 0), P(ln * 0.25, wd), P(-ln * 0.75, 0), P(ln * 0.25, -wd)]

        def nf(x, y):
            a, b = (x - cen[0]) * u[0] + (y - cen[1]) * u[1], (x - cen[0]) * v[0] + (y - cen[1]) * v[1]
            return (-0.55 if b < 0 else 0.45, -0.5 if a > ln * 0.25 else 0.3, 0.7)
        pid = c.poly(pts, ramp(col, spread=0.85), normal_fn=nf, spec=0.8)
        for y in range(c.h):
            for x in range(c.w):
                if c.part[y][x] == pid:
                    c.add_glow(x, y, 0.7)
        if core == "cluster":
            for b in (-1, 1):
                q = [P(ln * 0.5, b * wd * 1.5), P(-ln * 0.1, b * wd * 1.9), P(-ln * 0.5, b * wd * 1.2), P(0, b * wd * 0.9)]
                c.poly(q, ramp(col, spread=0.85), spec=0.6)
        c.dot(*P(ln * 0.35, -wd * 0.3), WHITE, glow=1.0)
        return
    if core in ("orb", "pearl", "portal", "heart", "sun"):
        if core == "heart":
            c.sphere(P(1.5, -2), r * 0.6, ramp(core_col, spread=0.85), spec=0.7, glow=0.6)
            c.sphere(P(1.5, 2), r * 0.6, ramp(core_col, spread=0.85), spec=0.7, glow=0.6)
            c.poly([P(1.5, -4.5), P(1.5, 4.5), P(-4.5, 0)], ramp(core_col, spread=0.85))
            return
        col = hexc(0xf4ece4) if core == "pearl" else core_col
        c.sphere(P(0, 0), r * (0.8 if core == "pearl" else 1), ramp(col, spread=0.85), spec=0.9, glow=0.8)
        if core == "portal":
            c.sphere(P(0, 0), r * 0.55, ramp(hexc(0x140820)), spec=0.0)
            for k in range(3):
                a = ph + k * 2.1
                c.dot(*P(math.cos(a) * r * 0.35, math.sin(a) * r * 0.35), energy, glow=1.0)
        if core == "sun":
            for k in range(8):
                a = k * math.pi / 4 + ph * 0.25
                c.line([P(math.cos(a) * r * 1.1, math.sin(a) * r * 1.1), P(math.cos(a) * r * 1.6, math.sin(a) * r * 1.6)], energy, glow=0.9)
        return
    if core == "star":
        c.star(P(0, 0), r * 1.3, r * 0.5, core_col, WHITE, points=5, rot=ph * 0.1, glow=1.0)
        return
    if core in ("skull", "totem", "anvil", "serpenthead"):
        col = {"skull": hexc(0xe8e0cc), "totem": hexc(0xc98a3a), "anvil": hexc(0x5a5a66), "serpenthead": hexc(0x3fae5a)}[core]
        c.sphere(P(0.5, 0), r * 0.95, ramp(col, spread=0.6), spec=0.3)
        if core in ("skull", "serpenthead", "totem"):
            for b in (-2.0, 2.0):
                c.sphere(P(0.5, b), max(1.1, r * 0.24), ramp(hexc(0x1a1020)), spec=0)
                c.dot(*P(0.5, b), energy, glow=1.0)
        return
    if core == "eye":
        c.sphere(P(0, 0), r, ramp(hexc(0xf0ece8), spread=0.5), spec=0.6)
        c.sphere(P(0.6 * math.cos(ph), 0.8 * math.sin(ph)), r * 0.55, ramp(core_col, spread=0.85), spec=0.9, glow=0.9)
        c.dot(*P(0.6 * math.cos(ph), 0.8 * math.sin(ph)), hexc(0x0a0612))
        return
    if core in ("flame", "lantern", "lanternCore"):
        flick = 1 + 0.12 * math.sin(ph * 2)
        pts = [P(r * 1.6 * flick, 0.4 * math.sin(ph)), P(r * 0.2, r * 0.8), P(-r * 0.7, r * 0.5), P(-r * 0.9, 0),
               P(-r * 0.7, -r * 0.5), P(r * 0.2, -r * 0.8)]
        pid = c.poly(pts, ramp(core_col, spread=0.9))
        inner = [P(r * 0.8 * flick, 0), P(-r * 0.1, r * 0.4), P(-r * 0.5, 0), P(-r * 0.1, -r * 0.4)]
        c.poly(inner, [mix(energy, WHITE, 0.5)] * 5)
        for y in range(c.h):
            for x in range(c.w):
                if c.part[y][x] == pid:
                    c.add_glow(x, y, 1.0)
        return
    if core in ("moon",):
        c.crescent(P(0, 0), r * 1.1, (v[0] * 3 + u[0] * 2, v[1] * 3 + u[1] * 2), r * 0.95, ramp(core_col, spread=0.8))
        return
    if core == "hourglass":
        c.poly([P(r, -r * 0.8), P(r, r * 0.8), P(-r, -r * 0.8), P(-r, r * 0.8)], ramp(mix(core_col, WHITE, 0.3)))
        return
    if core in ("blade", "shortBlade", "holyBlade", "cursedBlade", "prismBlade"):
        col = {"holyBlade": hexc(0xfff4c0), "cursedBlade": hexc(0x5a2a6a), "prismBlade": hexc(0x9fb7ff)}.get(core, hexc(0xd0d8e8))
        ln = r * (2.4 if core != "shortBlade" else 1.5)
        c.poly([P(ln, 0), P(-1, r * 0.6), P(-1, -r * 0.6)], ramp(col, spread=0.8), spec=0.7)
        c.line([P(-1, 0), P(ln * 0.85, 0)], mix(col, WHITE, 0.6), glow=0.5)
        return
    c.sphere(P(0, 0), r, ramp(core_col, spread=0.85), spec=0.9, glow=0.8)


def _draw_pommel(c, pommel, p, u, v, trim, core_col, energy):
    def P(a, b):
        return (p[0] + u[0] * a + v[0] * b, p[1] + u[1] * a + v[1] * b)
    if pommel in ("none", None):
        return
    if pommel in ("cap", "knot", "roots"):
        c.sphere(P(0, 0), 2.4, ramp(trim, spread=0.7), spec=0.7)
        if pommel == "roots":
            for b in (-1, 0, 1):
                c.capsule(P(0, b), P(-4, b * 3), 0.8, ramp(hexc(0x6a4a2a)), spec=0.2, r1=0.3)
        return
    if pommel in ("gem", "orb", "crystal", "pearl"):
        c.sphere(P(0, 0), 2.6 if pommel != "crystal" else 2.2, ramp(core_col if pommel != "pearl" else hexc(0xf4ece4), spread=0.85), spec=0.9, glow=0.6)
        return
    if pommel in ("spike", "blade", "hook"):
        c.poly([P(1, -1.8), P(1, 1.8), P(-6 if pommel != "hook" else -4, 0)], ramp(trim, spread=0.7), spec=0.6)
        return
    if pommel == "skull":
        c.sphere(P(-0.5, 0), 2.8, ramp(hexc(0xe8e0cc), spread=0.6), spec=0.3)
        c.dot(*P(-0.5, -1), hexc(0x1a1020))
        c.dot(*P(-0.5, 1), hexc(0x1a1020))
        return
    if pommel in ("lantern", "chain"):
        c.capsule(P(0, 0), P(-4, 0), 0.6, ramp(trim), spec=0.5)
        c.sphere(P(-5, 0), 1.8, ramp(energy, spread=0.85), spec=0.7, glow=0.8)
        return
    c.sphere(P(0, 0), 2.2, ramp(trim, spread=0.7), spec=0.7)


_FLOATER_SHAPES = {
    "stars": "star", "sparkCross": "star", "glints": "glint", "motes": "dot", "dust": "dot", "wisps": "wisp",
    "orbs": "orb", "bubbles": "orb", "pearls": "orb", "eyes": "eye", "runes": "rune", "sigils": "rune",
    "petals": "petal", "leaves": "leaf", "butterflies": "petal", "moons": "moon", "shards": "shard",
    "cubes": "cube", "daggers": "shard", "bells": "orb", "candles": "candle", "coins": "orb", "keys": "shard",
}


def _floater_positions(path: str, n: int, ph: float, head, R: float):
    hx, hy = head
    out = []
    for i in range(n):
        a = ph + i * 2 * math.pi / n
        if path in ("orbit", "crown"):
            out.append((hx + math.cos(a) * R, hy + math.sin(a) * R * (0.45 if path == "orbit" else 0.3) - (4 if path == "crown" else 0)))
        elif path in ("spiral", "helix"):
            k = (i / n + ph / (2 * math.pi)) % 1
            out.append((hx + math.cos(a * 2) * R * (0.5 + k * 0.6), hy + 8 - k * 18))
        elif path == "figure8":
            out.append((hx + math.sin(a) * R, hy + math.sin(2 * a) * R * 0.4))
        elif path == "pendulum":
            out.append((hx + math.sin(ph + i) * R * 0.6 + (i - n / 2) * 3, hy + 9 + math.cos(ph + i) * 2))
        elif path == "rain":
            k = (i / n + ph / (2 * math.pi)) % 1
            out.append((hx - R + i * 2 * R / max(1, n - 1), hy - 10 + k * 22))
        else:  # constellation
            out.append((hx + math.cos(i * 2.4) * R * 0.9, hy + math.sin(i * 2.4) * R * 0.7 + math.sin(ph + i) * 0.8))
    return out


def _draw_floaters(c, kind, path, n, ph, head, energy, core_col):
    if kind in ("none", None) or n <= 0:
        return
    shape = _FLOATER_SHAPES.get(kind, "dot")
    pts = _floater_positions(path, n, ph, head, 13)
    if path == "constellation":
        for p, q in zip(pts, pts[1:]):
            c.line([p, q], mix(energy, (0, 0, 0, 255), 0.35)[:3] + (160,))
    for i, (x, y) in enumerate(pts):
        if not (1 <= x < c.w - 1 and 1 <= y < c.h - 1):
            continue
        tw = 0.5 + 0.5 * math.sin(ph * 2 + i * 1.7)
        if shape == "star":
            c.star((x, y), 2.6, 1.0, energy, WHITE, points=4, glow=0.8)
        elif shape == "glint":
            if tw > 0.35:
                c.sparkle(x, y, mix(energy, WHITE, 0.5), size=1 if tw < 0.8 else 2)
        elif shape == "orb":
            c.sphere((x, y), 1.6, ramp(core_col, spread=0.85), spec=0.9, glow=0.6)
        elif shape == "eye":
            c.sphere((x, y), 1.8, ramp(hexc(0xf0ece8), spread=0.5), spec=0.4)
            c.dot(x, y, core_col, glow=0.8)
        elif shape == "rune":
            for dx, dy in ((0, -1), (0, 0), (0, 1), (-1, 0), (1, 1)):
                c.dot(x + dx, y + dy, energy, glow=0.9)
        elif shape in ("petal", "leaf"):
            col = hexc(0xf4a8c8) if shape == "petal" else hexc(0x6fc84a)
            c.poly([(x - 1.8, y), (x, y - 1.2), (x + 1.8, y), (x, y + 1.2)], ramp(col, spread=0.6))
        elif shape == "moon":
            c.crescent((x, y), 2.2, (1.0, -0.6), 1.9, ramp(mix(energy, WHITE, 0.4)))
        elif shape in ("shard", "cube"):
            c.poly([(x, y - 2.2), (x + 1.4, y), (x, y + 2.2), (x - 1.4, y)], ramp(core_col, spread=0.85), spec=0.7)
        elif shape == "candle":
            c.capsule((x, y + 1), (x, y + 3), 0.9, ramp(hexc(0xf0e8d8)))
            c.dot(x, y - 0.5, mix(energy, WHITE, 0.4), glow=1.0)
        elif shape == "wisp":
            c.dot(x, y, mix(energy, WHITE, 0.3), glow=1.0)
            c.dot(x, y + 1, energy, glow=0.6)
        else:
            c.dot(x, y, mix(energy, WHITE, 0.4), glow=0.8)


def _particles(c, rnd_seed, ph, head, energy):
    rnd = random.Random(rnd_seed)
    hx, hy = head
    for i in range(6):
        sx, sy = hx + rnd.uniform(-14, 14), hy + rnd.uniform(-6, 14)
        k = (ph / (2 * math.pi) + rnd.random()) % 1
        y = sy - k * 14
        if 0 <= y < c.h and 0 <= sx < c.w:
            col = mix(energy, WHITE, 0.3 + 0.4 * (1 - k))
            c.dot(sx + math.sin(k * 6 + i) * 1.2, y, col, glow=0.8 * (1 - k))


# ---------------------------------------------------------------- 本体

def render(cfg: dict, frame: int = 0, size: int = 64) -> Image.Image:
    """1 コマ描く (64px。size を変えると縮小・拡大)。"""
    ph = 2 * math.pi * frame / FRAMES
    seed = zlib.crc32(str(cfg["seed"]).encode()) % 100003
    rnd = random.Random(seed)
    base, metal, glow_mat = _mat(cfg["shaftMaterial"])
    trim, _tm, _tg = _mat(cfg["trimMaterial"])
    core_col = _c(cfg["coreColor"])
    energy = _c(cfg["energyColor"])
    c = hd.Canvas(64, 64)

    pts = _shaft_points(cfg["shape"], 0.92 * float(cfg["length"]), rnd)
    thick = {1: 1.1, 2: 1.5, 3: 1.9, 4: 2.3}.get(int(cfg["thickness"]), 1.5)
    tube = Tube(pts, thick, thick * (0.7 if cfg["taper"] else 1.0))
    head_t = 0.97 if cfg["shape"] != "hooked" else 0.7
    (tx, ty), (ux, uy) = tube.at(1.0 if cfg["shape"] != "hooked" else head_t)
    u = (0.7071, -0.7071)
    v = (0.7071, 0.7071)
    head = (tx + u[0] * 7.5, ty + u[1] * 7.5)
    if cfg["shape"] == "hooked":
        head = (pts[-1][0] + 1.5, pts[-1][1] + 7)

    # 奥の台座 → 柄 → 帯・握り → 柄頭 → 核 → 手前の飾り → 浮遊物・粒子
    if cfg["mount"] in ("wings", "antler", "crescent", "ring", "astrolabe", "lyre", "arch", "curl", "scythe"):
        _draw_mount(c, cfg["mount"], head, u, v, trim, energy, ph)
    _draw_shaft(c, tube, base, metal, glow_mat, cfg["pattern"], trim, energy, seed)
    for i in range(int(cfg.get("bands", 0))):
        t = 0.35 + 0.5 * (i + 0.5) / max(1, int(cfg["bands"]))
        (bx, by), _ = tube.at(t)
        nx, ny = tube.normal(t)
        r = thick + 0.7
        c.capsule((bx - nx * r, by - ny * r), (bx + nx * r, by + ny * r), 0.9, ramp(trim), spec=0.7)
    if cfg.get("grip"):
        gcol = {"wrap": hexc(0x7a2a2a), "leather": hexc(0x5a3a22), "chain": hexc(0x9aa0aa), "studded": hexc(0x4a3626),
                "cord": hexc(0xc8b080), "woven": hexc(0x2a6a6a)}.get(cfg.get("gripStyle"), hexc(0x5a3a22))
        for i in range(6):
            t = 0.08 + i * 0.035
            (gx, gy), _ = tube.at(t)
            nx, ny = tube.normal(t)
            r = thick + 0.5
            c.capsule((gx - nx * r, gy - ny * r), (gx + nx * r, gy + ny * r), 0.95, ramp(gcol, spread=0.6),
                      spec=0.6 if cfg.get("gripStyle") == "chain" else 0.1)
    (px0, py0), _ = tube.at(0.0)
    _draw_pommel(c, cfg["pommel"], (px0 - u[0] * 1.5, py0 - u[1] * 1.5), u, v, trim, core_col, energy)
    core_center = head
    if cfg.get("floatingCore"):
        core_center = (head[0] + u[0] * (1.2 + math.sin(ph)), head[1] + u[1] * (1.2 + math.sin(ph)))
    if cfg["mount"] in ("cage", "lantern", "hourglass"):
        _draw_core(c, cfg["core"], core_center, float(cfg["coreSize"]), core_col, energy, ph, u, v)
        _draw_mount(c, cfg["mount"], head, u, v, trim, energy, ph)
    else:
        if cfg["mount"] not in ("wings", "antler", "crescent", "ring", "astrolabe", "lyre", "arch", "curl", "scythe"):
            _draw_mount(c, cfg["mount"], head, u, v, trim, energy, ph)
        _draw_core(c, cfg["core"], core_center, float(cfg["coreSize"]), core_col, energy, ph, u, v)
    _draw_floaters(c, cfg.get("floater"), cfg.get("floaterPath", "orbit"), min(6, int(cfg.get("floaterCount", 3))),
                   ph, head, energy, core_col)
    if cfg.get("particles"):
        _particles(c, seed, ph, head, energy)
    img = c.render(glow_color=energy, glow_radius=2)
    if size != 64:
        img = img.resize((size, size), Image.NEAREST if size > 64 else Image.LANCZOS)
    return img


def frames(cfg: dict, n: int = FRAMES) -> list[Image.Image]:
    return [render(cfg, f) for f in range(n)]
