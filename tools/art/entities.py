"""深淵MOB のテクスチャ (64x64)。

モデル空間 (tools/art/boxmodel.py と同じ 16 単位) と UV ゾーン:
  BODY  (0,0,48,32)   本体
  BELLY (48,0,64,32)  内側・明部
  DARK  (0,32,16,48)  目・口の暗部
  WHITE (16,32,32,48) 白目・牙
  ACC   (32,32,64,48) 発光部
  FREE  (0,48,64,64)  種別模様
"""
from __future__ import annotations

import random

from PIL import Image, ImageDraw

from . import core
from .core import mix, shade

DARKC = (11, 10, 18, 255)
WHITEC = (235, 240, 248, 255)

ENTITIES = {
    "slime": (0x4bd07a, 0xc8ffe0, "bubbles"),
    "splitter": (0x7dd6a0, 0xd6ffe8, "bubbles"),
    "bat": (0x9d6bff, 0xe6d6ff, "membrane"),
    "mirrorbat": (0x5cd8ff, 0xe0f8ff, "membrane"),
    "goblin": (0x7ec850, 0xf2ffd6, "cracks"),
    "skeleton": (0xdfe6ee, 0x8b95a5, "cracks"),
    "imp": (0xff7a3c, 0xffd9a8, "flames"),
    "bomber": (0xffb03c, 0xfff0c9, "flames"),
    "wraith": (0x6ad7ff, 0xe6fbff, "mist"),
    "golem": (0xa58a6b, 0xffd9a0, "cracks"),
    "phantom": (0xa070ff, 0xe8dcff, "mist"),
    "cultist": (0xff8ac2, 0xffd6ec, "flames"),
    "eye": (0xff5c9d, 0xffd6e8, "veins"),
    "boss_eye": (0xff5cc8, 0xffd6f2, "veins"),
    "charger": (0xc96a4a, 0xffe0cf, "cracks"),
    "dragonkin": (0xe0455f, 0xffd0d8, "scales"),
    "guard": (0x7d9eb9, 0xd7ebff, "plate"),
    "necromancer": (0x251b3c, 0xb28ce0, "runes"),
    "gate": (0x2a1626, 0xbd517d, "runes"),
    "goblin_king": (0x8fd44f, 0xffe98a, "crown"),
    "wyrm": (0x7c5cff, 0xd9c9ff, "scales"),
    "knight": (0x1b1226, 0xff3b5c, "plate"),
    "nihil": (0xb14bff, 0xf0d6ff, "starfield"),
    "zero": (0xe8e8f2, 0xe34bff, "starfield"),
    "omega": (0xff2a55, 0xffd700, "starfield"),
    "ascender": (0xc9a227, 0xffffff, "crown"),
}

DARKC = (11, 10, 18, 255)
WHITEC = (235, 240, 248, 255)


def _shade(c, k):
    return (max(0, min(255, int(c[0] * k))), max(0, min(255, int(c[1] * k))),
            max(0, min(255, int(c[2] * k))), 255)


def _flat(d, x0, y0, x1, y1, c):
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=c)


