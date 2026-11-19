"""テクスチャの加工エフェクト (MCAssetGen の texcraft の effects / pfEffects を Python で作り直したもの)。

アイコンやテクスチャに「重ねがけ」できる加工を集めた。書式は 1 行で:
    "gradmap:dark=1a0a2a,mid=8a5cff,light=fff4ff|edgewear:amount=0.4|sparkle:count=3"
`apply(img, spec)` で順に掛ける。どれも解像度に依存しない (太さ・大きさは 1/16 単位) ・乱数は種で決定的・
透明部分は触らない (glow / sparkle / halo / shockwave など外へ光を出すものを除く)。

元実装 (Canvas の ImageData を直接いじる 150 種) から、この mod のアイコン・素材・装備に効くものを選び、
質を上げて作り直した:
- 色: gradmap (3 色の階調へ写す) / posterize (段数を減らす) / palette (指定の色だけにする) / dither (Bayer で段差をならす) /
  iridescent (見る角度で色が変わる虹色) / pearl (真珠光沢) / chromatic (色収差) / tint
- 形・陰影: autoshade (左上光源の縁取り陰影) / bevel / emboss / outline (色付きの外縁) / vignette
- 質感: edgewear (縁の擦れ) / cracks (ひび。glow=1 で光る亀裂) / scratches / frost (霜) / veins (脈) /
  chainmail / leather / woodgrain / fabric / crystalline (面の明暗) / hatching (線描き)
- 光: glow (外への光暈) / sparkle (十字の煌めき) / halo (頭上の光輪) / shockwave (同心の波) / aurora / nebula
"""
from __future__ import annotations

import colorsys
import math
import random
import zlib

from PIL import Image

RGBA = tuple[int, int, int, int]


def _hx(v) -> tuple[int, int, int]:
    v = int(str(v).lstrip("#"), 16) if isinstance(v, str) else int(v)
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


def _c(v):
    return max(0, min(255, int(v + 0.5)))


def _lum(c) -> float:
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255


def _mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(_c(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _rnd(seed, *k) -> float:
    return (zlib.crc32(("|".join(map(str, (seed,) + k))).encode()) % 100000) / 100000


def _noise(x, y, seed, scale):
    gx, gy = x / scale, y / scale
    x0, y0 = math.floor(gx), math.floor(gy)
    fx, fy = gx - x0, gy - y0
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = _rnd(seed, x0, y0), _rnd(seed, x0 + 1, y0)
    c, d = _rnd(seed, x0, y0 + 1), _rnd(seed, x0 + 1, y0 + 1)
    return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy


def _solid(px, w, h, x, y):
    return 0 <= x < w and 0 <= y < h and px[x, y][3] >= 128


def _each(img, fn):
    """不透明画素ごとに fn(x, y, c) -> 新しい RGB (None なら据え置き)。"""
    out = img.copy()
    src, px = img.load(), out.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            c = src[x, y]
            if c[3] < 128:
                continue
            n = fn(x, y, c)
            if n is not None:
                px[x, y] = tuple(n[:3]) + (c[3],)
    return out


# ---------------------------------------------------------------- 色

def gradmap(img, dark="140c20", mid="8a5cff", light="fff4ff", amount=1.0, **_):
    d, m, l = _hx(dark), _hx(mid), _hx(light)

    def f(x, y, c):
        t = _lum(c)
        g = _mix(d, m, t / 0.5) if t < 0.5 else _mix(m, l, (t - 0.5) / 0.5)
        return _mix(c, g, float(amount))
    return _each(img, f)


def posterize(img, levels=5, **_):
    n = max(2, int(levels))
    return _each(img, lambda x, y, c: tuple(_c(round(v / 255 * (n - 1)) * 255 / (n - 1)) for v in c[:3]))


def palette(img, colors="1a1c2c,5d275d,b13e53,ef7d57,ffcd75,a7f070,38b764,257179,29366f,3b5dc9,41a6f6,73eff7,f4f4f4,94b0c2,566c86,333c57", **_):
    pal = [_hx(c) for c in str(colors).split(",")]
    return _each(img, lambda x, y, c: min(pal, key=lambda p: sum((p[i] - c[i]) ** 2 for i in range(3))))


_B4 = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))


