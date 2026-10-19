"""マテリアル定義と「面」を塗る処理。

(モデル, 素材) の組合せで 1 つの面がどう塗られるかを決める。
面ごとに「ベベル・円筒シェーディング・刃のエッジ・彫り・鋲」などを焼き込み、
単なる板ではなく加工物に見えるようにする。

座標系: 面の矩形はテクスチャのピクセル (x0,y0,x1,y1)、右端・下端は含まない。
面内の横軸/縦軸がモデルのどの軸に当たるかは face から決まる:

    north/south : 横 = X, 縦 = Y
    east/west   : 横 = Z, 縦 = Y
    up/down     : 横 = X, 縦 = Z
"""
from __future__ import annotations

import math
import random
from dataclasses import dataclass, field

from PIL import Image, ImageDraw

from . import core
from .core import RGBA, mix, shade

# 面の方向 (MC の Direction に対応)
DOWN, UP, NORTH, SOUTH, WEST, EAST = "down", "up", "north", "south", "west", "east"

# 面ごとの基本明暗 (MC のライティングを補う弱い焼き込み)
FACE_TONE = {
    UP: 1.05,
    DOWN: 0.80,
    NORTH: 1.04,
    SOUTH: 0.90,
    WEST: 0.86,
    EAST: 0.96,
}

# 面内の軸の対応 (u = 横, v = 縦)
FACE_AXES = {
    NORTH: ("x", "y"),
    SOUTH: ("x", "y"),
    EAST: ("z", "y"),
    WEST: ("z", "y"),
    UP: ("x", "z"),
    DOWN: ("x", "z"),
}

# 光源は左上 (カメラ側) から。面内で左が明るく右が暗い。
LIGHT_LEFT = True


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


@dataclass
class Ctx:
    """1 面を塗るための文脈。"""
    img: Image.Image
    d: ImageDraw.ImageDraw
    rect: tuple
    x0: int
    y0: int
    x1: int
    y1: int
    w: int
    h: int
    face: str
    base: RGBA
    mat: Material
    rnd: random.Random
    size: tuple = (1.0, 1.0, 1.0)   # モデル単位の (X, Y, Z) サイズ
    u_axis: str = "x"
    v_axis: str = "y"

    @property
    def vertical(self) -> bool:
        """面内の縦軸が長い (柱状) か。"""
        return self.h >= self.w

    @property
    def long_u(self) -> bool:
        return self.w > self.h

    def axis_len(self, axis: str) -> float:
        return {"x": self.size[0], "y": self.size[1], "z": self.size[2]}.get(axis, 1.0)

    def is_pole(self) -> bool:
        """縦に長い箱で、その側面か (円筒シェーディング対象)。"""
        mx, my, mz = self.size
        return self.face in (NORTH, SOUTH, EAST, WEST) and my > mx * 1.35 and my > mz * 1.35

    def is_bar(self) -> bool:
        """横に長い箱の側面か。"""
        mx, my, mz = self.size
        if self.face in (NORTH, SOUTH):
            return mx > my * 1.35
        if self.face in (EAST, WEST):
            return mz > my * 1.35
        return False


# ------------------------------------------------------------------ 下地ヘルパ

def _fill(ctx: Ctx, top: RGBA, bottom: RGBA) -> None:
    core.vgradient(ctx.img, ctx.rect, top, bottom)


def _bevel(ctx: Ctx, light: float = 1.30, dark: float = 0.62, inner: bool = True) -> None:
    """面の外周にベベル (上・左 = 光 / 下・右 = 影) を焼く。

    小さな面では線が全体を占めて縞になるため、十分な大きさのときだけ描く。
    """
    d, x0, y0, x1, y1 = ctx.d, ctx.x0, ctx.y0, ctx.x1, ctx.y1
    b = ctx.base
    lt, dk = shade(b, light), shade(b, dark)
    if ctx.h >= 5:
        d.line([(x0, y0), (x1 - 1, y0)], fill=lt)
        d.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=dk)
    if ctx.w >= 5:
        d.line([(x0, y0), (x0, y1 - 1)], fill=shade(b, 1.16))
        d.line([(x1 - 1, y0), (x1 - 1, y1 - 1)], fill=shade(b, 0.74))
    if inner and ctx.w >= 8 and ctx.h >= 8:
        d.line([(x0 + 1, y0 + 1), (x1 - 2, y0 + 1)], fill=shade(b, 1.12))
        d.line([(x0 + 1, y1 - 2), (x1 - 2, y1 - 2)], fill=shade(b, 0.84))


