"""素材ジェネレータ (MCAssetGen の minecraft-material-texture-generator を移植・改良したもの)。

形を「述語 (点が内側か)」で書くと、外周からの距離場で輪郭・光・影を自動で付けた
**役割文字のグリッド** になり、それをパレットで塗って 2D ドット絵にする。

役割文字
    .  透明        r  石の地 (鉱石ブロック用)
    #  輪郭 (最暗) d 深影 / s 影 / b 地色 / l 明 / h 光 / w 照り
    n  粒 (ノイズ) g  発光 (ルーン・魔力)
    2  副色 / 3 副色の明 / 4 副色の影

形は 2 系統
- `matgen_shapes.json`: 元アプリの 47 形 (インゴット・宝石・歯車・王冠・仮面・頭蓋骨…) を焼き出した 16x16 の行
- `PY_SHAPES`: このファイルで述語から組む形 (心臓・牙・巻貝・茸・鬼火・鐘・印章・卵・爪・蝋燭)。
  `N` を変えれば 32px などでも描ける

元アプリからの改良点
- 乱数を Math.random ではなく id 由来の種にして、生成のたびに同じ絵になる (差分書き込みと相性が良い)
- 副色を省略すると地色から自動で決める (色相を +30° / 彩度+ / 明度+)
- 質感 (style) と素材プリセット (preset) を名前で指定できる。プリセットは地色・副色・質感をまとめて決める
- 発光ハロ (半透明) は既定で付けない (インベントリで縁がにじむため)。`halo=True` で付く
- 未知の形・質感・プリセットは KeyError ではなく一覧付きの ValueError にする

使い方 (content.json):
    "shape": "mat:skull"                       区域素材・魔物素材の形
    "icon": "mat:chalice"                      消耗品のアイコン
    "accent": "c86bff"                         副色 (省略可)
    "style": "ancient"                         質感 (省略時は形の分類から決める)
CLI: `python tools/abyss.py matgen list | render | sheet` (tools/matgen_cli.py)
"""
from __future__ import annotations

import colorsys
import io
import json
import math
import os
import zlib
from collections import deque
from typing import Callable

from PIL import Image

Pred = Callable[[float, float], bool]
RGBA = tuple[int, int, int, int]

N = 16
_HERE = os.path.dirname(os.path.abspath(__file__))
SHAPES_JSON = os.path.join(_HERE, "matgen_shapes.json")

# ---------------------------------------------------------------- 述語 (形の部品)


def circle(cx, cy, r) -> Pred:
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def ellipse(cx, cy, rx, ry) -> Pred:
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1


def rect(x0, y0, x1, y1) -> Pred:
    return lambda x, y: x0 <= x <= x1 and y0 <= y <= y1


def poly(pts) -> Pred:
    pts = list(pts)

    def inside(x, y):
        res = False
        j = len(pts) - 1
        for i in range(len(pts)):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                res = not res
            j = i
        return res
    return inside


def seg_dist(x, y, x0, y0, x1, y1) -> float:
    dx, dy = x1 - x0, y1 - y0
    ln = dx * dx + dy * dy
    t = 0.0 if ln == 0 else max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / ln))
    return math.hypot(x0 + t * dx - x, y0 + t * dy - y)


def seg(x0, y0, x1, y1, w) -> Pred:
    return lambda x, y: seg_dist(x, y, x0, y0, x1, y1) <= w / 2


def union(*ps: Pred) -> Pred:
    return lambda x, y: any(p(x, y) for p in ps)


def minus(a: Pred, b: Pred) -> Pred:
    return lambda x, y: a(x, y) and not b(x, y)


def ring(cx, cy, ro, ri) -> Pred:
    return minus(circle(cx, cy, ro), circle(cx, cy, ri))


def ring_e(cx, cy, rxo, ryo, rxi, ryi) -> Pred:
    return minus(ellipse(cx, cy, rxo, ryo), ellipse(cx, cy, rxi, ryi))