def dither(img, strength=0.12, levels=6, **_):
    n = max(2, int(levels))
    s = float(strength) * 255

    def f(x, y, c):
        t = (_B4[y % 4][x % 4] / 16 - 0.5) * s
        return tuple(_c(round((v + t) / 255 * (n - 1)) * 255 / (n - 1)) for v in c[:3])
    return _each(img, f)


def iridescent(img, strength=0.45, phase=0.0, **_):
    w, h = img.size

    def f(x, y, c):
        hue = ((x / w + y / h) * 0.9 + float(phase) + _lum(c) * 0.5) % 1
        r, g, b = colorsys.hsv_to_rgb(hue, 0.55, 1.0)
        return _mix(c, (r * 255, g * 255, b * 255), float(strength) * (0.4 + 0.6 * _lum(c)))
    return _each(img, f)


def pearl(img, strength=0.5, **_):
    w, h = img.size

    def f(x, y, c):
        t = math.sin((x / w * 3 + y / h * 2) * math.pi) * 0.5 + 0.5
        sheen = _mix((236, 228, 255), (255, 244, 230), t)
        return _mix(c, sheen, float(strength) * (0.3 + 0.7 * _lum(c)))
    return _each(img, f)


def chromatic(img, shift=1, **_):
    k = max(1, int(float(shift) * img.width / 16))
    r, g, b, a = img.split()
    r = r.transform(img.size, Image.AFFINE, (1, 0, k, 0, 1, 0))
    b = b.transform(img.size, Image.AFFINE, (1, 0, -k, 0, 1, 0))
    return Image.merge("RGBA", (r, g, b, a))


def tint(img, color="ff6a2b", amount=0.35, **_):
    t = _hx(color)
    return _each(img, lambda x, y, c: _mix(c, t, float(amount)))


# ---------------------------------------------------------------- 形・陰影

def autoshade(img, strength=0.35, **_):
    src = img.load()
    w, h = img.size
    k = float(strength)

    def f(x, y, c):
        tl = not _solid(src, w, h, x - 1, y) or not _solid(src, w, h, x, y - 1)
        br = not _solid(src, w, h, x + 1, y) or not _solid(src, w, h, x, y + 1)
        if tl and not br:
            return _mix(c, (255, 255, 255), k * 0.6)
        if br and not tl:
            return _mix(c, (0, 0, 0), k)
        return None
    return _each(img, f)


def bevel(img, width=1, strength=0.35, **_):
    src = img.load()
    w, h = img.size
    n = max(1, int(float(width) * w / 16))

    def f(x, y, c):
        for d in range(1, n + 1):
            if not _solid(src, w, h, x - d, y - d):
                return _mix(c, (255, 255, 255), float(strength) * (1 - (d - 1) / n))
            if not _solid(src, w, h, x + d, y + d):
                return _mix(c, (0, 0, 0), float(strength) * (1 - (d - 1) / n))
        return None
    return _each(img, f)


def emboss(img, strength=0.5, **_):
    src = img.load()
    w, h = img.size

    def f(x, y, c):
        if not (_solid(src, w, h, x - 1, y - 1) and _solid(src, w, h, x + 1, y + 1)):
            return None
        d = _lum(src[x - 1, y - 1]) - _lum(src[x + 1, y + 1])
        return tuple(_c(v + d * 255 * float(strength)) for v in c[:3])
    return _each(img, f)


