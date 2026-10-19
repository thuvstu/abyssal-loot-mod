"""色操作と描画の共通ユーティリティ。

すべて RGBA (0-255) タプルを前提とする。
"""
from __future__ import annotations

import math
import random

from PIL import Image, ImageDraw, ImageFilter

RGBA = tuple[int, int, int, int]


# ---------------------------------------------------------------- 色 Utilities

def clamp(v: float, lo: float = 0.0, hi: float = 255.0) -> int:
    return int(max(lo, min(hi, round(v))))


def shade(c, k: float) -> RGBA:
    """明度を係数倍 (アルファ維持)。"""
    return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), c[3] if len(c) > 3 else 255)


def mix(a, b, t: float) -> RGBA:
    """2 色を線形補間。"""
    t = max(0.0, min(1.0, t))
    return (
        clamp(a[0] + (b[0] - a[0]) * t),
        clamp(a[1] + (b[1] - a[1]) * t),
        clamp(a[2] + (b[2] - a[2]) * t),
        clamp((a[3] if len(a) > 3 else 255) + ((b[3] if len(b) > 3 else 255) - (a[3] if len(a) > 3 else 255)) * t),
    )


def hexcolor(v: int) -> RGBA:
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)


def to_hex(c) -> int:
    return (c[0] << 16) | (c[1] << 8) | c[2]


def hsl(h: float, s: float, l: float, a: int = 255) -> RGBA:
    h = h % 360.0
    c = (1 - abs(2 * l - 1)) * s
    x = c * (1 - abs((h / 60.0) % 2 - 1))
    m = l - c / 2
    if h < 60:
        rgb = (c, x, 0)
    elif h < 120:
        rgb = (x, c, 0)
    elif h < 180:
        rgb = (0, c, x)
    elif h < 240:
        rgb = (0, x, c)
    elif h < 300:
        rgb = (x, 0, c)
    else:
        rgb = (c, 0, x)
    return (clamp((rgb[0] + m) * 255), clamp((rgb[1] + m) * 255), clamp((rgb[2] + m) * 255), a)


def luminance(c) -> float:
    return (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / 255.0


def readable_on(bg) -> RGBA:
    """背景に対して読みやすい前景色 (白 / 黒)。"""
    return (255, 255, 255, 255) if luminance(bg) < 0.5 else (18, 16, 24, 255)


# ---------------------------------------------------------------- 描画 Utilities

def new_canvas(w: int, h: int, bg=(0, 0, 0, 0)) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGBA", (w, h), bg)
    return img, ImageDraw.Draw(img)


def vgradient(img: Image.Image, box, top, bottom, steps: int | None = None) -> None:
    """矩形領域に垂直グラデーション。box=(x0,y0,x1,y1) (右端・下端は含まない)。"""
    x0, y0, x1, y1 = box
    h = max(1, y1 - y0)
    d = ImageDraw.Draw(img)
    for y in range(y0, y1):
        t = (y - y0) / max(1, h - 1)
        d.line([(x0, y), (x1 - 1, y)], fill=mix(top, bottom, t))


def hgradient(img: Image.Image, box, left, right) -> None:
    x0, y0, x1, y1 = box
    w = max(1, x1 - x0)
    d = ImageDraw.Draw(img)
    for x in range(x0, x1):
        t = (x - x0) / max(1, w - 1)
        d.line([(x, y0), (x, y1 - 1)], fill=mix(left, right, t))


def dithered_gradient(img: Image.Image, box, top, bottom, dither: bool = True) -> None:
    """ディザリング付きのグラデーション (ピクセルアート的な階調表現)。"""
    x0, y0, x1, y1 = box
    h = max(1, y1 - y0)
    d = ImageDraw.Draw(img)
    for y in range(y0, y1):
        t = (y - y0) / max(1, h - 1)
        c = mix(top, bottom, t)
        d.line([(x0, y), (x1 - 1, y)], fill=c)
        if dither and h > 3:
            if t < 0.5:
                alt = mix(top, bottom, min(1.0, t + 0.5 / h))
                for x in range(x0, x1):
                    if (x + y) % 4 == 0:
                        d.point((x, y), fill=alt)
            else:
                alt = mix(top, bottom, max(0.0, t - 0.5 / h))
                for x in range(x0, x1):
                    if (x + y) % 4 == 2:
                        d.point((x, y), fill=alt)


def noise(img: Image.Image, box, rnd: random.Random, colors, density: float = 0.12,
          size: int = 1) -> None:
    """矩形内に粒を撒く。"""
    x0, y0, x1, y1 = box
    if x1 <= x0 or y1 <= y0:
        return
    d = ImageDraw.Draw(img)
    n = int((x1 - x0) * (y1 - y0) * density)
    for _ in range(n):
        x = rnd.randrange(x0, x1)
        y = rnd.randrange(y0, y1)
        c = rnd.choice(colors)
        if size <= 1:
            d.point((x, y), fill=c)
        else:
            d.rectangle([x, y, x + size - 1, y + size - 1], fill=c)


def bevel(img: Image.Image, box, light=(255, 255, 255, 90), dark=(0, 0, 0, 110),
          width: int = 1, corners: bool = True) -> None:
    """矩形のベベル (上・左に光 / 下・右に影)。"""
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    w = max(0, width - 1)
    for i in range(width):
        a = 1.0 - i / max(1, width)
        lc = mix((0, 0, 0, 0), light, a)
        dc = mix((0, 0, 0, 0), dark, a)
        d.line([(x0 + i, y0 + i), (x1 - 1 - i, y0 + i)], fill=lc)
        d.line([(x0 + i, y0 + i), (x0 + i, y1 - 1 - i)], fill=lc)
        d.line([(x0 + i, y1 - 1 - i), (x1 - 1 - i, y1 - 1 - i)], fill=dc)
        d.line([(x1 - 1 - i, y0 + i), (x1 - 1 - i, y1 - 1 - i)], fill=dc)
    if corners and width > 0:
        d.point((x0, y0), fill=light)
        d.point((x1 - 1, y1 - 1), fill=dark)


def inner_shadow(img: Image.Image, box, color=(0, 0, 0, 120), width: int = 2) -> None:
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    for i in range(width):
        a = 1.0 - i / max(1, width)
        c = mix((0, 0, 0, 0), color, a)
        d.line([(x0 + i, y0 + i), (x1 - 1 - i, y0 + i)], fill=c)
        d.line([(x0 + i, y0 + i), (x0 + i, y1 - 1 - i)], fill=c)


def outline_poly(d: ImageDraw.ImageDraw, pts, fill, outline=(16, 12, 24, 255), width: int = 1) -> None:
    d.polygon(pts, fill=fill, outline=outline)
    if width > 1:
        d.line(list(pts) + [pts[0]], fill=outline, width=width)


def glow_blob(img: Image.Image, center, radius: int, color, strength: float = 1.0) -> None:
    """中心から減衰する発光。"""
    cx, cy = center
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    steps = max(3, radius)
    for i in range(steps, 0, -1):
        t = i / steps
        r = radius * t
        a = int(255 * strength * (1.0 - t) ** 1.6)
        if a <= 0:
            continue
        ld.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color[:3] + (a,))
    layer = layer.filter(ImageFilter.GaussianBlur(radius * 0.35))
    img.alpha_composite(layer)


