"""薬瓶 (16x16)。瓶の形 5 種 × 効果の紋章で、色違いだけの瓶が並ばないようにする。

形: flask_s / flask_m / flask (丸底) / vial (細瓶) / tonic (角瓶) / elixir (菱形の結晶瓶) / phial (三角フラスコ)
紋章: 液体の中に 5x5 で描く効果の印 (剣=筋力 / 二重矢印=速度 / 盾=耐性 / ハート=再生 …)
陰影・色付き輪郭は finish.finish が後から付けるので、ここでは平塗りと最小限の光だけ置く。
"""
from __future__ import annotations

from PIL import Image

from .core import mix, shade

OUTLINE = (24, 18, 36, 255)
CORK = (150, 100, 60, 255)
GOLD = (232, 190, 84, 255)
WHITE = (255, 255, 255, 255)

# o=輪郭 g=ガラス (空) l=液体 c=栓 m=金具 .=透明
FORMS: dict[str, list[str]] = {
    "flask": [
        "......oooo......",
        ".....occcco.....",
        ".....occcco.....",
        "......oooo......",
        "......oggo......",
        "......oggo......",
        ".....oggggo.....",
        "....oggggggo....",
        "...ollllllllo...",
        "..ollllllllllo..",
        "..ollllllllllo..",
        "..ollllllllllo..",
        "..ollllllllllo..",
        "...ollllllllo...",
        "....oooooooo....",
        "................",
    ],
    "flask_m": [
        "................",
        "......oooo......",
        ".....occcco.....",
        "......oooo......",
        "......oggo......",
        ".....oggggo.....",
        "....oggggggo....",
        "...ollllllllo...",
        "...ollllllllo...",
        "...ollllllllo...",
        "...ollllllllo...",
        "...ollllllllo...",
        "....ollllllo....",
        ".....oooooo.....",
        "................",
        "................",
    ],
    "flask_s": [
        "................",
        "................",
        "......oooo......",
        ".....occcco.....",
        "......oooo......",
        "......oggo......",
        ".....oggggo.....",
        "....ollllllo....",
        "....ollllllo....",
        "....ollllllo....",
        "....ollllllo....",
        "....ollllllo....",
        ".....ollllo.....",
        "......oooo......",
        "................",
        "................",
    ],
    "vial": [
        "......oooo......",
        ".....ommmmo.....",
        ".....occcco.....",
        "......oooo......",
        ".....oggggo.....",
        "....oggggggo....",
        "....ollllllo....",
        "....ollllllo....",
        "....ollllllo....",
        "....ollllllo....",
        "....ollllllo....",
        "....ollllllo....",
        "....ollllllo....",
        ".....ollllo.....",
        "......oooo......",
        "................",
    ],
    "tonic": [
        "......oooo......",
        "......occo......",
        ".....ommmmo.....",
        "......oggo......",
        "...oooggggooo...",
        "..oggggggggggo..",
        "..ollllllllllo..",
        "..ollllllllllo..",
        "..ollllllllllo..",
        "..ollllllllllo..",
        "..ollllllllllo..",
        "..ollllllllllo..",
        "..ollllllllllo..",
        "..ommmmmmmmmmo..",
        "..oooooooooooo..",
        "................",
    ],
    "elixir": [
        ".......oo.......",
        "......ommo......",
        "......occo......",
        ".....ommmmo.....",
        "......oggo......",
        ".....oggggo.....",
        "....ollllllo....",
        "...ollllllllo...",
        "..ollllllllllo..",
        "..ollllllllllo..",
        "...ollllllllo...",
        "....ollllllo....",
        ".....ollllo.....",
        "......ollo......",
        ".....ommmmo.....",
        "................",
    ],
    "phial": [
        "......oooo......",
        "......occo......",
        "......occo......",
        "......oggo......",
        "......oggo......",
        ".....oggggo.....",
        ".....oggggo.....",
        "....ollllllo....",
        "....ollllllo....",
        "...ollllllllo...",
        "...ollllllllo...",
        "..ollllllllllo..",
        "..ollllllllllo..",
        ".ollllllllllllo.",
        ".oooooooooooooo.",
        "................",
    ],
}

GLYPHS: dict[str, list[str]] = {
    "sword": ["....#", "...#.", "#.#..", ".#...", "#.#.."],
    "speed": ["#.#..", ".#.#.", "..#.#", ".#.#.", "#.#.."],
    "shield": ["#####", "#####", "#####", ".###.", "..#.."],
    "heart": [".#.#.", "#####", "#####", ".###.", "..#.."],
    "star": ["..#..", ".###.", "#####", ".###.", ".#.#."],
    "flame": ["..#..", ".##..", ".###.", "#####", ".###."],
    "pick": ["####.", "...#.", "..#.#", ".#...", "#...."],
    "arrow": ["..#..", ".###.", "#.#.#", "..#..", "..#.."],
    "eye": [".....", ".###.", "##.##", ".###.", "....."],
    "clover": [".#.#.", "#####", ".###.", "..#..", ".#..."],
    "feather": ["....#", "...##", "..##.", ".##..", "#...."],
    "drop": ["..#..", ".###.", "#####", "#####", ".###."],
    "moon": [".##..", "##...", "##...", "##...", ".##.."],
    "cross": ["..#..", "..#..", "#####", "..#..", "..#.."],
    "swirl": [".###.", "#....", "#.##.", "#...#", ".###."],
    "skull": [".###.", "#.#.#", "#####", ".###.", ".#.#."],
    "bolt": ["..##.", ".##..", "####.", "..##.", ".##.."],
    "coin": [".###.", "#...#", "#.#.#", "#...#", ".###."],
}