def polyline(pts, w) -> Pred:
    return union(*(seg(*pts[i], *pts[i + 1], w) for i in range(len(pts) - 1)))


def regular(cx, cy, r, n, rot=-math.pi / 2):
    return [(cx + r * math.cos(rot + i * 2 * math.pi / n), cy + r * math.sin(rot + i * 2 * math.pi / n))
            for i in range(n)]


def star_pts(cx, cy, ro, ri, n, rot=-math.pi / 2):
    return [(cx + (ro if i % 2 == 0 else ri) * math.cos(rot + i * math.pi / n),
             cy + (ro if i % 2 == 0 else ri) * math.sin(rot + i * math.pi / n)) for i in range(n * 2)]


def gear(cx, cy, ro, ri, teeth, hole) -> Pred:
    def inside(x, y):
        dx, dy = x - cx, y - cy
        r = math.hypot(dx, dy)
        if r <= hole:
            return False
        t = max(0.0, min(1.0, math.cos(teeth * math.atan2(dy, dx)) * 2 + 0.6))
        return r <= ri + (ro - ri) * t
    return inside


def at(p: Pred, x: int, y: int) -> bool:
    return p(x + 0.5, y + 0.5)


def dist(x, y, cx, cy) -> float:
    return math.hypot(x + 0.5 - cx, y + 0.5 - cy)


def sector(x, y, cx, cy, chars: str) -> str:
    a = math.atan2(y + 0.5 - cy, x + 0.5 - cx)
    return chars[int((a + math.pi) / (2 * math.pi) * len(chars)) % len(chars)]


_SEC = {"b": "2", "l": "3", "h": "3", "w": "3", "s": "4", "d": "4"}


def to_sec(ch: str) -> str:
    return _SEC.get(ch, ch)


# ---------------------------------------------------------------- 自動陰影

def shade(pred: Pred, overlay=None, outline: bool = True, bg: str = ".", gradient: bool = True,
          n: int = N) -> list[str]:
    """述語から陰影付きの役割文字グリッドを作る。光源は左上。

    overlay(x, y, ch, depth, v) -> ch で文字を差し替えられる (depth = 外周からの距離, v = 光の当たり)。
    """
    ins = [[pred(x + 0.5, y + 0.5) for x in range(n)] for y in range(n)]

    def inside(x, y):
        return 0 <= x < n and 0 <= y < n and ins[y][x]

    inf = 1 << 30
    depth = [[inf] * n for _ in range(n)]
    q = deque()
    for y in range(n):
        for x in range(n):
            if ins[y][x] and not (inside(x + 1, y) and inside(x - 1, y) and inside(x, y + 1) and inside(x, y - 1)):
                depth[y][x] = 1
                q.append((x, y))
    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if inside(nx, ny) and depth[ny][nx] == inf:
                depth[ny][nx] = depth[y][x] + 1
                q.append((nx, ny))

    def hgt(x, y):
        return min(depth[y][x], 4) if inside(x, y) else 0

    cells = [(x, y) for y in range(n) for x in range(n) if ins[y][x]]
    if cells:
        bx0, bx1 = min(c[0] for c in cells), max(c[0] for c in cells)
        by0, by1 = min(c[1] for c in cells), max(c[1] for c in cells)
    else:
        bx0 = by0 = 0
        bx1 = by1 = 1
    bw, bh = max(1, bx1 - bx0), max(1, by1 - by0)
    use_grad = gradient and bw >= 7 * n / 16 and bh >= 7 * n / 16
    levels = "dsblh"
    rows = []
    for y in range(n):
        row = []
        for x in range(n):
            if not ins[y][x]:
                row.append(bg)
                continue
            d = depth[y][x]
            v = hgt(x + 1, y) - hgt(x - 1, y) + hgt(x, y + 1) - hgt(x, y - 1)
            if d == 1 and outline:
                ch = "#"
            else:
                lvl = 4 if v >= 3 else 3 if v >= 1 else 2 if v >= -1 else 1 if v >= -3 else 0
                if d == 2 and v >= 2:
                    lvl = 4
                if use_grad and d >= 3:
                    t = ((x - bx0) / bw + (y - by0) / bh) / 2
                    lvl += 1 if t < 0.28 else -1 if t > 0.74 else 0
                ch = levels[max(0, min(4, lvl))]
            if overlay:
                ch = overlay(x, y, ch, d, v)
            row.append(ch)
        rows.append("".join(row))
    return rows