def outline(img, color="140c20", **_):
    out = img.copy()
    src, px = img.load(), out.load()
    w, h = img.size
    col = _hx(color) + (255,)
    for y in range(h):
        for x in range(w):
            if src[x, y][3] < 128 and any(_solid(src, w, h, x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = col
    return out


def vignette(img, strength=0.4, **_):
    w, h = img.size
    return _each(img, lambda x, y, c: _mix(c, (0, 0, 0), float(strength) * (math.hypot(x / w - 0.5, y / h - 0.5) * 2) ** 2))


# ---------------------------------------------------------------- 質感

def edgewear(img, amount=0.4, seed=1, **_):
    src = img.load()
    w, h = img.size

    def f(x, y, c):
        edge = any(not _solid(src, w, h, x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge and _rnd(seed, x, y) < float(amount):
            return _mix(c, (255, 255, 255), 0.35) if _lum(c) < 0.6 else _mix(c, (0, 0, 0), 0.3)
        return None
    return _each(img, f)


def cracks(img, count=3, glow=0, color="ff8a3c", seed=2, **_):
    out = img.copy()
    px = out.load()
    w, h = img.size
    rnd = random.Random(seed)
    solid = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] >= 128]
    if not solid:
        return out
    gc = _hx(color)
    for _k in range(int(count)):
        x, y = rnd.choice(solid)
        for _s in range(int(6 * w / 16)):
            if 0 <= x < w and 0 <= y < h and px[x, y][3] >= 128:
                c = px[x, y]
                px[x, y] = (_mix(c, gc, 0.9) if int(glow) else _mix(c, (0, 0, 0), 0.55)) + (c[3],)
            x += rnd.choice((-1, 0, 1))
            y += rnd.choice((0, 1, 1))
    return out


def scratches(img, count=5, seed=3, **_):
    out = img.copy()
    px = out.load()
    w, h = img.size
    rnd = random.Random(seed)
    for _k in range(int(count)):
        x, y = rnd.randrange(w), rnd.randrange(h)
        ln = rnd.randint(2, max(3, w // 5))
        for i in range(ln):
            xi, yi = x + i, y - i // 2
            if 0 <= xi < w and 0 <= yi < h and px[xi, yi][3] >= 128:
                c = px[xi, yi]
                px[xi, yi] = _mix(c, (255, 255, 255), 0.3) + (c[3],)
    return out


def frost(img, amount=0.35, seed=4, **_):
    src = img.load()
    w, h = img.size

    def f(x, y, c):
        top = not _solid(src, w, h, x, y - 1)
        n = _noise(x, y, seed, max(1, w / 8))
        k = float(amount) * (0.6 * n + (0.6 if top else 0))
        return _mix(c, (226, 246, 255), k)
    return _each(img, f)


def veins(img, color="ff4d6d", amount=0.6, seed=5, **_):
    w = img.width
    vc = _hx(color)

    def f(x, y, c):
        n = _noise(x, y, seed, max(1, w / 5))
        return _mix(c, vc, float(amount)) if abs(n - 0.5) < 0.04 else None
    return _each(img, f)


def chainmail(img, **_):
    s = max(2, img.width // 8)

    def f(x, y, c):
        lx, ly = (x + (y // s % 2) * s // 2) % s, y % s
        ring = abs(math.hypot(lx - s / 2 + 0.5, ly - s / 2 + 0.5) - s * 0.32) < s * 0.14
        return _mix(c, (255, 255, 255), 0.25) if ring else _mix(c, (0, 0, 0), 0.35)
    return _each(img, f)


def leather(img, seed=6, **_):
    w = img.width
    return _each(img, lambda x, y, c: _mix(c, (0, 0, 0), 0.25 * _noise(x, y, seed, max(1, w / 10))))


def woodgrain(img, seed=7, **_):
    w = img.width

    def f(x, y, c):
        g = math.sin((x / w * 6 + _noise(x, y, seed, max(1, w / 6)) * 3) * math.pi)
        return _mix(c, (0, 0, 0), 0.22) if g > 0.6 else None
    return _each(img, f)


def fabric(img, **_):
    return _each(img, lambda x, y, c: _mix(c, (0, 0, 0), 0.18) if (x + y) % 2 == 0 and (x // 2 + y // 2) % 2 else None)


def crystalline(img, seed=8, **_):
    w = img.width
    s = max(2, w // 5)

    def f(x, y, c):
        cell = _rnd(seed, x // s, y // s)
        up = (x % s) > (y % s)
        return _mix(c, (255, 255, 255), 0.25 * cell) if up else _mix(c, (0, 0, 0), 0.2 * cell)
    return _each(img, f)


def hatching(img, **_):
    return _each(img, lambda x, y, c: _mix(c, (0, 0, 0), 0.35) if _lum(c) < 0.45 and (x + y) % 3 == 0 else None)


# ---------------------------------------------------------------- 光

def glow(img, color="ffd23c", radius=1.5, strength=0.6, **_):
    out = img.copy()
    src, px = img.load(), out.load()
    w, h = img.size
    r = float(radius) * w / 16
    gc = _hx(color)
    ri = int(math.ceil(r))
    for y in range(h):
        for x in range(w):
            if src[x, y][3] >= 128:
                continue
            best = 0.0
            for dy in range(-ri, ri + 1):
                for dx in range(-ri, ri + 1):
                    if _solid(src, w, h, x + dx, y + dy):
                        d = math.hypot(dx, dy)
                        if d <= r:
                            best = max(best, 1 - d / (r + 0.5))
            if best > 0:
                px[x, y] = gc + (_c(255 * float(strength) * best),)
    return out


def sparkle(img, count=3, color="ffffff", seed=9, **_):
    out = img.copy()
    px = out.load()
    w, h = img.size
    rnd = random.Random(seed)
    sc = _hx(color)
    arm = max(1, w // 16)
    for _k in range(int(count)):
        cx, cy = rnd.randrange(w // 8, w - w // 8), rnd.randrange(h // 8, h - h // 8)
        for i in range(-arm * 2, arm * 2 + 1):
            for x, y in ((cx + i, cy), (cx, cy + i)):
                if 0 <= x < w and 0 <= y < h:
                    a = 1 - abs(i) / (arm * 2 + 1)
                    c = px[x, y]
                    px[x, y] = _mix(c[:3] if c[3] else sc, sc, a) + (max(c[3], _c(255 * a)),)
    return out


def halo(img, color="fff4c0", **_):
    out = img.copy()
    px = out.load()
    w, h = img.size
    cx, cy, rx, ry = w / 2, h * 0.12, w * 0.28, h * 0.07
    hc = _hx(color)
    for y in range(h):
        for x in range(w):
            d = abs(((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 - 1)
            if d < 0.35 and px[x, y][3] < 128:
                px[x, y] = hc + (_c(255 * (1 - d / 0.35)),)
    return out


def shockwave(img, color="7ce8ff", rings=2, **_):
    out = img.copy()
    px = out.load()
    w, h = img.size
    sc = _hx(color)
    for k in range(int(rings)):
        r = w * (0.3 + 0.16 * k)
        for y in range(h):
            for x in range(w):
                d = abs(math.hypot(x - w / 2, y - h / 2) - r)
                if d < w / 32 + 0.5 and px[x, y][3] < 128:
                    px[x, y] = sc + (_c(200 * (1 - k * 0.35)),)
    return out


def aurora(img, strength=0.45, seed=10, **_):
    w, h = img.size

    def f(x, y, c):
        band = math.sin((y / h * 5 + _noise(x, 0, seed, max(1, w / 4)) * 3) * math.pi) * 0.5 + 0.5
        col = _mix((60, 255, 180), (180, 90, 255), x / w)
        return _mix(c, col, float(strength) * band * (0.3 + 0.7 * _lum(c)))
    return _each(img, f)


def nebula(img, strength=0.5, seed=11, **_):
    w = img.width

    def f(x, y, c):
        n = _noise(x, y, seed, max(1, w / 4)) * 0.6 + _noise(x, y, seed + 3, max(1, w / 9)) * 0.4
        col = _mix((40, 20, 90), (255, 110, 200), n)
        out = _mix(c, col, float(strength) * n)
        return _mix(out, (255, 255, 255), 0.9) if _rnd(seed, x, y) > 0.985 else out
    return _each(img, f)


EFFECTS = {f.__name__: f for f in (gradmap, posterize, palette, dither, iridescent, pearl, chromatic, tint, autoshade,
                                     bevel, emboss, outline, vignette, edgewear, cracks, scratches, frost, veins,
                                     chainmail, leather, woodgrain, fabric, crystalline, hatching, glow, sparkle, halo,
                                     shockwave, aurora, nebula)}


def parse(spec: str) -> list[tuple[str, dict]]:
    out = []
    for part in filter(None, (p.strip() for p in str(spec).split("|"))):
        name, _, args = part.partition(":")
        if name not in EFFECTS:
            raise ValueError(f"未知のエフェクト '{name}'。使えるもの: {', '.join(EFFECTS)}")
        kw = dict(kv.split("=", 1) for kv in args.split(",") if "=" in kv)
        out.append((name, kw))
    return out


def apply(img: Image.Image, spec: str) -> Image.Image:
    """spec ("名前:引数=値,…|名前…") のエフェクトを順に掛ける。"""
    img = img.convert("RGBA")
    for name, kw in parse(spec):
        img = EFFECTS[name](img, **kw)
    return img