def _cel(d, x0, y0, x1, y1, base):
    """マイクラ式ベタ塗り: 本体フラット + 上ハイライト + 下陰 + 側面ベベル"""
    _flat(d, x0, y0, x1, y1, base)
    h = y1 - y0
    _flat(d, x0, y0, x1, y0 + max(2, h // 6), _shade(base, 1.18))
    _flat(d, x0, y1 - max(2, h // 4), x1, y1, _shade(base, 0.68))
    _flat(d, x0, y0, x0 + 1, y1, _shade(base, 0.85))
    _flat(d, x1 - 2, y0, x1, y1, _shade(base, 0.85))


def _vgrad(d, x0, y0, x1, y1, stops):
    """垂直グラデーション。stops=[(t, color)...]"""
    import random
    h = y1 - y0
    for y in range(y0, y1):
        t = (y - y0) / max(1, h - 1)
        for i in range(len(stops) - 1):
            t0, c0 = stops[i]
            t1, c1 = stops[i + 1]
            if t0 <= t <= t1:
                k = (t - t0) / max(1e-6, t1 - t0)
                c = tuple(int(c0[j] + (c1[j] - c0[j]) * k) for j in range(3)) + (255,)
                break
        else:
            c = stops[-1][1]
        d.line([(x0, y), (x1 - 1, y)], fill=c)


def _speckle(d, rnd, x0, y0, x1, y1, colors, n, size=1):
    for _ in range(n):
        x = rnd.randrange(x0, x1)
        y = rnd.randrange(y0, y1)
        c = colors[rnd.randrange(len(colors))]
        if size <= 1:
            d.point((x, y), fill=c)
        else:
            d.rectangle([x, y, x + size - 1, y + size - 1], fill=c)


def _pattern_free(d, rnd, pattern, base, accent, dark):
    x0, y0, x1, y1 = 0, 48, 64, 64
    if pattern == "bubbles":
        for _ in range(10):
            x = rnd.randrange(x0, x1 - 3)
            y = rnd.randrange(y0, y1 - 3)
            d.ellipse([x, y, x + 3, y + 3], outline=accent)
        _speckle(d, rnd, x0, y0, x1, y1, [accent], 10, 2)
    elif pattern == "membrane":
        for i in range(8):
            x = x0 + i * 8
            d.rectangle([x, y0, x + 1, y1 - 1], fill=_shade(base, 0.5))
        for i in range(4):
            x = x0 + 4 + i * 16
            d.line([(x, y0), (x, y1 - 1)], fill=accent)
    elif pattern == "cracks":
        for _ in range(5):
            x = rnd.randrange(x0 + 2, x1 - 2)
            y = rnd.randrange(y0, y1 - 4)
            d.line([(x, y), (x + rnd.randrange(-5, 6), y + 3)], fill=dark, width=2)
            d.line([(x + rnd.randrange(-5, 6), y + 3), (x + rnd.randrange(-7, 8), y + 6)], fill=dark, width=1)
    elif pattern == "flames":
        for i in range(10):
            x = x0 + 2 + i * 6
            h = rnd.randrange(6, 14)
            c = WHITEC if i % 3 == 0 else accent
            d.rectangle([x, y1 - h, x + 1, y1 - 1], fill=c)
    elif pattern == "mist":
        for i in range(4):
            y = y0 + 1 + i * 4
            d.rectangle([x0, y, x1 - 1, y + 1], fill=_shade(accent, 0.9))
    elif pattern == "veins":
        for _ in range(6):
            x = rnd.randrange(x0 + 4, x1 - 4)
            d.line([(x, y0), (x + rnd.randrange(-6, 7), y1 - 1)], fill=_shade(base, 0.4), width=2)
        _speckle(d, rnd, x0, y0, x1, y1, [accent], 8)
    elif pattern == "scales":
        for row in range(2):
            y = y0 + row * 8
            for i in range(7):
                x = x0 + i * 9 + (4 if row % 2 else 0)
                d.arc([x, y, x + 8, y + 8], 170, 370, fill=_shade(base, 0.55))
                d.point((x + 4, y + 3), fill=accent)
    elif pattern == "plate":
        d.rectangle([x0 + 2, y0 + 2, x1 - 3, y1 - 3], outline=accent, width=2)
        for x in (x0 + 7, x1 - 8):
            for y in (y0 + 4, y1 - 5):
                d.rectangle([x, y, x + 1, y + 1], fill=accent)
        d.rectangle([x0 + 28, y0 + 5, x0 + 35, y1 - 6], outline=_shade(base, 0.6))
    elif pattern == "runes":
        for _ in range(7):
            x = rnd.randrange(x0 + 3, x1 - 5)
            y = rnd.randrange(y0 + 1, y1 - 9)
            d.line([(x + 1, y), (x + 1, y + 7)], fill=accent, width=2)
            d.line([(x, y + 2), (x + 3, y + 2)], fill=accent)
            d.line([(x, y + 5), (x + 3, y + 5)], fill=accent)
    elif pattern == "starfield":
        for _ in range(26):
            d.point((rnd.randrange(x0, x1), rnd.randrange(y0, y1)),
                    fill=WHITEC if rnd.random() < 0.6 else accent)
        for _ in range(5):
            x = rnd.randrange(x0 + 2, x1 - 2)
            y = rnd.randrange(y0 + 2, y1 - 2)
            d.line([(x - 1, y), (x + 1, y)], fill=WHITEC)
            d.line([(x, y - 1), (x, y + 1)], fill=WHITEC)
    elif pattern == "crown":
        _flat(d, x0, y0, x1, y1, (232, 190, 84, 255))
        _flat(d, x0, y1 - 4, x1, y1, (150, 105, 30, 255))
        _flat(d, x0, y0, x1, y0 + 2, (255, 226, 150, 255))
        for i in range(5):
            x = x0 + 6 + i * 11
            gc = (230, 60, 70, 255) if i % 2 == 0 else (70, 180, 255, 255)
            d.rectangle([x, y0 + 5, x + 2, y0 + 7], fill=gc)
            d.point((x, y0 + 5), fill=WHITEC)
    else:
        _speckle(d, rnd, x0, y0, x1, y1, [accent], 12)


def _body_surface(d, rnd, x0, y0, x1, y1, base, pattern):
    """本体面の塗り分け (グラデーション + まだら + 斑点 + 割れ目)。"""
    top = _shade(base, 1.16)
    bot = _shade(base, 0.74)
    _vgrad(d, x0, y0, x1, y1, [(0.0, top), (0.45, base), (1.0, bot)])
    # 大きなまだら (うねりのある肌)
    for _ in range(7):
        bx = rnd.randrange(x0, x1 - 3)
        by = rnd.randrange(y0, y1 - 3)
        r = rnd.randrange(3, 7)
        c = _shade(base, rnd.choice([0.86, 1.08]))
        d.ellipse([bx, by, bx + r, by + r], fill=c)
    _speckle(d, rnd, x0, y0, x1, y1, [_shade(base, 0.86), _shade(base, 1.08)], 30)
    # 下端の影 (接地感)
    for y in range(y1 - 3, y1):
        d.line([(x0, y), (x1 - 1, y)], fill=_shade(base, 0.82 - (y1 - y) * 0.06))
    if pattern in ("cracks", "scales", "plate"):
        for _ in range(4):
            x = rnd.randrange(x0, x1 - 2)
            y = rnd.randrange(y0, y1 - 2)
            d.line([(x, y), (x + 2, y + 3)], fill=_shade(base, 0.62))


def entity_tex(base, accent, pattern):
    """64x64。標準ゾーン分割 (AbyssModels の UV と対応)。
    BODY(0,0,48,32) / BELLY(48,0,16,32) / DARK(0,32,16,16) /
    WHITE(16,32,16,16) / ACC(32,32,32,16) / FREE(0,48,64,16)+模様"""
    import random
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rnd = random.Random(0)
    dark = _shade(base, 0.45)
    # BODY: グラデーション + まだら + 斑点 (面ごとに微妙に色味が変わる)
    _body_surface(d, rnd, 0, 0, 48, 32, base, pattern)
    # BELLY: 明部 (淡いグラデ)
    _vgrad(d, 48, 0, 64, 32, [(0.0, _shade(accent, 1.12)), (1.0, _shade(accent, 0.82))])
    _speckle(d, rnd, 49, 1, 63, 31, [_shade(accent, 0.88)], 10)
    # DARK: 目・口 (艶ピクセル付き)
    _vgrad(d, 0, 32, 16, 48, [(0.0, (26, 24, 36, 255)), (1.0, (6, 6, 12, 255))])
    d.point((3, 35), fill=WHITEC)
    d.point((11, 35), fill=WHITEC)
    d.point((4, 36), fill=WHITEC)
    d.point((12, 36), fill=WHITEC)
    # WHITE: 白目・牙・光輪
    _vgrad(d, 16, 32, 32, 48, [(0.0, (252, 252, 255, 255)), (1.0, (206, 212, 226, 255))])
    # ACC: 発光部 (中心が白く光る)
    _vgrad(d, 32, 32, 64, 48, [(0.0, _shade(accent, 1.28)), (1.0, _shade(accent, 0.9))])
    for x in range(34, 62, 4):
        d.point((x, 38), fill=WHITEC)
        d.point((x + 2, 40), fill=(255, 255, 255, 200))
    # FREE: 種別模様 (下地フラット+大柄)
    _flat(d, 0, 48, 64, 64, base)
    _pattern_free(d, rnd, pattern, base, accent, dark)
    return img
