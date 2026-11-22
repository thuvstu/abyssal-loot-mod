"""形に沿う装飾 (MCAssetGen の SkyBlock テクスチャ生成器の「body-aware decorations」を移植・改良)。

固定座標に飾りを押すのではなく、先に絵の形を解析する (重心・主軸 (柄→切っ先)・軸に沿った幅・
柄と切っ先の位置・鍔の位置)。飾りはその形に結びつけるので、剣でも杖でも斧でも傾きが違っても沿う。

decorate(img, "halo|orbit:count=6|thorns", color=0x7ce8ff) のように | 区切りで重ねる。
- 後ろ (本体の下に描く): halo 光輪 / orbit 周回粒子 / wings 翼 / runering 魔法陣 / crown 王冠 / chain 鎖
- 前 (本体の上): thorns 棘 / crystal 結晶 / flame 炎 / lightning 放電 / roots 根蔦 / eyes 魔眼 / shard 破片
"""
from __future__ import annotations

import math
import random
from dataclasses import dataclass

from PIL import Image

BACK = ("halo", "orbit", "wings", "runering", "crown", "chain")
FRONT = ("thorns", "crystal", "flame", "lightning", "roots", "eyes", "shard")


@dataclass
class Body:
    w: int
    h: int
    mask: list
    cx: float
    cy: float
    ax: float  # 主軸 (柄→切っ先)
    ay: float
    nx: float  # 法線 (+ = 右下 = 影側)
    ny: float
    hilt: tuple
    tip: tuple
    length: float
    half: float  # 最大の半幅
    edges: list  # 輪郭の (x, y)


