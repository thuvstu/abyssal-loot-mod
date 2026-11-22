"""2D アイコンの部位別・レイヤー式アニメーション (縦に並べたコマ + .mcmeta)。

1 枚の絵を部位に分け (形の解析: art/decorate.analyze の主軸で 柄 / 鍔 / 刃 / 切っ先、色で 宝石・発光部)、
レイヤーを重ねてコマを作る:
    奥 (back)   : aura (形に沿った光暈の呼吸) / orbit (周回する光点) / ring (足元の魔法陣の回転)
    本体 (body) : 元の絵 (部位ごとに sway (揺れ) / bob (上下) を掛けられる)
    手前 (front): sheen (刃を走る照り) / gem (宝石の脈動) / rune (刃の刻印が順に灯る) / sparkle (切っ先の煌めき) /
                  ember (立ち上る火の粉) / drip (滴) / frost (霜の結晶が瞬く)

書式 (1 行): "sheen:speed=1,width=3|gem:strength=0.6|aura:color=7ce8ff,strength=0.5"
共通の引数: speed (周期の倍率) / strength (強さ 0..1) / phase (位相 0..1) / color (rrggbb、省略は武器色) / part
frames / frametime は animate() の引数 (既定 12 コマ・2 tick)。
"""
from __future__ import annotations

import colorsys
import math
import random

from PIL import Image

from .decorate import analyze

LAYERS = {"aura": "back", "orbit": "back", "ring": "back", "sway": "body", "bob": "body",
          "sheen": "front", "gem": "front", "rune": "front", "sparkle": "front", "ember": "front",
          "drip": "front", "frost": "front"}


def _rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def _put(px, w, h, x, y, c, a):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < w and 0 <= y < h and a > 0:
        o = px[x, y]
        t = min(1.0, a / 255)
        if o[3]:
            px[x, y] = tuple(int(o[i] + (c[i] - o[i]) * t) for i in range(3)) + (max(o[3], min(255, int(a))),)
        else:
            px[x, y] = tuple(int(v) for v in c[:3]) + (min(255, int(a)),)


class Parts:
    """部位: 軸上の位置 t (0 = 柄尻 … 1 = 切っ先) と、宝石 (高彩度で明るい) 画素。"""

    def __init__(self, img):
        self.img = img
        self.b = analyze(img)
        b = self.b
        w, h = img.size
        px = img.load()
        self.t = {}
        self.gems = []
        for y in range(h):
            for x in range(w):
                c = px[x, y]
                if c[3] < 128:
                    continue
                s = ((x + 0.5 - b.hilt[0]) * b.ax + (y + 0.5 - b.hilt[1]) * b.ay) / max(1, b.length)
                self.t[(x, y)] = s
                hh, ss, vv = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
                if ss > 0.45 and vv > 0.6:
                    self.gems.append((x, y))
        # 刃: 主軸方向の後ろ 30% より先 (鍔より先) を刃とみなす
        self.blade = [p for p, s in self.t.items() if s > 0.32]
        self.hilt = [p for p, s in self.t.items() if s <= 0.32]

    def of(self, part):
        return {"blade": self.blade, "hilt": self.hilt, "gem": self.gems}.get(part, list(self.t))