# 効果名 (kind の fx=) -> 紋章
FX_GLYPH = {
    "strength": "sword", "speed": "speed", "resistance": "shield", "regeneration": "heart",
    "absorption": "star", "fire_resistance": "flame", "haste": "pick", "jump_boost": "arrow",
    "night_vision": "eye", "luck": "clover", "slow_falling": "feather", "water_breathing": "drop",
    "invisibility": "moon",
}


def glyph_for_kind(kind: str) -> str | None:
    """消耗品の kind (mix:heal=20;fx=speed,60,1 …) の最初の効果から紋章を選ぶ。"""
    for part in kind.removeprefix("mix:").split(";"):
        if part.startswith("fx="):
            name = part[3:].split(",")[0]
            if name in FX_GLYPH:
                return FX_GLYPH[name]
        elif part.startswith("heal="):
            return "cross"
        elif part.startswith("gauge="):
            return "swirl"
    return None


def potion(liquid, form: str = "vial", glyph: str | None = None) -> Image.Image:
    rows = FORMS[form]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    glass = mix((214, 228, 240, 255), liquid, 0.18)
    deep = shade(liquid, 0.74)
    lq = []
    for y, row in enumerate(rows):
        assert len(row) == 16, (form, y, row)
        for x, ch in enumerate(row):
            if ch == "o":
                px[x, y] = OUTLINE
            elif ch == "g":
                px[x, y] = glass
            elif ch == "c":
                px[x, y] = CORK
            elif ch == "m":
                px[x, y] = GOLD
            elif ch == "l":
                lq.append((x, y))
    if not lq:
        return img
    top = min(y for _, y in lq)
    bot = max(y for _, y in lq)
    for x, y in lq:
        # 液面は明るく、底へ向かって沈める (2 段)
        px[x, y] = mix(liquid, WHITE, 0.35) if y == top else (deep if y >= bot - 1 else liquid)
    # ガラスの照り返し: 液体部の左内側に縦の光、肩に一点
    xs_left = {}
    for x, y in lq:
        xs_left[y] = min(xs_left.get(y, 99), x)
    for y in range(top + 1, min(bot, top + 4)):
        if y in xs_left:
            px[xs_left[y], y] = mix(liquid, WHITE, 0.7)
    if glyph:
        g = GLYPHS[glyph]
        cx = (min(x for x, _ in lq) + max(x for x, _ in lq) + 1) // 2
        cy = (top + 1 + bot + 1) // 2
        bright = sum(liquid[:3]) >= 600
        gc = shade(liquid, 0.30) if bright else mix(liquid, WHITE, 0.92)
        sh = shade(liquid, 0.55) if not bright else mix(liquid, WHITE, 0.6)
        ox, oy = cx - 2, cy - 2
        inside = set(lq)
        on = {(ox + gx, oy + gy) for gy, grow in enumerate(g) for gx, ch in enumerate(grow) if ch == "#"}
        # 紋章の影 (右下 1px) を先に置いてから紋章本体
        for x, y in on:
            if (x + 1, y + 1) in inside and (x + 1, y + 1) not in on:
                px[x + 1, y + 1] = sh
        for x, y in on:
            if (x, y) in inside:
                px[x, y] = gc
    return img


GLYPHS.update({
    "shuriken": ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"],
    "axe": [".##..", "###..", ".###.", "...#.", "....#"],
    "gun": ["####.", "#####", "..#..", "..#..", "....."],
})


def scroll(color, glyph: str | None = None) -> Image.Image:
    """巻物 (上下の軸 + 羊皮紙 + 色の紋章 + 封蝋の紐)。"""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    rows = [
        "................",
        ".oooooooooooooo.",
        ".owwwwwwwwwwwwo.",
        ".oooooooooooooo.",
        "...oppppppppo...",
        "...oppppppppo...",
        "...oppppppppo...",
        "...oppppppppo...",
        "...oppppppppo...",
        "...oppppppppo...",
        "...oppppppppo...",
        "...oqqqqqqqqo...",
        ".oooooooooooooo.",
        ".owwwwwwwwwwwwo.",
        ".oooooooooooooo.",
        "................",
    ]
    wood = (136, 88, 52, 255)
    paper = (240, 228, 200, 255)
    pal = {"o": OUTLINE, "w": wood, "p": paper, "q": shade(paper, 0.82)}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                px[x, y] = pal[ch]
    # 軸の端は武器種色の飾り
    for x, y in ((1, 2), (14, 2), (1, 13), (14, 13)):
        px[x, y] = GOLD
    if glyph:
        gc = shade(color, 0.42) if sum(color[:3]) > 450 else color
        for gy, grow in enumerate(GLYPHS[glyph]):
            for gx, ch in enumerate(grow):
                if ch == "#":
                    px[5 + gx, 5 + gy] = gc
    # 封蝋の紐 (右下に垂れる)
    px[11, 12] = color
    px[12, 13] = color
    px[12, 14] = shade(color, 0.7)
    return img