# ---------------------------------------------------------------- このファイルで組む形


def _heart():
    body = union(circle(5.3, 6.2, 3.4), circle(10.7, 6.2, 3.4), poly([(2.1, 7.4), (13.9, 7.4), (8, 14.6)]))
    vessel = polyline([(8, 3.6), (7.2, 6.5), (8.6, 9.2), (8, 12)], 1.1)

    def ov(x, y, ch, d, v):
        if ch == "#":
            return ch
        if at(vessel, x, y):
            return "g"
        if (x, y) in ((4, 4), (4, 5)):
            return "w"
        return ch
    return shade(body, ov)


def _tooth():
    body = poly([(4, 2), (12, 2), (12.6, 6), (10.6, 14.6), (8.8, 9), (7.2, 9), (5.4, 14.6), (3.4, 6)])

    def ov(x, y, ch, d, v):
        if ch == "#":
            return ch
        if y <= 3:
            return to_sec(ch)
        return "w" if (x, y) == (5, 5) else ch
    return shade(body, ov)


def _shell():
    body = union(poly([(2, 12.5), (4, 5), (8, 1.8), (12, 5), (14, 12.5), (8, 14.5)]), rect(6.5, 12.5, 9.5, 15))

    def ov(x, y, ch, d, v):
        if ch == "#":
            return ch
        if d >= 2 and y >= 4 and abs((x + 0.5 - 8) / max(1.0, (y + 0.5 - 1.5))) * 6 % 2 > 1.55:
            return "d"
        if y >= 13:
            return to_sec(ch)
        return ch
    return shade(body, ov)


def _mushroom():
    cap = poly([(1.5, 8.5), (3, 4.2), (8, 1.8), (13, 4.2), (14.5, 8.5)])
    stem = union(rect(6, 8, 10, 14.5), ellipse(8, 14.2, 3.6, 1.2))
    spots = union(circle(5.4, 5.4, 1.1), circle(9.8, 4.2, 1.0), circle(11.8, 7, 0.9))

    def ov(x, y, ch, d, v):
        if ch == "#":
            return ch
        if at(stem, x, y) and not at(cap, x, y):
            return to_sec(ch) if ch != "w" else "3"
        if at(spots, x, y):
            return "3"
        return ch
    return shade(union(cap, stem), ov)


def _wisp():
    flame = poly([(8, 0.8), (10.2, 4.6), (13.2, 7.2), (12.8, 11.4), (10.4, 14.4), (5.6, 14.4),
                  (3.2, 11.4), (3.6, 7.8), (6, 5.6), (6.6, 3)])
    core = ellipse(8, 10.6, 2.4, 3.0)

    def ov(x, y, ch, d, v):
        if ch == "#":
            return ch
        if at(core, x, y):
            return "w" if dist(x, y, 8, 11) <= 1.1 else "g"
        return ch
    return shade(flame, ov)


def _bell():
    body = union(poly([(3, 12.5), (4, 6), (6, 3), (10, 3), (12, 6), (13, 12.5)]), rect(1.6, 11.6, 14.4, 13.4),
                 ring(8, 2.2, 1.8, 0.8), circle(8, 14.4, 1.5))

    def ov(x, y, ch, d, v):
        if ch == "#":
            return ch
        if y in (12, 13) and 2 <= x <= 13:
            return to_sec(ch)
        if y == 8 and 4 <= x <= 11:
            return "d"
        if dist(x, y, 8, 14.4) <= 1.0:
            return "g"
        return ch
    return shade(body, ov)


