"""64x64 などの高解像度ドット絵の描画基盤 (エンドコンテンツ武器・上位武器アイコン用)。

部品 (円柱・球・三日月・星・刃・帯) を「画素ごとの法線」で塗る。
- 光源は左上手前 (LIGHT)。明暗は 5 段の色ランプに量子化し、影は寒色・光は暖色へ色相をずらす
- 部品は描いた順に上書き (後に描いた部品が手前)
- 仕上げに selout (塗り色を沈めた色付き輪郭) と 1px の黒縁を付ける
アニメーションは呼び出し側がフレームごとに描き直し、strip() で縦に並べる。
"""
from __future__ import annotations

import colorsys
import math

from PIL import Image

RGBA = tuple[int, int, int, int]
LIGHT = (-0.55, -0.62, 0.56)  # 左上手前 (x 右+, y 下+, z 手前+)
_ln = math.sqrt(sum(v * v for v in LIGHT))
LIGHT = tuple(v / _ln for v in LIGHT)


def hexc(v: int, a: int = 255) -> RGBA:
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, a)


def _hsv_shift(c, dv, hue_to, dh, ds) -> RGBA:
    h, s, v = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
    if s > 0.06:
        diff = ((hue_to - h + 0.5) % 1.0) - 0.5
        h = (h + max(-dh, min(dh, diff))) % 1.0
        s = max(0.0, min(1.0, s + ds))
    v = max(0.0, min(1.0, v + dv))
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return (int(r * 255 + 0.5), int(g * 255 + 0.5), int(b * 255 + 0.5), c[3] if len(c) > 3 else 255)


def ramp(base, steps: int = 5, spread: float = 0.62) -> list[RGBA]:
    """暗→明の色ランプ。中央が base。暗側は青紫へ、明側は黄へ色相をずらす。"""
    out = []
    mid = (steps - 1) / 2
    for i in range(steps):
        t = (i - mid) / mid  # -1..1
        if t < 0:
            out.append(_hsv_shift(base, t * spread * 0.55, 0.70, -t * 0.06, -t * 0.10))
        else:
            out.append(_hsv_shift(base, t * spread * 0.45, 0.14, t * 0.05, -t * 0.22))
    return out


def mix(a, b, t: float) -> RGBA:
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# 仕上げの作風 (外部の参考資料の武器アイコンに寄せたもの): 光の当たる縁に照りの筋・内側の彩度と明暗差を上げる・
# 外縁を不透明で濃く締める。False で従来の仕上げ (縁はすべて沈める selout・外縁は半透明)
POLISH = True