def _cyl_shade(ctx: Ctx, k_side: float = 1.26, k_edge: float = 0.62, bias: float = 0.30) -> None:
    """面内の横方向に円筒シェーディングをかける (柱が丸く見える)。"""
    x0, y0, x1, y1 = ctx.rect
    w = max(1, x1 - x0)
    d = ctx.d
    b = ctx.base
    for i in range(w):
        t = i / max(1, w - 1)
        # 左寄りにハイライト、右へ向かって落ちる → 曲面
        a = math.cos((t - bias) * math.pi)          # -1..1
        k = 1.0 + (k_side - 1.0) * a if a > 0 else 1.0 + (1.0 - k_edge) * a * -1.0
        k = max(k_edge, min(k_side, k))
        d.line([(x0 + i, y0), (x0 + i, y1 - 1)], fill=shade(b, k))


def _grain_v(ctx: Ctx, colors, density: float = 0.5, width: int = 1) -> None:
    """縦方向の木目・筋。"""
    x0, y0, x1, y1 = ctx.rect
    for _ in range(max(1, int((x1 - x0) * density))):
        x = ctx.rnd.randrange(x0, x1)
        c = ctx.rnd.choice(colors)
        jx = 0
        for y in range(y0, y1):
            if ctx.rnd.random() < 0.18:
                jx = ctx.rnd.choice([-1, 0, 1])
            if 0 <= x + jx < x1:
                ctx.d.point((x + jx, y), fill=c)


