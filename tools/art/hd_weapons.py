"""武器のインベントリ用アイコン (64x64・hd.py の陰影レンダラ)。

元型 (剣・刀・短剣・斧・槍・杖・槌・弓・鎌・鞭・輪刃・魔導書・拳・角笛・銃・道具) ごとの描画関数に、
武器ごとの形・材質・装飾を指定して描く。すべて左下 (柄) → 右上 (先端) の対角配置。
叙事 (EPIC) 級はアニメーション (ANIMATED) で、光の筋が流れる / 炎が揺れる。

    icon("abyss_katana", frame=0) -> 64x64 Image
"""
from __future__ import annotations

import math

from PIL import Image

from . import hd
from .hd import hexc, mix, ramp

WHITE = (255, 255, 255, 255)
SQ = 1 / math.sqrt(2)
U = (SQ, -SQ)   # 軸方向 (右上へ)
V = (SQ, SQ)    # 軸に直交 (右下へ)

# 材質 (中間色)
STEEL = hexc(0xc4ccd8)
DARK_STEEL = hexc(0x5c6478)
GOLD = hexc(0xe8be54)
BRONZE = hexc(0xb07a3a)
WOOD = hexc(0x7a4e2e)
DARK_WOOD = hexc(0x4a2f1e)
LEATHER = hexc(0x46344a)
BONE = hexc(0xe6dcc2)
VOID = hexc(0x2e2450)
ICE = hexc(0xbfefff)

ANIMATED = {"abyss_katana", "abyss_frost_katana", "abyss_tome", "abyss_chaos_tome",
            "abyss_flame_grimoire", "abyss_rift_blade"}
FRAMES = 8


class Ax:
    """対角の軸。at(t, off) は t (0=柄尻, 1=先端) と直交方向のずれ off の点。"""

    def __init__(self, p0, p1):
        self.p0, self.p1 = p0, p1
        self.L = math.dist(p0, p1)
        self.u = ((p1[0] - p0[0]) / self.L, (p1[1] - p0[1]) / self.L)
        self.v = (-self.u[1], self.u[0])
        if self.v[0] + self.v[1] < 0:  # v は右下向きに揃える
            self.v = (-self.v[0], -self.v[1])

    def at(self, t: float, off: float = 0.0):
        return (self.p0[0] + (self.p1[0] - self.p0[0]) * t + self.v[0] * off,
                self.p0[1] + (self.p1[1] - self.p0[1]) * t + self.v[1] * off)

    def proj(self, x: float, y: float):
        """点 → (t, off)"""
        dx, dy = x - self.p0[0], y - self.p0[1]
        return ((dx * self.u[0] + dy * self.u[1]) / self.L, dx * self.v[0] + dy * self.v[1])


# ================================================================ 共通部品

def curved(ax: "Ax", k: float = 0.5, tilt=(-0.15, -0.2)):
    """軸に沿った曲面の法線関数 (軸から離れるほど外へ傾く)。"""
    def fn(x, y):
        t, off = ax.proj(x, y)
        s = max(-1.0, min(1.0, off * k / 10))
        nx = ax.v[0] * s + tilt[0]
        ny = ax.v[1] * s + tilt[1]
        nz = math.sqrt(max(0.05, 1 - nx * nx - ny * ny))
        return (nx, ny, nz)
    return fn


def blade(c: hd.Canvas, ax: Ax, t0: float, t1: float, width, metal, *, curve=None, edge_hi=True,
          fuller: float = 0.0, fuller_col=None, spec=0.45) -> int:
    """刃。width(t) は半幅、curve(t) は中心線のずれ (反り)。稜線の左右で面の向きを変える。"""
    curve = curve or (lambda t: 0.0)
    n = 28
    ts = [t0 + (t1 - t0) * i / n for i in range(n + 1)]
    left = [ax.at(t, curve(t) - width(t)) for t in ts]
    right = [ax.at(t, curve(t) + width(t)) for t in reversed(ts)]
    rmp = ramp(metal, spread=0.8)

    def normal(x, y):
        t, off = ax.proj(x, y)
        s = off - curve(t)
        if s < 0:
            return (-ax.v[0] * 0.55 - ax.u[0] * 0.1, -ax.v[1] * 0.55 - ax.u[1] * 0.1, 0.83)
        return (ax.v[0] * 0.6, ax.v[1] * 0.6, 0.8)

    pid = c.poly(left + right, rmp, normal_fn=normal, spec=spec)
    if fuller > 0:
        col = fuller_col or hd._hsv_shift(metal, -0.3, 0.7, 0.05, 0.1)
        pts = [ax.at(t, curve(t)) for t in ts if t <= t0 + (t1 - t0) * fuller]
        c.line(pts, col)
    if edge_hi:
        pts = [ax.at(t, curve(t) - width(t) * 0.78) for t in ts[1:-3]]
        c.line(pts, mix(metal, WHITE, 0.72))
    return pid


def grip(c: hd.Canvas, ax: Ax, t0: float, t1: float, r: float, mat, wraps: int = 4, wrap_col=None) -> None:
    c.capsule(ax.at(t0), ax.at(t1), r, ramp(mat), spec=0.2)
    wc = wrap_col or hd._hsv_shift(mat, -0.25, 0.7, 0.04, 0.05)
    for i in range(wraps):
        t = t0 + (t1 - t0) * (i + 0.5) / wraps
        x, y = ax.at(t)
        c.line([(x - ax.v[0] * r, y - ax.v[1] * r), (x + ax.v[0] * r, y + ax.v[1] * r)], wc)


def crossguard(c: hd.Canvas, ax: Ax, t: float, span: float, r: float, mat, curl: float = 0.0) -> None:
    """鍔 (軸に直交)。curl > 0 で両端が先端側へ反る。"""
    rmp = ramp(mat, spread=0.7)
    x, y = ax.at(t)
    a = (x - ax.v[0] * span, y - ax.v[1] * span)
    b = (x + ax.v[0] * span, y + ax.v[1] * span)
    c.capsule(a, b, r, rmp, spec=0.6)
    if curl:
        for p in (a, b):
            c.capsule(p, (p[0] + ax.u[0] * curl, p[1] + ax.u[1] * curl), r * 0.8, rmp, spec=0.6)


def gem(c: hd.Canvas, pos, r: float, color, glow: float = 0.6) -> None:
    c.sphere(pos, r, ramp(color, spread=0.85), spec=0.9, glow=glow)


def pommel(c: hd.Canvas, ax: Ax, t: float, r: float, mat) -> None:
    c.sphere(ax.at(t), r, ramp(mat, spread=0.7), spec=0.7)


def sparkles(c: hd.Canvas, pts, color, ph: float) -> None:
    for i, (x, y) in enumerate(pts):
        if math.sin(ph + i * 2.1) > 0.2:
            c.sparkle(x, y, color, size=1)


