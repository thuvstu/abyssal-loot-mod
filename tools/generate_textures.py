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

# 深淵MOBの固有テクスチャ (原作 ENEMIES / BOSSES の color・accent)
ENTITIES = {
    "slime": (0x4bd07a, 0xc8ffe0),
    "splitter": (0x7dd6a0, 0xd6ffe8),
    "bat": (0x9d6bff, 0xe6d6ff),
    "mirrorbat": (0x5cd8ff, 0xe0f8ff),
    "goblin": (0x7ec850, 0xf2ffd6),
    "skeleton": (0xdfe6ee, 0x8b95a5),
    "imp": (0xff7a3c, 0xffd9a8),
    "bomber": (0xffb03c, 0xfff0c9),
    "wraith": (0x6ad7ff, 0xe6fbff),
    "golem": (0xa58a6b, 0xffd9a0),
    "phantom": (0xa070ff, 0xe8dcff),
    "cultist": (0xff8ac2, 0xffd6ec),
    "eye": (0xff5c9d, 0xffd6e8),
    "boss_eye": (0xff5cc8, 0xffd6f2),
    "charger": (0xc96a4a, 0xffe0cf),
    "dragonkin": (0xe0455f, 0xffd0d8),
    "guard": (0x7d9eb9, 0xd7ebff),
    "necromancer": (0xb28ce0, 0xe9d2ff),
    "gate": (0xbd517d, 0xffc4d8),
    "goblin_king": (0x8fd44f, 0xffe98a),
    "wyrm": (0x7c5cff, 0xd9c9ff),
    "knight": (0xff3b5c, 0xffc9d2),
    "nihil": (0xb14bff, 0xf0d6ff),
    "zero": (0xffffff, 0xe34bff),
    "omega": (0xff2a55, 0xffd700),
    "ascender": (0xffd700, 0xffffff),
}


def entity_tex(base, accent):
    """64x64 の縦グラデーション + 斑点。箱モデルの全パート原点参照用"""
    import random
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    dark = tuple(max(0, c - 70) for c in base[:3]) + (255,)
    for y in range(64):
        t = y / 63
        if t < 0.25:
            c = accent
        elif t < 0.7:
            k = (t - 0.25) / 0.45
            c = tuple(int(accent[i] + (base[i] - accent[i]) * k) for i in range(3)) + (255,)
        else:
            k = (t - 0.7) / 0.3
            c = tuple(int(base[i] + (dark[i] - base[i]) * k) for i in range(3)) + (255,)
        d.line([(0, y), (63, y)], fill=c)
    rnd = random.Random(0)
    for _ in range(260):
        x = rnd.randrange(64)
        y = rnd.randrange(64)
        s = base if rnd.random() < 0.7 else accent
        d.point((x, y), fill=s)
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
    for ent_id, (base, accent) in ENTITIES.items():
        entity_tex(hexcolor(base), hexcolor(accent)).save(os.path.join(ent_dir, f"{ent_id}.png"))
    print(f"generated {len(WEAPONS) + 1 + len(MATERIALS)} textures + {len(ENTITIES)} entity textures")


if __name__ == "__main__":
    main()