def _spec_sweep(ctx: Ctx, color: RGBA, width: int = 2, slope: float = 0.5) -> None:
    """斜めの反射帯 (金属の照り返し)。"""
    x0, y0, x1, y1 = ctx.rect
    for i in range(ctx.h):
        if i % max(2, ctx.h // 5) >= width and i != 0:
            continue
        off = int(i * slope)
        a = x0 + (ctx.w // 5) + off
        b = min(x1 - 1, a + width)
        if a < x1:
            ctx.d.line([(a, y0 + i), (b, y0 + i)], fill=color)


def _rivets(ctx: Ctx, color: RGBA, spacing: int = 6, inset: int = 2) -> None:
    """鋲 (角に 1px の明暗つき)。"""
    if ctx.w < 6 or ctx.h < 6:
        return
    d = ctx.d
    for yy in range(ctx.y0 + inset, ctx.y1 - inset, spacing):
        for xx in range(ctx.x0 + inset, ctx.x1 - inset, spacing):
            d.point((xx, yy), fill=color)
            if xx + 1 < ctx.x1 - 0:
                d.point((xx + 1, yy), fill=shade(color, 0.68))


def _stitch(ctx: Ctx, color: RGBA, inset: int = 1, step: int = 3) -> None:
    """縫い目 (破線)。"""
    d = ctx.d
    for xx in range(ctx.x0 + inset + 1, ctx.x1 - inset - 1, step):
        d.point((xx, ctx.y0 + inset), fill=color)
        if ctx.h > 4:
            d.point((xx, ctx.y1 - 1 - inset), fill=color)


def _facet(ctx: Ctx, bright: RGBA, dark: RGBA, cx_ratio: float = 0.38, cy_ratio: float = 0.34) -> None:
    """宝石のファセット (中心から角へ三角を張る)。"""
    x0, y0, x1, y1 = ctx.rect
    cx = x0 + max(1, int(ctx.w * cx_ratio))
    cy = y0 + max(1, int(ctx.h * cy_ratio))
    d = ctx.d
    corners = [(x0, y0), (x1 - 1, y0), (x1 - 1, y1 - 1), (x0, y1 - 1)]
    for i, (px, py) in enumerate(corners):
        px2, py2 = corners[(i + 1) % 4]
        col = mix(bright, dark, (i % 2) * 0.55 + (0.2 if i >= 2 else 0.0))
        d.polygon([(cx, cy), (px, py), (px2, py2)], fill=col)
    # 稜線
    for (px, py) in corners:
        d.line([(cx, cy), (px, py)], fill=shade(ctx.base, 0.72))
    if ctx.w >= 3 and ctx.h >= 3:
        d.point((cx, cy), fill=(255, 255, 255, 235))


def _crack(ctx: Ctx, color: RGBA, n: int = 2) -> None:
    if ctx.x1 - 1 <= ctx.x0 + 1 or ctx.y1 - 1 <= ctx.y0 + 1:
        return
    for _ in range(n):
        x = ctx.rnd.randrange(ctx.x0 + 1, ctx.x1 - 1)
        y = ctx.rnd.randrange(ctx.y0 + 1, ctx.y1 - 1)
        dx, dy = ctx.rnd.choice([(1, 1), (1, 0), (0, 1), (-1, 1)])
        for _ in range(ctx.rnd.randint(2, 4)):
            if ctx.x0 <= x < ctx.x1 and ctx.y0 <= y < ctx.y1:
                ctx.d.point((x, y), fill=color)
            x += dx
            y += dy


# ------------------------------------------------------------------ 材質の描画

def _paint_metal(ctx: Ctx) -> None:
    """金属板: 上下の勾配 + ヘアライン + 斜めの照り返し + 縁のベベル + 鋲。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.10), shade(b, 0.80))
    # ヘアライン (横に長い面は横、縦に長い面は縦)
    c = shade(b, 0.90)
    if ctx.long_u:
        for y in range(ctx.y0, ctx.y1):
            if (y - ctx.y0) % 3 == 1:
                ctx.d.line([(ctx.x0, y), (ctx.x1 - 1, y)], fill=c)
    else:
        for x in range(ctx.x0, ctx.x1):
            if (x - ctx.x0) % 3 == 1:
                ctx.d.line([(x, ctx.y0), (x, ctx.y1 - 1)], fill=c)
    # 照り返しの帯
    if ctx.w >= 4 and ctx.h >= 4:
        hy = ctx.y0 + max(1, int(ctx.h * 0.28))
        ctx.d.line([(ctx.x0 + 1, hy), (ctx.x1 - 2, hy)], fill=shade(b, 1.42))
        if ctx.h > 10:
            ctx.d.line([(ctx.x0 + 1, hy + 1), (ctx.x1 - 2, hy + 1)], fill=shade(b, 1.16))
    # 打痕
    for _ in range(max(1, int(ctx.w * ctx.h * 0.012))):
        x = ctx.rnd.randrange(ctx.x0, ctx.x1)
        y = ctx.rnd.randrange(ctx.y0, ctx.y1)
        ctx.d.point((x, y), fill=shade(b, ctx.rnd.choice([0.70, 0.86, 1.20])))
    _bevel(ctx, 1.32, 0.60)
    _rivets(ctx, shade(b, 1.30), spacing=max(5, min(ctx.w, ctx.h) - 2), inset=1)
    if ctx.mat.accent:
        ctx.d.line([(ctx.x0, ctx.y1 - 1), (ctx.x1 - 1, ctx.y1 - 1)], fill=ctx.mat.accent)


def _paint_blade(ctx: Ctx) -> None:
    """刃の平地: 先端ほど明るい鋼の勾配 + 樋 (fuller) + 焼き入れの筋。"""
    b = ctx.base
    x0, y0, x1, y1 = ctx.rect
    if ctx.h >= ctx.w:
        _fill(ctx, shade(b, 1.08), shade(b, 0.82))
        # 樋 (中央の溝) — 上側に光、下側に影
        if ctx.w >= 5:
            m = (x0 + x1) // 2
            ctx.d.line([(m - 1, y0 + 1), (m - 1, y1 - 2)], fill=shade(b, 1.26))
            ctx.d.line([(m, y0 + 1), (m, y1 - 2)], fill=shade(b, 0.78))
            if ctx.w >= 9:
                ctx.d.line([(m - 2, y0 + 1), (m - 2, y1 - 2)], fill=shade(b, 1.10))
                ctx.d.line([(m + 1, y0 + 1), (m + 1, y1 - 2)], fill=shade(b, 0.90))
        # 焼き入れの筋 (刃先側の淡い帯)
        if ctx.w >= 7 and ctx.h >= 8:
            for yy in range(y0 + 2, y1 - 2, 4):
                ctx.d.point((x1 - 2, yy), fill=shade(b, 1.08))
        # 上下端の稜線 (大きな面のみ)
        if ctx.w >= 6 and ctx.h >= 8:
            ctx.d.line([(x0, y0), (x1 - 1, y0)], fill=shade(b, 1.12))
            ctx.d.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=shade(b, 0.78))
        # 焼き入れの波紋
        if ctx.h >= 10:
            for yy in range(y0 + 3, y1 - 2, 5):
                for xx in range(x0 + 1, x1 - 1):
                    if (xx + yy) % 3 == 0:
                        ctx.d.point((xx, yy), fill=shade(b, 0.90))
        if ctx.mat.accent and ctx.h > 10:
            for i in range(3, min(9, ctx.h - 3), 3):
                ctx.d.point(((x0 + x1) // 2, y0 + i), fill=ctx.mat.accent)
    else:
        _fill(ctx, shade(b, 1.14), shade(b, 0.78))
        _bevel(ctx, 1.24, 0.74)


def _paint_blade_edge(ctx: Ctx) -> None:
    """刃先 (薄い側面): 磨かれた明るい鋼。白飛びしないよう抑える。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.06), shade(b, 0.88))
    x0, y0, x1, y1 = ctx.rect
    if 2 <= ctx.w <= 5:
        cx = (x0 + x1) // 2
        ctx.d.line([(cx, y0), (cx, y1 - 1)], fill=shade(b, 1.22))
        ctx.d.line([(x0, y0), (x0, y1 - 1)], fill=shade(b, 0.90))
        ctx.d.line([(x1 - 1, y0), (x1 - 1, y1 - 1)], fill=shade(b, 0.86))
    elif ctx.w > 5:
        _bevel(ctx, 1.18, 0.82)