def flow_glow(c: hd.Canvas, ax: Ax, t0: float, t1: float, off, color, ph: float) -> None:
    """軸に沿って流れる光の筋 (アニメーション用)。"""
    n = 30
    for i in range(n):
        t = t0 + (t1 - t0) * i / n
        if math.sin(t * 22 - ph * 2) > 0.55:
            x, y = ax.at(t, off(t))
            c.dot(x, y, mix(color, WHITE, 0.6), glow=0.9)


# ================================================================ 剣類

def sword_like(accent, *, length=1.0, w=3.4, guard=GOLD, metal=STEEL, grip_col=LEATHER, runes=False,
               serrated=False, gem_col=None, curl=0.0, ph=0.0, anim=False, wide_guard=1.0, glow_col=None):
    c = hd.Canvas()
    ax = Ax((7, 57), (7 + 50 * length, 57 - 50 * length))

    def width(t):
        if t < 0.30:
            return 0
        k = (t - 0.30) / 0.70
        base = w if k < 0.72 else w * max(0.0, 1 - (k - 0.72) / 0.28) ** 0.85
        if serrated and 0.1 < k < 0.8 and int(k * 22) % 2 == 0:
            base += 1.1
        return base

    blade(c, ax, 0.30, 1.0, width, metal, fuller=0.6)
    if runes:
        for i in range(5):
            t = 0.36 + i * 0.09
            x, y = ax.at(t)
            lit = (not anim) or math.sin(ph - i) > -0.2
            c.dot(x, y, accent if lit else hd._hsv_shift(accent, -0.4, 0.6, 0, 0), glow=0.7 if lit else 0.2)
            c.dot(x + 1, y, accent, glow=0.4)
    if anim:
        flow_glow(c, ax, 0.32, 0.95, lambda t: -width(t) * 0.5, glow_col or accent, ph)
    crossguard(c, ax, 0.29, 7.0 * wide_guard, 1.6, guard, curl=curl)
    gem(c, ax.at(0.29), 2.0, gem_col or accent, glow=0.6)
    grip(c, ax, 0.12, 0.27, 1.6, grip_col)
    pommel(c, ax, 0.09, 2.4, guard)
    return c, ax


def sword(accent, ph=0.0, anim=False):
    return sword_like(accent, gem_col=hexc(0x5c8cff))[0].render()


