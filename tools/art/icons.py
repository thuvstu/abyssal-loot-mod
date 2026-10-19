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


def _shade(c, k):
    """旧実装の明度倍率 (アルファ維持)。"""
    return (max(0, min(255, int(c[0] * k))), max(0, min(255, int(c[1] * k))),
            max(0, min(255, int(c[2] * k))), 255)


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


# ===================================================================
# 2D 武器アイコン (インベントリ表示用。所持時は 3D モデル)
# ===================================================================


def _dia_blade(d, x0, y0, x1, y1, accent):
    """斜め刃の基本形: 幅3の鋼 + 樋 + 切先 + 二重煌き。"""
    line(d, [(x0, y0), (x1, y1)], STEEL_LIGHT, 3)
    line(d, [(x0, y0), (x1, y1)], STEEL, 1)
    d.line([(x0 + 1, y0), (x1, y1 - 1)], fill=STEEL_DARK, width=1)
    tx, ty = x1 + 1, y1 - 1
    poly(d, [(x1 - 1, y1 + 1), (tx + 1, ty - 1), (x1 + 1, y1 + 3)], STEEL_LIGHT)
    d.point((x0 + (x1 - x0) * 2 // 3, y0 + (y1 - y0) * 2 // 3), fill=WHITE)
    d.point((x0 + (x1 - x0) * 5 // 6, y0 + (y1 - y0) * 5 // 6), fill=WHITE)
    d.point((x0 + (x1 - x0) // 3 + 1, y0 + (y1 - y0) // 3), fill=accent)


def _guard_grip(d, gx, gy, accent):
    """鍔(金・斜め) + 柄(木) + 柄頭(金・種別色)。"""
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