def _paint_wood(ctx: Ctx) -> None:
    """木: 円筒シェーディング + 木目 + 節目。"""
    b = ctx.base
    if ctx.is_pole():
        _fill(ctx, shade(b, 1.0), shade(b, 1.0))
        _cyl_shade(ctx, 1.22, 0.66, 0.34)
        _grain_v(ctx, [shade(b, 0.82), shade(b, 1.14), shade(b, 0.92)], density=0.34)
        if ctx.w >= 4 and ctx.h > 8 and ctx.rnd.random() < 0.55:
            ky = ctx.rnd.randrange(ctx.y0 + 2, max(ctx.y0 + 3, ctx.y1 - 3))
            kx = ctx.x0 + ctx.w // 2
            ctx.d.ellipse([kx - 1, ky - 1, kx + 1, ky + 1], outline=shade(b, 0.55))
    elif ctx.h > 8:
        _fill(ctx, shade(b, 1.18), shade(b, 0.72))
        for _ in range(max(1, int(ctx.w * 0.7))):
            x = ctx.rnd.randrange(ctx.x0, ctx.x1)
            c = shade(b, ctx.rnd.choice([0.80, 0.90, 1.12]))
            ctx.d.line([(x, ctx.y0 + 1), (x, ctx.y1 - 2)], fill=c)
        _bevel(ctx, 1.24, 0.66)
    else:
        _fill(ctx, shade(b, 1.16), shade(b, 0.74))
        for _ in range(max(1, int(ctx.h * 0.9))):
            y = ctx.rnd.randrange(ctx.y0, ctx.y1)
            c = shade(b, ctx.rnd.choice([0.80, 0.90, 1.12]))
            ctx.d.line([(ctx.x0 + 1, y), (ctx.x1 - 2, y)], fill=c)
        _bevel(ctx, 1.22, 0.68)


