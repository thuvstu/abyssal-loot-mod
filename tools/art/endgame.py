"""エンドコンテンツ武器 (夜想曲ノクターン・相棒の剣) の見た目 (64x64・手続き生成)。

設定画 (mm/ の「夜想曲 Nocturne」) をもとに hd.py の陰影レンダラで描く。
- ノクターン: 三日月の環に四芒星を抱いた杖 + 柄に巻きつく螺旋の帯。
  左クリックカウント 0〜4 で色が変わり (紫→蒼→水→桃→白金)、1〜4 は 8 コマのアニメーション
  (螺旋が流れる・星が瞬く・音符が周回する)。
- 相棒の剣: 段階 0〜5 で 6 段階に姿が変わる (鋼 → 金の縁 → ルーン → 翼の鍔 → 蒼光の刃 → 光輝)。
  段階 3 以上はアニメーション。
どちらも CUSTOM_MODEL_DATA の floats[0] (Java 側の Endgame.putInts) で切り替える。
"""
from __future__ import annotations

import math

from PIL import Image, ImageDraw

from . import hd
from .hd import hexc, mix, ramp

NOCTURNE_STAGES = 5
AIBOU_LOOKS = 6
ANIM_FRAMES = 8

WHITE = (255, 255, 255, 255)

# カウントごとの色 (主色, 光暈)。Java の Nocturne.colorOf と一致させる
NOCTURNE_THEMES = [
    (0xb48cff, 0xd9c2ff),  # 0: 紫 (夜)
    (0x6f8cff, 0xb8c8ff),  # 1: 蒼
    (0x5ce8ff, 0xc2f8ff),  # 2: 水
    (0xff6ad5, 0xffc2ee),  # 3: 桃
    (0xffe7a0, 0xfff6d8),  # 4: 白金 (終曲)
]


# ================================================================ ノクターン

def _axis(t: float):
    """柄の軸 (t=0 石突 / t=1 頭)。左下→右上の対角。"""
    x0, y0, x1, y1 = 7.5, 56.5, 40.0, 24.0
    return x0 + (x1 - x0) * t, y0 + (y1 - y0) * t