def _sigil():
    disk = circle(8, 8, 7.2)
    star = poly(star_pts(8, 8, 5.0, 2.0, 5))

    def ov(x, y, ch, d, v):
        if ch == "#":
            return ch
        r = dist(x, y, 8, 8)
        if 5.6 <= r <= 6.4:
            return "s"
        if at(star, x, y):
            return "g" if r > 1.2 else "w"
        return to_sec(ch) if r < 5.6 else ch
    return shade(disk, ov)


def _egg():
    body = ellipse(8, 8.8, 5.2, 6.6)
    spots = [(6, 6), (10, 8), (7, 11), (11, 12), (5, 9), (9, 4)]

    def ov(x, y, ch, d, v):
        if ch == "#":
            return ch
        if (x, y) in spots:
            return "4"
        if (x, y) in ((6, 4), (5, 5)):
            return "w"
        return ch
    return shade(body, ov)


def _claw():
    # 三日月 (大円 - ずらした円) を根元の帯で留めた鉤爪
    talon = minus(circle(11.5, 11.5, 10.2), circle(14.6, 13.2, 9.4))
    talon = minus(talon, rect(10.5, -1, 17, 17))
    band = rect(1.6, 11.2, 7.4, 14.6)
    body = union(minus(talon, rect(-1, 11.2, 17, 17)), band)

    def ov(x, y, ch, d, v):
        if ch == "#":
            return ch
        if at(band, x, y):
            return "4" if y == 13 else to_sec(ch)
        return ch
    return shade(body, ov)


def _candle():
    wax = union(rect(5, 6, 11, 14), ellipse(8, 14.2, 5, 1.4), ellipse(8, 6, 3, 1))
    flame = poly([(8, 0.6), (9.6, 3.2), (9, 5.2), (7, 5.2), (6.4, 3.2)])

    def ov(x, y, ch, d, v):
        if ch == "#":
            return ch
        if at(flame, x, y):
            return "w" if y >= 3 else "g"
        if y >= 14:
            return to_sec(ch)
        if (x, y) in ((10, 8), (10, 9), (6, 11)):
            return "s"
        return ch
    return shade(union(wax, flame), ov)


# id -> (名前, 分類, 作る関数)
PY_SHAPES: dict[str, tuple[str, str, Callable[[], list[str]]]] = {
    "heart": ("心臓", "relic", _heart),
    "tooth": ("牙歯", "raw", _tooth),
    "shell": ("巻貝", "raw", _shell),
    "mushroom": ("茸", "raw", _mushroom),
    "wisp": ("鬼火", "relic", _wisp),
    "bell": ("鐘", "relic", _bell),
    "sigil": ("印章", "relic", _sigil),
    "egg": ("卵", "raw", _egg),
    "claw": ("爪", "raw", _claw),
    "candle": ("蝋燭", "relic", _candle),
}

# ---------------------------------------------------------------- 形の一覧

_CACHE: dict[str, dict] = {}


def shapes() -> dict[str, dict]:
    """id -> {name, category, rows, rock?}。JSON の 47 形 + PY_SHAPES。"""
    if not _CACHE:
        with io.open(SHAPES_JSON, encoding="utf-8") as f:
            _CACHE.update(json.load(f)["shapes"])
        for sid, (name, cat, fn) in PY_SHAPES.items():
            _CACHE[sid] = {"name": name, "category": cat, "rows": fn()}
    return _CACHE


def shape(sid: str) -> dict:
    table = shapes()
    if sid not in table:
        raise ValueError(f"未知の形 '{sid}'。使える形: {', '.join(sorted(table))}")
    return table[sid]


# ---------------------------------------------------------------- 色

def _hsl_of(c) -> tuple[float, float, float]:
    h, l, s = colorsys.rgb_to_hls(c[0] / 255, c[1] / 255, c[2] / 255)
    return h * 360, s * 100, l * 100