def _paint_leather(ctx: Ctx) -> None:
    """革: 細かい粒 + 縫い目 + 折り目の陰。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.14), shade(b, 0.76))
    core.noise(ctx.img, ctx.rect, ctx.rnd, [shade(b, 0.86), shade(b, 1.12)], density=0.18)
    if ctx.h > 5:
        ctx.d.line([(ctx.x0, ctx.y0 + 2), (ctx.x1 - 1, ctx.y0 + 2)], fill=shade(b, 0.66))
        ctx.d.line([(ctx.x0, ctx.y1 - 3), (ctx.x1 - 1, ctx.y1 - 3)], fill=shade(b, 0.70))
    _stitch(ctx, shade(b, 1.30), inset=0, step=2)
    _bevel(ctx, 1.24, 0.70)


def _paint_cloth(ctx: Ctx) -> None:
    """布: 織り目 + しわ + 縁。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.12), shade(b, 0.78))
    for y in range(ctx.y0, ctx.y1):
        if (y - ctx.y0) % 2 == 0:
            ctx.d.line([(ctx.x0, y), (ctx.x1 - 1, y)], fill=shade(b, 0.92))
    for x in range(ctx.x0, ctx.x1):
        if (x - ctx.x0) % 3 == 0:
            ctx.d.line([(x, ctx.y0), (x, ctx.y1 - 1)], fill=shade(b, 1.07))
    # しわ (斜めの陰)
    for _ in range(max(1, ctx.w // 4)):
        sx = ctx.rnd.randrange(ctx.x0, ctx.x1)
        for i in range(min(ctx.h, 6)):
            if sx + i < ctx.x1:
                ctx.d.point((sx + i, ctx.y0 + i + ctx.rnd.randint(0, 2)), fill=shade(b, 0.80))
    if ctx.mat.accent:
        ctx.d.line([(ctx.x0, ctx.y1 - 1), (ctx.x1 - 1, ctx.y1 - 1)], fill=ctx.mat.accent)
    _bevel(ctx, 1.18, 0.74)


def _paint_crystal(ctx: Ctx) -> None:
    """結晶: ファセット + 内部発光 + 縁の輝き。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.30), shade(b, 0.66))
    _facet(ctx, shade(b, 1.45), shade(b, 0.62))
    # 内部の芯
    if ctx.w >= 3 and ctx.h >= 3:
        cx0 = ctx.x0 + ctx.w // 3
        cy0 = ctx.y0 + ctx.h // 3
        cx1 = max(cx0 + 1, ctx.x0 + (ctx.w * 2) // 3)
        cy1 = max(cy0 + 1, ctx.y0 + (ctx.h * 2) // 3)
        core.vgradient(ctx.img, (cx0, cy0, cx1, cy1), shade(b, 1.6), shade(b, 1.0))
    # きらめき
    for _ in range(max(1, int(ctx.w * ctx.h * 0.02))):
        x = ctx.rnd.randrange(ctx.x0, ctx.x1)
        y = ctx.rnd.randrange(ctx.y0, ctx.y1)
        ctx.d.point((x, y), fill=shade(b, 1.7))
    _bevel(ctx, 1.55, 0.60)


def _paint_ice(ctx: Ctx) -> None:
    """氷: 淡い青 + 白い筋 + ひび。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.22), shade(b, 0.80))
    for _ in range(max(1, int(ctx.w * ctx.h * 0.035))):
        x = ctx.rnd.randrange(ctx.x0, ctx.x1)
        y = ctx.rnd.randrange(ctx.y0, ctx.y1)
        ctx.d.line([(x, y), (min(ctx.x1 - 1, x + 3), min(ctx.y1 - 1, y + 3))],
                   fill=shade(b, 1.36))
    _crack(ctx, shade(b, 1.5), n=2)
    _bevel(ctx, 1.45, 0.72)