def nocturne(count: int, frame: int = 0, frames: int = ANIM_FRAMES) -> Image.Image:
    main_hex, glow_hex = NOCTURNE_THEMES[count]
    main, glow = hexc(main_hex), hexc(glow_hex)
    ph = 2 * math.pi * frame / frames
    c = hd.Canvas(64, 64)
    metal = ramp(mix(hexc(0x8c86b8), main, 0.32), spread=0.7)  # 主色を帯びた銀の金属
    shaft = ramp(hexc(0x2d2652), spread=0.7)  # 夜色の柄
    gem = ramp(main, spread=0.8)
    gold = ramp(hexc(0xe8be54))
    ux, uy = (1 / math.sqrt(2), -1 / math.sqrt(2))  # 軸方向
    vx, vy = (1 / math.sqrt(2), 1 / math.sqrt(2))   # 軸に直交

    def ribbon(front: bool):
        pts = []
        for i in range(0, 61):
            t = 0.12 + 0.70 * i / 60
            ax, ay = _axis(t)
            w = 2 * math.pi * (t * 3.2) - ph
            s = math.sin(w)
            is_front = math.cos(w) > 0
            if is_front == front:
                pts.append((ax + vx * 3.0 * s, ay + vy * 3.0 * s, t))
            elif pts:
                _draw_ribbon(c, pts, main, glow, front, ph)
                pts = []
        if pts:
            _draw_ribbon(c, pts, main, glow, front, ph)

    ribbon(front=False)                      # 柄の裏を回る帯
    c.capsule(_axis(0.04), _axis(0.97), 1.7, shaft, spec=0.35)
    for t in (0.10, 0.84):                   # 金の留め環
        ax, ay = _axis(t)
        c.capsule((ax - vx * 2.4, ay - vy * 2.4), (ax + vx * 2.4, ay + vy * 2.4), 1.1, gold, spec=0.5)
    c.sphere((6.5, 57.5), 3.2, gem, spec=0.7, glow=0.6)  # 石突の宝珠
    ribbon(front=True)                       # 手前を回る帯
    # 頭: 三日月の環 (下側=柄側が太い)
    hx, hy = 46.5, 16.5
    c.crescent((hx, hy), 13.0, (4.2, -4.2), 10.6, metal)
    # 三日月の両端に小さな飾り玉
    c.sphere((hx + 9.5, hy + 8.0), 1.6, gem, spec=0.8, glow=0.5)
    c.sphere((hx - 8.0, hy - 9.5), 1.6, gem, spec=0.8, glow=0.5)
    c.sphere(_axis(0.97), 2.4, gem, spec=0.8, glow=0.7)  # 頭と柄の接ぎの宝石
    # 四芒星 (瞬き: 大きさと回転が脈打つ)
    pulse = 0.5 + 0.5 * math.sin(ph) if count else 0.6
    outer = 6.0 + 1.6 * pulse
    c.star((hx + 3.0, hy - 3.0), outer, 1.6, main, mix(glow, WHITE, 0.6), points=4,
           rot=0.08 * math.sin(ph) if count else 0.0, glow=1.0)
    c.star((hx + 3.0, hy - 3.0), outer * 0.55, 1.0, glow, WHITE, points=4, rot=math.pi / 4, glow=1.0)
    # 音符ときらめき (カウントが高いほど多く、周回する)
    for k in range(count + 1):
        a = ph + k * 2 * math.pi / (count + 1)
        x = hx - 4 + math.cos(a) * 13
        y = hy + 4 + math.sin(a) * 13
        if 2 <= x < 60 and 6 <= y < 61:
            if k % 2 == 0:
                _note(c, x, y, glow)
            else:
                c.sparkle(x, y, glow, size=1)
    if count >= 3:  # 終盤は柄にも光の粒
        t = (frame / frames + 0.3) % 1.0
        ax, ay = _axis(0.12 + 0.7 * t)
        c.sparkle(ax - vx * 4, ay - vy * 4, glow, size=1)
    return c.render(glow_color=glow, glow_radius=2)


def _draw_ribbon(c: hd.Canvas, pts, main, glow, front: bool, ph: float) -> None:
    col = main if front else hd._hsv_shift(main, -0.35, 0.72, 0.05, 0.05)
    pid = c._new_part()
    for (x0, y0, t0), (x1, y1, _t1) in zip(pts, pts[1:]):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(n + 1):
            t = i / n
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            # 帯の上を光が流れる (手前側のみ)
            lit = front and math.sin(t0 * 40 - ph * 2) > 0.85
            c.put(int(x), int(y), mix(col, WHITE, 0.55) if lit else col, pid)
            if front:
                c.add_glow(int(x), int(y), 0.45)


def _note(c: hd.Canvas, x: float, y: float, color) -> None:
    """八分音符 (4x6)。"""
    x, y = int(x), int(y)
    pid = c._new_part()
    for dy in range(5):
        c.put(x + 1, y - dy, color, pid)
    c.put(x + 2, y - 4, color, pid)
    c.put(x + 3, y - 3, color, pid)
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 1)):
        c.put(x + dx, y + dy, color, pid)
    c.add_glow(x, y, 0.6)


# ================================================================ 相棒の剣

AIBOU_THEMES = [
    # (刃の色, 光の色, 鍔の色)
    (0xc8d0dc, None, 0x6a6f80),       # 0 鋼
    (0xd0d8e6, None, 0xe8be54),       # 1 金の縁
    (0xc0d4ea, 0x7ce8ff, 0xe8be54),   # 2 ルーン
    (0xb8d8f0, 0x7ce8ff, 0xe8be54),   # 3 翼の鍔
    (0x9ee8ff, 0x55ffff, 0xf0d070),   # 4 蒼光の刃
    (0xe8fbff, 0xaef6ff, 0xfff0b0),   # 5 光輝
]