def _from_hsl(h, s, l) -> RGBA:
    s = max(0.0, min(100.0, s)) / 100
    l = max(0.0, min(100.0, l)) / 100
    r, g, b = colorsys.hls_to_rgb((h % 360) / 360, l, s)
    return (int(r * 255 + 0.5), int(g * 255 + 0.5), int(b * 255 + 0.5), 255)


def rgb(v) -> RGBA:
    """0xRRGGBB / "rrggbb" / "#rrggbb" / (r,g,b[,a]) を RGBA に。"""
    if isinstance(v, str):
        v = int(v.lstrip("#"), 16)
    if isinstance(v, int):
        return ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)
    return tuple(v[:3]) + (255,)


def auto_secondary(base) -> RGBA:
    h, s, l = _hsl_of(rgb(base))
    return _from_hsl(h + 30, min(95, s + 20), min(78, l + 14))


def palette(base, secondary=None, contrast: float = 1.0, hue_shift: float = 8, sat_boost: float = 0,
            outline: bool = True) -> dict[str, RGBA]:
    """役割文字 -> 色。地色の HSL を明暗に振り、影は色相を負に・光は正にずらす。"""
    base = rgb(base)
    sec = rgb(secondary) if secondary is not None else auto_secondary(base)
    c, hs = contrast, hue_shift
    h, s0, l = _hsl_of(base)
    s = max(0.0, min(100.0, s0 + sat_boost))

    def mk(dh, ds, dl):
        return _from_hsl(h + dh, s + ds, l + dl)
    deep = mk(-hs, 8 * c, -24 * c)
    sh, ss, sl = _hsl_of(sec)
    return {
        "#": _from_hsl(h - hs, s + 4 * c, max(4, l - 38 * c)) if outline else deep,
        "d": deep,
        "s": mk(-hs * 0.5, 6 * c, -12 * c),
        "b": base if sat_boost == 0 else mk(0, 0, 0),
        "l": mk(hs * 0.5, -4 * c, 12 * c),
        "h": mk(hs, -10 * c, 24 * c),
        "w": _from_hsl(h + hs, s * 0.25, min(97, l + 36 * c)),
        "2": sec,
        "3": _from_hsl(sh + hs * 0.5, ss - 4 * c, sl + 12 * c),
        "4": _from_hsl(sh - hs * 0.5, ss + 6 * c, sl - 12 * c),
        "g": _from_hsl(sh, min(100, ss + 25), min(92, sl + 40)),
    }


# ---------------------------------------------------------------- 質感とプリセット

# grain=ざらつき brushed=ブラシ目 facet=面の明暗 patina=錆・緑青 (副色が斑に出る) glow=発光ハロの濃さ
STYLES = {
    "metal": {"grain": 0.2, "brushed": 0.45, "facet": 0, "patina": 0, "glow": 0.3},
    "gem": {"grain": 0.1, "brushed": 0, "facet": 0.7, "patina": 0, "glow": 0.8},
    "ancient": {"grain": 0.4, "brushed": 0.1, "facet": 0, "patina": 0.75, "glow": 0.6},
    "magic": {"grain": 0.15, "brushed": 0, "facet": 0.25, "patina": 0, "glow": 1.0},
    "organic": {"grain": 0.55, "brushed": 0, "facet": 0, "patina": 0, "glow": 0},
}
STYLE_NAMES = {"metal": "金属", "gem": "宝石", "ancient": "古代", "magic": "魔法", "organic": "有機"}
# 形の分類 -> 質感を省略したときの既定
CATEGORY_STYLE = {"metal": "metal", "gem": "gem", "raw": "organic", "block": "organic", "part": "metal",
                  "relic": "ancient"}