def _paint_bone(ctx: Ctx) -> None:
    """骨: 象牙色 + 縦の筋 + ひび。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.08), shade(b, 0.76))
    if ctx.h >= ctx.w:
        _grain_v(ctx, [shade(b, 0.92), shade(b, 1.08)], density=0.4)
    else:
        for _ in range(max(1, int(ctx.h * 0.9))):
            y = ctx.rnd.randrange(ctx.y0, ctx.y1)
            ctx.d.line([(ctx.x0 + 1, y), (ctx.x1 - 2, y)], fill=shade(b, 0.92))
    _crack(ctx, shade(b, 0.64), n=1)
    _bevel(ctx, 1.20, 0.70)


def _paint_stone(ctx: Ctx) -> None:
    """石: まだら + ひび + 欠け。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.08), shade(b, 0.80))
    core.noise(ctx.img, ctx.rect, ctx.rnd, [shade(b, 0.82), shade(b, 1.14), shade(b, 0.96)],
               density=0.22)
    _crack(ctx, shade(b, 0.58), n=2)
    for _ in range(max(1, int(ctx.w * ctx.h * 0.006))):
        x = ctx.rnd.randrange(ctx.x0, ctx.x1)
        y = ctx.rnd.randrange(ctx.y0, ctx.y1)
        ctx.d.point((x, y), fill=shade(b, 0.66))
    _bevel(ctx, 1.22, 0.62)