def aibou(stage: int, frame: int = 0, frames: int = ANIM_FRAMES) -> Image.Image:
    blade_hex, glow_hex, guard_hex = AIBOU_THEMES[stage]
    ph = 2 * math.pi * frame / frames
    glow = hexc(glow_hex) if glow_hex else None
    c = hd.Canvas(64, 64)
    blade = ramp(hexc(blade_hex), spread=0.75)
    guard = ramp(hexc(guard_hex), spread=0.7)
    grip = ramp(hexc(0x34405e))
    ux, uy = 1 / math.sqrt(2), -1 / math.sqrt(2)
    vx, vy = 1 / math.sqrt(2), 1 / math.sqrt(2)

    def at(t, off=0.0):
        """刃の軸上の点 (t=0 鍔 / t=1 切っ先)、off は直交方向のずれ。"""
        x0, y0, x1, y1 = 19.0, 45.0, 57.5, 6.5
        return x0 + (x1 - x0) * t + vx * off, y0 + (y1 - y0) * t + vy * off

    # 刃: 中央の稜線で左右の面を分けた多角形 (刃幅は鍔元 5.0 → 切っ先 0)
    w0 = 3.6 if stage < 3 else 4.1

    def bw(t):
        # 刃幅: 鍔元から 7 割は平行、そこから切っ先へ絞る
        return w0 if t < 0.7 else w0 * max(0.0, 1 - (t - 0.7) / 0.3) ** 0.85

    ts = [i / 20 for i in range(20)]
    left = [at(t, -bw(t)) for t in ts]
    right = [at(t, bw(t)) for t in reversed(ts)]
    tip = at(1.0)
    ridge = [at(0.0), tip]

    def blade_normal(x, y):
        # 稜線のどちら側か (v 方向の符号) で面の向きを変える
        ox, oy = x - 45 + 19, y - 45
        s = (x - at(0)[0]) * vx + (y - at(0)[1]) * vy
        return (-vx * 0.55, -vy * 0.55, 0.83) if s < 0 else (vx * 0.6, vy * 0.6, 0.8)

    bp = c.poly(left + [tip] + right, blade, normal_fn=blade_normal, spec=0.4)
    if stage >= 5:  # 光輝: 刃そのものが光を放つ
        for y in range(64):
            for x in range(64):
                if c.part[y][x] == bp:
                    c.add_glow(x, y, 0.55 + 0.25 * math.sin(ph + (x - y) * 0.15))
    # 樋 (中央の溝)
    c.line([at(0.03), at(0.68)], hd._hsv_shift(hexc(blade_hex), -0.32, 0.7, 0.05, 0.1))
    # 刃先の光 (照らされる側の縁)
    c.line([at(0.05, -w0 * 0.92), at(0.9, -w0 * 0.12)], mix(hexc(blade_hex), WHITE, 0.7))
    if stage >= 2:  # ルーン (樋の上に点々。段階4以上は光が流れる)
        for i in range(6):
            t = 0.08 + i * 0.1
            lit = stage >= 4 and (math.sin(ph - i * 0.9) > 0.3)
            col = mix(glow, WHITE, 0.6) if lit else hd._hsv_shift(glow, -0.38, 0.62, 0.05, 0.2)
            x, y = at(t)
            c.dot(x, y, col, glow=0.8 if lit else 0.4)
            c.dot(x + 1, y, col, glow=0.5)
            c.dot(x, y - 1, col, glow=0.5)
            c.dot(x + 1, y - 1, hd._hsv_shift(glow, -0.2, 0.6, 0, 0), glow=0.3)
    if stage >= 4:  # 蒼光の刃縁
        for t in [i / 30 for i in range(30)]:
            for side in (-1, 1):
                x, y = at(t, side * (bw(t) + 0.8))
                if math.sin(t * 18 - ph * 2) > 0.2 or stage >= 5:
                    c.add_glow(int(x), int(y), 0.7)
    # リカッソの金の帯
    if stage >= 1:
        a, b = at(0.0, -w0 - 0.3), at(0.0, w0 + 0.3)
        c.capsule(a, b, 1.3, guard, spec=0.6)
    # 鍔
    gx, gy = at(-0.04)
    span = 8.5 if stage < 3 else 7.5
    c.capsule((gx - vx * span, gy - vy * span), (gx + vx * span, gy + vy * span), 1.8, guard, spec=0.6,
              r1=1.8)
    if stage >= 3:  # 翼の鍔: 両端から切っ先側へ反り返る羽根
        for side in (-1, 1):
            bx, by = gx + vx * span * side, gy + vy * span * side
            big = 1.8 if stage >= 5 else 1.4
            pts = [(bx - vx * side * 1.5, by - vy * side * 1.5),
                   (bx + ux * 9 * big + vx * side * 4 * big, by + uy * 9 * big + vy * side * 4 * big),
                   (bx + ux * 5 * big + vx * side * 1.5, by + uy * 5 * big + vy * side * 1.5),
                   (bx + ux * 6 * big - vx * side * 0.5, by + uy * 6 * big - vy * side * 0.5),
                   (bx + ux * 2 - vx * side * 2, by + uy * 2 - vy * side * 2)]
            c.poly(pts, guard, normal_fn=lambda x, y: (-0.3, -0.4, 0.86), spec=0.5)
    # 鍔中央の宝石
    gem_col = glow if glow else hexc(0x5c8cff)
    c.sphere((gx, gy), 2.3, ramp(gem_col, spread=0.8), spec=0.9, glow=0.8 if glow else 0.0)
    # 柄と柄頭
    p0 = at(-0.08)
    p1 = at(-0.30)
    c.capsule(p0, p1, 1.7, grip, spec=0.2)
    for i in range(4):  # 柄巻き
        t = -0.11 - i * 0.05
        x, y = at(t)
        c.line([(x - vx * 1.6, y - vy * 1.6), (x + vx * 1.6, y + vy * 1.6)], hexc(0x1c2238))
    c.sphere(at(-0.34), 2.6, guard, spec=0.7)
    # 浮遊する欠片・きらめき
    if stage >= 4:
        n = 3 if stage == 4 else 5
        for k in range(n):
            a = ph + k * 2 * math.pi / n
            t = 0.25 + 0.6 * ((k / n + frame / frames) % 1.0)
            x, y = at(t, math.sin(a) * 10)
            c.sparkle(x, y, glow, size=1 if stage == 4 else (2 if k % 2 == 0 else 1))
    return c.render(glow_color=glow, glow_radius=2 if stage >= 4 else 1)