# name: (表示名, 地色, 副色, 色相のずれ, 質感)
PRESETS = {
    "copper": ("銅", "c47a45", "5fb08a", 10, "metal"), "tin": ("スズ", "c3c8cc", "8ea0b0", 4, "metal"),
    "silver": ("銀", "d6dde6", "9aa7b8", 6, "metal"), "lead": ("鉛", "646a85", "9b9fb5", -4, "metal"),
    "steel": ("鋼", "8c929c", "4d535c", 2, "metal"), "bronze": ("青銅", "c58f45", "7a5a2d", 10, "metal"),
    "brass": ("真鍮", "d6b14a", "9c7d2c", 8, "metal"), "gold": ("金", "f1c232", "f8e7a0", 10, "metal"),
    "iron": ("鉄", "d0d0d0", "8f8f8f", 0, "metal"), "platinum": ("白金", "b9d9e6", "7ea9bb", 6, "metal"),
    "titanium": ("チタン", "9aa3ab", "c5a0d9", -6, "metal"), "cobalt": ("コバルト", "2f55d4", "7ea0ff", -8, "metal"),
    "mithril": ("ミスリル", "7fe3ea", "c8fbff", 8, "magic"), "adamantite": ("アダマンタイト", "b83a3a", "6b1c1c", -6, "metal"),
    "ruby": ("ルビー", "e0304c", "ffb3c0", -8, "gem"), "sapphire": ("サファイア", "2e63d8", "9fc3ff", -10, "gem"),
    "emerald": ("エメラルド", "1fc266", "9bf5c3", 8, "gem"), "amethyst": ("アメジスト", "9b5de5", "dcc2ff", -8, "gem"),
    "topaz": ("トパーズ", "f3a11e", "ffe08a", 10, "gem"), "obsidian": ("黒曜石", "2e1f45", "7c5ca8", -6, "gem"),
    "quartz": ("水晶", "eef0f3", "c7cad1", 0, "gem"), "ancient_gold": ("古代金", "d9a441", "3f8f6b", 8, "ancient"),
    "jade": ("翡翠", "3fa77a", "a8f0c8", 6, "gem"), "lapis": ("ラピス", "2b4fb8", "e2c24c", -6, "gem"),
    "bone": ("骨", "e6dcc2", "a89b7b", 4, "organic"), "void": ("虚空", "4b1d7a", "c86bff", -10, "magic"),
    "soul": ("ソウル", "3bd3e8", "bdf7ff", 8, "magic"),
}

_STONE = [(0x6e,) * 3 + (255,), (0x7f,) * 3 + (255,), (0x8a,) * 3 + (255,), (0x77,) * 3 + (255,), (0x92,) * 3 + (255,)]
_DEEPSLATE = [(0x3b, 0x3b, 0x40, 255), (0x48, 0x48, 0x4d, 255), (0x52, 0x52, 0x57, 255), (0x41, 0x41, 0x46, 255),
              (0x5a, 0x5a, 0x60, 255)]


def _rand(x: int, y: int, seed: int) -> float:
    """座標と種から 0..1 の決定的な乱数 (元実装と同じハッシュ)。"""
    m = 0xFFFFFFFF
    h = ((x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)) & m
    h = ((h ^ (h >> 13)) * 1274126177) & m
    h ^= h >> 16
    return h / 4294967296


def seed_of(text: str) -> int:
    return zlib.crc32(text.encode("utf-8")) % 99991


def _adjust_l(c: RGBA, dl: float) -> RGBA:
    if abs(dl) < 0.01:
        return c
    h, s, l = _hsl_of(c)
    return _from_hsl(h, s, max(0.0, min(100.0, l + dl)))


def _mix(a, b, t) -> RGBA:
    return tuple(int(a[i] + (b[i] - a[i]) * t + 0.5) for i in range(3)) + (255,)