def _paint_obsidian(ctx: Ctx) -> None:
    """黒曜石: 漆黒 + 紫の照り返し + 鋭いハイライト。"""
    b = ctx.base
    glow = ctx.mat.accent or (140, 100, 220, 255)
    _fill(ctx, shade(b, 1.55), shade(b, 0.70))
    # 斜めの反射帯
    for i in range(0, ctx.h, max(3, ctx.h // 6)):
        off = int(i * 0.6)
        a = ctx.x0 + ctx.w // 4 + off
        if a < ctx.x1:
            ctx.d.point((min(ctx.x1 - 1, a), ctx.y0 + i), fill=mix(b, glow, 0.40))
            if a + 1 < ctx.x1:
                ctx.d.point((a + 1, ctx.y0 + i), fill=mix(b, glow, 0.18))
    for _ in range(max(1, int(ctx.w * ctx.h * 0.015))):
        x = ctx.rnd.randrange(ctx.x0, ctx.x1)
        y = ctx.rnd.randrange(ctx.y0, ctx.y1)
        ctx.d.point((x, y), fill=mix(b, glow, ctx.rnd.choice([0.55, 0.25])))
    _bevel(ctx, 1.7, 0.55)


def _paint_paper(ctx: Ctx) -> None:
    b = ctx.base
    _fill(ctx, shade(b, 1.05), shade(b, 0.84))
    core.noise(ctx.img, ctx.rect, ctx.rnd, [shade(b, 0.94), shade(b, 1.03)], density=0.12)
    if ctx.h > 6:
        for i in (2, ctx.h - 3):
            ctx.d.line([(ctx.x0 + 1, ctx.y0 + i), (ctx.x1 - 2, ctx.y0 + i)], fill=shade(b, 0.86))


def _paint_liquid(ctx: Ctx) -> None:
    b = ctx.base
    _fill(ctx, shade(b, 1.18), shade(b, 0.74))
    if ctx.h > 4:
        ctx.d.line([(ctx.x0, ctx.y0 + 1), (ctx.x1 - 1, ctx.y0 + 1)], fill=shade(b, 1.55))
    if ctx.w > 3 and ctx.h > 3:
        ctx.d.ellipse([ctx.x0 + 1, ctx.y0 + 1, ctx.x0 + 3, ctx.y0 + 3],
                      fill=(255, 255, 255, 200))
        ctx.d.ellipse([ctx.x1 - 4, ctx.y1 - 4, ctx.x1 - 2, ctx.y1 - 2],
                      fill=shade(b, 1.35))


def _paint_scale(ctx: Ctx) -> None:
    """鱗: 重なる鱗 + 上端のハイライト。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.10), shade(b, 0.72))
    step = max(2, min(ctx.w, ctx.h) // 2)
    for row, y in enumerate(range(ctx.y0, ctx.y1, step)):
        off = (row % 2) * (step // 2)
        for x in range(ctx.x0 - step, ctx.x1 + step, step):
            ctx.d.ellipse([x + off, y, x + off + step - 1, y + step - 1],
                          outline=shade(b, 0.60))
            ctx.d.line([(x + off, y), (x + off + step - 1, y)], fill=shade(b, 1.28))
            ctx.d.point((x + off, y), fill=shade(b, 1.45))
    _bevel(ctx, 1.16, 0.66)


def _paint_rune(ctx: Ctx) -> None:
    """ルーン刻印: 暗い地 + 発光する紋様。"""
    b = ctx.base
    glow = ctx.mat.accent or (150, 110, 255, 255)
    _fill(ctx, shade(b, 1.16), shade(b, 0.62))
    x0, y0, x1, y1 = ctx.rect
    # 縦の刻み線
    if ctx.h > ctx.w:
        ctx.d.line([(x0 + ctx.w // 2, y0 + 1), (x0 + ctx.w // 2, y1 - 2)], fill=shade(b, 0.7))
    rnd = _rnd(ctx.rnd.random() and 0 or 7)
    for _ in range(max(1, int(ctx.w * ctx.h * 0.05))):
        x = rnd.randrange(x0, x1)
        y = rnd.randrange(y0, y1)
        ctx.d.point((x, y), fill=glow)
    # 紋様 (ジグザグ)
    if ctx.w >= 3 and ctx.h >= 5:
        cx = x0 + ctx.w // 2
        for i in range(y0 + 2, y1 - 2, 3):
            ctx.d.point((cx, i), fill=shade(glow, 1.25))
            xx = cx - 1 if (i // 3) % 2 else cx + 1
            if x0 <= xx < x1:
                ctx.d.point((xx, min(y1 - 1, i + 1)), fill=glow)
    _bevel(ctx, 1.3, 0.60)


def _paint_flat(ctx: Ctx) -> None:
    b = ctx.base
    _fill(ctx, shade(b, 1.08), shade(b, 0.84))
    _bevel(ctx, 1.22, 0.74)


PAINTERS = {
    "metal": _paint_metal,
    "blade": _paint_blade,
    "blade_edge": _paint_blade_edge,
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

# 旧名の別名 (既存の形状定義からの参照を維持)
ALIASES = {
    "metal_dark": "metal",
}


def paint_face(img: Image.Image, box, mat: Material, face: str, seed: int,
               size: tuple = (1.0, 1.0, 1.0)) -> None:
    """1 面を mat の質感で塗る。box は画像内のピクセル矩形。"""
    x0, y0, x1, y1 = box
    if x1 <= x0 or y1 <= y0:
        return
    kind = mat.kind
    tags = mat.tags or ()
    if "blade" in tags:
        kind = "blade"
    painter = PAINTERS.get(kind, _paint_flat)
    u_axis, v_axis = FACE_AXES.get(face, ("x", "y"))
    ctx = Ctx(
        img=img, d=ImageDraw.Draw(img), rect=box,
        x0=x0, y0=y0, x1=x1, y1=y1, w=x1 - x0, h=y1 - y0,
        face=face, base=mat.toned(face), mat=mat, rnd=_rnd(seed),
        size=size, u_axis=u_axis, v_axis=v_axis,
    )
    painter(ctx)