class Canvas:
    def __init__(self, w: int = 64, h: int = 64):
        self.w, self.h = w, h
        self.col: list[list[RGBA | None]] = [[None] * w for _ in range(h)]
        self.glow: list[list[float]] = [[0.0] * w for _ in range(h)]
        self.part: list[list[int]] = [[0] * w for _ in range(h)]
        self._pid = 0

    # ------------------------------------------------------------ 基本
    def _new_part(self) -> int:
        self._pid += 1
        return self._pid

    def put(self, x: int, y: int, c: RGBA, pid: int | None = None) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            self.col[y][x] = c
            self.part[y][x] = pid if pid is not None else self._pid

    def shade_put(self, x, y, n, rmp, pid, bias: float = 0.0, spec: float = 0.0) -> None:
        """法線 n で照らした色をランプから選んで置く。"""
        d = n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]
        d = max(-1.0, min(1.0, d * 0.9 + 0.15 + bias))
        i = int(round((d + 1) / 2 * (len(rmp) - 1)))
        c = rmp[max(0, min(len(rmp) - 1, i))]
        if spec > 0:
            # 鏡面反射 (視線は +z)
            rz = 2 * d * n[2] - LIGHT[2]
            if rz > 0.93:
                c = mix(c, (255, 255, 255, 255), spec)
        self.put(x, y, c, pid)

    # ------------------------------------------------------------ 部品
    def capsule(self, p0, p1, r: float, rmp, spec: float = 0.0, r1: float | None = None) -> int:
        """太さ r (先端 r1) の円柱。断面の法線で円柱らしく塗る。"""
        pid = self._new_part()
        r1 = r if r1 is None else r1
        (x0, y0), (x1, y1) = p0, p1
        dx, dy = x1 - x0, y1 - y0
        L2 = dx * dx + dy * dy or 1e-9
        L = math.sqrt(L2)
        px, py = -dy / L, dx / L  # 軸に直交
        pad = max(r, r1) + 1
        for y in range(int(min(y0, y1) - pad), int(max(y0, y1) + pad) + 1):
            for x in range(int(min(x0, x1) - pad), int(max(x0, x1) + pad) + 1):
                cx, cy = x + 0.5, y + 0.5
                t = max(0.0, min(1.0, ((cx - x0) * dx + (cy - y0) * dy) / L2))
                rr = r + (r1 - r) * t
                qx, qy = x0 + dx * t, y0 + dy * t
                ox, oy = cx - qx, cy - qy
                dist = math.hypot(ox, oy)
                if dist <= rr:
                    s = ((ox * px + oy * py) / max(rr, 1e-6))
                    s = max(-1.0, min(1.0, s))
                    nz = math.sqrt(max(0.0, 1 - s * s))
                    self.shade_put(x, y, (px * s, py * s, nz), rmp, pid, spec=spec)
        return pid

    def sphere(self, c, r: float, rmp, spec: float = 0.6, glow: float = 0.0) -> int:
        pid = self._new_part()
        cx0, cy0 = c
        for y in range(int(cy0 - r - 1), int(cy0 + r + 2)):
            for x in range(int(cx0 - r - 1), int(cx0 + r + 2)):
                ox, oy = (x + 0.5 - cx0) / r, (y + 0.5 - cy0) / r
                q = ox * ox + oy * oy
                if q <= 1.0:
                    self.shade_put(x, y, (ox, oy, math.sqrt(1 - q)), rmp, pid, spec=spec)
                    if glow:
                        self.add_glow(x, y, glow * (1 - q * 0.5))
        return pid

    def crescent(self, c, r: float, cut_off, cut_r: float, rmp, thickness_bias: float = 0.0) -> int:
        """三日月: 円 (中心 c, 半径 r) から、ずらした円 (c+cut_off, cut_r) をくり抜く。"""
        pid = self._new_part()
        cx0, cy0 = c
        kx, ky = cx0 + cut_off[0], cy0 + cut_off[1]
        for y in range(int(cy0 - r - 1), int(cy0 + r + 2)):
            for x in range(int(cx0 - r - 1), int(cx0 + r + 2)):
                fx, fy = x + 0.5, y + 0.5
                d0 = math.hypot(fx - cx0, fy - cy0)
                d1 = math.hypot(fx - kx, fy - ky)
                if d0 <= r and d1 > cut_r:
                    # 帯の中心線からの距離で丸みを付ける (外周と内周の中間が最も手前)
                    w = (r - d0) + (d1 - cut_r)
                    s = ((r - d0) - (d1 - cut_r)) / max(w, 1e-6)
                    ox, oy = (fx - cx0) / max(d0, 1e-6), (fy - cy0) / max(d0, 1e-6)
                    nz = math.sqrt(max(0.0, 1 - s * s))
                    self.shade_put(x, y, (ox * -s, oy * -s, nz), rmp, pid, bias=thickness_bias, spec=0.5)
        return pid

    def star(self, c, outer: float, inner: float, color: RGBA, core: RGBA, points: int = 4,
             rot: float = 0.0, glow: float = 0.8) -> int:
        """光る星 (平塗り + 中心の白)。"""
        pid = self._new_part()
        cx0, cy0 = c
        pts = []
        for i in range(points * 2):
            a = rot + math.pi * i / points - math.pi / 2
            rr = outer if i % 2 == 0 else inner
            pts.append((cx0 + math.cos(a) * rr, cy0 + math.sin(a) * rr))
        for y in range(int(cy0 - outer - 1), int(cy0 + outer + 2)):
            for x in range(int(cx0 - outer - 1), int(cx0 + outer + 2)):
                if _in_poly(x + 0.5, y + 0.5, pts):
                    d = math.hypot(x + 0.5 - cx0, y + 0.5 - cy0) / outer
                    self.put(x, y, core if d < 0.28 else (mix(core, color, 0.5) if d < 0.5 else color), pid)
                    self.add_glow(x, y, glow)
        return pid

    def poly(self, pts, rmp, normal_fn=None, spec: float = 0.0) -> int:
        """多角形。normal_fn(x, y) -> 法線 (省略時は正面 + 縁の丸み)。"""
        pid = self._new_part()
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        for y in range(int(min(ys)) - 1, int(max(ys)) + 2):
            for x in range(int(min(xs)) - 1, int(max(xs)) + 2):
                if _in_poly(x + 0.5, y + 0.5, pts):
                    n = normal_fn(x + 0.5, y + 0.5) if normal_fn else (0, 0, 1)
                    self.shade_put(x, y, n, rmp, pid, spec=spec)
        return pid

    def line(self, pts, color: RGBA, glow: float = 0.0, width: int = 1) -> int:
        """折れ線 (平塗り)。"""
        pid = self._new_part()
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
            for i in range(n + 1):
                t = i / n
                x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
                for ox in range(width):
                    for oy in range(width):
                        self.put(int(x) + ox, int(y) + oy, color, pid)
                        if glow:
                            self.add_glow(int(x) + ox, int(y) + oy, glow)
        return pid

    def dot(self, x, y, color: RGBA, glow: float = 0.0) -> None:
        self.put(int(x), int(y), color, self._new_part())
        if glow:
            self.add_glow(int(x), int(y), glow)

    def sparkle(self, x, y, color: RGBA, size: int = 1) -> None:
        """十字のきらめき (size=1: 5px / size=2: 9px)。"""
        pid = self._new_part()
        x, y = int(x), int(y)
        self.put(x, y, (255, 255, 255, 255), pid)
        for k in range(1, size + 1):
            c = color if k == size else mix(color, (255, 255, 255, 255), 0.5)
            for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
                self.put(x + dx, y + dy, c, pid)

    def add_glow(self, x, y, v: float) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            self.glow[y][x] = max(self.glow[y][x], v)

    # ------------------------------------------------------------ 仕上げ
    def render(self, outline: bool = True, glow_color: RGBA | None = None, glow_radius: int = 2) -> Image.Image:
        img = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        px = img.load()
        for y in range(self.h):
            for x in range(self.w):
                if self.col[y][x] is not None:
                    px[x, y] = self.col[y][x]
        if outline:
            src = [[self.col[y][x] for x in range(self.w)] for y in range(self.h)]
            for y in range(self.h):
                for x in range(self.w):
                    c = src[y][x]
                    if c is None:
                        continue
                    # 外周: 透明に接する画素は塗りを沈めた色 (selout)
                    edge = any(not (0 <= x + dx < self.w and 0 <= y + dy < self.h) or src[y + dy][x + dx] is None
                               for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                    if edge:
                        tl = (y == 0 or src[y - 1][x] is None) or (x == 0 or src[y][x - 1] is None)
                        br = (y == self.h - 1 or src[y + 1][x] is None) or (x == self.w - 1 or src[y][x + 1] is None)
                        if POLISH and tl and not br:
                            # 光の当たる縁は照りの筋 (明るく・暖色へ・少し脱色)。白飛び手前で止める
                            px[x, y] = _hsv_shift(c, 0.2, 0.14, 0.04, -0.14)
                        elif POLISH and tl and br:
                            px[x, y] = _hsv_shift(c, 0.06, 0.14, 0.02, -0.04)  # 1px の細い線は僅かに明るく
                        else:
                            px[x, y] = _hsv_shift(c, -0.42 if tl else -0.55, 0.72, 0.06, 0.12)
                    elif POLISH:
                        # 内側は彩度と明暗差を少し上げる (中間色をはっきりさせる)
                        v = max(c[:3]) / 255
                        px[x, y] = _hsv_shift(c, (v - 0.5) * 0.14, 0.7, 0.0, 0.08)
            # 外側に 1px の濃い縁 (背景から浮かせる)
            out = img.copy()
            op = out.load()
            for y in range(self.h):
                for x in range(self.w):
                    if px[x, y][3] == 0:
                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                            nx, ny = x + dx, y + dy
                            if 0 <= nx < self.w and 0 <= ny < self.h and px[nx, ny][3] > 0:
                                c = px[nx, ny]
                                op[x, y] = (_hsv_shift(c, -0.82, 0.72, 0.1, 0.1)[:3] + (255,) if POLISH
                                            else _hsv_shift(c, -0.75, 0.72, 0.1, 0.1)[:3] + (235,))
                                break
            img = out
            px = img.load()
        if glow_color is not None:
            # 発光部の周りに半透明の光暈 (透明画素にだけ置く)
            out = img.copy()
            op = out.load()
            for y in range(self.h):
                for x in range(self.w):
                    if px[x, y][3] > 0:
                        continue
                    best = 0.0
                    for dy in range(-glow_radius, glow_radius + 1):
                        for dx in range(-glow_radius, glow_radius + 1):
                            nx, ny = x + dx, y + dy
                            if 0 <= nx < self.w and 0 <= ny < self.h and self.glow[ny][nx] > 0:
                                d = math.hypot(dx, dy)
                                if d <= glow_radius:
                                    best = max(best, self.glow[ny][nx] * (1 - d / (glow_radius + 1)))
                    if best > 0.05:
                        op[x, y] = glow_color[:3] + (int(150 * best),)
            img = out
        return img


def _in_poly(x: float, y: float, pts) -> bool:
    inside = False
    n = len(pts)
    j = n - 1
    for i in range(n):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / ((yj - yi) or 1e-9) + xi:
            inside = not inside
        j = i
    return inside


def strip(frames: list[Image.Image]) -> Image.Image:
    w, h = frames[0].size
    out = Image.new("RGBA", (w, h * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (0, h * i))
    return out
