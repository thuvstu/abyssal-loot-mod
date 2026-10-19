"""2D アイコン (消耗品・欠片・素材) の描画。

旧 generate_textures.py から移植。描画品質は core / materials の補助関数で底上げしている。
"""
from __future__ import annotations

import math
import random

from PIL import Image, ImageDraw

from . import core
from .core import mix, shade

OUTLINE = (24, 18, 36, 255)
STEEL = (206, 214, 226, 255)
STEEL_DARK = (120, 130, 150, 255)
WOOD = (120, 78, 46, 255)
WOOD_DARK = (74, 46, 28, 255)
GOLD = (232, 190, 84, 255)
STEEL_LIGHT = (232, 238, 248, 255)
WHITE = (255, 255, 255, 255)


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
    d.polygon(pts, fill=color)
    d.line(pts + [pts[0]], fill=OUTLINE, width=1)


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


def mine_tool(color):
    """采矿お道具の台形"""
    img, d = canvas()
    line(d, [(7, 14), (11, 5)], WOOD, 2)
    poly(d, [(8, 1), (14, 3), (13, 9), (9, 7)], STEEL_LIGHT)
    poly(d, [(10, 4), (12, 6), (11, 8), (9, 6)], color)
    d.point((12, 3), fill=WHITE)
    return img


def bell(color):
    """収集の鐘 (ぶら下がりの鐘)"""
    img, d = canvas()
    poly(d, [(4, 4), (12, 4), (13, 11), (3, 11)], OUTLINE)
    poly(d, [(5, 5), (11, 5), (12, 10), (4, 10)], color)
    d.rectangle([6, 11, 10, 13], fill=OUTLINE)  # 振り子
    d.rectangle([7, 12, 9, 13], fill=GOLD)
    d.line([(2, 2), (4, 4)], fill=OUTLINE, width=2)
    d.line([(14, 2), (12, 4)], fill=OUTLINE, width=2)
    d.point((8, 6), fill=WHITE)
    d.point((8, 12), fill=accent_hl(color))
    return img


def accent_hl(c):
    return (min(255, c[0] + 60), min(255, c[1] + 60), min(255, c[2] + 60), 255)


def bomblet(color):
    """小型爆薬 (軽量爆薬・深爆弾・連鎖爆弾で共有)"""
    img, d = canvas()
    d.ellipse([4, 6, 13, 14], fill=OUTLINE)
    d.ellipse([5, 7, 12, 13], fill=(40, 32, 50, 255))
    d.ellipse([6, 8, 11, 12], fill=OUTLINE)
    d.ellipse([7, 9, 10, 11], fill=color)
    d.line([(8, 6), (12, 2)], fill=WOOD, width=1)
    d.point((12, 2), fill=(255, 200, 80, 255))
    d.point((6, 8), fill=WHITE)
    return img


def scroll_tool(color):
    """系統の巻物・ポーション粉用 (縦長の袋)"""
    img, d = canvas()
    poly(d, [(4, 3), (12, 3), (13, 12), (8, 15), (3, 12)], OUTLINE)
    poly(d, [(5, 4), (11, 4), (12, 11), (8, 13), (4, 11)], (238, 230, 210, 255))
    d.line([(5, 4), (8, 1), (11, 4)], fill=OUTLINE, width=2)
    d.line([(6, 4), (8, 2), (10, 4)], fill=color, width=1)
    d.line([(6, 6), (10, 7)], fill=color, width=1)
    d.line([(6, 9), (10, 10)], fill=color, width=1)
    d.point((7, 5), fill=WHITE)
    return img


def firstaid(color):
    """応急箱 (白十字の箱)"""
    img, d = canvas()
    d.rectangle([2, 5, 13, 14], fill=OUTLINE)
    d.rectangle([3, 6, 12, 13], fill=color)
    d.rectangle([7, 4, 11, 5], fill=OUTLINE)  # 取っ手
    d.rectangle([8, 5, 10, 5], fill=accent_hl(color))
    d.rectangle([7, 7, 9, 12], fill=OUTLINE)  # 十字の縦
    d.rectangle([7, 7, 9, 12], fill=(245, 70, 80, 255))
    d.rectangle([5, 9, 11, 10], fill=(245, 70, 80, 255))
    d.point((4, 7), fill=WHITE)
    return img


def drum(color):
    """戦鼓 (すばやい太鼓)"""
    img, d = canvas()
    d.ellipse([3, 7, 13, 13], fill=OUTLINE)
    d.ellipse([4, 4, 12, 12], fill=OUTLINE)
    d.ellipse([5, 5, 11, 11], fill=(40, 30, 50, 255))
    d.ellipse([6, 6, 10, 10], fill=color)
    d.line([(5, 4), (5, 14)], fill=OUTLINE, width=1)
    d.line([(11, 4), (11, 14)], fill=OUTLINE, width=1)
    d.line([(4, 9), (6, 9)], fill=accent_hl(color), width=2)
    d.line([(10, 9), (12, 9)], fill=accent_hl(color), width=2)
    d.point((8, 7), fill=WHITE)
    return img


def net_orb(color):
    """索縛の網 (絡まった糸)"""
    img, d = canvas()
    for stroke in ([(3, 2), (13, 9)], [(3, 9), (13, 2)], [(3, 5), (13, 5)],
                   [(5, 2), (5, 9)], [(8, 2), (8, 13)], [(11, 2), (11, 9)]):
        d.line(stroke, fill=color, width=1)
    for cx, cy in ((5, 5), (8, 7), (11, 5), (8, 3)):
        d.point((cx, cy), fill=WHITE)
    d.ellipse([2, 2, 14, 10], outline=OUTLINE, width=1)
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


def _mats3_tex(color):
    """新素材12x12のテクスチャ (塊 + 輝点. 色は冒険系の物性を反映)"""
    img, d = canvas()
    c = color
    import random as _rnd
    # 色由来の決定的シード (Python の文字列 hash は実行ごとに変わるため使えない)
    rnd = _rnd.Random((c[0] << 16) | (c[1] << 8) | c[2])
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