def render(sid: str, base=None, secondary=None, style: str | None = None, preset: str | None = None,
           seed: int | str = 0, noise: float = 0.6, contrast: float = 1.0, hue_shift: float | None = None,
           sparkle: bool = True, halo: bool = False) -> Image.Image:
    """形 sid を塗って RGBA 画像にする。

    base/secondary は色 (int / "rrggbb")。preset を渡すと地色・副色・色相のずれ・質感の既定がそれになる。
    seed は粒・質感の乱数の種 (文字列ならハッシュ)。
    """
    sh = shape(sid)
    rows = sh["rows"]
    n = len(rows)
    hs = 8.0
    if preset is not None:
        if preset not in PRESETS:
            raise ValueError(f"未知のプリセット '{preset}'。使えるもの: {', '.join(PRESETS)}")
        _n, pb, ps, phs, pst = PRESETS[preset]
        base = pb if base is None else base
        secondary = ps if secondary is None else secondary
        style = style or pst
        hs = phs
    if base is None:
        raise ValueError("base か preset のどちらかが必要")
    if hue_shift is not None:
        hs = hue_shift
    style = style or CATEGORY_STYLE.get(sh["category"], "metal")
    if style not in STYLES:
        raise ValueError(f"未知の質感 '{style}'。使えるもの: {', '.join(STYLES)}")
    fx = STYLES[style]
    seed = seed_of(seed) if isinstance(seed, str) else int(seed)
    pal = palette(base, secondary, contrast=contrast, hue_shift=hs)
    rock = _DEEPSLATE if sh.get("rock") == "deepslate" else _STONE

    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = img.load()
    for y in range(n):
        for x in range(n):
            ch = rows[y][x]
            if ch == ".":
                continue
            if ch == "n":
                r, a = _rand(x, y, seed), noise
                if r < 0.2 * a:
                    col = pal["s"]
                elif r < 0.3 * a:
                    col = pal["d"]
                elif r > 1 - 0.22 * a:
                    col = pal["l"]
                elif r > 1 - 0.3 * a:
                    col = pal["h"]
                else:
                    col = pal["b"]
            elif ch == "r":
                r, r2 = _rand(x, y, seed + 17), _rand(x + 3, y + 5, seed + 29)
                col = rock[0] if r < 0.12 else rock[1] if r < 0.55 else rock[3] if r < 0.8 else \
                    rock[2] if r2 < 0.5 else rock[4]
            elif ch == "w" and not sparkle:
                col = pal["h"]
            else:
                col = pal.get(ch)
                if col is None:
                    continue
            # 質感 (輪郭・照り・発光には掛けない)
            if ch not in "#wgr":
                dl = 0.0
                if fx["grain"]:
                    dl += (_rand(x, y, seed + 101) - 0.5) * fx["grain"] * 16
                if fx["brushed"]:
                    dl += (_rand(0, y, seed + 7) - 0.5) * fx["brushed"] * 14
                    dl += (_rand(x, 0, seed + 9) - 0.5) * fx["brushed"] * 4
                if fx["facet"]:
                    dl += (1 if ((x + y) >> 2) & 1 else -1) * fx["facet"] * 7
                col = _adjust_l(col, dl)
                if fx["patina"] and ch in "bsdl" and _rand(x, y, seed + 55) < fx["patina"] * 0.5:
                    col = _mix(col, pal["2"], 0.55)
            px[x, y] = col
    if halo and fx["glow"] > 0:
        g = pal["g"]
        a = int(255 * (0.12 + 0.28 * fx["glow"]))
        for y in range(n):
            for x in range(n):
                if rows[y][x] != ".":
                    continue
                if any(0 <= x + dx < n and 0 <= y + dy < n and rows[y + dy][x + dx] == "g"
                       for dy in (-1, 0, 1) for dx in (-1, 0, 1)):
                    px[x, y] = g[:3] + (a,)
    return img


def spec_from_entry(e: dict, key: str) -> str | None:
    """content.json の値 "mat:<形>" から形の id を取り出す (mat: でなければ None)。"""
    v = e.get(key, "")
    return v[4:] if isinstance(v, str) and v.startswith("mat:") else None


def entry_icon(e: dict, key: str) -> Image.Image:
    """content.json の 1 件 (shape / icon が mat:<形>) を描く。種は id。"""
    return render(spec_from_entry(e, key), base=e["color"], secondary=e.get("accent"),
                  style=e.get("style"), seed=e["id"])
