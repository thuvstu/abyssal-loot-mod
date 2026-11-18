"""MCAssetGen 由来のジェネレータで描く追加武器 (剣: art/swordgen.py / 杖: art/staffgen.py)。

ここに 1 件足すと hd_weapons.ICONS (64px のインベントリ表示) と、奥義の断片の刻印用の 16px 縮小版に載る。
Java 側 (GameData.WeaponStyle / ModItems / WeaponArts) と generate_textures.py の WEAPON_MODELS (手持ちの立体) は別途。
"""
from __future__ import annotations

import math

from PIL import Image

from . import staffgen, swordgen

# id -> swordgen.options の引数
SWORDS = {
    "abyss_crystal_sword": dict(
        silhouette="straight", surface="crystal", crystal_shards=True, guard="wings", guard_width=2.0,
        handle="wire", pommel="gem", blade_color=0x9fd8f0, guard_color=0x8fa6e8, handle_color=0x2a3450,
        accent=0xa9e8ff, blade_width=3.4, seed="crystal_sword"),
    "abyss_sakura_blade": dict(
        silhouette="uchigatana", surface="choji", guard="tsuba", guard_width=1.6, handle="cord", pommel="tassel",
        fuller=False, blade_color=0x8a96ac, guard_color=0x4a3044, handle_color=0x8a2a52, accent=0xef8fb2,
        blade_width=2.6, edge_gradient=True, seed="sakura_blade"),
    "abyss_dragon_cleaver": dict(
        silhouette="falchion", surface="dragonsteel", bone_spurs=True, spur_count=3, guard="dread", guard_width=2.2,
        handle="dragon_scale", pommel="fang", blade_color=0x7a6060, guard_color=0x6a4630, handle_color=0x3a2420,
        accent=0xff7a3c, blade_width=3.4, blade_length=9.5, seed="dragon_cleaver"),
}

# id -> (杖の型, 属性, 上書き)
STAFFS = {
    "abyss_lunar_staff": ("lunar", "ice", {"shaftMaterial": "quartz", "trimMaterial": "silver"}),
    "abyss_chrono_staff": ("chronomancer", "time", {}),
    "abyss_soul_lantern": ("lanternbearer", "soul", {"trimMaterial": "copper"}),
}

ANIMATED = set(STAFFS)  # 杖は浮遊物・粒子が動く


def _sword_icon(item_id):
    def fn(accent, ph=0.0, anim=False):
        return swordgen.render(swordgen.options(**SWORDS[item_id]))
    return fn


def _staff_icon(item_id):
    kind, element, over = STAFFS[item_id]
    cfg = staffgen.config(kind, element=element, seed=item_id, **over)

    def fn(accent, ph=0.0, anim=False):
        frame = int(round(ph / (2 * math.pi) * staffgen.FRAMES)) % staffgen.FRAMES if anim else 1
        return staffgen.render(cfg, frame)
    return fn


ICONS = {**{k: _sword_icon(k) for k in SWORDS}, **{k: _staff_icon(k) for k in STAFFS}}


def small_icon(item_id):
    """16px の縮小版 (奥義の断片に刻む刻印用)。半透明の光暈は落とし、輪郭をはっきりさせる。"""
    def fn(_color):
        img = ICONS[item_id](None)
        img = img.resize((16, 16), Image.BOX)
        px = img.load()
        for y in range(16):
            for x in range(16):
                r, g, b, a = px[x, y]
                px[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
        return img
    return fn
