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
from .core import RGBA, hi, mix, shade

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
    lt, dk = hi(b, light), shade(b, dark)
    if ctx.h >= 5:
        d.line([(x0, y0), (x1 - 1, y0)], fill=lt)
        d.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=dk)
    if ctx.w >= 5:
        d.line([(x0, y0), (x0, y1 - 1)], fill=hi(b, 1.16))
        d.line([(x1 - 1, y0), (x1 - 1, y1 - 1)], fill=shade(b, 0.74))
    if inner and ctx.w >= 8 and ctx.h >= 8:
        d.line([(x0 + 1, y0 + 1), (x1 - 2, y0 + 1)], fill=hi(b, 1.12))
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
    # 大きな面では一定間隔、小さな面では四隅のみ
    step = spacing if min(ctx.w, ctx.h) >= spacing * 2 else max(4, min(ctx.w, ctx.h) - 2)
    d = ctx.d
    for yy in range(ctx.y0 + inset, ctx.y1 - inset, step):
        for xx in range(ctx.x0 + inset, ctx.x1 - inset, step):
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
    tones = (0.0, 0.62, 0.82, 0.28)
    for i, (px, py) in enumerate(corners):
        px2, py2 = corners[(i + 1) % 4]
        d.polygon([(cx, cy), (px, py), (px2, py2)], fill=mix(bright, dark, tones[i]))
    # 稜線 (暗い線で面を分ける)
    for (px, py) in corners:
        d.line([(cx, cy), (px, py)], fill=shade(ctx.base, 0.50))
    if ctx.w >= 3 and ctx.h >= 3:
        d.point((cx, cy), fill=(255, 255, 255, 235))
        if cx + 1 < x1:
            d.point((cx + 1, cy), fill=shade(bright, 0.92))


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


def _scratches(ctx: Ctx, color: RGBA, n: int = 3, length: int = 5) -> None:
    """細かい擦り傷 (斜めの 1px 線)。"""
    if ctx.w < 3 or ctx.h < 3:
        return
    x0, y0, x1, y1 = ctx.rect
    for _ in range(max(1, n)):
        x = ctx.rnd.randrange(x0, x1)
        y = ctx.rnd.randrange(y0, y1)
        dx, dy = ctx.rnd.choice([(1, 1), (1, -1), (1, 0), (0, 1)])
        for _ in range(ctx.rnd.randint(2, max(2, length))):
            if x0 <= x < x1 and y0 <= y < y1:
                ctx.d.point((x, y), fill=color)
            x += dx
            y += dy


def _edge_wear(ctx: Ctx, light: RGBA, dark: RGBA, chance: float = 0.45) -> None:
    """縁の摩耗: 上・左の縁に擦れた光、下・右の縁に落ちた陰 (ランダムに途切れる)。"""
    x0, y0, x1, y1 = ctx.rect
    if ctx.w < 4 or ctx.h < 4:
        return
    for x in range(x0 + 1, x1 - 1):
        if ctx.rnd.random() < chance:
            ctx.d.point((x, y0), fill=light)
        if ctx.rnd.random() < chance * 0.75:
            ctx.d.point((x, y1 - 1), fill=dark)
    for y in range(y0 + 1, y1 - 1):
        if ctx.rnd.random() < chance * 0.8:
            ctx.d.point((x0, y), fill=light)
        if ctx.rnd.random() < chance * 0.6:
            ctx.d.point((x1 - 1, y), fill=dark)


def _speckle(ctx: Ctx, colors, density: float = 0.04) -> None:
    """細かい粒 (鉱物の粒・骨の気孔・砂)。"""
    n = max(1, int(ctx.w * ctx.h * density))
    for _ in range(n):
        x = ctx.rnd.randrange(ctx.x0, ctx.x1)
        y = ctx.rnd.randrange(ctx.y0, ctx.y1)
        ctx.d.point((x, y), fill=ctx.rnd.choice(colors))


