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


STEEL_LIGHT = (232, 238, 248, 255)
WHITE = (255, 255, 255, 255)


def _dia_blade(d, x0, y0, x1, y1, accent):
    """斜め刃の基本形: 幅3の鋼 + 樋 + 切先 + 二重煌き。握り側(x0,y0)→切先(x1,y1)"""
    line(d, [(x0, y0), (x1, y1)], STEEL_LIGHT, 3)
    line(d, [(x0, y0), (x1, y1)], STEEL, 1)
    d.line([(x0 + 1, y0), (x1, y1 - 1)], fill=STEEL_DARK, width=1)  # 樋
    tx, ty = x1 + 1, y1 - 1
    poly(d, [(x1 - 1, y1 + 1), (tx + 1, ty - 1), (x1 + 1, y1 + 3)], STEEL_LIGHT)  # 切先
    d.point((x0 + (x1 - x0) * 2 // 3, y0 + (y1 - y0) * 2 // 3), fill=WHITE)  # 煌き (中央)
    d.point((x0 + (x1 - x0) * 5 // 6, y0 + (y1 - y0) * 5 // 6), fill=WHITE)  # 煌き (切先側)
    d.point((x0 + (x1 - x0) // 3 + 1, y0 + (y1 - y0) // 3), fill=accent)


def _guard_grip(d, gx, gy, accent):
    """鍔(金・斜め) + 柄(木) + 柄頭(金・種別色)。(gx,gy)=鍔中心"""
    line(d, [(gx - 2, gy - 2), (gx + 2, gy + 2)], GOLD, 1)
    line(d, [(gx + 1, gy + 1), (gx - 2, gy + 4)], WOOD, 2)
    line(d, [(gx - 1, gy + 3), (gx - 2, gy + 4)], WOOD_DARK, 1)
    d.rectangle([gx - 3, gy + 3, gx - 1, gy + 5], fill=OUTLINE)
    d.rectangle([gx - 2, gy + 4, gx - 2, gy + 4], fill=accent)
    d.point((gx, gy), fill=accent)


def sword(accent):
    img, d = canvas()
    _dia_blade(d, 4, 12, 13, 3, accent)
    _guard_grip(d, 3, 11, accent)
    return img


def axe(accent):
    img, d = canvas()
    line(d, [(2, 15), (9, 6)], WOOD, 2)
    line(d, [(2, 15), (9, 6)], WOOD_DARK, 1)
    poly(d, [(7, 1), (15, 0), (15, 7), (9, 10)], STEEL_LIGHT)  # 双刃
    poly(d, [(9, 3), (13, 2), (13, 6), (9, 8)], STEEL)
    d.line([(9, 3), (13, 2)], fill=WHITE, width=1)  # 刃先の光
    d.point((12, 4), fill=accent)
    d.point((11, 5), fill=WHITE)
    line(d, [(7, 8), (9, 10)], GOLD, 1)  # 柄巻
    line(d, [(4, 12), (6, 10)], GOLD, 1)
    d.point((8, 4), fill=OUTLINE)  # 目釘
    return img


def spear(accent):
    img, d = canvas()
    line(d, [(1, 15), (10, 6)], WOOD, 1)
    line(d, [(2, 14), (9, 7)], WOOD_DARK, 1)
    poly(d, [(9, 1), (14, 4), (10, 8)], STEEL_LIGHT)  # 穂先
    poly(d, [(10, 3), (12, 5), (10, 7)], STEEL)
    d.line([(10, 3), (12, 5)], fill=WHITE, width=1)
    line(d, [(8, 8), (10, 10)], GOLD, 1)  # 鎺
    d.line([(7, 9), (5, 11)], fill=accent, width=1)  # 房
    d.point((11, 4), fill=accent)
    return img


def staff(accent):
    img, d = canvas()
    line(d, [(7, 15), (7, 8)], WOOD, 2)
    line(d, [(7, 15), (7, 8)], WOOD_DARK, 1)
    line(d, [(4, 8), (10, 8)], GOLD, 1)  # 受け座
    d.ellipse([4, 0, 11, 7], fill=OUTLINE)
    d.ellipse([5, 1, 10, 6], fill=accent)  # 宝珠
    d.ellipse([6, 2, 8, 4], fill=WHITE)
    d.point((9, 2), fill=WHITE)
    d.point((3, 3), fill=accent)
    d.point((11, 5), fill=accent)
    return img


def bow(accent):
    img, d = canvas()
    d.arc([0, 0, 11, 15], start=-75, end=75, fill=OUTLINE, width=4)
    d.arc([1, 1, 10, 14], start=-75, end=75, fill=WOOD, width=2)
    d.arc([1, 1, 10, 14], start=-30, end=30, fill=accent, width=1)  # 握り巻
    d.line([(8, 1), (8, 14)], fill=(230, 230, 230, 255), width=1)  # 弦
    d.line([(8, 8), (14, 8)], fill=WOOD_DARK, width=1)  # 矢柄
    poly(d, [(13, 7), (15, 8), (13, 9)], STEEL_LIGHT)  # 矢尻
    d.point((9, 8), fill=accent)
    d.point((5, 3), fill=accent)
    d.point((5, 12), fill=accent)
    return img


def dagger(accent):
    img, d = canvas()
    _dia_blade(d, 7, 9, 13, 3, accent)
    line(d, [(5, 7), (8, 10)], GOLD, 1)  # S字鍔
    line(d, [(6, 10), (3, 13)], WOOD, 2)
    d.point((4, 12), fill=accent)
    d.point((3, 13), fill=GOLD)
    return img


def hammer(accent):
    img, d = canvas()
    line(d, [(3, 15), (8, 8)], WOOD, 2)
    line(d, [(3, 15), (8, 8)], WOOD_DARK, 1)
    poly(d, [(6, 1), (15, 3), (13, 10), (4, 8)], STEEL_DARK)  # 大頭
    poly(d, [(7, 3), (13, 4), (12, 8), (6, 7)], STEEL_LIGHT)
    d.rectangle([9, 4, 11, 6], fill=accent)  # 核
    d.point((8, 5), fill=WHITE)
    line(d, [(5, 12), (7, 10)], GOLD, 1)  # 柄巻
    return img


def katana(accent):
    img, d = canvas()
    poly(d, [(2, 10), (7, 6), (12, 2), (14, 1), (13, 4), (8, 8), (3, 12)], STEEL_LIGHT)  # 反り刃
    poly(d, [(3, 10), (7, 7), (11, 3), (12, 3), (11, 5), (7, 8), (4, 11)], STEEL)
    d.line([(4, 10), (11, 3)], fill=WHITE, width=1)  # 反りの光
    d.point((12, 3), fill=accent)
    d.ellipse([0, 9, 4, 13], fill=OUTLINE)  # 鍔(円)
    d.ellipse([1, 10, 3, 12], fill=GOLD)
    d.point((2, 11), fill=accent)
    line(d, [(1, 12), (0, 15)], WOOD_DARK, 2)  # 柄
    d.line([(1, 13), (0, 14)], fill=accent, width=1)  # 柄巻
    return img


def scythe(accent):
    img, d = canvas()
    line(d, [(2, 15), (8, 5)], WOOD, 2)
    line(d, [(2, 15), (8, 5)], WOOD_DARK, 1)
    d.arc([4, -1, 16, 11], start=170, end=305, fill=OUTLINE, width=4)
    d.arc([5, 0, 15, 10], start=170, end=305, fill=STEEL_LIGHT, width=2)
    d.arc([5, 0, 15, 10], start=180, end=230, fill=WHITE, width=1)
    d.point((13, 2), fill=accent)
    line(d, [(7, 6), (9, 4)], GOLD, 1)  # 目釘座
    d.line([(4, 12), (3, 13)], fill=accent, width=1)
    return img


def gauntlet(accent):
    img, d = canvas()
    d.rectangle([3, 9, 11, 14], fill=OUTLINE)  # 手甲
    d.rectangle([4, 10, 10, 13], fill=GOLD)
    d.line([(4, 10), (10, 10)], fill=STEEL_LIGHT, width=1)
    for i, x in enumerate((4, 6, 8, 10)):
        d.rectangle([x, 4, x + 1, 9], fill=OUTLINE)  # 指
        d.rectangle([x, 5, x, 8], fill=STEEL if i % 2 == 0 else STEEL_LIGHT)
    d.rectangle([5, 11, 8, 12], fill=accent)  # 拳核
    d.point((6, 11), fill=WHITE)
    return img


def chakram(accent):
    img, d = canvas()
    d.ellipse([0, 0, 15, 15], outline=OUTLINE, width=4)
    d.ellipse([1, 1, 14, 14], outline=STEEL_LIGHT, width=2)
    for cx, cy in ((7, 0), (7, 14), (0, 7), (14, 7)):
        d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=OUTLINE)  # 外刃
        d.point((cx, cy), fill=STEEL_LIGHT)
    d.ellipse([4, 4, 11, 11], outline=OUTLINE, width=2)
    d.ellipse([5, 5, 10, 10], outline=accent, width=1)  # 内輪
    d.ellipse([6, 6, 9, 9], fill=accent, outline=OUTLINE)
    d.point((7, 7), fill=WHITE)
    return img


def tome(accent):
    img, d = canvas()
    poly(d, [(1, 4), (12, 2), (14, 11), (3, 13)], (46, 28, 70, 255))  # 表紙
    poly(d, [(2, 5), (11, 3), (12, 10), (3, 12)], accent)
    d.line([(3, 6), (10, 4)], fill=(245, 240, 230, 255), width=1)  # 本文
    d.line([(3, 8), (10, 6)], fill=(245, 240, 230, 255), width=1)
    d.line([(4, 10), (9, 9)], fill=(245, 240, 230, 255), width=1)
    line(d, [(2, 5), (1, 13)], GOLD, 1)  # 背・留金
    d.rectangle([5, 6, 8, 9], fill=OUTLINE)  # 紋章枠
    d.point((6, 7), fill=WHITE)
    d.point((7, 8), fill=GOLD)
    return img


# ---------------- 追加武器12種 (原作色・固有形状) ----------------

def tome(accent):
    img, d = canvas()
    poly(d, [(1, 4), (12, 2), (14, 11), (3, 13)], (46, 28, 70, 255))  # 表紙
    poly(d, [(2, 5), (11, 3), (12, 10), (3, 12)], accent)
    d.line([(3, 6), (10, 4)], fill=(245, 240, 230, 255), width=1)  # 本文
    d.line([(3, 8), (10, 6)], fill=(245, 240, 230, 255), width=1)
    d.line([(4, 10), (9, 9)], fill=(245, 240, 230, 255), width=1)
    line(d, [(2, 5), (1, 13)], GOLD, 1)  # 背・留金
    d.rectangle([5, 6, 8, 9], fill=OUTLINE)  # 紋章枠
    d.point((6, 7), fill=WHITE)
    d.point((7, 8), fill=GOLD)
    return img


def rune_blade(accent):
    img, d = canvas()
    _dia_blade(d, 4, 12, 13, 3, accent)
    line(d, [(2, 8), (6, 12)], GOLD, 1)  # 十字鍔
    line(d, [(4, 12), (2, 14)], WOOD_DARK, 2)
    for x, y in ((7, 9), (9, 7), (11, 5)):  # ルーン刻み
        d.point((x, y), fill=accent)
        d.point((x + 1, y), fill=WHITE)
    d.point((3, 13), fill=accent)
    return img


def blood_axe(accent):
    img, d = canvas()
    line(d, [(2, 15), (9, 6)], WOOD_DARK, 2)
    poly(d, [(7, 1), (15, 2), (14, 9), (8, 10)], STEEL_LIGHT)  # 大刃
    poly(d, [(10, 3), (14, 4), (13, 8), (9, 9)], accent)  # 血刃
    d.line([(10, 3), (14, 4)], fill=WHITE, width=1)
    d.line([(8, 10), (6, 12)], fill=accent, width=1)  # 血雫
    d.point((12, 6), fill=WHITE)
    line(d, [(5, 12), (7, 10)], GOLD, 1)
    return img


def storm_spear(accent):
    img, d = canvas()
    line(d, [(1, 15), (9, 7)], WOOD, 1)
    poly(d, [(8, 0), (11, 1), (10, 6)], STEEL_LIGHT)  # 主穂
    poly(d, [(11, 2), (14, 3), (11, 6)], STEEL_LIGHT)  # 副穂
    d.line([(9, 2), (10, 5)], fill=WHITE, width=1)
    d.line([(12, 3), (11, 5)], fill=WHITE, width=1)
    d.point((10, 4), fill=accent)
    line(d, [(7, 9), (9, 11)], GOLD, 1)
    d.line([(6, 10), (4, 12)], fill=accent, width=1)  # 雷房
    return img


def void_staff(accent):
    img, d = canvas()
    line(d, [(7, 15), (7, 8)], WOOD_DARK, 2)
    d.ellipse([3, 6, 12, 9], outline=GOLD, width=1)  # 環
    d.ellipse([4, 0, 11, 7], fill=OUTLINE)
    d.ellipse([5, 1, 10, 6], fill=(20, 8, 40, 255))  # 虚核
    d.ellipse([6, 2, 9, 5], fill=accent)
    d.point((7, 3), fill=WHITE)
    d.point((4, 8), fill=accent)
    d.point((11, 8), fill=accent)
    return img


def moon_bow(accent):
    img, d = canvas()
    d.arc([0, 0, 11, 15], start=-75, end=75, fill=OUTLINE, width=4)
    d.arc([1, 1, 10, 14], start=-75, end=75, fill=WOOD, width=2)
    d.ellipse([3, 0, 6, 3], fill=accent, outline=OUTLINE)  # 上弦月
    d.ellipse([3, 12, 6, 15], fill=accent, outline=OUTLINE)  # 下弦月
    d.line([(8, 1), (8, 14)], fill=(230, 230, 230, 255), width=1)
    d.line([(8, 8), (14, 8)], fill=STEEL_LIGHT, width=1)
    poly(d, [(13, 7), (15, 8), (13, 9)], accent)
    d.point((5, 7), fill=WHITE)
    return img


def fang_dagger(accent):
    img, d = canvas()
    poly(d, [(6, 4), (13, 2), (12, 5), (7, 9)], STEEL_LIGHT)  # 牙刃
    for x, y in ((9, 5), (10, 4), (11, 3)):  # 鋸歯
        d.point((x, y), fill=OUTLINE)
    d.line([(7, 6), (12, 3)], fill=WHITE, width=1)
    line(d, [(5, 7), (8, 10)], GOLD, 1)
    line(d, [(6, 10), (3, 13)], WOOD_DARK, 2)
    d.point((7, 5), fill=accent)
    d.point((4, 12), fill=accent)
    return img


def quake_hammer(accent):
    img, d = canvas()
    line(d, [(3, 15), (8, 8)], WOOD, 2)
    poly(d, [(4, 0), (15, 2), (13, 11), (2, 9)], STEEL_DARK)  # 巨頭
    poly(d, [(6, 2), (13, 4), (11, 9), (4, 7)], STEEL_LIGHT)
    d.line([(6, 3), (12, 5)], fill=accent, width=1)  # 亀裂光
    d.line([(5, 6), (10, 8)], fill=accent, width=1)
    d.point((9, 5), fill=WHITE)
    line(d, [(5, 12), (7, 10)], GOLD, 1)
    return img


def frost_katana(accent):
    img, d = canvas()
    poly(d, [(2, 10), (7, 6), (12, 2), (14, 1), (13, 4), (8, 8), (3, 12)], STEEL_LIGHT)
    d.line([(4, 10), (11, 3)], fill=accent, width=1)  # 氷の反り
    poly(d, [(11, 1), (13, 0), (13, 3)], WHITE)  # 氷晶
    d.point((6, 6), fill=WHITE)
    d.ellipse([0, 9, 4, 13], fill=OUTLINE)
    d.ellipse([1, 10, 3, 12], fill=GOLD)
    line(d, [(1, 12), (0, 15)], WOOD_DARK, 2)
    d.point((3, 5), fill=accent)
    return img


def soul_scythe(accent):
    img, d = canvas()
    line(d, [(2, 15), (8, 5)], WOOD_DARK, 2)
    d.arc([4, -1, 16, 11], start=170, end=305, fill=OUTLINE, width=4)
    d.arc([5, 0, 15, 10], start=170, end=305, fill=STEEL_LIGHT, width=2)
    d.ellipse([10, 1, 13, 4], fill=accent, outline=OUTLINE)  # 魂玉
    d.point((11, 2), fill=WHITE)
    for x, y in ((6, 4), (8, 6), (5, 8)):  # 魂気
        d.point((x, y), fill=accent)
    line(d, [(7, 6), (9, 4)], GOLD, 1)
    return img


def titan_gauntlet(accent):
    img, d = canvas()
    d.rectangle([2, 8, 12, 14], fill=OUTLINE)  # 大手甲
    d.rectangle([3, 9, 11, 13], fill=STEEL_DARK)
    d.line([(3, 9), (11, 9)], fill=STEEL_LIGHT, width=1)
    for i, x in enumerate((3, 5, 7, 9, 11)):
        d.rectangle([x, 3, x + 1, 8], fill=OUTLINE)  # 指5本
        d.rectangle([x, 4, x, 7], fill=STEEL_LIGHT)
        d.point((x, 3), fill=accent)  # 爪先鋲
    d.rectangle([5, 10, 9, 12], fill=accent)  # 拳核
    d.point((6, 10), fill=WHITE)
    return img


def eclipse_chakram(accent):
    img, d = canvas()
    d.ellipse([0, 0, 15, 15], outline=OUTLINE, width=4)
    d.ellipse([1, 1, 14, 14], outline=(20, 10, 40, 255), width=2)  # 蝕輪
    for cx, cy in ((7, 0), (7, 14), (0, 7), (14, 7), (2, 2), (13, 2), (2, 13), (13, 13)):
        d.point((cx, cy), fill=accent)  # 輪刃の星
    d.ellipse([4, 4, 11, 11], outline=accent, width=2)
    d.ellipse([6, 6, 9, 9], fill=accent, outline=OUTLINE)
    d.ellipse([7, 7, 8, 8], fill=(20, 10, 40, 255))
    return img


def chaos_tome(accent):
    img, d = canvas()
    poly(d, [(1, 4), (12, 2), (14, 11), (3, 13)], (60, 10, 30, 255))  # 混沌表紙
    poly(d, [(3, 5), (10, 4), (11, 9), (4, 11)], accent)
    d.arc([5, 5, 10, 10], start=0, end=270, fill=(245, 240, 230, 255), width=1)  # 渦巻文
    d.point((7, 7), fill=WHITE)
    d.point((11, 3), fill=accent)
    d.point((2, 10), fill=accent)
    line(d, [(2, 5), (1, 13)], GOLD, 1)
    return img


def rod(accent):
    img, d = canvas()
    line(d, [(4, 15), (8, 7)], WOOD_DARK, 2)  # 長柄
    line(d, [(5, 14), (8, 8)], WOOD, 1)  # 捻り
    line(d, [(4, 14), (7, 9)], accent, 1)
    d.ellipse([4, 0, 12, 8], fill=OUTLINE)  # 大水晶
    poly(d, [(8, 1), (11, 4), (8, 7), (5, 4)], accent)
    poly(d, [(8, 3), (9, 4), (8, 5), (7, 4)], WHITE)
    d.point((5, 8), fill=GOLD)
    d.point((11, 8), fill=GOLD)
    d.point((10, 3), fill=accent)
    return img


def wand(accent):
    img, d = canvas()
    line(d, [(3, 14), (9, 8)], WOOD, 2)  # 短柄
    line(d, [(3, 14), (9, 8)], WOOD_DARK, 1)
    line(d, [(8, 9), (10, 7)], GOLD, 1)  # 口金
    d.ellipse([8, 2, 14, 8], fill=OUTLINE)  # 宝石
    d.ellipse([9, 3, 13, 7], fill=accent)
    d.ellipse([10, 4, 11, 5], fill=WHITE)
    d.point((6, 10), fill=accent)
    d.point((12, 9), fill=WHITE)
    return img


def greatsword(accent):
    img, d = canvas()
    line(d, [(3, 13), (12, 4)], STEEL_LIGHT, 4)  # 巨刃
    line(d, [(4, 13), (12, 5)], STEEL, 2)
    d.line([(4, 12), (11, 5)], fill=STEEL_DARK, width=1)  # 樋
    poly(d, [(11, 2), (15, 1), (13, 6)], STEEL_LIGHT)  # 切先
    d.line([(5, 11), (10, 6)], fill=WHITE, width=1)  # 刃文
    line(d, [(1, 10), (5, 14)], GOLD, 2)  # 大鍔
    line(d, [(3, 13), (1, 15)], WOOD_DARK, 2)
    d.point((6, 10), fill=accent)
    d.point((10, 6), fill=accent)
    return img


def twin_dagger(accent):
    img, d = canvas()
    poly(d, [(4, 2), (10, 4), (8, 8), (3, 6)], STEEL_LIGHT)  # 上の刃
    poly(d, [(12, 8), (14, 12), (10, 13), (8, 9)], STEEL_LIGHT)  # 下の刃
    d.line([(5, 4), (8, 6)], fill=WHITE, width=1)
    d.line([(11, 10), (9, 11)], fill=WHITE, width=1)
    d.ellipse([6, 6, 10, 10], fill=OUTLINE)  # 連結環
    d.ellipse([7, 7, 9, 9], fill=accent)
    d.point((8, 8), fill=WHITE)
    d.point((5, 3), fill=accent)
    d.point((12, 11), fill=accent)
    return img


def war_horn(accent):
    img, d = canvas()
    poly(d, [(2, 12), (6, 6), (12, 4), (13, 7), (7, 9), (4, 14)], WOOD)  # 角
    poly(d, [(6, 6), (12, 4), (12, 6), (7, 8)], STEEL_LIGHT)  # 金口
    d.line([(3, 11), (6, 7)], fill=accent, width=1)  # 紋帯
    d.line([(4, 12), (7, 8)], fill=accent, width=1)
    d.point((11, 5), fill=WHITE)
    d.point((5, 9), fill=GOLD)
    line(d, [(2, 13), (4, 15)], GOLD, 1)  # 尾金具
    return img


def wind_blade(accent):
    img, d = canvas()
    # 回転する風輪 (2枚の弧) + 中心核 + 風の走り
    d.arc([1, 1, 14, 14], start=200, end=330, fill=OUTLINE, width=4)
    d.arc([2, 2, 13, 13], start=200, end=330, fill=STEEL_LIGHT, width=2)
    d.arc([1, 1, 14, 14], start=20, end=150, fill=OUTLINE, width=4)
    d.arc([2, 2, 13, 13], start=20, end=150, fill=STEEL_LIGHT, width=2)
    d.arc([2, 2, 13, 13], start=30, end=70, fill=accent, width=1)  # 風の走り
    d.arc([2, 2, 13, 13], start=210, end=250, fill=accent, width=1)
    d.line([(4, 1), (7, 1)], fill=accent, width=1)  # 残風 (左上)
    d.line([(11, 14), (14, 12)], fill=accent, width=1)  # 残風 (右下)
    d.point((2, 6), fill=WHITE)
    d.ellipse([5, 5, 10, 10], fill=OUTLINE)          # 核の外輪
    d.ellipse([6, 6, 9, 9], fill=accent)
    d.ellipse([7, 7, 8, 8], fill=OUTLINE)            # 核の目玉
    d.point((7, 6), fill=WHITE)
    return img


def ember_flail(accent):
    img, d = canvas()
    line(d, [(8, 15), (8, 11)], WOOD_DARK, 2)  # 握り
    for i, (x, y) in enumerate(((8, 10), (7, 8), (9, 6), (8, 4))):  # 鎖 (明暗交互)
        link = STEEL_DARK if i % 2 else STEEL
        d.ellipse([x - 1, y - 1, x + 1, y + 1], outline=link)
        if i % 2:
            d.point((x, y), fill=WHITE)
    d.ellipse([4, 0, 12, 6], fill=OUTLINE)  # 分銅頭
    d.ellipse([5, 1, 11, 5], fill=STEEL_LIGHT)
    d.ellipse([6, 2, 10, 4], fill=accent)  # 焔核
    d.line([(6, 1), (10, 1)], fill=WHITE, width=1)
    d.point((4, 7), fill=accent)  # 飛び火
    d.point((12, 8), fill=accent)
    d.point((10, 11), fill=accent)
    return img


def astral_lance(accent):
    img, d = canvas()
    line(d, [(2, 15), (9, 8)], WOOD, 1)  # 柄
    poly(d, [(9, 2), (13, 6), (10, 9)], STEEL_LIGHT)  # 槍身
    poly(d, [(10, 4), (12, 6), (10, 8)], STEEL)
    d.ellipse([12, 1, 15, 4], fill=accent, outline=OUTLINE)  # 星
    d.point((13, 2), fill=WHITE)
    d.point((9, 5), fill=accent)
    d.point((11, 9), fill=accent)  # 流星の尾
    d.point((10, 11), fill=WHITE)
    d.point((3, 3), fill=WHITE)      # 小星
    d.point((5, 1), fill=accent)
    d.point((1, 6), fill=accent)
    return img


def tide_trident(accent):
    img, d = canvas()
    line(d, [(8, 15), (8, 7)], WOOD, 2)
    line(d, [(4, 3), (4, 12)], STEEL_DARK, 1)  # 左股
    line(d, [(12, 3), (12, 12)], STEEL_DARK, 1)
    line(d, [(4, 12), (8, 14)], STEEL_DARK, 1)
    line(d, [(12, 12), (8, 14)], STEEL_DARK, 1)
    line(d, [(4, 3), (4, 6)], STEEL_LIGHT, 2)
    line(d, [(12, 3), (12, 6)], STEEL_LIGHT, 2)
    line(d, [(8, 2), (8, 8)], STEEL_LIGHT, 2)  # 中股
    poly(d, [(3, 0), (5, 0), (4, 3)], STEEL_LIGHT)  # 左穂
    poly(d, [(11, 0), (13, 0), (12, 3)], STEEL_LIGHT)
    poly(d, [(7, 0), (9, 0), (8, 3)], STEEL_LIGHT)
    d.arc([1, 9, 6, 14], start=200, end=330, fill=accent, width=1)  # 渦の走り
    d.point((4, 5), fill=accent)
    d.point((12, 5), fill=accent)
    d.point((8, 5), fill=WHITE)
    d.point((14, 12), fill=WHITE)  # 水滴
    return img


def moon_mace(accent):
    img, d = canvas()
    line(d, [(5, 15), (9, 9)], WOOD, 2)  # 柄
    line(d, [(5, 15), (9, 9)], WOOD_DARK, 1)
    d.ellipse([6, 8, 14, 15], fill=OUTLINE)      # 三日月頭 (下弦)
    d.ellipse([7, 9, 13, 14], fill=STEEL_LIGHT)
    d.ellipse([4, 8, 13, 14], fill=OUTLINE)      # 二重で欠けを作る
    d.ellipse([5, 9, 11, 13], fill=(10, 8, 20, 255))
    d.point((13, 10), fill=accent)
    d.point((13, 11), fill=accent)
    d.point((12, 9), fill=WHITE)
    d.point((10, 6), fill=accent)                # 月の輪
    return img


def depth_gun(accent):
    img, d = canvas()
    line(d, [(3, 13), (6, 10)], WOOD_DARK, 2)    # 握り
    poly(d, [(5, 8), (14, 4), (15, 6), (7, 11)], STEEL_LIGHT)   # 銃身 (斜め)
    poly(d, [(7, 9), (12, 6), (13, 7), (8, 10)], STEEL)
    d.rectangle([12, 4, 14, 5], fill=accent)     # 銃口の照星
    d.point((13, 6), fill=WHITE)
    d.line([(6, 11), (8, 13)], fill=accent, width=1)  # 浮き彫りの紋
    d.point((5, 9), fill=accent)
    return img


def venom_whip(accent):
    img, d = canvas()
    line(d, [(3, 14), (7, 9)], WOOD_DARK, 2)     # 柄
    line(d, [(7, 9), (12, 6)], STEEL_DARK, 1)    # 鞭の骨
    d.arc([5, 1, 15, 11], start=100, end=320, fill=STEEL_LIGHT, width=1)  # 蛇行
    d.arc([6, 2, 14, 10], start=90, end=330, fill=accent, width=1)
    d.arc([7, 3, 13, 9], start=90, end=340, fill=STEEL, width=1)
    d.point((13, 2), fill=accent)                # 穂先 (毒珠)
    d.point((10, 4), fill=WHITE)
    d.point((12, 8), fill=accent)
    d.point((4, 12), fill=accent)
    return img


def flame_grimoire(accent):
    img, d = canvas()
    poly(d, [(1, 4), (12, 2), (14, 11), (3, 13)], (70, 22, 10, 255))  # 焔典表紙
    poly(d, [(2, 5), (11, 3), (12, 10), (3, 12)], accent)
    for x, h in ((4, 2), (6, 3), (8, 2), (10, 3)):  # 連載架の火柱
        d.rectangle([x, 8 - h, x, 7], fill=(255, 200, 80, 255))
        d.point((x, 8), fill=WHITE)
    d.line([(2, 5), (1, 13)], fill=GOLD, width=1)
    d.point((11, 3), fill=WHITE)
    return img


def rift_blade(accent):
    img, d = canvas()
    line(d, [(5, 13), (7, 10)], WOOD_DARK, 2)
    poly(d, [(7, 1), (14, 5), (11, 9), (6, 6)], STEEL_LIGHT)  # 不定形の刃
    poly(d, [(9, 3), (12, 6), (10, 8), (8, 6)], accent)
    d.line([(3, 10), (12, 3)], fill=accent, width=1)           # 断層の光
    d.line([(5, 12), (13, 5)], fill=WHITE, width=1)
    d.point((2, 6), fill=accent)   # 虚空の粒
    d.point((14, 12), fill=accent)
    return img


def meteor_lance(accent):
    img, d = canvas()
    line(d, [(2, 15), (8, 9)], WOOD, 1)
    line(d, [(3, 14), (8, 10)], WOOD_DARK, 1)
    poly(d, [(8, 2), (13, 7), (9, 10)], STEEL_LIGHT)  # 槍身
    poly(d, [(9, 4), (11, 7), (10, 8)], STEEL)
    d.ellipse([11, 2, 15, 6], fill=accent, outline=OUTLINE)  # 落下の火玉
    d.line([(13, 4), (15, 2)], fill=WHITE, width=1)          # 尾
    d.point((8, 5), fill=accent)
    d.point((6, 7), fill=WHITE)
    return img


def echo_katana(accent):
    img, d = canvas()
    poly(d, [(2, 10), (7, 6), (12, 2), (14, 1), (13, 4), (8, 8), (3, 12)], STEEL_LIGHT)
    poly(d, [(3, 10), (7, 7), (11, 3), (12, 3), (11, 5), (7, 8), (4, 11)], STEEL)
    d.line([(4, 10), (11, 3)], fill=accent, width=1)  # 残影の軌跡
    d.point((6, 8), fill=WHITE)                       # 残影の粒
    d.ellipse([0, 9, 4, 13], fill=OUTLINE)
    d.ellipse([1, 10, 3, 12], fill=GOLD)
    line(d, [(1, 12), (0, 15)], WOOD_DARK, 2)
    d.point((12, 2), fill=accent)
    return img


def howling_claw(accent):
    img, d = canvas()
    d.rectangle([3, 10, 9, 14], fill=OUTLINE)     # 手甲
    d.rectangle([4, 11, 8, 13], fill=STEEL_DARK)
    for x, tip in ((5, 2), (7, 1), (9, 2), (11, 3)):  # 四爪 (曲がった爪)
        d.line([(x, 10), (x + 1, 6)], fill=STEEL_LIGHT, width=1)
        d.line([(x + 1, 6), (x + tip, 4)], fill=STEEL, width=2)
        d.point((x + tip, 4), fill=accent)
    d.rectangle([5, 11, 7, 12], fill=accent)      # 掌核
    d.point((6, 11), fill=WHITE)
    return img


def aegis(color):
    """巨壁の粉 (盾紋章の袋。青の輪を太く見せる)"""
    img, d = canvas()
    poly(d, [(4, 2), (11, 2), (12, 8), (8, 15), (4, 8)], OUTLINE)
    poly(d, [(5, 3), (10, 3), (11, 8), (8, 13), (5, 8)], color)
    poly(d, [(6, 4), (9, 4), (10, 8), (8, 11), (6, 8)], (238, 240, 248, 255))
    d.line([(6, 4), (6, 8)], fill=color, width=1)      # 内枠の青
    d.line([(10, 4), (10, 8)], fill=color, width=1)
    d.point((8, 6), fill=color)
    d.point((8, 7), fill=WHITE)
    d.point((4, 11), fill=GOLD)                        # 腰当ての金具
    d.point((11, 11), fill=GOLD)
    return img


def pouch(color):
    """幸運の粉 (首括りの袋)"""
    img, d = canvas()
    poly(d, [(5, 5), (11, 5), (13, 12), (8, 15), (3, 12)], OUTLINE)
    poly(d, [(6, 6), (10, 6), (12, 12), (8, 13), (4, 12)], color)
    d.line([(5, 5), (8, 1), (11, 5)], fill=OUTLINE, width=2)
    d.line([(6, 5), (8, 2), (10, 5)], fill=GOLD, width=1)
    d.point((7, 8), fill=WHITE)
    d.point((9, 10), fill=WHITE)
    return img


def ration(_c=None):
    """軍糧丸 (海苔巻きのおにぎり)"""
    img, d = canvas()
    poly(d, [(8, 1), (14, 8), (12, 14), (4, 14), (2, 8)], OUTLINE)      # 三角おにぎりの輪郭
    poly(d, [(8, 3), (12, 8), (11, 12), (5, 12), (4, 8)], (246, 242, 232, 255))  # 白飯
    d.rectangle([5, 9, 11, 14], fill=OUTLINE)                          # 海苔 (下)
    d.rectangle([6, 10, 10, 13], fill=(24, 34, 28, 255))
    d.line([(6, 10), (10, 10)], fill=(52, 70, 60, 255), width=1)       # 海苔の粗め
    d.ellipse([6, 4, 8, 6], fill=(255, 255, 255, 255))                 # 飯の粒あしらい
    d.point((10, 5), fill=(246, 242, 232, 255))
    d.point((8, 12), fill=(90, 120, 100, 255))                         # 具の芯
    return img


def fishing_rod(accent):
    img, d = canvas()
    line(d, [(2, 14), (12, 2)], WOOD, 1)  # 竿
    line(d, [(2, 14), (12, 2)], WOOD_DARK, 1)
    d.line([(5, 11), (7, 9)], fill=accent, width=1)  # 握り巻
    d.line([(12, 2), (12, 8)], fill=(230, 230, 230, 255), width=1)  # 糸
    d.line([(12, 8), (10, 10)], fill=STEEL, width=1)  # 針
    d.point((12, 8), fill=accent)
    return img


def shears(accent):
    img, d = canvas()
    line(d, [(3, 13), (10, 3)], STEEL_LIGHT, 2)  # 刃1
    line(d, [(5, 14), (12, 4)], STEEL, 2)  # 刃2
    d.ellipse([3, 10, 6, 13], fill=OUTLINE)  # 指輪1
    d.ellipse([4, 11, 5, 12], fill=accent)
    d.ellipse([6, 12, 9, 15], fill=OUTLINE)  # 指輪2
    d.point((7, 13), fill=accent)
    d.point((6, 8), fill=WHITE)
    return img


def pickaxe(accent):
    img, d = canvas()
    line(d, [(6, 15), (8, 5)], WOOD, 2)  # 柄
    poly(d, [(2, 1), (14, 1), (12, 4), (10, 3), (6, 3), (4, 4)], STEEL_LIGHT)  # 鶴嘴
    d.line([(3, 2), (13, 2)], fill=accent, width=1)  # 深淵の帯
    d.point((8, 2), fill=WHITE)
    line(d, [(7, 5), (9, 5)], GOLD, 1)  # 目釘座
    return img


def tool_axe(accent):
    img, d = canvas()
    line(d, [(5, 15), (7, 5)], WOOD, 2)
    poly(d, [(6, 1), (14, 3), (12, 10), (6, 8)], STEEL_LIGHT)  # 斧身
    poly(d, [(8, 3), (12, 4), (11, 8), (7, 7)], accent)  # 刃の刻み
    d.line([(8, 3), (12, 4)], fill=WHITE, width=1)
    line(d, [(6, 9), (8, 7)], GOLD, 1)
    return img


def shovel(accent):
    img, d = canvas()
    line(d, [(8, 15), (8, 7)], WOOD, 2)
    line(d, [(8, 15), (8, 7)], WOOD_DARK, 1)
    poly(d, [(5, 1), (11, 1), (12, 5), (8, 8), (4, 5)], STEEL_LIGHT)  # 鋤先
    d.line([(6, 2), (10, 2)], fill=accent, width=1)
    d.point((8, 4), fill=WHITE)
    line(d, [(7, 8), (9, 8)], GOLD, 1)
    return img


def hoe(accent):
    img, d = canvas()
    line(d, [(4, 15), (8, 7)], WOOD, 2)
    line(d, [(4, 15), (8, 7)], WOOD_DARK, 1)
    poly(d, [(7, 3), (14, 3), (14, 6), (8, 7)], STEEL_LIGHT)  # 横刃
    d.line([(8, 4), (13, 4)], fill=accent, width=1)
    d.point((12, 5), fill=WHITE)
    line(d, [(7, 8), (9, 6)], GOLD, 1)
    return img


ABYSS_PLATE = (58, 40, 92, 255)
ABYSS_DARK = (30, 20, 52, 255)
ABYSS_LIGHT = (120, 92, 190, 255)


def _plate(d, box, trim=True):
    d.rectangle(box, fill=OUTLINE)
    x0, y0, x1, y1 = box
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=ABYSS_PLATE)
    d.line([(x0 + 1, y0 + 1), (x1 - 1, y0 + 1)], fill=ABYSS_LIGHT, width=1)
    if trim:
        d.point((x0 + 2, y1 - 2), fill=GOLD)
        d.point((x1 - 2, y1 - 2), fill=GOLD)


def helm(accent, trim=GOLD):
    img, d = canvas()
    _plate(d, [3, 3, 12, 11])
    d.rectangle([5, 7, 10, 9], fill=ABYSS_DARK)  # 面頬
    d.line([(5, 8), (10, 8)], fill=accent, width=1)  # 目元光
    d.rectangle([7, 2, 8, 4], fill=trim)  # 前立
    d.point((7, 3), fill=WHITE)
    return img


def chest(accent, trim=GOLD):
    img, d = canvas()
    _plate(d, [4, 4, 11, 12])
    d.rectangle([1, 5, 3, 11], fill=OUTLINE)  # 腕
    d.rectangle([2, 6, 2, 10], fill=ABYSS_PLATE)
    d.rectangle([12, 5, 14, 11], fill=OUTLINE)
    d.rectangle([13, 6, 13, 10], fill=ABYSS_PLATE)
    d.line([(4, 9), (11, 9)], fill=trim, width=1)  # 帯金具
    d.rectangle([7, 5, 8, 7], fill=accent)  # 胸核
    d.point((7, 5), fill=WHITE)
    return img


def legs(accent, trim=GOLD):
    img, d = canvas()
    d.rectangle([3, 2, 12, 5], fill=OUTLINE)  # 腰
    d.rectangle([4, 3, 11, 4], fill=ABYSS_PLATE)
    _plate(d, [3, 5, 7, 13], trim=False)  # 左脚
    _plate(d, [8, 5, 12, 13], trim=False)  # 右脚
    d.line([(3, 4), (12, 4)], fill=trim, width=1)
    d.point((5, 7), fill=accent)
    d.point((10, 7), fill=accent)
    return img


def boots(accent, trim=GOLD):
    img, d = canvas()
    _plate(d, [2, 3, 6, 11], trim=False)  # 左
    _plate(d, [9, 3, 13, 11], trim=False)  # 右
    d.rectangle([2, 10, 6, 13], fill=OUTLINE)  # 爪先
    d.rectangle([9, 10, 13, 13], fill=OUTLINE)
    d.rectangle([3, 11, 5, 12], fill=ABYSS_DARK)
    d.rectangle([10, 11, 12, 12], fill=ABYSS_DARK)
    d.point((3, 5), fill=accent)
    d.point((12, 5), fill=accent)
    d.point((4, 11), fill=trim)
    d.point((11, 11), fill=trim)
    return img


def shard():
    img, d = canvas()
    poly(d, [(8, 1), (13, 7), (8, 15), (3, 7)], (80, 200, 255, 255))
    poly(d, [(8, 4), (11, 7), (8, 12), (5, 7)], (150, 230, 255, 255))
    d.point((7, 5), fill=(255, 255, 255, 255))
    return img


WEAPONS = {
    # accent は原作 WEAPON_CLASSES の color
    "abyss_sword": (sword, 0xe9e3d5, "handheld"),
    "abyss_axe": (axe, 0xff8a2b, "handheld"),
    "abyss_spear": (spear, 0x5cc8ff, "handheld"),
    "abyss_staff": (staff, 0xc58bff, "handheld"),
    "abyss_bow": (bow, 0x7cffb2, "handheld"),
    "abyss_dagger": (dagger, 0xffd23c, "handheld"),
    "abyss_hammer": (hammer, 0xff6a7d, "handheld"),
    "abyss_katana": (katana, 0xff4d5e, "handheld"),
    "abyss_scythe": (scythe, 0xa0e87c, "handheld"),
    "abyss_gauntlet": (gauntlet, 0xffb84a, "handheld"),
    "abyss_chakram": (chakram, 0x7ce8ff, "generated"),
    "abyss_tome": (tome, 0xe34bff, "generated"),
    "abyss_rune_blade": (rune_blade, 0x7ce8ff, "handheld"),
    "abyss_blood_axe": (blood_axe, 0xff2a55, "handheld"),
    "abyss_storm_spear": (storm_spear, 0x9fd8ff, "handheld"),
    "abyss_void_staff": (void_staff, 0x8a5cff, "handheld"),
    "abyss_moon_bow": (moon_bow, 0xd6f0ff, "handheld"),
    "abyss_fang_dagger": (fang_dagger, 0xffe14a, "handheld"),
    "abyss_quake_hammer": (quake_hammer, 0xc9a227, "handheld"),
    "abyss_frost_katana": (frost_katana, 0xaee9ff, "handheld"),
    "abyss_soul_scythe": (soul_scythe, 0xc9a0ff, "handheld"),
    "abyss_titan_gauntlet": (titan_gauntlet, 0xffb03c, "handheld"),
    "abyss_eclipse_chakram": (eclipse_chakram, 0x5c8cff, "generated"),
    "abyss_chaos_tome": (chaos_tome, 0xff5c8a, "generated"),
    "abyss_rod": (rod, 0x9fd8ff, "handheld"),
    "abyss_wand": (wand, 0xd6b8ff, "handheld"),
    "abyss_greatsword": (greatsword, 0xe9e3d5, "handheld"),
    "abyss_twin_dagger": (twin_dagger, 0xffe14a, "handheld"),
    "abyss_war_horn": (war_horn, 0xffd092, "handheld"),
    "abyss_wind_blade": (wind_blade, 0x9fe8d8, "handheld"),
    "abyss_ember_flail": (ember_flail, 0xff8a3c, "handheld"),
    "abyss_astral_lance": (astral_lance, 0xffe9a8, "handheld"),
    "abyss_tide_trident": (tide_trident, 0x7cd8ff, "handheld"),
    "abyss_moon_mace": (moon_mace, 0xd8e8ff, "handheld"),
    "abyss_depth_gun": (depth_gun, 0xff4d6d, "handheld"),
    "abyss_venom_whip": (venom_whip, 0x8ff06e, "handheld"),
    "abyss_flame_grimoire": (flame_grimoire, 0xff6a2b, "handheld"),
    "abyss_rift_blade": (rift_blade, 0x8a5cff, "handheld"),
    "abyss_meteor_lance": (meteor_lance, 0xffd23c, "handheld"),
    "abyss_echo_katana": (echo_katana, 0xa8e8ff, "handheld"),
    "abyss_howling_claw": (howling_claw, 0xffb03c, "handheld"),
    "abyss_fishing_rod": (fishing_rod, 0x7ce8ff, "handheld"),
    "abyss_shears": (shears, 0xb28ce0, "handheld"),
    "abyss_pickaxe": (pickaxe, 0xb28ce0, "handheld"),
    "abyss_tool_axe": (tool_axe, 0xb28ce0, "handheld"),
    "abyss_shovel": (shovel, 0xb28ce0, "handheld"),
    "abyss_hoe": (hoe, 0xb28ce0, "handheld"),
}


def _liquid_shade(c, k=0.7):
    """液体の底下り (濃色)"""
    return (max(0, int(c[0] * k)), max(0, int(c[1] * k)), max(0, int(c[2] * k)), 255)


def flask(liquid):
    """回復薬フラスコ (液面の沈み・気泡つき)"""
    img, d = canvas()
    d.ellipse([3, 6, 12, 15], fill=OUTLINE)
    d.ellipse([4, 7, 11, 14], fill=liquid)
    d.rectangle([5, 11, 10, 14], fill=_liquid_shade(liquid))  # 液面の底下
    d.ellipse([5, 8, 7, 10], fill=WHITE)
    d.point((9, 12), fill=WHITE)  # 気泡
    d.rectangle([6, 1, 9, 6], fill=OUTLINE)  # 首
    d.rectangle([7, 2, 8, 5], fill=WOOD)
    d.rectangle([6, 0, 9, 1], fill=GOLD)  # 栓
    d.point((7, 0), fill=(255, 240, 200, 255))  # 栓の照り
    return img


def vial(liquid):
    """細首の薬瓶 (キャップ金具・底色・気泡つき)"""
    img, d = canvas()
    d.rectangle([5, 5, 10, 14], fill=OUTLINE)
    d.rectangle([6, 6, 9, 13], fill=liquid)
    d.rectangle([6, 10, 9, 13], fill=_liquid_shade(liquid))  # 底の沈み
    d.line([(6, 6), (6, 13)], fill=WHITE, width=1)
    d.point((8, 7), fill=WHITE)  # 気泡
    d.rectangle([6, 1, 9, 5], fill=OUTLINE)
    d.rectangle([6, 2, 9, 3], fill=GOLD)  # キャップ金具
    d.rectangle([7, 3, 8, 4], fill=WOOD_DARK)
    d.point((7, 2), fill=(255, 240, 200, 255))
    return img


def orb(liquid):
    """魂のオーブ"""
    img, d = canvas()
    d.ellipse([3, 3, 12, 14], fill=OUTLINE)
    d.ellipse([4, 4, 11, 13], fill=liquid)
    d.ellipse([4, 10, 11, 13], fill=_liquid_shade(liquid))  # 底の沈み
    d.ellipse([6, 5, 8, 7], fill=WHITE)
    d.point((9, 10), fill=WHITE)
    d.point((5, 11), fill=_liquid_shade(liquid, 0.55))
    return img


def bomb(color, crown=False):
    """爆弾 (導火線つき黒玉+色帯+飛び火)。crown=True で炎の冠 (業火弾)"""
    img, d = canvas()
    d.ellipse([2, 5, 13, 15], fill=OUTLINE)
    d.ellipse([3, 6, 12, 14], fill=(35, 30, 45, 255))
    d.arc([4, 7, 11, 13], start=90, end=200, fill=color, width=2)
    d.line([(9, 5), (12, 1)], fill=WOOD, width=1)  # 導火線
    d.point((12, 1), fill=(255, 200, 80, 255))  # 火花
    d.point((12, 2), fill=(255, 110, 40, 255))  # 火花の暈
    d.point((10, 1), fill=(255, 240, 180, 255))
    if crown:
        for x, h in ((8, 3), (10, 2), (12, 4)):  # 炎の冠
            c = (255, 240, 180, 255) if h == 4 else (255, 150, 50, 255)
            d.rectangle([x, 3 - h, x, 3 - 1], fill=c) if h > 1 else d.point((x, 3 - h), fill=c)
        d.point((11, 0), fill=WHITE)
    d.point((5, 8), fill=WHITE)
    d.point((10, 9), fill=WHITE)
    return img


def rune_stone(color):
    """転移のルーン石"""
    img, d = canvas()
    poly(d, [(4, 2), (11, 4), (12, 11), (5, 14), (2, 9)], (90, 85, 110, 255))
    d.line([(6, 5), (6, 11)], fill=color, width=1)
    d.line([(6, 6), (9, 8)], fill=color, width=1)
    d.line([(6, 10), (9, 8)], fill=color, width=1)
    d.point((8, 5), fill=WHITE)
    return img


def scroll(_c=None):
    """予言の羊皮紙"""
    img, d = canvas()
    poly(d, [(3, 2), (12, 4), (12, 12), (3, 14)], (232, 220, 190, 255))
    d.line([(2, 2), (2, 14)], fill=WOOD, width=2)  # 軸
    d.line([(13, 4), (13, 12)], fill=WOOD, width=1)
    d.line([(5, 6), (10, 7)], fill=(120, 60, 140, 255), width=1)
    d.line([(5, 9), (10, 10)], fill=(120, 60, 140, 255), width=1)
    d.point((7, 5), fill=(255, 220, 120, 255))
    return img


def feast(_c=None):
    """満腹の宴 (骨付き肉)"""
    img, d = canvas()
    d.ellipse([2, 3, 11, 12], fill=OUTLINE)
    d.ellipse([3, 4, 10, 11], fill=(190, 110, 70, 255))
    d.ellipse([4, 5, 7, 8], fill=(240, 200, 160, 255))
    d.line([(10, 10), (14, 14)], fill=(235, 230, 220, 255), width=2)
    d.point((12, 12), fill=OUTLINE)
    return img


def lodestone(color):
    """魂の磁石"""
    img, d = canvas()
    d.arc([2, 2, 13, 13], start=0, end=360, fill=OUTLINE, width=5)
    d.arc([3, 3, 12, 12], start=0, end=360, fill=color, width=3)
    d.rectangle([6, 11, 9, 14], fill=(235, 235, 240, 255))  # 両極
    d.line([(6, 11), (6, 14)], fill=OUTLINE, width=1)
    d.line([(9, 11), (9, 14)], fill=OUTLINE, width=1)
    d.point((5, 5), fill=WHITE)
    return img


CONSUMABLES = {
    "abyss_heal_s": (flask, 0xff5c5c, "generated"),
    "abyss_heal_m": (flask, 0xff2a55, "generated"),
    "abyss_heal_l": (flask, 0xffd0d8, "generated"),
    "abyss_soul_s": (orb, 0xb14bff, "generated"),
    "abyss_soul_l": (orb, 0xe34bff, "generated"),
    "abyss_pwr": (vial, 0xff5c5c, "generated"),
    "abyss_spd": (vial, 0x7ce8ff, "generated"),
    "abyss_irn": (vial, 0x9aa5b5, "generated"),
    "abyss_rgn": (vial, 0x4bd07a, "generated"),
    "abyss_abs": (vial, 0xffd700, "generated"),
    "abyss_fir": (vial, 0xff8a2b, "generated"),
    "abyss_owl": (vial, 0x5cd8ff, "generated"),
    "abyss_fth": (vial, 0xe8e8f2, "generated"),
    "abyss_lck": (vial, 0x4ae88a, "generated"),
    "abyss_bomb_f": (bomb, 0xff5c2a, "generated"),
    "abyss_bomb_t": (bomb, 0xffe14a, "generated"),
    "abyss_bomb_i": (bomb, 0xaee9ff, "generated"),
    "abyss_tele_a": (rune_stone, 0x7ce8ff, "generated"),
    "abyss_tele_s": (rune_stone, 0x7ec850, "generated"),
    "abyss_cure": (vial, 0xffffff, "generated"),
    "abyss_magnet": (lodestone, 0xff4d6d, "generated"),
    "abyss_reveal": (scroll, 0xffffff, "generated"),
    "abyss_feast": (feast, 0xffffff, "generated"),
    "abyss_smoke": (bomb, 0x8a8a95, "generated"),
    "abyss_berserk": (vial, 0xd84a6a, "generated"),
    "abyss_bulwark": (aegis, 0x6a8cff, "generated"),
    "abyss_bomb_h": None,  # 炎の冠つき (下で差し替え)
    "abyss_fortune": (pouch, 0xffd23c, "generated"),
    "abyss_ration": (ration, 0xffffff, "generated"),
}
CONSUMABLES["abyss_bomb_h"] = (lambda c: bomb(c, crown=True), 0xff4a2a, "generated")

NEWMATS = {
    "abyss_blood_crystal": 0xff2a55,
    "abyss_shadow_stone": 0x4a2a6a,
    "abyss_star_dust": 0xbfe9ff,
    "abyss_wyrm_scale": 0x4bd07a,
    "abyss_ancient_core": 0xffd700,
    "abyss_void_piece": 0x2a0a3a,
}

# 第3弾の新素材 (gem と diffuse で見分けられるよう配置で描き分け)
NEWMATS3 = {
    "abyss_teardrop": 0x7ce8ff,
    "abyss_ember": 0xff8a2b,
    "abyss_frost_prism": 0xaee9ff,
    "abyss_moonstone": 0xd8f0ff,
    "abyss_bone_charm": 0xe8e8f2,
    "abyss_bloodroot": 0xff4d6d,
    "abyss_thunder_thread": 0xffe14a,
    "abyss_venom_sac": 0x8ff06e,
    "abyss_ash_powder": 0x8a8a95,
    "abyss_void_splinter": 0x8a5cff,
    "abyss_gold_vein": 0xffd23c,
    "abyss_iron_rose": 0xd8b8c8,
    "abyss_night_pearl": 0x9a8ecf,
    "abyss_lava_pearl": 0xff5c2a,
    "abyss_mist_wisp": 0x9fd8ff,
    "abyss_star_seed": 0xffe9a8,
}


def _mats3_tex(color):
    """新素材12x12のテクスチャ (塊 + 輝点. 色は冒険系の物性を反映)"""
    img, d = canvas()
    c = color
    import random as _rnd
    rnd = _rnd.Random(hash(str(c)) & 0xffff)
    poly(d, [(4, 2), (11, 4), (12, 11), (6, 14), (3, 9)], OUTLINE)
    poly(d, [(5, 4), (10, 6), (11, 10), (7, 12), (5, 9)], c)
    d.line([(6, 5), (8, 7)], fill=_shade(c, 1.25), width=1)
    for _ in range(3):
        x = rnd.randrange(6, 11)
        y = rnd.randrange(6, 11)
        d.point((x, y), fill=_shade(c, 0.7))
    d.point((7, 5), fill=WHITE)
    d.point((9, 9), fill=_shade(c, 1.35))
    return img


def shard_bag(state):
    """魂の欠片の束 (袋→包→核 と詰め増し)"""
    img, d = canvas()
    if state == 0:   # 袋
        poly(d, [(4, 6), (12, 6), (13, 13), (8, 15), (3, 13)], OUTLINE)
        poly(d, [(5, 7), (11, 7), (12, 12), (8, 13), (4, 12)], (150, 230, 255, 255))
        d.line([(4, 6), (8, 3), (12, 6)], fill=OUTLINE, width=2)
        d.point((7, 9), fill=WHITE)
    elif state == 1:  # 包
        poly(d, [(3, 5), (13, 5), (14, 12), (8, 15), (2, 12)], OUTLINE)
        poly(d, [(4, 6), (12, 6), (13, 12), (8, 13), (3, 12)], (110, 200, 255, 255))
        d.arc([5, 0, 11, 6], start=180, end=360, fill=GOLD, width=2)
        d.line([(6, 8), (10, 11)], fill=WHITE, width=1)
        d.point((6, 9), fill=WHITE)
        d.point((9, 10), fill=WHITE)
    else:            # 核
        d.ellipse([2, 2, 14, 14], fill=OUTLINE)
        d.ellipse([3, 3, 13, 13], fill=(150, 230, 255, 255))
        d.ellipse([5, 5, 11, 11], fill=(200, 240, 255, 255))
        d.ellipse([6, 6, 10, 10], fill=OUTLINE)
        d.ellipse([7, 7, 9, 9], fill=(240, 250, 255, 255))
        for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
            d.line([(x, y), (x, y)], fill=GOLD)
    return img

ARMORS = {
    "abyss_helm": (helm, 0xb28ce0, None, "generated"),
    "abyss_chest": (chest, 0xb28ce0, None, "generated"),
    "abyss_legs": (legs, 0xb28ce0, None, "generated"),
    "abyss_boots": (boots, 0xb28ce0, None, "generated"),
}

# 防具セット (blood/shadow/rock/star × 4部位)。trim・accent をセット色に
SET_COLORS = {
    "blood": (0xffd0d8, 0xff4d6d),
    "shadow": (0xe0d8ff, 0x8a7cff),
    "rock": (0xffe9a8, 0xc9a227),
    "star": (0xe0f8ff, 0x7ce8ff),
}
SET_ARMORS = {}
for _set, (_acc, _trim) in SET_COLORS.items():
    _TA = hexcolor(_trim)
    SET_ARMORS[f"abyss_{_set}_helm"] = (helm, _acc, _TA, "generated")
    SET_ARMORS[f"abyss_{_set}_chest"] = (chest, _acc, _TA, "generated")
    SET_ARMORS[f"abyss_{_set}_legs"] = (legs, _acc, _TA, "generated")
    SET_ARMORS[f"abyss_{_set}_boots"] = (boots, _acc, _TA, "generated")
del _set, _acc, _trim, _TA


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


def entity_tex(base, accent, pattern):
    """64x64。標準ゾーン分割 (AbyssModels の UV と対応)。
    BODY(0,0,48,32) / BELLY(48,0,16,32) / DARK(0,32,16,16) /
    WHITE(16,32,16,16) / ACC(32,32,32,16) / FREE(0,48,64,16)+模様"""
    import random
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rnd = random.Random(0)
    dark = _shade(base, 0.45)
    # BODY: バニラ式フラット + 疎らな斑点。立体感はMCのライティングに任せる
    _flat(d, 0, 0, 48, 32, base)
    _speckle(d, rnd, 1, 1, 47, 31, [_shade(base, 0.78), _shade(base, 1.12)], 40)
    # BELLY: 明部フラット
    _flat(d, 48, 0, 64, 32, accent)
    _speckle(d, rnd, 49, 1, 63, 31, [_shade(accent, 0.8)], 8)
    # DARK: 目・口 (艶ピクセル付き)
    d.rectangle([0, 32, 15, 47], fill=DARKC)
    d.point((3, 35), fill=WHITEC)
    d.point((11, 35), fill=WHITEC)
    d.point((4, 36), fill=WHITEC)
    d.point((12, 36), fill=WHITEC)
    # WHITE: 白目・牙・光輪
    _flat(d, 16, 32, 32, 48, WHITEC)
    # ACC: 発光部フラット
    _flat(d, 32, 32, 64, 48, accent)
    _speckle(d, rnd, 34, 34, 62, 46, [WHITEC], 8)
    # FREE: 種別模様 (下地フラット+大柄)
    _flat(d, 0, 48, 64, 64, base)
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
    for armor_id, (fn, color, trim, parent) in list(ARMORS.items()) + list(SET_ARMORS.items()):
        fn(hexcolor(color), trim if trim else GOLD).save(os.path.join(TEX_DIR, f"{armor_id}.png"))
        write_model(armor_id, parent)
    for con_id, (fn, color, parent) in CONSUMABLES.items():
        fn(hexcolor(color)).save(os.path.join(TEX_DIR, f"{con_id}.png"))
        write_model(con_id, parent)
    for mat_id, color in NEWMATS.items():
        gem(hexcolor(color), (230, 240, 255, 255)).save(os.path.join(TEX_DIR, f"{mat_id}.png"))
        write_model(mat_id, "generated")
    for mat_id, color in NEWMATS3.items():
        _mats3_tex(hexcolor(color)).save(os.path.join(TEX_DIR, f"{mat_id}.png"))
        write_model(mat_id, "generated")
    for state, item_id in enumerate(("soul_shard_pouch", "soul_shard_bale", "soul_shard_core")):
        shard_bag(state).save(os.path.join(TEX_DIR, f"{item_id}.png"))
        write_model(item_id, "generated")
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
    # 奥義の断片 (WeaponStyle の色を鍔元に、小さな欠片として生成)
    with open(os.path.join(ROOT, "src", "main", "java", "com", "abyssalloot", "GameData.java"), encoding="utf-8") as f:
        import re
        styles = re.findall(r'(\w+)\("([\w_]+)", "[^"]+", "[^"]+", "[^"]+", (0x[0-9a-fA-F]+)\)', f.read())
    for _, sid, col in styles:
        # gem の上半分だけを欠片として切る: 左上半分の粒
        c = hexcolor(int(col, 16))
        img, d = canvas()
        poly(d, [(3, 5), (8, 2), (13, 5), (13, 9), (8, 13), (3, 9)], (60, 50, 90, 255))
        poly(d, [(5, 6), (8, 4), (11, 6), (11, 9), (8, 11), (5, 9)], OUTLINE)
        poly(d, [(6, 7), (8, 5.8), (10, 7), (10, 8), (8, 9), (6, 8)], c)
        pil_img = _flat_small(img, c)
        d2 = ImageDraw.Draw(pil_img)
        pil_img.save(os.path.join(TEX_DIR, f"art_shard_{sid}.png"))
        write_model(f"art_shard_{sid}", "generated")
    print(f"generated {len(WEAPONS) + 1 + len(MATERIALS) + len(NEWMATS3) + len(styles)} textures + {len(ENTITIES)} entity textures")


def _flat_small(img, c):
    """断片用: 中央に小さな輝石を重ねる"""
    d = ImageDraw.Draw(img)
    d.ellipse([6, 6, 10, 10], outline=(20, 14, 30, 255), width=1)
    d.point((8, 7), fill=WHITE)
    return img


if __name__ == "__main__":
    main()