def is_animated_aibou(stage: int) -> bool:
    return stage >= 3


# ================================================================ 共通

def frames_strip(fn, n: int = ANIM_FRAMES) -> Image.Image:
    return hd.strip([fn(f) for f in range(n)])


def anim_meta(frametime: int = 2) -> dict:
    return {"animation": {"frametime": frametime, "interpolate": False}}


def limit_stone() -> Image.Image:
    """限界突破の楔 (16x16): 蒼く光る楔形の結晶に金の帯。"""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    outline = (18, 22, 40, 255)
    body = (70, 200, 255, 255)
    light = (190, 248, 255, 255)
    dark = (30, 110, 170, 255)
    gold = (240, 196, 84, 255)
    pts = [(8, 1), (13, 6), (10, 15), (6, 15), (3, 6)]
    d.polygon(pts, fill=outline)
    d.polygon([(8, 2), (12, 6), (9, 14), (7, 14), (4, 6)], fill=body)
    d.polygon([(8, 2), (8, 14), (7, 14), (4, 6)], fill=dark)
    d.line([(8, 3), (6, 7)], fill=light)
    d.line([(9, 4), (11, 6)], fill=light)
    d.line([(4, 9), (12, 9)], fill=outline)
    d.line([(5, 10), (11, 10)], fill=gold)
    d.point((8, 10), fill=(255, 250, 220, 255))
    for p in ((2, 2), (14, 3), (13, 12)):
        d.point(p, fill=light)
    return img