def _seam(ctx: Ctx, light: RGBA, dark: RGBA, at: float = 0.62, vertical: bool = False) -> None:
    """板の継ぎ目 (影 + 光の 2px)。"""
    if vertical:
        x = ctx.x0 + int(ctx.w * at)
        if ctx.x0 + 1 < x < ctx.x1 - 2:
            ctx.d.line([(x, ctx.y0 + 1), (x, ctx.y1 - 2)], fill=dark)
            ctx.d.line([(x + 1, ctx.y0 + 1), (x + 1, ctx.y1 - 2)], fill=light)
    else:
        y = ctx.y0 + int(ctx.h * at)
        if ctx.y0 + 1 < y < ctx.y1 - 2:
            ctx.d.line([(ctx.x0 + 1, y), (ctx.x1 - 2, y)], fill=dark)
            ctx.d.line([(ctx.x0 + 1, y + 1), (ctx.x1 - 2, y + 1)], fill=light)


# ------------------------------------------------------------------ 材質の描画

def _paint_metal(ctx: Ctx) -> None:
    """金属板: 勾配 + ヘアライン + 照り返し + 継ぎ目 + 擦り傷 + 打痕 + 縁の摩耗 + 鋲。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.12), shade(b, 0.78))
    # ヘアライン (横に長い面は横、縦に長い面は縦)
    c = shade(b, 0.91)
    if ctx.long_u:
        for y in range(ctx.y0, ctx.y1):
            if (y - ctx.y0) % 3 == 1:
                ctx.d.line([(ctx.x0, y), (ctx.x1 - 1, y)], fill=c)
    else:
        for x in range(ctx.x0, ctx.x1):
            if (x - ctx.x0) % 3 == 1:
                ctx.d.line([(x, ctx.y0), (x, ctx.y1 - 1)], fill=c)
    # 照り返しの帯 (3 段で減衰)
    if ctx.w >= 4 and ctx.h >= 4:
        hy = ctx.y0 + max(1, int(ctx.h * 0.26))
        ctx.d.line([(ctx.x0 + 1, hy), (ctx.x1 - 2, hy)], fill=hi(b, 1.44))
        if ctx.h > 10:
            ctx.d.line([(ctx.x0 + 1, hy + 1), (ctx.x1 - 2, hy + 1)], fill=hi(b, 1.16))
            ctx.d.line([(ctx.x0 + 1, hy + 2), (ctx.x1 - 2, hy + 2)], fill=shade(b, 0.96))
    # 継ぎ目 (板の合わせ目)
    if ctx.w >= 9 and ctx.h >= 7:
        _seam(ctx, hi(b, 1.08), shade(b, 0.70), at=ctx.rnd.choice([0.34, 0.62]))
    # 擦り傷 (明るい傷 + 暗い傷)
    _scratches(ctx, hi(b, 1.30), n=max(1, int(ctx.w * ctx.h * 0.004)),
               length=max(3, min(ctx.w, ctx.h) // 2))
    _scratches(ctx, shade(b, 0.70), n=max(1, int(ctx.w * ctx.h * 0.002)), length=3)
    # 打痕 (2px の陰 + 1px の光)
    for _ in range(max(1, int(ctx.w * ctx.h * 0.008))):
        x = ctx.rnd.randrange(ctx.x0, ctx.x1)
        y = ctx.rnd.randrange(ctx.y0, ctx.y1)
        ctx.d.point((x, y), fill=shade(b, 0.72))
        if x + 1 < ctx.x1:
            ctx.d.point((x + 1, y), fill=shade(b, 0.86))
        if y + 1 < ctx.y1:
            ctx.d.point((x, y + 1), fill=hi(b, 1.16))
    _bevel(ctx, 1.34, 0.58)
    _edge_wear(ctx, hi(b, 1.40), shade(b, 0.64), 0.35)
    # 大きな板は縁を一段落として板金の合わせ目に見せる
    if ctx.w >= 22 and ctx.h >= 18:
        ctx.d.rectangle([ctx.x0 + 2, ctx.y0 + 2, ctx.x1 - 3, ctx.y1 - 3], outline=shade(b, 0.74))
        ctx.d.rectangle([ctx.x0 + 3, ctx.y0 + 3, ctx.x1 - 4, ctx.y1 - 4], outline=hi(b, 1.10))
    _rivets(ctx, hi(b, 1.30), spacing=8, inset=1)
    if ctx.mat.accent:
        ctx.d.line([(ctx.x0, ctx.y1 - 1), (ctx.x1 - 1, ctx.y1 - 1)], fill=ctx.mat.accent)


def _paint_blade(ctx: Ctx) -> None:
    """刃の平地: 鋼の勾配 + 樋 (fuller) + 焼き入れ波 (hamon) + 擦り傷。"""
    b = ctx.base
    x0, y0, x1, y1 = ctx.rect
    if ctx.h >= ctx.w:
        _fill(ctx, shade(b, 1.08), shade(b, 0.82))
        # 樋 (中央の溝) — 上側に光、下側に影
        if ctx.w >= 5:
            m = (x0 + x1) // 2
            ctx.d.line([(m - 1, y0 + 1), (m - 1, y1 - 2)], fill=hi(b, 1.28))
            ctx.d.line([(m, y0 + 1), (m, y1 - 2)], fill=shade(b, 0.76))
            if ctx.w >= 9:
                ctx.d.line([(m - 2, y0 + 1), (m - 2, y1 - 2)], fill=hi(b, 1.12))
                ctx.d.line([(m + 1, y0 + 1), (m + 1, y1 - 2)], fill=shade(b, 0.88))
        # 焼き入れの波 (刃先側で波打つ淡い帯 + その外の暗い縁)
        if ctx.w >= 6 and ctx.h >= 10:
            ex = x1 - 2
            for yy in range(y0 + 1, y1 - 1):
                xx = ex - (1 if (yy // 3) % 2 == 0 else 0)
                if x0 <= xx < x1:
                    ctx.d.point((xx, yy), fill=hi(b, 1.30))
                if xx + 1 < x1:
                    ctx.d.point((xx + 1, yy), fill=shade(b, 0.88))
        # 上下端の稜線 (大きな面のみ)
        if ctx.w >= 6 and ctx.h >= 8:
            ctx.d.line([(x0, y0), (x1 - 1, y0)], fill=hi(b, 1.14))
            ctx.d.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=shade(b, 0.76))
        # 地鉄の微細な波紋と傷
        if ctx.h >= 8:
            _scratches(ctx, shade(b, 0.92), n=max(1, ctx.h // 6), length=3)
            _scratches(ctx, hi(b, 1.30), n=max(1, ctx.h // 8), length=4)
        if ctx.mat.accent and ctx.h > 10:
            for i in range(3, min(9, ctx.h - 3), 3):
                ctx.d.point(((x0 + x1) // 2, y0 + i), fill=ctx.mat.accent)
    else:
        _fill(ctx, shade(b, 1.14), shade(b, 0.78))
        _bevel(ctx, 1.26, 0.72)
        _scratches(ctx, hi(b, 1.34), n=max(1, ctx.w // 4), length=3)


def _paint_blade_edge(ctx: Ctx) -> None:
    """刃先 (薄い側面): 磨かれた明るい鋼 + 芯線のきらめき。白飛びしないよう抑える。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.06), shade(b, 0.88))
    x0, y0, x1, y1 = ctx.rect
    if 2 <= ctx.w <= 5:
        cx = (x0 + x1) // 2
        ctx.d.line([(cx, y0), (cx, y1 - 1)], fill=hi(b, 1.24))
        ctx.d.line([(x0, y0), (x0, y1 - 1)], fill=shade(b, 0.90))
        ctx.d.line([(x1 - 1, y0), (x1 - 1, y1 - 1)], fill=shade(b, 0.86))
        # 芯線のきらめき (等間隔のハイライト)
        if ctx.h >= 6:
            for yy in range(y0 + 1, y1 - 1, 3):
                ctx.d.point((cx, yy), fill=hi(b, 1.42))
    elif ctx.w > 5:
        _bevel(ctx, 1.20, 0.80)
        _scratches(ctx, hi(b, 1.38), n=max(1, ctx.h // 5), length=4)


def _paint_wood(ctx: Ctx) -> None:
    """木: 円筒シェーディング + 木目 + 節目 + 木口の年輪。"""
    b = ctx.base
    # 木口 (上下面) は年輪
    if ctx.face in (UP, DOWN) and ctx.w >= 5 and ctx.h >= 5:
        _fill(ctx, shade(b, 1.20), shade(b, 0.76))
        cx, cy = ctx.x0 + ctx.w // 2, ctx.y0 + ctx.h // 2
        for r in range(1, max(ctx.w, ctx.h) // 2 + 1, 2):
            col = shade(b, 0.82 if (r // 2) % 2 else 1.14)
            for a in range(0, 360, 20):
                x = cx + int(math.cos(math.radians(a)) * r)
                y = cy + int(math.sin(math.radians(a)) * r)
                if ctx.x0 <= x < ctx.x1 and ctx.y0 <= y < ctx.y1:
                    ctx.d.point((x, y), fill=col)
        ctx.d.point((cx, cy), fill=shade(b, 0.60))
        _bevel(ctx, 1.20, 0.66)
        return
    if ctx.is_pole():
        _fill(ctx, shade(b, 1.0), shade(b, 1.0))
        _cyl_shade(ctx, 1.24, 0.64, 0.34)
        _grain_v(ctx, [shade(b, 0.80), shade(b, 1.16), shade(b, 0.92)], density=0.42)
        # 節目 (同心円 + 周囲の盛り上がり)
        if ctx.w >= 4 and ctx.h > 8 and ctx.rnd.random() < 0.7:
            ky = ctx.rnd.randrange(ctx.y0 + 2, max(ctx.y0 + 3, ctx.y1 - 3))
            kx = ctx.x0 + ctx.w // 2
            ctx.d.ellipse([kx - 1, ky - 1, kx + 1, ky + 1], outline=shade(b, 0.52))
            ctx.d.point((kx, ky), fill=shade(b, 0.58))
            if kx + 2 < ctx.x1:
                ctx.d.point((kx + 2, ky - 1), fill=shade(b, 1.22))
            if kx - 2 >= ctx.x0:
                ctx.d.point((kx - 2, ky + 1), fill=shade(b, 0.72))
    elif ctx.h > 8:
        _fill(ctx, shade(b, 1.18), shade(b, 0.72))
        for _ in range(max(1, int(ctx.w * 0.7))):
            x = ctx.rnd.randrange(ctx.x0, ctx.x1)
            c = shade(b, ctx.rnd.choice([0.80, 0.90, 1.12]))
            ctx.d.line([(x, ctx.y0 + 1), (x, ctx.y1 - 2)], fill=c)
        _scratches(ctx, shade(b, 0.86), n=max(1, ctx.w // 3), length=3)
        _bevel(ctx, 1.24, 0.66)
    else:
        _fill(ctx, shade(b, 1.16), shade(b, 0.74))
        for _ in range(max(1, int(ctx.h * 0.9))):
            y = ctx.rnd.randrange(ctx.y0, ctx.y1)
            c = shade(b, ctx.rnd.choice([0.80, 0.90, 1.12]))
            ctx.d.line([(ctx.x0 + 1, y), (ctx.x1 - 2, y)], fill=c)
        _bevel(ctx, 1.22, 0.68)


def _paint_leather(ctx: Ctx) -> None:
    """革: 粒 + 折り目 + 中央の擦れ + 縫い目 + 縁。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.16), shade(b, 0.74))
    core.noise(ctx.img, ctx.rect, ctx.rnd, [shade(b, 0.84), shade(b, 1.14), shade(b, 0.98)],
               density=0.22)
    # 折り目 (斜めの陰 + すぐ下に光)
    if ctx.w >= 5 and ctx.h >= 5:
        for _ in range(max(1, ctx.w // 5)):
            sx = ctx.rnd.randrange(ctx.x0, max(ctx.x0 + 1, ctx.x1 - 3))
            sy = ctx.rnd.randrange(ctx.y0, ctx.y1)
            for i in range(min(4, ctx.x1 - sx)):
                ctx.d.point((sx + i, sy), fill=shade(b, 0.70))
                if sy + 1 < ctx.y1:
                    ctx.d.point((sx + i, sy + 1), fill=hi(b, 1.16))
    # 使い込まれた中央の艶
    if ctx.w >= 7 and ctx.h >= 7:
        wx0, wy0 = ctx.x0 + ctx.w // 4, ctx.y0 + ctx.h // 4
        wx1, wy1 = ctx.x1 - ctx.w // 4, ctx.y1 - ctx.h // 4
        if wx1 - wx0 >= 2 and wy1 - wy0 >= 2:
            core.vgradient(ctx.img, (wx0, wy0, wx1, wy1), shade(b, 1.16), shade(b, 0.96))
    if ctx.h > 5:
        ctx.d.line([(ctx.x0, ctx.y0 + 2), (ctx.x1 - 1, ctx.y0 + 2)], fill=shade(b, 0.64))
        ctx.d.line([(ctx.x0, ctx.y1 - 3), (ctx.x1 - 1, ctx.y1 - 3)], fill=shade(b, 0.68))
    _stitch(ctx, hi(b, 1.32), inset=0, step=2)
    _bevel(ctx, 1.26, 0.68)


def _paint_cloth(ctx: Ctx) -> None:
    """布: 綾織り + 横糸 + しわ + 縫い目 + 縁。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.13), shade(b, 0.77))
    # 綾織り (斜めに走る 2x2 の織り目)
    if ctx.w >= 4 and ctx.h >= 4:
        for y in range(ctx.y0, ctx.y1):
            off = (y - ctx.y0) % 4
            for x in range(ctx.x0 + off, ctx.x1, 4):
                ctx.d.point((x, y), fill=shade(b, 1.09 if off < 2 else 0.92))
    # 横糸 (薄い段)
    for y in range(ctx.y0, ctx.y1):
        if (y - ctx.y0) % 3 == 0:
            ctx.d.line([(ctx.x0, y), (ctx.x1 - 1, y)], fill=shade(b, 0.95))
    # しわ (斜めの陰 + すぐ横に光)
    for _ in range(max(1, ctx.w // 4)):
        sx = ctx.rnd.randrange(ctx.x0, ctx.x1)
        sy = ctx.rnd.randrange(ctx.y0, ctx.y1)
        for i in range(min(ctx.h, 7)):
            xx, yy = sx + i, sy + i
            if xx < ctx.x1 and yy < ctx.y1:
                ctx.d.point((xx, yy), fill=shade(b, 0.78))
                if xx + 1 < ctx.x1:
                    ctx.d.point((xx + 1, yy), fill=shade(b, 1.14))
    if ctx.mat.accent:
        ctx.d.line([(ctx.x0, ctx.y1 - 1), (ctx.x1 - 1, ctx.y1 - 1)], fill=ctx.mat.accent)
        _stitch(ctx, ctx.mat.accent, inset=1, step=3)
    _bevel(ctx, 1.20, 0.72)


def _paint_crystal(ctx: Ctx) -> None:
    """結晶: ファセット + 内部発光 + 稜線 + リムライト + きらめき。"""
    b = ctx.base
    _fill(ctx, hi(b, 1.30), shade(b, 0.66))
    _facet(ctx, hi(b, 1.45), shade(b, 0.50))
    # 内部の芯
    if ctx.w >= 3 and ctx.h >= 3:
        cx0 = ctx.x0 + ctx.w // 3
        cy0 = ctx.y0 + ctx.h // 3
        cx1 = max(cx0 + 1, ctx.x0 + (ctx.w * 2) // 3)
        cy1 = max(cy0 + 1, ctx.y0 + (ctx.h * 2) // 3)
        core.vgradient(ctx.img, (cx0, cy0, cx1, cy1), hi(b, 1.6), shade(b, 1.0))
    # 稜線 (中心から対角へ)
    if ctx.w >= 4 and ctx.h >= 4:
        cx, cy = ctx.x0 + ctx.w // 2, ctx.y0 + ctx.h // 2
        ctx.d.line([(ctx.x0, ctx.y0 + 1), (cx, cy)], fill=hi(b, 1.52))
        ctx.d.line([(ctx.x0 + 1, ctx.y1 - 1), (cx, cy)], fill=shade(b, 0.78))
    # きらめき (十字)
    for _ in range(max(1, int(ctx.w * ctx.h * 0.012))):
        x = ctx.rnd.randrange(ctx.x0, ctx.x1)
        y = ctx.rnd.randrange(ctx.y0, ctx.y1)
        ctx.d.point((x, y), fill=(255, 255, 255, 235))
        if x + 1 < ctx.x1:
            ctx.d.point((x + 1, y), fill=hi(b, 1.5))
        if y + 1 < ctx.y1:
            ctx.d.point((x, y + 1), fill=hi(b, 1.5))
    _bevel(ctx, 1.55, 0.60)
    # リムライト (上・左を強く)
    if ctx.w >= 3 and ctx.h >= 3:
        ctx.d.line([(ctx.x0, ctx.y0), (ctx.x1 - 1, ctx.y0)], fill=hi(b, 1.62))
        ctx.d.line([(ctx.x0, ctx.y0), (ctx.x0, ctx.y1 - 1)], fill=hi(b, 1.48))


def _paint_ice(ctx: Ctx) -> None:
    """氷: 淡い青 + 白い筋 + ひび + 霜の粒。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.22), shade(b, 0.80))
    for _ in range(max(1, int(ctx.w * ctx.h * 0.035))):
        x = ctx.rnd.randrange(ctx.x0, ctx.x1)
        y = ctx.rnd.randrange(ctx.y0, ctx.y1)
        ctx.d.line([(x, y), (min(ctx.x1 - 1, x + 3), min(ctx.y1 - 1, y + 3))],
                   fill=hi(b, 1.36))
    _crack(ctx, hi(b, 1.5), n=2)
    _speckle(ctx, [hi(b, 1.44), (255, 255, 255, 190)], density=0.02)
    _bevel(ctx, 1.45, 0.72)


def _paint_bone(ctx: Ctx) -> None:
    """骨: 象牙色 + 縦の筋 + 気孔 + ひび + 上端の明るみ。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.10), shade(b, 0.72))
    if ctx.h >= ctx.w:
        _grain_v(ctx, [shade(b, 0.86), hi(b, 1.10), shade(b, 0.94)], density=0.62)
    else:
        for _ in range(max(1, int(ctx.h * 1.1))):
            y = ctx.rnd.randrange(ctx.y0, ctx.y1)
            ctx.d.line([(ctx.x0 + 1, y), (ctx.x1 - 2, y)], fill=shade(b, 0.92))
    # 気孔
    _speckle(ctx, [shade(b, 0.80), shade(b, 0.86)], density=0.03)
    if ctx.h >= 6:
        ctx.d.line([(ctx.x0 + 1, ctx.y0 + 1), (ctx.x1 - 2, ctx.y0 + 1)], fill=hi(b, 1.26))
    _crack(ctx, shade(b, 0.62), n=1)
    _bevel(ctx, 1.22, 0.68)


def _paint_stone(ctx: Ctx) -> None:
    """石: まだら + 粒 + 小石 + ひび + 風化した縁。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.10), shade(b, 0.78))
    core.noise(ctx.img, ctx.rect, ctx.rnd, [shade(b, 0.84), shade(b, 1.14), shade(b, 0.96)],
               density=0.26)
    _speckle(ctx, [shade(b, 1.24), shade(b, 0.68), shade(b, 0.90)], density=0.03)
    # 小石 (明 + 影の 3px)
    if ctx.w >= 5 and ctx.h >= 5:
        for _ in range(max(1, int(ctx.w * ctx.h * 0.004))):
            x = ctx.rnd.randrange(ctx.x0 + 1, ctx.x1 - 1)
            y = ctx.rnd.randrange(ctx.y0 + 1, ctx.y1 - 1)
            ctx.d.point((x, y), fill=hi(b, 1.22))
            ctx.d.point((x + 1, y), fill=shade(b, 0.74))
            ctx.d.point((x, y + 1), fill=shade(b, 0.84))
    _crack(ctx, shade(b, 0.56), n=2)
    _bevel(ctx, 1.24, 0.60)
    _edge_wear(ctx, hi(b, 1.28), shade(b, 0.66), 0.30)


def _paint_obsidian(ctx: Ctx) -> None:
    """黒曜石: 漆黒 + 紫の照り返し + 鋭いハイライト + 光るひび。"""
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
    # 鋭い映り込み (細い斜線 + 芯)
    if ctx.w >= 5 and ctx.h >= 5:
        sx = ctx.x0 + ctx.w // 3
        for i in range(ctx.y0 + 1, ctx.y1 - 1):
            xx = sx + (i - ctx.y0) // 2
            if xx < ctx.x1:
                ctx.d.point((xx, i), fill=mix(b, glow, 0.34))
        cy = ctx.y0 + ctx.h // 3
        cx = sx + (cy - ctx.y0) // 2
        if cx < ctx.x1:
            ctx.d.point((cx, cy), fill=mix(glow, (255, 255, 255, 255), 0.55))
    # 光を受けた細かいひび
    _scratches(ctx, mix(b, glow, 0.55), n=max(1, ctx.w // 4), length=3)
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
    """鱗: 重なる鱗 + 縁の影 + 鱗ごとの照り (面外へはみ出さない)。"""
    b = ctx.base
    _fill(ctx, shade(b, 1.10), shade(b, 0.72))
    step = max(2, min(ctx.w, ctx.h) // 2)
    for row, y in enumerate(range(ctx.y0, ctx.y1, step)):
        off = (row % 2) * (step // 2)
        for x in range(ctx.x0 - step, ctx.x1 + step, step):
            # 鱗の縁 (円弧を点で描き、面内のみ)
            ccx = x + off + step // 2
            ccy = y + step // 2
            for a in range(0, 360, 30):
                px = ccx + int(math.cos(math.radians(a)) * (step / 2))
                py = ccy + int(math.sin(math.radians(a)) * (step / 2))
                if ctx.x0 <= px < ctx.x1 and ctx.y0 <= py < ctx.y1:
                    ctx.d.point((px, py), fill=shade(b, 0.58))
            # 上端の照り (面内にクリップ)
            lx0 = max(ctx.x0, x + off)
            lx1 = min(ctx.x1 - 1, x + off + step - 1)
            if lx0 <= lx1 and ctx.y0 <= y < ctx.y1:
                ctx.d.line([(lx0, y), (lx1, y)], fill=hi(b, 1.30))
                if ctx.x0 <= lx0 < ctx.x1:
                    ctx.d.point((lx0, y), fill=hi(b, 1.46))
            # 内側の照りと影
            if step >= 3:
                gl = (ccx - 1, ccy + 1)
                sh = (ccx + 1, ccy + 1)
                if ctx.x0 <= gl[0] < ctx.x1 and ctx.y0 <= gl[1] < ctx.y1:
                    ctx.d.point(gl, fill=hi(b, 1.16))
                if ctx.x0 <= sh[0] < ctx.x1 and ctx.y0 <= sh[1] < ctx.y1:
                    ctx.d.point(sh, fill=shade(b, 0.70))
    _bevel(ctx, 1.16, 0.66)


def _paint_rune(ctx: Ctx) -> None:
    """ルーン刻印: 暗い地 + 発光する輪 + 紋様。"""
    b = ctx.base
    glow = ctx.mat.accent or (150, 110, 255, 255)
    _fill(ctx, shade(b, 1.16), shade(b, 0.62))
    x0, y0, x1, y1 = ctx.rect
    rnd = _rnd(7)
    # 地の刻み (細かい点)
    for _ in range(max(1, int(ctx.w * ctx.h * 0.02))):
        x = rnd.randrange(x0, x1)
        y = rnd.randrange(y0, y1)
        ctx.d.point((x, y), fill=shade(b, 0.86))
    # 縦の刻み線
    if ctx.h > ctx.w:
        ctx.d.line([(x0 + ctx.w // 2, y0 + 1), (x0 + ctx.w // 2, y1 - 2)], fill=shade(b, 0.7))
    # 発光する輪 (大きい面のみ)
    if ctx.w >= 8 and ctx.h >= 8:
        cx, cy = x0 + ctx.w // 2, y0 + ctx.h // 2
        r = min(ctx.w, ctx.h) // 2 - 2
        for a in range(0, 360, 12):
            px = cx + int(math.cos(math.radians(a)) * r)
            py = cy + int(math.sin(math.radians(a)) * r)
            if x0 <= px < x1 and y0 <= py < y1:
                ctx.d.point((px, py), fill=glow)
        for a in (0, 90, 180, 270):
            px = cx + int(math.cos(math.radians(a)) * (r + 1))
            py = cy + int(math.sin(math.radians(a)) * (r + 1))
            if x0 <= px < x1 and y0 <= py < y1:
                ctx.d.point((px, py), fill=hi(glow, 1.30))
        ctx.d.point((cx, cy), fill=hi(glow, 1.40))
    # 紋様 (ジグザグ)
    if ctx.w >= 3 and ctx.h >= 5:
        cx = x0 + ctx.w // 2
        for i in range(y0 + 2, y1 - 2, 3):
            ctx.d.point((cx, i), fill=hi(glow, 1.25))
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
