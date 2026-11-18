"""アイコンのバリエーション生成 (MCAssetGen の texcraft `evolution.ts` を移植・改良したもの)。

既存のアイコン (どの解像度でもよい) を元に、同じ絵から派生形を作る。

| 系統 | 中身 | ゲーム内での使い道 |
|---|---|---|
| 強化段階 `tier(img, lv)` | +1 研ぎ / +3 ルーンと星 / +5 光をまとう (アニメ) | 武器の鍛造レベル (GearData.forgeLv) でインベントリ表示が育つ |
| 気配 `aura(img)` | 輪郭の外側に白い光 (脈動アニメ) | 特性ごとに `tints` で色を付けて重ねる |
| 特性の粒子 `trait_particles(id)` | 炎・雷・毒・血・闇… 10 種 (64px アニメ) | 武器の特性 (GearData.traitOf) を一目で分かるように重ねる |
| 属性違い / 素材違い `recolor` | 明度を保ったまま 3 色の階調へ置き換える | CLI で新武器・新素材の色案を出す |
| 形態変化 `form` | 鋸刃・欠けた刃・二振り | CLI で派生武器の形案を出す |
| 一時状態 `mode` | 凍結・過熱・祝福・隠密 | CLI で状態異常アイコンの案を出す |

元実装からの改良点
- 解像度に依存しない (16px でも 64px でも、太さ・半径は 1/16 単位で決める)
- 乱数は id 由来の種で決定的 (再生成しても差分が出ない)
- 「足すだけ」を徹底: 元の不透明ピクセルの色はほぼ変えず、光・粒は透明部分か上乗せで描く
  (元実装の sharpen/bevel で全体が濁る問題を避けた)
- 既にアニメーションするアイコンは、各コマに同じ時刻の効果を乗せる (コマ数を揃える)
- 元実装は 2144 行のエフェクト層に依存していたが、必要な処理だけをここで完結させた
"""
from __future__ import annotations

import colorsys
import math
import random
import zlib

from PIL import Image

RGBA = tuple[int, int, int, int]
FRAMES = 8

# ---------------------------------------------------------------- 基本処理


def _u(img: Image.Image) -> float:
    """1/16 単位の画素数 (16px なら 1、64px なら 4)。"""
    return img.width / 16


def _rng(*keys) -> random.Random:
    return random.Random(zlib.crc32("|".join(map(str, keys)).encode()))


def _lum(c) -> float:
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255


def _clamp(v) -> int:
    return max(0, min(255, int(v + 0.5)))


def _over(dst: RGBA, src: RGBA) -> RGBA:
    """src を dst の上に重ねる (通常合成)。"""
    sa = src[3] / 255
    if sa <= 0:
        return dst
    da = dst[3] / 255
    oa = sa + da * (1 - sa)
    if oa <= 0:
        return (0, 0, 0, 0)
    return tuple(_clamp((src[i] * sa + dst[i] * da * (1 - sa)) / oa) for i in range(3)) + (_clamp(oa * 255),)


def _add(dst: RGBA, col, k: float) -> RGBA:
    """加算 (発光)。透明なら col を k の濃さで置く。"""
    if dst[3] == 0:
        return col[:3] + (_clamp(255 * min(1.0, k)),)
    return tuple(_clamp(dst[i] + col[i] * k) for i in range(3)) + (dst[3],)


def _solid(img: Image.Image) -> list[list[bool]]:
    px = img.load()
    return [[px[x, y][3] >= 128 for x in range(img.width)] for y in range(img.height)]