def greatsword(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    ax = Ax((5, 59), (60, 4))

    def width(t):
        if t < 0.28:
            return 0
        k = (t - 0.28) / 0.72
        return 5.2 if k < 0.8 else 5.2 * max(0.0, 1 - (k - 0.8) / 0.2) ** 0.7

    blade(c, ax, 0.28, 1.0, width, STEEL, fuller=0.7)
    # 刃元の装甲板
    c.poly([ax.at(0.28, -5.6), ax.at(0.37, -5.6), ax.at(0.37, 5.6), ax.at(0.28, 5.6)],
           ramp(DARK_STEEL), normal_fn=lambda x, y: (-0.2, -0.4, 0.9), spec=0.5)
    gem(c, ax.at(0.325), 1.8, accent, glow=0.5)
    crossguard(c, ax, 0.27, 9.0, 2.0, DARK_STEEL, curl=3.0)
    grip(c, ax, 0.08, 0.25, 1.8, LEATHER, wraps=5)
    pommel(c, ax, 0.05, 2.8, DARK_STEEL)
    return c.render()


def rune_blade(accent, ph=0.0, anim=False):
    return sword_like(accent, runes=True, guard=hexc(0x8aa8c8), metal=hexc(0xb8d0e8), curl=2.0,
                      glow_col=accent)[0].render(glow_color=accent, glow_radius=1)


def rift_blade(accent, ph=0.0, anim=False):
    c, ax = sword_like(accent, metal=hexc(0x3a2c66), guard=hexc(0x5a4890), grip_col=hexc(0x221a3a),
                       serrated=True, runes=True, ph=ph, anim=anim, curl=2.5, gem_col=accent)
    # 裂け目の光
    sparkles(c, [ax.at(0.55, 7), ax.at(0.8, -6), ax.at(0.95, 4)], accent, ph)
    return c.render(glow_color=accent, glow_radius=2)


def wind_blade(accent, ph=0.0, anim=False):
    """風輪双刃: 細身の刃 2 本を少しずらして重ねる。"""
    c = hd.Canvas()
    for k, (p0, p1) in enumerate((((5, 53), (52, 6)), ((11, 59), (58, 12)))):
        ax = Ax(p0, p1)

        def width(t):
            return 0 if t < 0.32 else (2.2 if t < 0.85 else 2.2 * max(0.0, 1 - (t - 0.85) / 0.15))

        blade(c, ax, 0.32, 1.0, width, hexc(0xd8f0ea), curve=lambda t: 1.6 * math.sin(math.pi * t))
        crossguard(c, ax, 0.31, 4.0, 1.2, hexc(0x5aa898))
        grip(c, ax, 0.12, 0.29, 1.3, hexc(0x2e5a54), wraps=3)
    for i in range(3):  # 風の筋
        a = 0.3 + i * 0.22
        c.line([(14 + i * 9, 30 - i * 3), (22 + i * 9, 26 - i * 3), (28 + i * 9, 27 - i * 3)], accent, glow=0.4)
    return c.render(glow_color=accent, glow_radius=1)


def katana(accent, ph=0.0, anim=False, metal=STEEL, ice=False):
    c = hd.Canvas()
    ax = Ax((8, 58), (59, 5))

    def curve(t):
        return -3.2 * math.sin(math.pi * min(1.0, t * 0.95))  # 反り (刃側=右下が凸)

    def width(t):
        if t < 0.30:
            return 0
        k = (t - 0.30) / 0.70
        return 1.9 if k < 0.88 else 1.9 * max(0.0, 1 - (k - 0.88) / 0.12)

    blade(c, ax, 0.30, 1.0, width, metal, curve=curve, fuller=0.0)
    # 刃文 (刃側の波線)
    pts = [ax.at(t, curve(t) + width(t) * (0.35 + 0.25 * math.sin(t * 40))) for t in
           [0.32 + i * 0.02 for i in range(30)]]
    c.line(pts, mix(accent if ice else WHITE, WHITE, 0.5))
    if anim:
        flow_glow(c, ax, 0.32, 0.96, lambda t: curve(t), accent, ph)
    # 鍔 (円) とはばき
    x, y = ax.at(0.29, curve(0.29))
    c.sphere((x, y), 3.4, ramp(hexc(0x2a2a34)), spec=0.5)
    c.sphere((x, y), 1.4, ramp(GOLD), spec=0.8)
    grip(c, ax, 0.08, 0.27, 1.7, accent if not ice else hexc(0x3a6a8a), wraps=5, wrap_col=hexc(0x1a1420))
    pommel(c, ax, 0.065, 1.8, GOLD)
    if ice:
        sparkles(c, [ax.at(0.6, -6), ax.at(0.85, 5), ax.at(0.45, 6)], accent, ph)
        return c.render(glow_color=accent, glow_radius=2)
    return c.render(glow_color=accent if anim else None, glow_radius=1)


def echo_katana(accent, ph=0.0, anim=False):
    """残刀: 刀 + 残像 (半透明の二重)。"""
    base = katana(accent)
    ghost = base.copy()
    gp = ghost.load()
    for y in range(64):
        for x in range(64):
            r, g, b, a = gp[x, y]
            gp[x, y] = (int(r * 0.6 + accent[0] * 0.4), int(g * 0.6 + accent[1] * 0.4),
                        int(b * 0.6 + accent[2] * 0.4), a // 3)
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    out.alpha_composite(ghost, (3, 3))
    out.alpha_composite(base, (0, 0))
    return out


def frost_katana(accent, ph=0.0, anim=False):
    return katana(accent, ph, anim, metal=ICE, ice=True)


def dagger(accent, ph=0.0, anim=False, metal=STEEL, curve_amt=0.0, guard=GOLD, offset=(0, 0), small=1.0):
    c = hd.Canvas()
    ox, oy = offset
    ax = Ax((14 + ox, 50 + oy), (14 + ox + 34 * small, 50 + oy - 34 * small))

    def width(t):
        if t < 0.40:
            return 0
        k = (t - 0.40) / 0.60
        return 3.0 * (1 - k) ** 0.7 + 0.3

    blade(c, ax, 0.40, 1.0, width, metal, curve=lambda t: curve_amt * math.sin(math.pi * t), fuller=0.5)
    crossguard(c, ax, 0.39, 4.5, 1.3, guard, curl=1.5)
    gem(c, ax.at(0.39), 1.4, accent, glow=0.4)
    grip(c, ax, 0.15, 0.37, 1.4, LEATHER, wraps=3)
    pommel(c, ax, 0.12, 1.9, guard)
    return c


def dagger_icon(accent, ph=0.0, anim=False):
    return dagger(accent).render()


def fang_dagger(accent, ph=0.0, anim=False):
    return dagger(accent, metal=BONE, curve_amt=-3.0, guard=hexc(0x8a6a3a)).render()


def twin_dagger(accent, ph=0.0, anim=False):
    a = dagger(accent, offset=(-6, 2), small=0.95).render()
    b = dagger(accent, offset=(6, -2), small=0.95).render()
    b = b.transpose(Image.FLIP_LEFT_RIGHT)
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    out.alpha_composite(b)
    out.alpha_composite(a)
    return out


# ================================================================ 斧・道具

def axe(accent, ph=0.0, anim=False, double=False, head=STEEL, edge=None, haft=WOOD):
    c = hd.Canvas()
    ax = Ax((9, 58), (50, 12))
    c.capsule(ax.at(0.0), ax.at(1.0), 2.0, ramp(haft), spec=0.25)
    grip(c, ax, 0.05, 0.28, 2.3, LEATHER, wraps=4)
    sides = (-1, 1) if double else (1,)
    hm = ramp(hd._hsv_shift(head, -0.12, 0.7, 0.03, 0.05), spread=0.85)
    for s_ in sides:
        p = [ax.at(0.80, 0), ax.at(0.76, s_ * 6), ax.at(0.64, s_ * 13), ax.at(0.70, s_ * 17),
             ax.at(0.84, s_ * 18.5), ax.at(0.98, s_ * 17), ax.at(1.02, s_ * 13), ax.at(0.93, s_ * 6),
             ax.at(0.92, 0)]
        c.poly(p, hm, normal_fn=curved(ax, 0.55 * s_, (-0.1, -0.15)), spec=0.35)
        e = [ax.at(0.645 + 0.37 * i / 12, s_ * (13.3 + 4.6 * math.sin(math.pi * i / 12))) for i in range(13)]
        c.line(e, edge or mix(head, WHITE, 0.75))
        c.sphere(ax.at(0.84, s_ * 9), 2.2, ramp(hexc(0x141020)), spec=0.0)  # 刃の肉抜き
    c.capsule(ax.at(0.76), ax.at(1.0), 2.8, ramp(DARK_STEEL), spec=0.5)
    c.capsule(ax.at(1.0), ax.at(1.07), 1.6, ramp(DARK_STEEL), spec=0.5, r1=0.4)
    gem(c, ax.at(0.88), 1.9, accent, glow=0.5)
    return c.render(glow_color=accent if edge else None, glow_radius=1)

def blood_axe(accent, ph=0.0, anim=False):
    return axe(accent, double=True, head=hexc(0x6a2a34), edge=accent, haft=DARK_WOOD)


def tool_axe(accent, ph=0.0, anim=False):
    return axe(accent, head=hexc(0x9a90b8), haft=WOOD)


def pickaxe(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    ax = Ax((10, 57), (46, 16))
    c.capsule(ax.at(0), ax.at(1.0), 1.8, ramp(WOOD), spec=0.25)
    head = ramp(hexc(0x8a80a8), spread=0.8)
    a, b = ax.at(1.0, -20), ax.at(1.0, 20)
    pts = [ax.at(0.92, -2), ax.at(0.98, -12), a, ax.at(1.06, -10), ax.at(1.08, 0), ax.at(1.06, 10), b,
           ax.at(0.98, 12), ax.at(0.92, 2)]
    c.poly(pts, head, normal_fn=curved(ax, 0.5, (0.05, 0.02)), spec=0.4)
    gem(c, ax.at(1.0), 1.8, accent, glow=0.4)
    return c.render()


def shovel(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    ax = Ax((9, 58), (54, 10))
    c.capsule(ax.at(0), ax.at(0.72), 1.7, ramp(WOOD), spec=0.25)
    c.capsule(ax.at(0.02, -3), ax.at(0.02, 3), 1.4, ramp(WOOD))
    pts = [ax.at(0.70, -4), ax.at(0.95, -5.5), ax.at(1.02, 0), ax.at(0.95, 5.5), ax.at(0.70, 4)]
    c.poly(pts, ramp(hexc(0x8a80a8), spread=0.8), normal_fn=lambda x, y: (0.05 + (x - 46) * 0.03, 0.05 + (y - 16) * 0.03, 0.95), spec=0.4)
    gem(c, ax.at(0.72), 1.5, accent, glow=0.3)
    return c.render()


def hoe(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    ax = Ax((10, 58), (50, 14))
    c.capsule(ax.at(0), ax.at(1.0), 1.7, ramp(WOOD), spec=0.25)
    pts = [ax.at(0.94, 0), ax.at(1.0, -1), ax.at(0.98, -14), ax.at(0.88, -15), ax.at(0.88, -2)]
    c.poly(pts, ramp(hexc(0x8a80a8), spread=0.8), normal_fn=lambda x, y: (0.05 + (x - 40) * 0.02, 0.05 + (y - 20) * 0.02, 0.95), spec=0.4)
    gem(c, ax.at(0.97), 1.5, accent, glow=0.3)
    return c.render()


def shears(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    for s in (-1, 1):
        ax = Ax((22 + s * 6, 50), (48 - s * 4, 14))
        blade(c, ax, 0.45, 1.0, lambda t: 2.4 * (1 - (t - 0.45) / 0.55) + 0.4, hexc(0xb8b0d0))
        c.sphere(ax.at(0.25), 4.0, ramp(accent), spec=0.5)
        c.sphere(ax.at(0.25), 2.0, ramp(hexc(0x1a1626)), spec=0.0)
    c.sphere((32, 34), 1.6, ramp(GOLD), spec=0.8)
    return c.render()


# ================================================================ 長柄

def polearm(accent, ph=0.0, anim=False, head="leaf", haft=DARK_WOOD, metal=STEEL):
    c = hd.Canvas()
    ax = Ax((4, 60), (60, 4))
    c.capsule(ax.at(0.02), ax.at(0.70), 1.5, ramp(haft), spec=0.3)
    for t in (0.08, 0.36, 0.66):
        c.capsule(ax.at(t, -1.9), ax.at(t, 1.9), 1.0, ramp(GOLD), spec=0.6)
    if head == "leaf":
        blade(c, ax, 0.70, 1.0, lambda t: 3.6 * max(0.0, math.sin(math.pi * (t - 0.70) / 0.30)) ** 0.8 + 0.2, metal, fuller=0.7)
        c.capsule(ax.at(0.68, -2), ax.at(0.68, 2), 1.4, ramp(GOLD), spec=0.6)
        # 房飾り
        for k in range(3):
            x, y = ax.at(0.66, 1.5 + k)
            c.line([(x, y), (x + 2 + k, y + 5)], accent)
    elif head == "storm":
        blade(c, ax, 0.72, 1.0, lambda t: 2.4 * (1 - (t - 0.72) / 0.28) + 0.2, metal)
        for s in (-1, 1):  # 稲妻形の副刃
            p = [ax.at(0.72, s * 1), ax.at(0.80, s * 6), ax.at(0.82, s * 3.5), ax.at(0.90, s * 7.5),
                 ax.at(0.85, s * 2)]
            c.poly(p, ramp(accent, spread=0.8), normal_fn=lambda x, y: (-0.2, -0.3, 0.93), spec=0.5)
        c.line([ax.at(0.4, 3), ax.at(0.5, -2), ax.at(0.58, 3), ax.at(0.66, -1)], mix(accent, WHITE, 0.4), glow=0.7)
    elif head == "star":
        blade(c, ax, 0.72, 1.0, lambda t: 2.0 * (1 - (t - 0.72) / 0.28) + 0.3, hexc(0xf0e8d0))
        x, y = ax.at(0.74)
        c.star((x, y), 7.5, 2.2, accent, WHITE, points=4, rot=math.pi / 4, glow=1.0)
        c.star((x, y), 4.0, 1.4, mix(accent, WHITE, 0.5), WHITE, points=4, glow=1.0)
        sparkles(c, [ax.at(0.5, 7), ax.at(0.9, -7), ax.at(0.62, -6)], accent, 1.0)
    elif head == "meteor":
        blade(c, ax, 0.72, 1.0, lambda t: 2.6 * (1 - (t - 0.72) / 0.28) + 0.3, hexc(0x8a7a6a))
        x, y = ax.at(0.75)
        c.sphere((x, y), 5.0, ramp(hexc(0x6a4a3a), spread=0.8), spec=0.3)
        for k in range(6):  # 溶岩の割れ目
            a = k * 1.05
            c.dot(x + math.cos(a) * 2.5, y + math.sin(a) * 2.5, accent, glow=0.8)
        for k in range(5):  # 炎の尾 (柄側へ)
            t = 0.6 - k * 0.05
            c.dot(*ax.at(t, (k % 2 * 2 - 1) * 2), mix(accent, hexc(0xff5a1a), k / 5), glow=0.7)
    elif head == "trident":
        c.capsule(ax.at(0.70, -6), ax.at(0.70, 6), 1.3, ramp(GOLD), spec=0.6)
        for s in (-6, 0, 6):
            t1 = 1.0 if s == 0 else 0.94
            blade(c, ax, 0.70, t1, lambda t, t1=t1: 1.2 if t < t1 - 0.06 else 1.2 * (t1 - t) / 0.06 + 0.1,
                  hexc(0xd0e8f0), curve=lambda t, s=s: s, edge_hi=False)
            p = ax.at(t1 - 0.07, s)
            c.poly([(p[0] + ax.v[0] * -2.4, p[1] + ax.v[1] * -2.4), ax.at(t1 - 0.02, s + (1.5 if s <= 0 else -1.5)),
                    (p[0] + ax.v[0] * 2.4, p[1] + ax.v[1] * 2.4)], ramp(hexc(0xe8f6ff)),
                   normal_fn=lambda x, y: (-0.3, -0.3, 0.9), spec=0.6)
        gem(c, ax.at(0.70), 1.8, accent, glow=0.6)
        for k in range(4):  # 水しぶき
            c.dot(*ax.at(0.55 + k * 0.08, (-1) ** k * 5), mix(accent, WHITE, 0.4), glow=0.5)
    return c.render(glow_color=accent if head in ("star", "meteor", "storm") else None, glow_radius=1)


# ================================================================ 杖類

def staff(accent, ph=0.0, anim=False, orb="cage"):
    c = hd.Canvas()
    ax = Ax((6, 58), (50, 14))
    c.capsule(ax.at(0.02), ax.at(0.92), 1.6, ramp(DARK_WOOD), spec=0.3)
    for t in (0.1, 0.5, 0.88):
        c.capsule(ax.at(t, -2), ax.at(t, 2), 1.0, ramp(GOLD), spec=0.6)
    hx, hy = ax.at(1.08)
    if orb == "cage":
        gem(c, (hx, hy), 6.0, accent, glow=0.9)
        for a in (0.3, 1.9, 3.5, 5.0):  # 籠の爪
            p0 = ax.at(0.92)
            p1 = (hx + math.cos(a) * 7.0, hy + math.sin(a) * 7.0)
            c.capsule(p0, p1, 0.7, ramp(GOLD), spec=0.6)
    elif orb == "void":
        c.sphere((hx, hy), 6.5, ramp(VOID, spread=0.6), spec=0.4)
        c.sphere((hx + 0.5, hy - 0.5), 2.6, ramp(accent), spec=0.9, glow=1.0)  # 瞳
        c.dot(hx + 0.5, hy - 0.5, hexc(0x0a0614))
        for a in (0.8, 2.4, 4.0, 5.6):
            p0 = (hx + math.cos(a) * 6, hy + math.sin(a) * 6)
            p1 = (hx + math.cos(a) * 9.5, hy + math.sin(a) * 9.5)
            c.capsule(p0, p1, 0.8, ramp(hexc(0x4a3a78)), spec=0.4, r1=0.3)
    return c.render(glow_color=accent, glow_radius=2)


def void_staff(accent, ph=0.0, anim=False):
    return staff(accent, orb="void")


def rod(accent, ph=0.0, anim=False):
    """ロッド: 先端が渦を巻く杖 + 宝珠。"""
    c = hd.Canvas()
    ax = Ax((6, 58), (44, 20))
    c.capsule(ax.at(0.02), ax.at(1.0), 1.5, ramp(hexc(0x5a6a8a)), spec=0.4)
    cx, cy = ax.at(1.0)
    pts = []
    for i in range(40):
        a = 3.6 - i * 0.16
        r = 11 - i * 0.2
        pts.append((cx + 4 + math.cos(a) * r, cy - 4 + math.sin(a) * r))
    for p, q in zip(pts, pts[1:]):
        c.capsule(p, q, 1.5, ramp(hexc(0x8aa0c8)), spec=0.5)
    gem(c, (cx + 4, cy - 4), 3.2, accent, glow=1.0)
    for k in range(3):
        c.dot(*ax.at(0.3 + k * 0.2, 3), accent, glow=0.5)
    return c.render(glow_color=accent, glow_radius=2)


def wand(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    ax = Ax((14, 52), (46, 20))
    c.capsule(ax.at(0), ax.at(0.9), 1.3, ramp(hexc(0xe8e0f0)), spec=0.6, r1=0.9)
    grip(c, ax, 0.0, 0.3, 1.5, hexc(0x5a3a7a), wraps=3)
    x, y = ax.at(1.0)
    c.star((x, y), 7, 2.3, accent, WHITE, points=5, glow=1.0)
    sparkles(c, [(x - 9, y - 2), (x + 3, y + 9), (x + 8, y - 8)], accent, 1.5)
    return c.render(glow_color=accent, glow_radius=2)


def fishing_rod(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    ax = Ax((8, 58), (56, 6))
    c.capsule(ax.at(0), ax.at(1.0), 1.6, ramp(WOOD), spec=0.3, r1=0.6)
    grip(c, ax, 0.02, 0.2, 1.9, LEATHER, wraps=3)
    c.sphere(ax.at(0.24, 3), 2.2, ramp(GOLD), spec=0.6)  # リール
    tip = ax.at(1.0)
    c.line([tip, (tip[0] + 1, tip[1] + 18), (tip[0] - 1, tip[1] + 30)], hexc(0xe0e8f0))
    c.sphere((tip[0] - 1, tip[1] + 31), 2.0, ramp(accent), spec=0.6, glow=0.4)
    return c.render()


# ================================================================ 槌類

def hammer(accent, ph=0.0, anim=False, size=1.0, head=DARK_STEEL, cracks=False):
    c = hd.Canvas()
    ax = Ax((9, 58), (44, 20))
    c.capsule(ax.at(0), ax.at(1.0), 2.0, ramp(DARK_WOOD), spec=0.3)
    grip(c, ax, 0.04, 0.32, 2.3, LEATHER, wraps=5)
    hx, hy = ax.at(1.06)
    w, d = 11.0 * size, 6.0 * size
    hm = ramp(head, spread=0.8)
    pts = [(hx - ax.v[0] * w - ax.u[0] * d, hy - ax.v[1] * w - ax.u[1] * d),
           (hx - ax.v[0] * w + ax.u[0] * d, hy - ax.v[1] * w + ax.u[1] * d),
           (hx + ax.v[0] * w + ax.u[0] * d, hy + ax.v[1] * w + ax.u[1] * d),
           (hx + ax.v[0] * w - ax.u[0] * d, hy + ax.v[1] * w - ax.u[1] * d)]

    def nf(x, y):
        dx, dy = x - hx, y - hy
        a = (dx * ax.u[0] + dy * ax.u[1]) / d  # 打面ブロックの丸み (u 方向)
        nx, ny = ax.u[0] * a * 0.7 - 0.12, ax.u[1] * a * 0.7 - 0.18
        return (nx, ny, math.sqrt(max(0.05, 1 - nx * nx - ny * ny)))

    c.poly(pts, hm, normal_fn=nf, spec=0.3)
    for s_ in (-1, 1):  # 両端の打面と金の帯
        p = (hx + ax.v[0] * w * s_, hy + ax.v[1] * w * s_)
        c.capsule((p[0] - ax.u[0] * d, p[1] - ax.u[1] * d), (p[0] + ax.u[0] * d, p[1] + ax.u[1] * d),
                  1.6, ramp(STEEL), spec=0.6)
        q = (hx + ax.v[0] * w * 0.55 * s_, hy + ax.v[1] * w * 0.55 * s_)
        c.capsule((q[0] - ax.u[0] * d, q[1] - ax.u[1] * d), (q[0] + ax.u[0] * d, q[1] + ax.u[1] * d),
                  0.9, ramp(GOLD), spec=0.7)
    gem(c, (hx, hy), 2.6 * size, accent, glow=0.7)
    if cracks:
        for k in range(5):
            a = k * 1.3 + 0.4
            c.line([(hx, hy), (hx + math.cos(a) * 6 * size, hy + math.sin(a) * 6 * size)], accent, glow=0.7)
    return c.render(glow_color=accent if cracks else None, glow_radius=1)

def quake_hammer(accent, ph=0.0, anim=False):
    return hammer(accent, size=1.25, head=hexc(0x6a5a3a), cracks=True)


def moon_mace(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    ax = Ax((9, 58), (44, 20))
    c.capsule(ax.at(0), ax.at(1.0), 1.8, ramp(hexc(0x5a6478)), spec=0.5)
    grip(c, ax, 0.04, 0.3, 2.1, LEATHER, wraps=4)
    hx, hy = ax.at(1.12)
    for k in range(8):  # 突起
        a = k * math.pi / 4
        c.capsule((hx, hy), (hx + math.cos(a) * 9, hy + math.sin(a) * 9), 1.2, ramp(hexc(0xc8d4e8)),
                  spec=0.6, r1=0.3)
    c.sphere((hx, hy), 6.2, ramp(hexc(0xb8c8e0), spread=0.8), spec=0.7)
    c.crescent((hx, hy), 3.6, (1.6, -1.6), 2.9, ramp(accent))
    return c.render()


# ================================================================ 弓

def bow(accent, ph=0.0, anim=False, moon=False):
    c = hd.Canvas()
    # 弧: 左上→右下へ張った弓 (握りは中央)
    cx, cy, r = 20.0, 20.0, 34.0
    limb = ramp(hexc(0xd6e4f4) if moon else WOOD, spread=0.7)
    pts = []
    for i in range(41):
        a = math.radians(-8 + i * (106 / 40))
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    for i, (p, q) in enumerate(zip(pts, pts[1:])):
        th = 2.2 - abs(i - 20) / 20 * 1.3
        c.capsule(p, q, th, limb, spec=0.4)
    c.line([pts[0], pts[-1]], hexc(0xe8e8f0))  # 弦
    gx, gy = pts[20]
    c.capsule((gx - 2.5, gy - 2.5), (gx + 2.5, gy + 2.5), 2.4, ramp(LEATHER), spec=0.2)
    for p in (pts[0], pts[-1]):
        if moon:
            c.crescent(p, 3.6, (1.4, 1.4), 2.8, ramp(accent))
        else:
            gem(c, p, 1.8, accent, glow=0.4)
    # 矢
    c.line([(18, 46), (44, 20)], hexc(0x8a6a4a))
    c.poly([(44, 20), (47, 14), (50, 17)], ramp(STEEL), normal_fn=lambda x, y: (-0.3, -0.3, 0.9), spec=0.6)
    c.line([(16, 48), (13, 47)], accent)
    c.line([(18, 50), (17, 53)], accent)
    if moon:
        sparkles(c, [(46, 46), (52, 30), (30, 52)], accent, 1.0)
        return c.render(glow_color=accent, glow_radius=1)
    return c.render()


def moon_bow(accent, ph=0.0, anim=False):
    return bow(accent, moon=True)


# ================================================================ 鎌

def scythe(accent, ph=0.0, anim=False, soul=False):
    c = hd.Canvas()
    ax = Ax((10, 60), (40, 6))
    c.capsule(ax.at(0), ax.at(1.0), 1.7, ramp(VOID if soul else DARK_WOOD), spec=0.3)
    grip(c, ax, 0.35, 0.47, 2.0, LEATHER, wraps=3)
    # 刃: 先端から左上へ大きく弧を描く
    hx, hy = ax.at(0.96)
    outer, inner = [], []
    for i in range(25):
        k = i / 24
        a = math.radians(200 - k * 90)
        ro = 31 - k * 10
        ri = ro - (9.0 * (1 - k) ** 0.8 + 0.3)
        outer.append((hx + 24 + math.cos(a) * ro, hy + 8 + math.sin(a) * ro * 0.75))
        inner.append((hx + 24 + math.cos(a) * ri, hy + 8 + math.sin(a) * ri * 0.75))
    metal = hexc(0x50466e) if soul else hexc(0xb8c4cc)
    c.poly(outer + inner[::-1], ramp(metal, spread=0.8), normal_fn=lambda x, y: (-0.3, -0.5, 0.81), spec=0.5)
    c.line(inner, mix(accent, WHITE, 0.3), glow=0.6 if soul else 0.0)
    c.capsule(ax.at(0.92, -2), ax.at(0.92, 2), 1.6, ramp(GOLD), spec=0.6)
    gem(c, ax.at(0.92), 1.7, accent, glow=0.6)
    if soul:
        for k in range(3):  # 魂の灯
            x, y = inner[6 + k * 6]
            c.sphere((x + 2, y + 4), 1.4, ramp(accent), spec=0.9, glow=1.0)
        return c.render(glow_color=accent, glow_radius=2)
    return c.render()


def soul_scythe(accent, ph=0.0, anim=False):
    return scythe(accent, soul=True)


# ================================================================ 鞭・連接棍

def venom_whip(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    ax = Ax((6, 58), (22, 42))
    grip(c, ax, 0.0, 1.0, 2.0, hexc(0x3a5a2a), wraps=4)
    c.sphere(ax.at(0.0), 2.4, ramp(GOLD), spec=0.6)
    pts = []
    for i in range(36):
        t = i / 35
        x = 22 + t * 36
        y = 42 - t * 36 + math.sin(t * 9) * 6 * (1 - t * 0.4)
        pts.append((x, y))
    for i, (p, q) in enumerate(zip(pts, pts[1:])):
        th = 1.7 - i / 35 * 1.1
        c.capsule(p, q, th, ramp(hexc(0x4a8a3a) if i % 4 else accent), spec=0.3)
    for k in range(4):  # 毒の滴
        x, y = pts[8 + k * 7]
        c.sphere((x, y + 4), 1.2, ramp(accent), spec=0.9, glow=0.6)
    return c.render(glow_color=accent, glow_radius=1)


def ember_flail(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    ax = Ax((6, 58), (22, 42))
    grip(c, ax, 0.0, 1.0, 2.1, DARK_WOOD, wraps=4)
    c.sphere(ax.at(1.0), 2.0, ramp(DARK_STEEL), spec=0.6)
    for i in range(7):  # 鎖
        x = 24 + i * 3.6
        y = 40 - i * 3.0 + math.sin(i) * 1.5
        c.sphere((x, y), 1.4, ramp(DARK_STEEL), spec=0.6)
    bx, by = 50, 16
    for k in range(8):
        a = k * math.pi / 4
        c.capsule((bx, by), (bx + math.cos(a) * 9, by + math.sin(a) * 9), 1.1, ramp(DARK_STEEL), spec=0.6, r1=0.2)
    c.sphere((bx, by), 6.0, ramp(hexc(0x4a3a3a), spread=0.7), spec=0.4)
    for k in range(5):  # 熾火の割れ目
        a = k * 1.25
        c.dot(bx + math.cos(a) * 3, by + math.sin(a) * 3, accent, glow=0.9)
    for k in range(6):  # 炎
        a = k * 1.0 + 0.3
        c.sphere((bx + math.cos(a) * 8, by + math.sin(a) * 8 - 2), 1.2, ramp(mix(accent, hexc(0xffe14a), k / 6)),
                 spec=0.0, glow=1.0)
    return c.render(glow_color=accent, glow_radius=2)


# ================================================================ 輪刃

def chakram(accent, ph=0.0, anim=False, eclipse=False):
    c = hd.Canvas()
    cx, cy = 32, 32
    for k in range(8):  # 外向きの刃
        a = k * math.pi / 4 + 0.2
        p = [(cx + math.cos(a) * 15, cy + math.sin(a) * 15), (cx + math.cos(a + 0.18) * 27, cy + math.sin(a + 0.18) * 27),
             (cx + math.cos(a + 0.42) * 16, cy + math.sin(a + 0.42) * 16)]
        c.poly(p, ramp(hexc(0x3a2e5a) if eclipse else STEEL, spread=0.8),
               normal_fn=lambda x, y: (-0.3, -0.4, 0.86), spec=0.6)
    # 輪 (トーラス近似: 外円 - 内円)
    c.crescent((cx, cy), 17, (0, 0), 10.5, ramp(hexc(0x2a2244) if eclipse else accent, spread=0.75))
    c.crescent((cx, cy), 12.5, (0, 0), 10.0, ramp(GOLD))
    if eclipse:
        c.sphere((cx, cy), 6.5, ramp(hexc(0x120a22)), spec=0.0)
        for k in range(12):  # 蝕の光冠
            a = k * math.pi / 6
            c.dot(cx + math.cos(a) * 8.5, cy + math.sin(a) * 8.5, accent, glow=1.0)
        return c.render(glow_color=accent, glow_radius=2)
    c.sphere((cx, cy), 3.0, ramp(accent), spec=0.9, glow=0.6)
    return c.render()


def eclipse_chakram(accent, ph=0.0, anim=False):
    return chakram(accent, eclipse=True)


# ================================================================ 魔導書

def tome(accent, ph=0.0, anim=False, cover=None, emblem="eye"):
    c = hd.Canvas()
    cov = cover or mix(accent, hexc(0x1e1230), 0.72)
    # 本体 (やや斜めの正面): 表紙・背表紙・ページの厚み
    body = [(12, 14), (48, 8), (54, 48), (18, 56)]
    pages = [(18, 56), (54, 48), (55, 51), (19, 59)]
    spine = [(8, 16), (12, 14), (18, 56), (14, 58)]
    c.poly(pages, ramp(hexc(0xf0e6cc)), normal_fn=lambda x, y: (0, 0.5, 0.86))
    c.poly(spine, ramp(hd._hsv_shift(cov, -0.15, 0.7, 0, 0)), normal_fn=lambda x, y: (-0.7, 0, 0.7))
    c.poly(body, ramp(cov, spread=0.75), normal_fn=lambda x, y: ((x - 33) * 0.012 + 0.12, (y - 32) * 0.012 + 0.1, 0.97), spec=0.15)
    # 金の角金具と縁取り
    for p in body:
        c.sphere((p[0] + (2 if p[0] < 30 else -2), p[1] + (2 if p[1] < 30 else -2)), 2.2, ramp(GOLD), spec=0.7)
    c.line([(15, 17), (47, 11), (51, 45), (20, 52), (15, 17)], GOLD)
    mx, my = 33, 31
    pulse = 0.5 + 0.5 * math.sin(ph) if anim else 0.7
    if emblem == "eye":
        c.crescent((mx, my), 7.5, (0, 4.5), 7.2, ramp(GOLD))
        c.sphere((mx, my + 1), 3.6, ramp(accent), spec=0.9, glow=0.6 + 0.4 * pulse)
        c.dot(mx, my + 1, hexc(0x14081e))
    elif emblem == "chaos":
        for k in range(8):
            a = k * math.pi / 4 + ph * 0.2
            c.line([(mx, my), (mx + math.cos(a) * 9, my + math.sin(a) * 9)], accent, glow=0.5 + 0.4 * pulse)
            c.dot(mx + math.cos(a) * 10, my + math.sin(a) * 10, mix(accent, WHITE, 0.4), glow=0.8)
        c.sphere((mx, my), 3.0, ramp(hexc(0x1a0a1a)), spec=0.3)
    elif emblem == "flame":
        for k in range(7):
            h = 9 + 3 * math.sin(ph + k)
            x = mx - 6 + k * 2
            c.line([(x, my + 6), (x + math.sin(ph + k) * 1.5, my + 6 - h)],
                   mix(accent, hexc(0xffe14a), k / 7), glow=0.8)
    if anim:
        for k in range(3):  # 頁から立ちのぼる魔力
            a = ph + k * 2.1
            c.dot(mx + math.cos(a) * 16, my - 14 + math.sin(a) * 4, mix(accent, WHITE, 0.5), glow=1.0)
    return c.render(glow_color=accent, glow_radius=2)


def chaos_tome(accent, ph=0.0, anim=False):
    return tome(accent, ph, anim, cover=hexc(0x3a1424), emblem="chaos")


def flame_grimoire(accent, ph=0.0, anim=False):
    return tome(accent, ph, anim, cover=hexc(0x4a1a10), emblem="flame")


# ================================================================ 拳・爪・角笛・銃

def gauntlet(accent, ph=0.0, anim=False, big=False):
    """拳套: 腕甲 (左下) から握り拳 (右上) へ。指の装甲板・親指・拳の鋲。"""
    c = hd.Canvas()
    k = 1.15 if big else 1.0
    metal = hexc(0x9a8462) if big else hexc(0xc0a46a)
    dark = hexc(0x4a3e34) if big else DARK_STEEL
    ax = Ax((8, 58), (46, 20))
    c.capsule(ax.at(0.0), ax.at(0.42), 8.0 * k, ramp(dark, spread=0.75), spec=0.4, r1=7.0 * k)
    for t in (0.06, 0.38):
        c.capsule(ax.at(t, -8.2 * k), ax.at(t, 8.2 * k), 1.4, ramp(GOLD), spec=0.7)
    F = ax.at(0.70)
    c.sphere(F, 9.5 * k, ramp(metal, spread=0.8), spec=0.4)  # 手の甲
    for i in range(4):  # 指
        off = (-6.0 + 4.0 * i) * k
        b0 = ax.at(0.70, off)
        b1 = ax.at(0.92, off)
        c.capsule(b0, b1, 2.4 * k, ramp(metal, spread=0.8), spec=0.6)
        c.sphere(b1, 2.7 * k, ramp(hd._hsv_shift(metal, 0.05, 0.14, 0, -0.05), spread=0.8), spec=0.8)
        if big:
            p = ax.at(0.97, off)
            c.capsule(b1, (p[0] + ax.u[0] * 3, p[1] + ax.u[1] * 3), 1.2, ramp(DARK_STEEL), spec=0.6, r1=0.2)
    c.capsule(ax.at(0.62, -9 * k), ax.at(0.76, -10.5 * k), 2.3 * k, ramp(metal), spec=0.5)  # 親指
    gem(c, ax.at(0.66, 1.5), 2.8 * k, accent, glow=0.8)
    return c.render(glow_color=accent, glow_radius=1)

def titan_gauntlet(accent, ph=0.0, anim=False):
    return gauntlet(accent, big=True)


def howling_claw(accent, ph=0.0, anim=False):
    """哮爪: 毛皮の手甲から扇状に伸びる 3 本の鉤爪。"""
    c = hd.Canvas()
    fur = hexc(0x6a5038)
    c.capsule((8, 58), (20, 46), 7.0, ramp(fur, spread=0.7), spec=0.1)
    for i in range(5):  # 毛並み
        c.line([(9 + i * 3, 50 - i * 2), (12 + i * 3, 54 - i * 2)], ramp(fur)[0])
    c.sphere((25, 40), 9.0, ramp(hexc(0x7a5e42), spread=0.75), spec=0.2)
    for k, ang in enumerate((-80, -50, -20)):
        a = math.radians(ang)
        base = (25 + math.cos(a) * 7, 40 + math.sin(a) * 7)
        outer, inner = [], []
        for i in range(12):
            t = i / 11
            bend = math.radians(ang + 25 * t)  # 先が外側へ反る
            L = 26 * t
            x = base[0] + math.cos(a) * L * 0.6 + math.cos(bend) * L * 0.4
            y = base[1] + math.sin(a) * L * 0.6 + math.sin(bend) * L * 0.4
            w = 3.0 * (1 - t) ** 0.9 + 0.2
            nx, ny = -math.sin(bend), math.cos(bend)
            outer.append((x + nx * w, y + ny * w))
            inner.append((x - nx * w, y - ny * w))
        c.poly(outer + inner[::-1], ramp(BONE, spread=0.85),
               normal_fn=lambda x, y: (-0.25, -0.3, 0.92), spec=0.6)
        c.line(inner[2:-2], mix(BONE, WHITE, 0.7))
    gem(c, (24, 41), 2.6, accent, glow=0.8)
    return c.render(glow_color=accent, glow_radius=1)

def war_horn(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    pts = []
    for i in range(30):
        t = i / 29
        a = math.radians(210 - t * 150)
        r = 22 - t * 4
        pts.append((34 + math.cos(a) * r, 40 + math.sin(a) * r * 0.9))
    for i, (p, q) in enumerate(zip(pts, pts[1:])):
        th = 1.6 + (i / 29) ** 1.6 * 6.5
        c.capsule(p, q, th, ramp(BONE, spread=0.75), spec=0.4)
    for k in (8, 16, 24):  # 金の帯
        p = pts[k]
        th = 1.6 + (k / 29) ** 1.6 * 6.5 + 0.6
        c.sphere(p, th, ramp(GOLD), spec=0.6)
    end = pts[-1]
    c.sphere(end, 7.0, ramp(hexc(0x3a2a22)), spec=0.2)  # 吹き口の開口
    c.sphere(end, 4.6, ramp(hexc(0x120c0a)), spec=0.0)
    c.capsule((pts[4][0], pts[4][1] + 6), (pts[20][0], pts[20][1] + 6), 0.8, ramp(LEATHER))  # 吊り紐
    gem(c, pts[16], 1.8, accent, glow=0.5)
    return c.render()


def depth_gun(accent, ph=0.0, anim=False):
    c = hd.Canvas()
    c.poly([(14, 36), (26, 30), (21, 54), (10, 56)], ramp(hexc(0x4a2a24), spread=0.7),
           normal_fn=lambda x, y: (-0.3, -0.25, 0.92), spec=0.2)  # 銃把
    for i in range(3):
        c.line([(13 + i, 42 + i * 4), (21 + i, 39 + i * 4)], hexc(0x2a1612))
    c.capsule((18, 36), (52, 14), 4.6, ramp(hexc(0x3a4052), spread=0.8), spec=0.5)  # 機関部
    c.capsule((30, 28), (58, 10), 2.2, ramp(hexc(0x5a6276), spread=0.8), spec=0.7)  # 銃身
    c.capsule((56, 11), (59, 9), 3.0, ramp(GOLD), spec=0.7)  # 銃口
    c.capsule((22, 40), (28, 44), 1.0, ramp(GOLD), spec=0.6)  # 用心金
    gem(c, (30, 30), 2.6, accent, glow=0.9)
    for k in range(4):  # 機関部の光る管
        c.dot(35 + k * 4, 25 - k * 2.6, accent, glow=0.7)
    return c.render(glow_color=accent, glow_radius=1)

# ================================================================ 表

ICONS = {
    "abyss_sword": sword, "abyss_greatsword": greatsword, "abyss_rune_blade": rune_blade,
    "abyss_rift_blade": rift_blade, "abyss_wind_blade": wind_blade, "abyss_echo_katana": echo_katana,
    "abyss_katana": katana, "abyss_frost_katana": frost_katana, "abyss_dagger": dagger_icon,
    "abyss_fang_dagger": fang_dagger, "abyss_twin_dagger": twin_dagger, "abyss_axe": axe,
    "abyss_blood_axe": blood_axe, "abyss_tool_axe": tool_axe, "abyss_pickaxe": pickaxe,
    "abyss_shovel": shovel, "abyss_hoe": hoe, "abyss_shears": shears,
    "abyss_spear": lambda a, ph=0.0, anim=False: polearm(a, head="leaf"),
    "abyss_storm_spear": lambda a, ph=0.0, anim=False: polearm(a, head="storm"),
    "abyss_astral_lance": lambda a, ph=0.0, anim=False: polearm(a, head="star", haft=hexc(0x3a3456)),
    "abyss_meteor_lance": lambda a, ph=0.0, anim=False: polearm(a, head="meteor"),
    "abyss_tide_trident": lambda a, ph=0.0, anim=False: polearm(a, head="trident", haft=hexc(0x2a4a5a)),
    "abyss_rod": rod, "abyss_fishing_rod": fishing_rod, "abyss_staff": staff, "abyss_void_staff": void_staff,
    "abyss_wand": wand, "abyss_hammer": hammer, "abyss_quake_hammer": quake_hammer, "abyss_moon_mace": moon_mace,
    "abyss_bow": bow, "abyss_moon_bow": moon_bow, "abyss_scythe": scythe, "abyss_soul_scythe": soul_scythe,
    "abyss_venom_whip": venom_whip, "abyss_ember_flail": ember_flail, "abyss_chakram": chakram,
    "abyss_eclipse_chakram": eclipse_chakram, "abyss_tome": tome, "abyss_chaos_tome": chaos_tome,
    "abyss_flame_grimoire": flame_grimoire, "abyss_gauntlet": gauntlet, "abyss_titan_gauntlet": titan_gauntlet,
    "abyss_howling_claw": howling_claw, "abyss_war_horn": war_horn, "abyss_depth_gun": depth_gun,
}


def excavator(accent, ph=0.0, anim=False):
    """深淵の掘削槌: 重い槌頭の両端につるはしの爪。"""
    return hammer(accent, ph, anim, size=1.15, head=hexc(0x3a3f52), cracks=True)


def lumber_axe(accent, ph=0.0, anim=False):
    return tool_axe(accent, ph, anim)


def sickle(accent, ph=0.0, anim=False):
    return scythe(accent, ph, anim)


ICONS.update({"abyss_excavator": excavator, "abyss_lumber_axe": lumber_axe, "abyss_sickle": sickle})


def _register_generated() -> None:
    """MCAssetGen 由来のジェネレータで描く追加武器 (art/new_weapons.py) を表に載せる。"""
    from . import new_weapons
    ICONS.update(new_weapons.ICONS)
    ANIMATED.update(new_weapons.ANIMATED)


_register_generated()


def icon(item_id: str, accent_hex: int, frame: int = 0) -> Image.Image:
    accent = hexc(accent_hex)
    anim = item_id in ANIMATED
    ph = 2 * math.pi * frame / FRAMES
    return ICONS[item_id](accent, ph, anim)


def icon_strip(item_id: str, accent_hex: int) -> Image.Image:
    return hd.strip([icon(item_id, accent_hex, f) for f in range(FRAMES)])