def analyze(img: Image.Image) -> Body:
    w, h = img.size
    a = img.getchannel("A").load()
    mask = [[a[x, y] >= 128 for x in range(w)] for y in range(h)]
    pts = [(x + 0.5, y + 0.5) for y in range(h) for x in range(w) if mask[y][x]]
    if len(pts) < 4:
        return Body(w, h, mask, w / 2, h / 2, 0.707, -0.707, 0.707, 0.707, (w * 0.2, h * 0.8), (w * 0.8, h * 0.2), w, 2, [])
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    cxx = sum((p[0] - cx) ** 2 for p in pts) / len(pts)
    cyy = sum((p[1] - cy) ** 2 for p in pts) / len(pts)
    cxy = sum((p[0] - cx) * (p[1] - cy) for p in pts) / len(pts)
    tr, det = cxx + cyy, cxx * cyy - cxy * cxy
    disc = math.sqrt(max(0.0, tr * tr / 4 - det))
    elong = math.sqrt((tr / 2 + disc) / max(0.05, tr / 2 - disc))
    if elong >= 1.8:
        ang = 0.5 * math.atan2(2 * cxy, cxx - cyy)
        ax, ay = math.cos(ang), math.sin(ang)
        if ax - ay < 0:  # 切っ先は右上 (MC の手持ちの向き)
            ax, ay = -ax, -ay
    else:
        ax, ay = 0.0, -1.0
    nx, ny = -ay, ax
    ss = [((p[0] - cx) * ax + (p[1] - cy) * ay, (p[0] - cx) * nx + (p[1] - cy) * ny) for p in pts]
    smin, smax = min(s for s, _ in ss), max(s for s, _ in ss)
    half = max(abs(t) for _, t in ss)
    edges = [(x, y) for y in range(h) for x in range(w) if mask[y][x] and any(
        not (0 <= x + dx < w and 0 <= y + dy < h) or not mask[y + dy][x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    return Body(w, h, mask, cx, cy, ax, ay, nx, ny, (cx + ax * smin, cy + ay * smin), (cx + ax * smax, cy + ay * smax),
                smax - smin, max(1.0, half), edges)


def _put(px, w, h, x, y, c, a=255):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < w and 0 <= y < h:
        o = px[x, y]
        t = a / 255
        px[x, y] = tuple(int(o[i] * (1 - t) + c[i] * t) if o[3] else c[i] for i in range(3)) + (max(o[3], a),)


def _rgb(c: int):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def _lit(c, k):
    return tuple(min(255, int(v + (255 - v) * k)) if k > 0 else int(v * (1 + k)) for v in c)


def _draw(name: str, layer: Image.Image, b: Body, col, rnd: random.Random, count: int):
    px = layer.load()
    w, h, s = b.w, b.h, b.w / 64
    light, dark = _lit(col, 0.55), _lit(col, -0.45)
    if name == "halo":  # 切っ先の少し先に傾いた光輪
        cx, cy = b.tip[0] - b.ax * 2 * s, b.tip[1] - b.ay * 2 * s
        for i in range(90):
            t = i * math.pi / 45
            x = cx + math.cos(t) * 7 * s * abs(b.nx) + math.cos(t) * 3 * s
            y = cy + math.sin(t) * 3 * s
            _put(px, w, h, x, y, light if math.sin(t) < 0 else col, 230)
    elif name == "orbit":
        r = b.length * 0.42
        for k in range(count or 6):
            t = k * 2 * math.pi / (count or 6) + 0.3
            x, y = b.cx + math.cos(t) * r, b.cy + math.sin(t) * r * 0.45
            for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                _put(px, w, h, x + dx * s, y + dy * s, light if (dx, dy) == (0, 0) else col, 255 if (dx, dy) == (0, 0) else 150)
    elif name == "wings":  # 鍔の位置から左右へ羽を重ねる
        gx = b.hilt[0] + (b.tip[0] - b.hilt[0]) * 0.28
        gy = b.hilt[1] + (b.tip[1] - b.hilt[1]) * 0.28
        for side in (-1, 1):
            for f in range(5):
                ln = (12 - f * 1.6) * s
                ang = math.atan2(b.ay, b.ax) + side * (1.0 + f * 0.28)
                for i in range(int(ln)):
                    for wd in range(int(2 * s) + 1):
                        x = gx + math.cos(ang) * i + side * b.nx * wd
                        y = gy + math.sin(ang) * i + side * b.ny * wd
                        _put(px, w, h, x, y, _lit((236, 236, 250), -0.1 * f) if i < ln - 2 else light, 255)
    elif name == "runering":
        r = b.length * 0.48
        for i in range(160):
            t = i * math.pi / 80
            _put(px, w, h, b.cx + math.cos(t) * r, b.cy + math.sin(t) * r, col, 170)
            if i % 20 == 0:
                for d in range(3):
                    _put(px, w, h, b.cx + math.cos(t) * (r - d - 2), b.cy + math.sin(t) * (r - d - 2), light, 220)
    elif name == "crown":
        cx, cy = b.tip
        for k in range(5):
            x = cx + (k - 2) * 2.4 * s
            for i in range(int((3 + (k % 2) * 2) * s)):
                _put(px, w, h, x, cy - 3 * s - i, (240, 200, 70) if i else (255, 240, 160))
    elif name == "chain":
        x, y = b.hilt
        for i in range(int(14 * s)):
            yy = y + i
            xx = x - math.sin(i / (3 * s)) * 2 * s
            _put(px, w, h, xx, yy, (190, 190, 205) if (i // int(2 * s or 1)) % 2 else (110, 110, 125))
    elif name in ("thorns", "crystal", "shard"):
        cands = [e for e in b.edges if ((e[0] - b.hilt[0]) * b.ax + (e[1] - b.hilt[1]) * b.ay) > b.length * 0.3]
        for _ in range(count or (5 if name == "thorns" else 3)):
            if not cands:
                break
            ex, ey = rnd.choice(cands)
            sd = 1 if ((ex - b.cx) * b.nx + (ey - b.cy) * b.ny) > 0 else -1
            ln = (3 if name == "thorns" else 5) * s
            for i in range(int(ln) + 1):
                wd = (ln - i) / ln * (1.2 if name == "thorns" else 2.2) * s
                for j in range(-int(wd), int(wd) + 1):
                    x = ex + sd * b.nx * i + b.ax * (j + (i * 0.6 if name == "thorns" else 0))
                    y = ey + sd * b.ny * i + b.ay * (j + (i * 0.6 if name == "thorns" else 0))
                    c = dark if name == "thorns" else (light if j < 0 else col)
                    _put(px, w, h, x, y, c, 255)
    elif name == "flame":
        for _ in range(count or 18):
            t = rnd.random()
            x = b.hilt[0] + (b.tip[0] - b.hilt[0]) * (0.35 + 0.65 * t)
            y = b.hilt[1] + (b.tip[1] - b.hilt[1]) * (0.35 + 0.65 * t)
            off = (rnd.random() - 0.5) * b.half * 2.6
            for i in range(int(rnd.uniform(2, 5) * s)):
                _put(px, w, h, x + b.nx * off, y + b.ny * off - i, (255, 230, 120) if i < 2 else (255, 120, 40), 230 - i * 30)
    elif name == "lightning":
        x, y = b.hilt[0] + (b.tip[0] - b.hilt[0]) * 0.4, b.hilt[1] + (b.tip[1] - b.hilt[1]) * 0.4
        for _ in range(int(b.length * 0.7)):
            x += b.ax + (rnd.random() - 0.5) * 2.2
            y += b.ay + (rnd.random() - 0.5) * 2.2
            _put(px, w, h, x + b.nx * b.half * 1.6, y + b.ny * b.half * 1.6, (240, 250, 255), 255)
            _put(px, w, h, x + b.nx * b.half * 1.6 + 1, y + b.ny * b.half * 1.6, col, 160)
    elif name == "roots":
        for k in range(count or 3):
            x, y = b.hilt
            ang = math.atan2(-b.ay, -b.ax) + (k - 1) * 0.6
            for i in range(int(10 * s)):
                ang += (rnd.random() - 0.5) * 0.5
                x, y = x + math.cos(ang), y + math.sin(ang)
                _put(px, w, h, x, y, (90, 140, 60) if i % 4 else (150, 210, 90))
    elif name == "eyes":
        for _ in range(count or 2):
            t = rnd.uniform(0.4, 0.85)
            x = b.hilt[0] + (b.tip[0] - b.hilt[0]) * t
            y = b.hilt[1] + (b.tip[1] - b.hilt[1]) * t
            for dx in range(-int(2 * s), int(2 * s) + 1):
                for dy in range(-int(s), int(s) + 1):
                    _put(px, w, h, x + dx, y + dy, (250, 240, 230))
            _put(px, w, h, x, y, (200, 30, 60))
            _put(px, w, h, x + 1, y, (120, 10, 30))


def decorate(img: Image.Image, spec: str, color: int = 0x7ce8ff, seed: int = 1) -> Image.Image:
    img = img.convert("RGBA")
    b = analyze(img)
    rnd = random.Random(seed)
    back = Image.new("RGBA", img.size, (0, 0, 0, 0))
    front = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for part in filter(None, (p.strip() for p in spec.split("|"))):
        name, _, args = part.partition(":")
        if name not in BACK + FRONT:
            raise ValueError(f"未知の装飾 '{name}'。使えるもの: {', '.join(BACK + FRONT)}")
        kw = dict(kv.split("=", 1) for kv in args.split(",") if "=" in kv)
        col = _rgb(int(kw["color"], 16)) if "color" in kw else _rgb(color)
        _draw(name, back if name in BACK else front, b, col, rnd, int(kw.get("count", 0)))
    out = back
    out.alpha_composite(img)
    out.alpha_composite(front)
    return out