def drop_shadow(img: Image.Image, offset=(1, 1), blur=1, alpha=90) -> Image.Image:
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    alpha_ch = img.getchannel("A").point(lambda v: int(v * alpha / 255))
    shadow.paste((0, 0, 0, 255), (0, 0), alpha_ch)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.alpha_composite(shadow, offset)
    out.alpha_composite(img)
    return out


def emboss(img: Image.Image, light=(255, 255, 255, 76), dark=(0, 0, 0, 96),
           side=(0, 0, 0, 40)) -> Image.Image:
    """アルファ輪郭に凹凸を付ける (上=光 / 下=影)。
    2D アイコン全体に一括で適用し、フラットなドット絵に凹凸を与える。
    """
    px = img.load()
    w, h = img.size
    out = img.copy()
    op = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                continue
            up = y > 0 and px[x, y - 1][3] > 0
            dn = y < h - 1 and px[x, y + 1][3] > 0
            lf = x > 0 and px[x - 1, y][3] > 0
            rt = x < w - 1 and px[x + 1, y][3] > 0
            c = px[x, y]
            if not up:
                c = mix(c, light, 1.0)
            elif not dn:
                c = mix(c, dark, 1.0)
            elif not lf or not rt:
                c = mix(c, side, 0.5)
            op[x, y] = c
    return out


def scanline_shade(img: Image.Image, box, k: float = 0.92) -> None:
    """横方向の走査線 (ピクセルアート的な質感を足す)。"""
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    for y in range(y0, y1):
        if (y - y0) % 2 == 1:
            d.line([(x0, y), (x1 - 1, y)], fill=(0, 0, 0, 26))


def ridge_light(img: Image.Image, box, color=(255, 255, 255, 120), width: int = 1) -> None:
    """斜めの稜線ハイライト。"""
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    d.line([(x0, y1 - 1), (x1 - 1, y0)], fill=color, width=width)


def poly_shadow(d: ImageDraw.ImageDraw, pts, color=(0, 0, 0, 60), dx: int = 1, dy: int = 1) -> None:
    d.polygon([(x + dx, y + dy) for x, y in pts], fill=color)


def smooth_poly(pts, closed: bool = True):
    """Chaikin 分割で多角形を丸める (3D 箱の角を丸めたように見せる)。"""
    if len(pts) < 3:
        return list(pts)
    out = []
    n = len(pts)
    for i in range(n):
        p0 = pts[(i - 1) % n] if closed else pts[max(0, i - 1)]
        p1 = pts[i]
        p2 = pts[(i + 1) % n] if closed else pts[min(n - 1, i + 1)]
        out.append((p0[0] * 0.75 + p1[0] * 0.25, p0[1] * 0.75 + p1[1] * 0.25))
        out.append((p1[0] * 0.25 + p2[0] * 0.75, p1[1] * 0.25 + p2[1] * 0.75))
    return out


def rotate_point(px: float, py: float, cx: float, cy: float, deg: float):
    a = math.radians(deg)
    dx, dy = px - cx, py - cy
    return (cx + dx * math.cos(a) - dy * math.sin(a),
            cy + dx * math.sin(a) + dy * math.cos(a))


def fit_box(pts):
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return (min(xs), min(ys), max(xs), max(ys))
