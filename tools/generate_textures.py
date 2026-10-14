#!/usr/bin/env python3
"""Abyssal Loot のアイテムテクスチャ(16x16ドット絵)とモデルJSONを生成する.

再生成: /tmp/tsenv/bin/python tools/generate_textures.py   (Pillow が必要: pip install pillow)
出力先: src/main/resources/assets/abyssalloot/{textures,models}/item/
武器ごとに形状を変え、武器の色 (GameData.WeaponStyle.color) で刃・柄の差し色を決める.
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot")
TEX_DIR = os.path.join(ASSETS, "textures", "item")
MODEL_DIR = os.path.join(ASSETS, "models", "item")

OUTLINE = (24, 18, 36, 255)
STEEL = (206, 214, 226, 255)
STEEL_DARK = (120, 130, 150, 255)
WOOD = (120, 78, 46, 255)
WOOD_DARK = (74, 46, 28, 255)
GOLD = (232, 190, 84, 255)


def hexcolor(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)


def canvas():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def line(d, pts, color, width=1):
    """暗い縁取り付きの線 (ドット絵の輪郭を出すため)."""
    d.line(pts, fill=OUTLINE, width=width + 2)
    d.line(pts, fill=color, width=width)


def poly(d, pts, color):
    d.polygon(pts, fill=OUTLINE)
    inner = [(x, y) for x, y in pts]
    d.polygon(inner, fill=color)
    d.line(pts + [pts[0]], fill=OUTLINE, width=1)


def blade_glint(d, pts, color):
    d.line(pts, fill=color, width=1)


def sword(accent):
    img, d = canvas()
    line(d, [(3, 13), (11, 5)], STEEL, 2)
    line(d, [(2, 9), (6, 13)], GOLD, 1)  # 鍔
    line(d, [(3, 12), (2, 14)], WOOD, 1)  # 柄
    d.point((10, 6), fill=accent)
    d.point((11, 4), fill=(255, 255, 255, 255))
    return img


def axe(accent):
    img, d = canvas()
    line(d, [(4, 14), (9, 7)], WOOD, 1)
    poly(d, [(8, 3), (13, 2), (14, 7), (9, 9)], STEEL)
    line(d, [(9, 7), (13, 3)], STEEL_DARK, 1)
    d.point((11, 5), fill=accent)
    return img


def spear(accent):
    img, d = canvas()
    line(d, [(2, 14), (10, 6)], WOOD, 1)
    poly(d, [(9, 2), (14, 4), (10, 7)], STEEL)
    d.point((11, 4), fill=accent)
    line(d, [(10, 6), (12, 8)], GOLD, 1)
    return img


def staff(accent):
    img, d = canvas()
    line(d, [(8, 15), (8, 7)], WOOD, 1)
    d.ellipse([5, 1, 11, 7], fill=OUTLINE)
    d.ellipse([6, 2, 10, 6], fill=accent)
    d.point((7, 3), fill=(255, 255, 255, 255))
    line(d, [(5, 7), (11, 7)], GOLD, 1)
    return img


def bow(accent):
    img, d = canvas()
    d.arc([1, 1, 12, 15], start=-70, end=70, fill=OUTLINE, width=3)
    d.arc([2, 2, 11, 14], start=-70, end=70, fill=WOOD, width=1)
    d.line([(9, 2), (9, 14)], fill=(230, 230, 230, 255), width=1)
    d.line([(9, 8), (15, 8)], fill=accent, width=1)  # 矢
    d.point((15, 8), fill=STEEL)
    return img


def dagger(accent):
    img, d = canvas()
    poly(d, [(6, 3), (10, 9), (8, 10), (5, 6)], STEEL)
    d.point((7, 5), fill=accent)
    line(d, [(6, 10), (10, 6)], GOLD, 1)  # 鍔
    line(d, [(8, 11), (4, 14)], WOOD, 1)
    return img


def hammer(accent):
    img, d = canvas()
    line(d, [(4, 15), (9, 8)], WOOD, 1)
    poly(d, [(7, 2), (14, 4), (13, 9), (6, 8)], STEEL_DARK)
    d.rectangle([9, 4, 11, 6], fill=accent)
    return img


def katana(accent):
    img, d = canvas()
    d.arc([2, -2, 16, 12], start=200, end=280, fill=OUTLINE, width=3)
    d.arc([3, -1, 15, 11], start=200, end=280, fill=STEEL, width=1)
    line(d, [(2, 13), (5, 11)], WOOD_DARK, 2)  # 柄
    d.point((12, 4), fill=accent)
    return img


def scythe(accent):
    img, d = canvas()
    line(d, [(3, 15), (9, 5)], WOOD, 1)
    d.arc([5, 0, 15, 10], start=180, end=300, fill=OUTLINE, width=3)
    d.arc([6, 1, 14, 9], start=180, end=300, fill=STEEL, width=1)
    d.point((12, 3), fill=accent)
    return img


def gauntlet(accent):
    img, d = canvas()
    d.rectangle([4, 7, 11, 13], fill=OUTLINE)
    d.rectangle([5, 8, 10, 12], fill=GOLD)
    for x in (6, 8, 10):
        d.line([(x, 3), (x, 7)], fill=OUTLINE, width=2)
        d.line([(x, 3), (x, 7)], fill=GOLD, width=1)
    d.rectangle([6, 9, 9, 10], fill=accent)
    return img


def chakram(accent):
    img, d = canvas()
    d.ellipse([1, 1, 15, 15], outline=OUTLINE, width=3)
    d.ellipse([2, 2, 14, 14], outline=STEEL, width=1)
    d.ellipse([6, 6, 10, 10], fill=accent, outline=OUTLINE)
    return img


def tome(accent):
    img, d = canvas()
    d.rectangle([2, 3, 13, 13], fill=OUTLINE)
    d.rectangle([3, 4, 12, 12], fill=accent)
    d.line([(4, 5), (4, 11)], fill=GOLD, width=1)  # 背
    d.line([(7, 6), (11, 6)], fill=(245, 240, 230, 255), width=1)
    d.line([(7, 8), (11, 8)], fill=(245, 240, 230, 255), width=1)
    d.point((9, 10), fill=(255, 255, 255, 255))
    return img


def shard():
    img, d = canvas()
    poly(d, [(8, 1), (13, 7), (8, 15), (3, 7)], (80, 200, 255, 255))
    poly(d, [(8, 4), (11, 7), (8, 12), (5, 7)], (150, 230, 255, 255))
    d.point((7, 5), fill=(255, 255, 255, 255))
    return img


WEAPONS = {
    "abyss_sword": (sword, 0xd6e8ff, "handheld"),
    "abyss_axe": (axe, 0xff9465, "handheld"),
    "abyss_spear": (spear, 0x86dcf4, "handheld"),
    "abyss_staff": (staff, 0xbca2ff, "handheld"),
    "abyss_bow": (bow, 0x92ebbd, "handheld"),
    "abyss_dagger": (dagger, 0xe4c8ff, "handheld"),
    "abyss_hammer": (hammer, 0xf3cf75, "handheld"),
    "abyss_katana": (katana, 0xffb5bf, "handheld"),
    "abyss_scythe": (scythe, 0xaff097, "handheld"),
    "abyss_gauntlet": (gauntlet, 0xffd092, "handheld"),
    "abyss_chakram": (chakram, 0x91f2eb, "generated"),
    "abyss_tome": (tome, 0xde9df7, "generated"),
}


def gem(outer, inner):
    img, d = canvas()
    poly(d, [(8, 2), (12, 6), (8, 14), (4, 6)], outer)
    poly(d, [(8, 5), (10, 7), (8, 11), (6, 7)], inner)
    d.point((7, 5), fill=(255, 255, 255, 255))
    return img


def ancient_stone(accent):
    img, d = canvas()
    poly(d, [(4, 4), (12, 4), (12, 12), (4, 12)], (120, 110, 90, 255))
    poly(d, [(6, 6), (10, 6), (10, 10), (6, 10)], (245, 197, 66, 255))
    d.point((7, 7), fill=(255, 255, 255, 255))
    return img


MATERIALS = {
    # 原作 MATERIALS の色に対応 (魂の欠片は shard() のまま)
    "ancient_stone": 0xf5c542,
    "chaos_gem": 0xe34bff,
    "void_essence": 0x7c8cff,
}

# 深淵MOBの固有テクスチャ (原作 ENEMIES / BOSSES の color・accent・shape準拠)。
# (base, accent, pattern): pattern は FREE ゾーン (0,48,64,16) の模様。
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
        _speckle(d, rnd, x0, y0, x1, y1, [accent, _shade(accent, 0.85)], 26, 2)
        for _ in range(8):
            x = rnd.randrange(x0, x1 - 2)
            y = rnd.randrange(y0, y1 - 2)
            d.ellipse([x, y, x + 2, y + 2], outline=accent)
    elif pattern == "membrane":
        for i in range(8):
            x = x0 + i * 8
            d.line([(x, y0), (x + 3, y1)], fill=_shade(base, 0.55))
        _speckle(d, rnd, x0, y0, x1, y1, [accent], 20)
    elif pattern == "cracks":
        for _ in range(7):
            x = rnd.randrange(x0, x1)
            y = rnd.randrange(y0, y1)
            d.line([(x, y), (x + rnd.randrange(-6, 7), y + rnd.randrange(2, 7))], fill=dark)
        _speckle(d, rnd, x0, y0, x1, y1, [accent], 24)
    elif pattern == "flames":
        for i in range(16):
            x = x0 + i * 4
            h = rnd.randrange(4, 14)
            d.line([(x, y1 - 1), (x + 1, y1 - h)], fill=accent if i % 2 == 0 else _shade(accent, 0.8))
    elif pattern == "mist":
        for _ in range(10):
            y = rnd.randrange(y0, y1)
            d.line([(x0, y), (x1 - 1, y)], fill=_shade(accent, rnd.random() * 0.4 + 0.5))
    elif pattern == "veins":
        for _ in range(9):
            x = rnd.randrange(x0, x1)
            d.line([(x, y0), (x + rnd.randrange(-8, 9), y1 - 1)], fill=_shade(base, 0.45))
        _speckle(d, rnd, x0, y0, x1, y1, [accent], 16)
    elif pattern == "scales":
        for row in range(4):
            y = y0 + row * 4
            for i in range(8):
                x = x0 + i * 8 + (4 if row % 2 else 0)
                d.arc([x, y, x + 7, y + 7], 180, 360, fill=_shade(base, 0.6))
        _speckle(d, rnd, x0, y0, x1, y1, [accent], 12)
    elif pattern == "plate":
        d.rectangle([x0 + 2, y0 + 2, x1 - 3, y1 - 3], outline=accent)
        for x in (x0 + 6, x1 - 7):
            for y in (y0 + 4, y1 - 5):
                d.point((x, y), fill=accent)
        _speckle(d, rnd, x0 + 4, y0 + 4, x1 - 4, y1 - 4, [_shade(base, 0.8)], 30)
    elif pattern == "runes":
        for _ in range(8):
            x = rnd.randrange(x0 + 2, x1 - 4)
            y = rnd.randrange(y0 + 2, y1 - 8)
            d.line([(x, y), (x, y + 5)], fill=accent)
            d.line([(x, y + 1), (x + 3, y + 3)], fill=accent)
            d.line([(x, y + 3), (x + 3, y + 1)], fill=accent)
    elif pattern == "starfield":
        for _ in range(40):
            d.point((rnd.randrange(x0, x1), rnd.randrange(y0, y1)),
                    fill=WHITEC if rnd.random() < 0.6 else accent)
    elif pattern == "crown":
        _vgrad(d, x0, y0, x1, y1, [(0, (255, 220, 120)), (1, (150, 105, 30))])
        for i in range(6):
            x = x0 + 5 + i * 10
            d.point((x, y0 + 5), fill=(255, 80, 80, 255) if i % 2 == 0 else (80, 220, 255, 255))
    else:
        _speckle(d, rnd, x0, y0, x1, y1, [accent], 30)


def entity_tex(base, accent, pattern):
    """64x64。標準ゾーン分割 (AbyssModels の UV と対応)。
    BODY(0,0,48,32) / BELLY(48,0,16,32) / DARK(0,32,16,16) /
    WHITE(16,32,16,16) / ACC(32,32,32,16) / FREE(0,48,64,16)+模様"""
    import random
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rnd = random.Random(0)
    dark = _shade(base, 0.45)
    # BODY: 上アクセント→本体→下暗部
    _vgrad(d, 0, 0, 48, 32, [(0, accent), (0.3, base), (1, dark)])
    _speckle(d, rnd, 0, 0, 48, 32, [base, _shade(base, 1.08), dark], 130)
    # BELLY: 明部
    _vgrad(d, 48, 0, 64, 32, [(0, WHITEC), (0.5, accent), (1, _shade(accent, 0.7))])
    # DARK: 目・口
    d.rectangle([0, 32, 15, 47], fill=DARKC)
    _speckle(d, rnd, 0, 32, 16, 48, [_shade(DARKC[:3] + (255,), 1.0)], 0)
    d.point((3, 35), fill=WHITEC)
    d.point((11, 35), fill=WHITEC)
    # WHITE: 白目・牙・光輪
    _vgrad(d, 16, 32, 32, 48, [(0, WHITEC), (1, _shade(WHITEC[:3] + (255,), 0.75))])
    # ACC: 発光部
    _vgrad(d, 32, 32, 64, 48, [(0, accent), (1, _shade(accent, 0.65))])
    _speckle(d, rnd, 32, 32, 64, 48, [WHITEC, accent], 40)
    # FREE: 種別模様
    _vgrad(d, 0, 48, 64, 64, [(0, base), (1, dark)])
    _pattern_free(d, rnd, pattern, base, accent, dark)
    return img


ITEMDEF_DIR = os.path.join(ASSETS, "items")


def write_model(item_id, parent):
    model = {"parent": f"minecraft:item/{parent}",
             "textures": {"layer0": f"abyssalloot:item/{item_id}"}}
    with open(os.path.join(MODEL_DIR, f"{item_id}.json"), "w", encoding="utf-8") as f:
        json.dump(model, f, indent=2, ensure_ascii=False)
        f.write("\n")
    # MC 1.21.4+ は items/*.json の定義が必須 (models/item 直置きは読まれない)
    itemdef = {"model": {"type": "minecraft:model", "model": f"abyssalloot:item/{item_id}"}}
    with open(os.path.join(ITEMDEF_DIR, f"{item_id}.json"), "w", encoding="utf-8") as f:
        json.dump(itemdef, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(MODEL_DIR, exist_ok=True)
    os.makedirs(ITEMDEF_DIR, exist_ok=True)
    for item_id, (fn, color, parent) in WEAPONS.items():
        fn(hexcolor(color)).save(os.path.join(TEX_DIR, f"{item_id}.png"))
        write_model(item_id, parent)
    shard().save(os.path.join(TEX_DIR, "soul_shard.png"))
    write_model("soul_shard", "generated")
    for item_id, color in MATERIALS.items():
        if item_id == "ancient_stone":
            ancient_stone(hexcolor(color)).save(os.path.join(TEX_DIR, f"{item_id}.png"))
        else:
            gem(hexcolor(color), (230, 240, 255, 255)).save(os.path.join(TEX_DIR, f"{item_id}.png"))
        write_model(item_id, "generated")
    ent_dir = os.path.join(ASSETS, "textures", "entity")
    os.makedirs(ent_dir, exist_ok=True)
    for ent_id, (base, accent, pattern) in ENTITIES.items():
        entity_tex(hexcolor(base), hexcolor(accent), pattern).save(os.path.join(ent_dir, f"{ent_id}.png"))
    print(f"generated {len(WEAPONS) + 1 + len(MATERIALS)} textures + {len(ENTITIES)} entity textures")


if __name__ == "__main__":
    main()