def _dist_outside(img: Image.Image, limit: float) -> dict[tuple[int, int], float]:
    """不透明部の外側 limit 以内の透明画素 -> 最寄りの不透明画素までの距離。"""
    w, h = img.size
    solid = _solid(img)
    pts = [(x, y) for y in range(h) for x in range(w) if solid[y][x]
           and any(not (0 <= x + dx < w and 0 <= y + dy < h and solid[y + dy][x + dx])
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    out: dict[tuple[int, int], float] = {}
    r = int(math.ceil(limit))
    for sx, sy in pts:
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                x, y = sx + dx, sy + dy
                if not (0 <= x < w and 0 <= y < h) or solid[y][x]:
                    continue
                d = math.hypot(dx, dy)
                if d <= limit and d < out.get((x, y), 1e9):
                    out[(x, y)] = d
    return out


def _outer_glow(img: Image.Image, color, radius: float, strength: float) -> Image.Image:
    out = img.copy()
    px = out.load()
    for (x, y), d in _dist_outside(img, radius).items():
        a = strength * (1 - d / (radius + 0.5)) ** 1.6
        px[x, y] = _over(px[x, y], color[:3] + (_clamp(255 * a),))
    return out


def _sparkle(img: Image.Image, cx: float, cy: float, size: float, color, alpha: float = 1.0,
             star: bool = False) -> None:
    """十字の煌めき (star=True で斜めの短い腕も付ける)。"""
    px = img.load()
    w, h = img.size
    arm = max(1, int(round(size)))
    pts = [(0, 0, 1.0)]
    for i in range(1, arm + 1):
        k = 1 - i / (arm + 1)
        pts += [(i, 0, k), (-i, 0, k), (0, i, k), (0, -i, k)]
    if star:
        for i in range(1, max(1, arm // 2) + 1):
            k = 0.6 * (1 - i / (arm + 1))
            pts += [(i, i, k), (-i, -i, k), (i, -i, k), (-i, i, k)]
    for dx, dy, k in pts:
        x, y = int(cx + dx), int(cy + dy)
        if 0 <= x < w and 0 <= y < h:
            px[x, y] = _over(px[x, y], color[:3] + (_clamp(255 * k * alpha),))


def _bbox(img: Image.Image):
    b = img.getchannel("A").point(lambda a: 255 if a >= 128 else 0).getbbox()
    return b or (0, 0, img.width, img.height)


def _opaque(img: Image.Image) -> list[tuple[int, int]]:
    px = img.load()
    return [(x, y) for y in range(img.height) for x in range(img.width) if px[x, y][3] >= 128]


# ---------------------------------------------------------------- 強化段階

TIER_STEPS = (0, 1, 3, 5)  # 鍛造レベル 0..5 -> 見た目は 4 段 (+0 / +1〜2 / +3〜4 / +5)


def tier_look(level: int) -> int:
    """鍛造レベル (0..5) を見た目の段 (0..3) に。"""
    return 3 if level >= 5 else 2 if level >= 3 else 1 if level >= 1 else 0


def _hone(img: Image.Image, accent) -> Image.Image:
    """+1: 研ぎ。左上向きの縁を光らせ、右下向きの縁を締める。輪郭の外に副色の細い縁取り。"""
    out = img.copy()
    src, px = img.load(), out.load()
    w, h = img.size
    for x, y in _opaque(img):
        c = src[x, y]
        if _lum(c) < 0.12:  # 輪郭はそのまま
            continue

        def edge(dx, dy):
            nx, ny = x + dx, y + dy
            return not (0 <= nx < w and 0 <= ny < h) or src[nx, ny][3] < 128 or _lum(src[nx, ny]) < 0.12
        if edge(-1, 0) or edge(0, -1):
            px[x, y] = tuple(_clamp(c[i] + (255 - c[i]) * 0.28) for i in range(3)) + (c[3],)
        elif edge(1, 0) or edge(0, 1):
            px[x, y] = tuple(_clamp(c[i] * 0.84) for i in range(3)) + (c[3],)
    rim = tuple(_clamp(v * 0.55) for v in accent[:3]) + (150,)
    for (x, y), d in _dist_outside(img, 1.0).items():
        if d <= 1.0:
            px[x, y] = _over(px[x, y], rim)
    return out


def _runes(img: Image.Image, accent, seed: str, count: int) -> Image.Image:
    """+3: 明るい面 (刃) に副色の刻印を点々と光らせる。"""
    out = img.copy()
    px = out.load()
    u = _u(img)
    pts = sorted(_opaque(img), key=lambda p: -_lum(img.getpixel(p)))
    pts = pts[: max(4, len(pts) // 4)]
    rnd = _rng("runes", seed)
    rnd.shuffle(pts)
    placed: list[tuple[int, int]] = []
    for x, y in pts:
        if len(placed) >= count:
            break
        if any(math.hypot(x - a, y - b) < 3.2 * u for a, b in placed):
            continue
        placed.append((x, y))
        s = max(1, int(u * 0.75))
        glow = tuple(min(255, v + 60) for v in accent[:3])
        for dy in range(s):
            for dx in range(s):
                if 0 <= x + dx < img.width and 0 <= y + dy < img.height and px[x + dx, y + dy][3] >= 128:
                    px[x + dx, y + dy] = _add(px[x + dx, y + dy], glow, 0.75)
    return out


def _shimmer(img: Image.Image, t: float, color=(255, 255, 255), width: float = 0.16,
             strength: float = 0.55) -> Image.Image:
    """斜めの光の帯が不透明部を横切る (t=0..1 で一往復分)。"""
    out = img.copy()
    px = out.load()
    w, h = img.size
    center = -0.3 + 1.6 * t
    for x, y in _opaque(img):
        p = (x / w + (h - y) / h) / 2  # 左下 0 → 右上 1
        d = abs(p - center)
        if d < width:
            px[x, y] = _add(px[x, y], color, strength * (1 - d / width))
    return out


def tier(img: Image.Image, look: int, accent, seed: str = "", t: float = 0.0) -> Image.Image:
    """強化段階の見た目 (look = tier_look の 0..3)。t はアニメーションの時刻 (0..1)。"""
    if look <= 0:
        return img
    accent = tuple(accent[:3])
    u = _u(img)
    out = _hone(img, accent)
    if look >= 2:
        out = _runes(out, accent, seed, 3 + look)
        x0, y0, x1, y1 = _bbox(img)
        if look == 2:
            _sparkle(out, x1 - 1.5 * u, y0 + 1.5 * u, 1.6 * u, (255, 255, 255), 0.9)
            out = _outer_glow(out, accent, 1.3 * u, 0.32)
    if look >= 3:
        k = 0.5 + 0.5 * math.sin(t * 2 * math.pi)
        out = _outer_glow(out, accent, 2.0 * u, 0.35 + 0.3 * k)
        out = _shimmer(out, t)
        rnd = _rng("tier5", seed)
        x0, y0, x1, y1 = _bbox(img)
        for i in range(3):
            ph = (t + i / 3) % 1.0
            a = math.sin(ph * math.pi)
            if a < 0.15:
                continue
            sx = x0 + (x1 - x0) * rnd.uniform(0.15, 0.95)
            sy = y0 + (y1 - y0) * rnd.uniform(0.05, 0.85)
            _sparkle(out, sx, sy, 1.8 * u * a, (255, 255, 255), a, star=True)
    return out


# ---------------------------------------------------------------- 気配 (特性ごとに着色して重ねる)

def aura(img: Image.Image, t: float = 0.0) -> Image.Image:
    """輪郭の外側だけの白い光 (元の絵は含まない)。item model の tints で色を付けて使う。"""
    u = _u(img)
    radius = 2.2 * u
    k = 0.75 + 0.25 * math.sin(t * 2 * math.pi)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    px = out.load()
    for (x, y), d in _dist_outside(img, radius).items():
        a = k * 0.85 * (1 - d / (radius + 0.5)) ** 1.4
        px[x, y] = (255, 255, 255, _clamp(255 * a))
    return out


# ---------------------------------------------------------------- 特性の粒子 (どの武器にも共通で重ねる)

# 特性 id -> (名前, 気配の色, 粒子の種類)
TRAITS = {
    "sharp": ("鋭刃", 0xe8f4ff, "glint"),
    "vampiric": ("吸血鬼", 0xd0203a, "blood"),
    "explosive": ("爆砕", 0xff8a2a, "burst"),
    "terminator": ("断末魔", 0x7a4ad0, "dark"),
    "chain_l": ("連鎖雷", 0xffe14a, "thunder"),
    "infernus": ("業火", 0xff5a1a, "fire"),
    "venomous": ("猛毒", 0x6fd65a, "poison"),
    "rend": ("裂傷", 0xff4d6d, "slash"),
    "frenzy": ("狂戦士", 0xff2a2a, "rage"),
    "fortify": ("不壊", 0xffd23c, "ward"),
}


def trait_particles(kind: str, t: float, size: int = 64) -> Image.Image:
    """特性の粒子 1 コマ。アイコンの縁 (主に右下と左上の空き) に出し、中央はなるべく空ける。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    u = size / 16
    rnd = _rng("particles", kind)

    def dot(x, y, col, a=1.0, s=1):
        for dy in range(s):
            for dx in range(s):
                xi, yi = int(x) + dx, int(y) + dy
                if 0 <= xi < size and 0 <= yi < size:
                    px[xi, yi] = _over(px[xi, yi], col[:3] + (_clamp(255 * a),))

    def edge_points(n):
        """縁に沿った出現位置 (中央 8x8 を避ける)。"""
        pts = []
        while len(pts) < n:
            x, y = rnd.uniform(0, size), rnd.uniform(0, size)
            if abs(x - size / 2) < 4.5 * u and abs(y - size / 2) < 4.5 * u:
                continue
            pts.append((x, y, rnd.random()))
        return pts

    if kind == "fire":
        for x, y, ph in edge_points(9):
            p = (t + ph) % 1.0
            yy = (y - p * 7 * u) % size
            col = (255, 230, 120) if p < 0.3 else (255, 140, 40) if p < 0.7 else (200, 50, 20)
            dot(x + math.sin((p + ph) * 6) * u * 0.6, yy, col, 1 - p * 0.6, max(1, int(u * (1.2 - p))))
    elif kind == "thunder":
        flash = int(t * FRAMES) % 4
        for k, (x, y, _ph) in enumerate(edge_points(4)):
            if k % 4 != flash and (k + 2) % 4 != flash:
                continue
            cx, cy = x, y
            for _ in range(5):
                nx, ny = cx + rnd.choice((-1, 1)) * u, cy + u
                for s in range(int(u) + 1):
                    dot(cx + (nx - cx) * s / (u + 1e-6), cy + (ny - cy) * s / (u + 1e-6), (255, 250, 190))
                cx, cy = nx, ny
            dot(x, y, (255, 255, 255), 1, max(1, int(u)))
    elif kind == "poison":
        for x, y, ph in edge_points(7):
            p = (t + ph) % 1.0
            r = max(1, int(u * (0.6 + 0.8 * p)))
            yy = (y - p * 5 * u) % size
            for dy in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    d = math.hypot(dx, dy)
                    if r - 0.9 <= d <= r + 0.2:
                        dot(x + dx, yy + dy, (130, 230, 90), 0.9 * (1 - p))
                    elif d < r - 0.9 and dx < 0 and dy < 0:
                        dot(x + dx, yy + dy, (220, 255, 190), 0.6 * (1 - p))
    elif kind == "blood":
        for x, y, ph in edge_points(6):
            p = (t + ph) % 1.0
            yy = (y + p * 6 * u) % size
            for i in range(max(2, int(2.5 * u))):
                dot(x, yy - i, (210, 20, 45), 0.4 + 0.6 * (1 - p) * (1 - i / (2.5 * u + 1)), max(1, int(u)))
            dot(x, yy, (255, 90, 100), 1 - p)
    elif kind == "burst":
        p = t % 1.0
        for k in range(10):
            ang = k / 10 * 2 * math.pi + rnd.uniform(-0.2, 0.2)
            rad = (5 + p * 3) * u
            dot(size / 2 + math.cos(ang) * rad, size / 2 + math.sin(ang) * rad,
                (255, 220, 120) if p < 0.5 else (255, 120, 40), 1 - p * 0.8, max(1, int(u * 0.6)))
    elif kind == "dark":
        for x, y, ph in edge_points(8):
            p = (t + ph) % 1.0
            yy = (y - p * 4 * u) % size
            dot(x + math.sin(p * 6 + ph * 9) * u, yy, (60, 20, 90), 0.85 * (1 - p), max(1, int(u * 1.4)))
            dot(x + math.sin(p * 6 + ph * 9) * u, yy, (170, 120, 255), 0.6 * (1 - p))
    elif kind == "slash":
        p = t % 1.0
        for k in range(2):
            off = (k * 5 - 2.5) * u
            for i in range(int(14 * u)):
                f = i / (14 * u)
                if abs(f - p) > 0.28:
                    continue
                dot(size * 0.1 + f * size * 0.8 + off, size * 0.9 - f * size * 0.8 + off,
                    (255, 120, 140), 1 - abs(f - p) / 0.28)
    elif kind == "rage":
        k = 0.5 + 0.5 * math.sin(t * 2 * math.pi)
        for cx, cy in ((2.5 * u, 2.5 * u), (size - 3.5 * u, size - 3.5 * u)):
            for ang in (0, math.pi / 2, math.pi, 3 * math.pi / 2):
                for i in range(1, int(2 * u) + 1):
                    a2 = ang + 0.4
                    dot(cx + math.cos(a2) * i, cy + math.sin(a2) * i, (255, 40, 40), 0.5 + 0.5 * k)
    elif kind == "ward":
        for k2, (x, y, ph) in enumerate(edge_points(5)):
            a = math.sin(((t + ph) % 1.0) * math.pi)
            _sparkle(img, x, y, 1.5 * u * a + 0.5, (255, 230, 140), a, star=True)
    elif kind == "glint":
        for x, y, ph in edge_points(4):
            a = math.sin(((t + ph) % 1.0) * math.pi)
            if a > 0.2:
                _sparkle(img, x, y, 1.8 * u * a, (255, 255, 255), a)
    else:
        raise ValueError(f"未知の粒子 '{kind}'")
    return img


# ---------------------------------------------------------------- 色・形・状態 (CLI 用の案出し)

ELEMENTS = {  # id: (名前, 暗, 中, 明)
    "fire": ("炎", 0x3a0a00, 0xe25822, 0xffe08a), "ice": ("氷", 0x08243a, 0x5ec8e8, 0xeefcff),
    "thunder": ("雷", 0x2a2400, 0xe8d024, 0xfffde0), "dark": ("闇", 0x0a0014, 0x6a28b0, 0xe0c0ff),
    "holy": ("聖", 0x3a2a00, 0xf0c04a, 0xfff8dc), "poison": ("毒", 0x08200a, 0x4cb828, 0xd8ff9a),
    "water": ("水", 0x04203c, 0x2a7ac8, 0xd0f4ff), "blood": ("血", 0x1a0000, 0xb01818, 0xffb0a0),
}
MATERIALS = {
    "wood": ("木", 0x3a2410, 0x8a5a2b, 0xe0b070), "stone": ("石", 0x2a2a2a, 0x7a7a7a, 0xd0d0d0),
    "iron": ("鉄", 0x2a2a30, 0x9a9aa4, 0xf0f0f4), "gold": ("金", 0x3a2000, 0xd9a000, 0xfff4b0),
    "diamond": ("ダイヤ", 0x062a33, 0x2ec4c0, 0xeafffd), "netherite": ("ネザライト", 0x120e10, 0x4a4044, 0xc0b4b0),
}


def _hx(v) -> tuple[int, int, int]:
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


def recolor(img: Image.Image, dark, mid, light, amount: float = 0.92, keep_outline: bool = True) -> Image.Image:
    """明度を 3 色の階調 (暗→中→明) に写す。輪郭 (ごく暗い画素) は残す。"""
    dark, mid, light = (_hx(c) if isinstance(c, int) else c for c in (dark, mid, light))
    out = img.copy()
    px = out.load()
    for x, y in _opaque(img):
        c = px[x, y]
        lv = _lum(c)
        if keep_outline and lv < 0.1:
            continue
        if lv < 0.5:
            k = lv / 0.5
            g = tuple(dark[i] + (mid[i] - dark[i]) * k for i in range(3))
        else:
            k = (lv - 0.5) / 0.5
            g = tuple(mid[i] + (light[i] - mid[i]) * k for i in range(3))
        px[x, y] = tuple(_clamp(c[i] + (g[i] - c[i]) * amount) for i in range(3)) + (c[3],)
    return out


def form(img: Image.Image, kind: str, accent=(255, 255, 255), seed: str = "") -> Image.Image:
    """形態変化。serrated=刃の縁に棘 / broken=切っ先 (右上) が欠ける / twin=少しずらした二振り。"""
    u = _u(img)
    if kind == "twin":
        out = img.transform(img.size, Image.AFFINE, (1, 0, -2 * u, 0, 1, 2 * u))  # 手前の一振り
        dim = img.transform(img.size, Image.AFFINE, (1, 0, 2 * u, 0, 1, -2 * u))  # 奥の一振り (暗く)
        r, g, b, al = dim.split()
        dim = Image.merge("RGBA", (r.point(lambda v: v * 0.7), g.point(lambda v: v * 0.7),
                                   b.point(lambda v: v * 0.7), al))
        res = Image.new("RGBA", img.size, (0, 0, 0, 0))
        res.alpha_composite(dim)
        res.alpha_composite(out)
        return res
    out = img.copy()
    px = out.load()
    w, h = img.size
    if kind == "broken":
        rnd = _rng("broken", seed)
        x0, y0, x1, y1 = _bbox(img)
        cut = 0.72 + rnd.uniform(-0.05, 0.05)
        for x, y in ((x, y) for y in range(h) for x in range(w) if px[x, y][3] > 0):
            p = ((x - x0) / max(1, x1 - x0) + (y1 - y) / max(1, y1 - y0)) / 2
            jag = 0.04 * math.sin(x * 1.7 / u + y * 0.9 / u)
            if p > cut + jag:
                px[x, y] = (0, 0, 0, 0)  # 半透明の光 (宝玉のにじみ等) も一緒に欠く
            elif p > cut + jag - 0.05 and px[x, y][3] >= 128:
                px[x, y] = (40, 30, 50, 255)
        return out
    if kind == "serrated":
        spikes = sorted(_dist_outside(img, 1.0).items(), key=lambda kv: kv[0][0] - kv[0][1])
        x0, y0, x1, y1 = _bbox(img)
        step = max(2, int(2.5 * u))
        for i, ((x, y), _d) in enumerate(spikes):
            p = ((x - x0) / max(1, x1 - x0) + (y1 - y) / max(1, y1 - y0)) / 2
            if p > 0.35 and (x + y) % step == 0:
                px[x, y] = tuple(accent[:3]) + (255,)
        return out
    raise ValueError(f"未知の形態 '{kind}' (serrated / broken / twin)")


def mode(img: Image.Image, kind: str, t: float = 0.0, seed: str = "") -> Image.Image:
    """一時状態。frozen=霜 / overheat=赤熱 / blessed=金の縁と星 / stealth=半透明 / charged=縁が発光。"""
    u = _u(img)
    if kind == "stealth":
        out = img.copy()
        out.putalpha(img.getchannel("A").point(lambda v: int(v * (0.45 + 0.12 * math.sin(t * 2 * math.pi)))))
        return out
    if kind == "charged":
        return _outer_glow(img, (140, 220, 255), 2 * u, 0.5 + 0.3 * math.sin(t * 2 * math.pi))
    if kind == "blessed":
        out = _outer_glow(img, (255, 228, 140), 1.0 * u, 0.9)
        x0, y0, x1, y1 = _bbox(img)
        rnd = _rng("blessed", seed)
        for i in range(3):
            a = math.sin(((t + i / 3) % 1.0) * math.pi)
            _sparkle(out, rnd.uniform(x0, x1), rnd.uniform(y0, y1), 1.6 * u * a, (255, 244, 192), a, star=True)
        return out
    out = img.copy()
    px = out.load()
    if kind == "frozen":
        for x, y in _opaque(img):
            c = px[x, y]
            px[x, y] = tuple(_clamp(c[i] * 0.7 + (200, 235, 255)[i] * 0.3) for i in range(3)) + (c[3],)
        for (x, y), d in _dist_outside(img, 1.0).items():
            if (x * 7 + y * 3) % 5 == 0:
                px[x, y] = (230, 248, 255, 220)
        return _shimmer(out, t, (220, 245, 255), 0.12, 0.4)
    if kind == "overheat":
        for x, y in _opaque(img):
            c = px[x, y]
            if _lum(c) > 0.45:
                k = 0.45 + 0.2 * math.sin(t * 2 * math.pi + x * 0.3 / u)
                px[x, y] = tuple(_clamp(c[i] + ((255, 110, 40)[i] - c[i]) * k) for i in range(3)) + (c[3],)
        return out
    raise ValueError(f"未知の状態 '{kind}' (frozen / overheat / blessed / stealth / charged)")


def frames(fn, n: int = FRAMES) -> list[Image.Image]:
    return [fn(i / n) for i in range(n)]


def strip(imgs: list[Image.Image]) -> Image.Image:
    w, h = imgs[0].size
    out = Image.new("RGBA", (w, h * len(imgs)), (0, 0, 0, 0))
    for i, im in enumerate(imgs):
        out.alpha_composite(im, (0, h * i))
    return out


def hsv_tint(c, dh: float = 0.0) -> RGBA:
    h, s, v = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
    r, g, b = colorsys.hsv_to_rgb((h + dh) % 1, s, v)
    return (_clamp(r * 255), _clamp(g * 255), _clamp(b * 255), 255)