def _fx(name, kw, P: Parts, layer, u, color, rnd):
    """u = 周期の中の位置 0..1。"""
    b = P.b
    w, h = layer.size
    px = layer.load()
    k = float(kw.get("strength", 0.7))
    col = _rgb(int(kw["color"], 16)) if "color" in kw else color
    light = tuple(min(255, v + (255 - v) * 0.6) for v in col)
    if name == "sheen":  # 刃を柄から切っ先へ走る斜めの照り
        width = float(kw.get("width", 3)) * w / 64
        pos = -0.2 + 1.4 * u
        for (x, y) in P.of(kw.get("part", "blade")):
            d = abs(P.t[(x, y)] - pos) * b.length
            if d < width:
                _put(px, w, h, x, y, (255, 255, 255), 255 * k * (1 - d / width) ** 1.5)
    elif name == "gem":
        a = 0.5 + 0.5 * math.sin(u * 2 * math.pi)
        for (x, y) in P.gems:
            _put(px, w, h, x, y, light, 200 * k * a)
        if a > 0.85:  # 頂点で十字の煌めき
            for (x, y) in P.gems[:: max(1, len(P.gems) // 2)][:2]:
                for d in (-2, -1, 1, 2):
                    _put(px, w, h, x + d, y, (255, 255, 255), 160 * k / abs(d))
                    _put(px, w, h, x, y + d, (255, 255, 255), 160 * k / abs(d))
    elif name == "rune":  # 刃の芯に沿って光が順に灯る
        n = int(kw.get("count", 5))
        for i in range(n):
            t = 0.38 + 0.55 * (i + 0.5) / n
            on = 0.5 + 0.5 * math.cos((u - i / n) * 2 * math.pi)
            x, y = b.hilt[0] + b.ax * b.length * t, b.hilt[1] + b.ay * b.length * t
            _put(px, w, h, x, y, light, 255 * k * on)
            _put(px, w, h, x + b.ax, y + b.ay, col, 200 * k * on)
    elif name == "sparkle":
        a = max(0.0, math.sin(u * 2 * math.pi))
        x, y = b.tip
        ln = int(4 * w / 64 * a) + 1
        for d in range(-ln, ln + 1):
            f = 1 - abs(d) / (ln + 1)
            _put(px, w, h, x + d, y, (255, 255, 255), 255 * k * f * a)
            _put(px, w, h, x, y + d, (255, 255, 255), 255 * k * f * a)
    elif name == "ember":
        for i in range(int(kw.get("count", 6))):
            s = rnd.random()
            ph = (u + i / 6) % 1
            x0 = b.hilt[0] + (b.tip[0] - b.hilt[0]) * (0.4 + 0.6 * s)
            y0 = b.hilt[1] + (b.tip[1] - b.hilt[1]) * (0.4 + 0.6 * s)
            _put(px, w, h, x0 + math.sin(ph * 6 + i) * 1.5, y0 - ph * 10 * w / 64, (255, 200, 90), 255 * k * (1 - ph))
    elif name == "drip":
        for i in range(int(kw.get("count", 2))):
            ph = (u + i * 0.5) % 1
            x, y = b.tip[0] - b.ax * b.length * (0.15 + 0.3 * i), b.tip[1] - b.ay * b.length * (0.15 + 0.3 * i)
            _put(px, w, h, x, y + ph * 8 * w / 64, col, 255 * k * (1 - ph * 0.7))
    elif name == "frost":
        pts = P.of("blade")
        for i in range(int(kw.get("count", 4))):
            if not pts:
                break
            x, y = pts[(i * 7919 + 13) % len(pts)]
            a = max(0.0, math.sin((u + i / 4) * 2 * math.pi))
            for dx, dy in ((0, 0), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                _put(px, w, h, x + dx, y + dy, (230, 250, 255), 230 * k * a * (1 if (dx, dy) == (0, 0) else 0.5))
    elif name == "aura":  # 形に沿った光暈の呼吸 (透明画素だけ)
        r = (1.2 + 1.3 * (0.5 + 0.5 * math.sin(u * 2 * math.pi))) * w / 64
        src = P.img.load()
        ri = int(math.ceil(r))
        for y in range(h):
            for x in range(w):
                if src[x, y][3] >= 128:
                    continue
                best = 0.0
                for dy in range(-ri, ri + 1):
                    for dx in range(-ri, ri + 1):
                        xx, yy = x + dx, y + dy
                        if 0 <= xx < w and 0 <= yy < h and src[xx, yy][3] >= 128:
                            best = max(best, 1 - math.hypot(dx, dy) / (r + 0.5))
                if best > 0:
                    _put(px, w, h, x, y, col, 170 * k * best)
    elif name == "orbit":
        n = int(kw.get("count", 4))
        R = b.length * float(kw.get("radius", 0.42))
        for i in range(n):
            a = (u + i / n) * 2 * math.pi
            x, y = b.cx + math.cos(a) * R, b.cy + math.sin(a) * R * 0.45
            front = math.sin(a) > 0
            for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                _put(px, w, h, x + dx, y + dy, light if (dx, dy) == (0, 0) else col,
                     (255 if (dx, dy) == (0, 0) else 120) * k * (1 if front else 0.55))
    elif name == "ring":
        R = b.length * 0.46
        for i in range(120):
            a = i * 2 * math.pi / 120
            on = (i + int(u * 120)) % 15 < 8
            _put(px, w, h, b.cx + math.cos(a) * R, b.cy + math.sin(a) * R, light if on else col, (220 if on else 90) * k)


def _transform_body(img, P: Parts, mods, u):
    """sway (切っ先側ほど左右に揺れる) / bob (全体の上下)。"""
    if not mods:
        return img
    w, h = img.size
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    src, dst = img.load(), out.load()
    sway = sum(float(kw.get("strength", 0.6)) for n, kw in mods if n == "sway")
    bob = sum(float(kw.get("strength", 0.6)) for n, kw in mods if n == "bob")
    dy_all = round(math.sin(u * 2 * math.pi) * bob * 1.5 * w / 64)
    for (x, y), t in P.t.items():
        off = math.sin(u * 2 * math.pi) * sway * 2 * w / 64 * max(0.0, t - 0.3)
        nx, ny = int(round(x + P.b.nx * off)), int(round(y + P.b.ny * off + dy_all))
        if 0 <= nx < w and 0 <= ny < h:
            dst[nx, ny] = src[x, y]
    return out


def parse(spec: str):
    out = []
    for part in filter(None, (p.strip() for p in spec.split("|"))):
        name, _, args = part.partition(":")
        if name not in LAYERS:
            raise ValueError(f"未知のアニメ '{name}'。使えるもの: {', '.join(LAYERS)}")
        out.append((name, dict(kv.split("=", 1) for kv in args.split(",") if "=" in kv)))
    return out


def frames(img: Image.Image, spec: str, color: int = 0x7ce8ff, n: int = 12, seed: int = 1) -> list[Image.Image]:
    img = img.convert("RGBA")
    P = Parts(img)
    fx = parse(spec)
    col = _rgb(color)
    body_mods = [(nm, kw) for nm, kw in fx if LAYERS[nm] == "body"]
    out = []
    for f in range(n):
        layers = {"back": Image.new("RGBA", img.size, (0, 0, 0, 0)), "front": Image.new("RGBA", img.size, (0, 0, 0, 0))}
        for nm, kw in fx:
            if LAYERS[nm] == "body":
                continue
            u = (f / n * float(kw.get("speed", 1)) + float(kw.get("phase", 0))) % 1
            _fx(nm, kw, P, layers[LAYERS[nm]], u, col, random.Random(seed))
        body = _transform_body(img, P, body_mods, f / n)
        frame = layers["back"]
        frame.alpha_composite(body)
        frame.alpha_composite(layers["front"])
        out.append(frame)
    return out


def strip(img, spec, color=0x7ce8ff, n=12) -> Image.Image:
    fs = frames(img, spec, color, n)
    s = Image.new("RGBA", (img.width, img.height * n), (0, 0, 0, 0))
    for i, f in enumerate(fs):
        s.alpha_composite(f, (0, i * img.height))
    return s
