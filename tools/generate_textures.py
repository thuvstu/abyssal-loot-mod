#!/usr/bin/env python3
"""Abyssal Loot のアイテム / MOB アセットを生成する。

出力:
  src/main/resources/assets/abyssalloot/textures/item/*.png   アイテムのテクスチャ
  src/main/resources/assets/abyssalloot/textures/entity/*.png  深淵MOB のテクスチャ
  src/main/resources/assets/abyssalloot/models/item/*.json     立体モデル (elements)
  src/main/resources/assets/abyssalloot/items/*.json           アイテム定義 (1.21.4+)

    python tools/generate_textures.py

Pillow が必要: pip install pillow
"""
from __future__ import annotations

import io
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

from art import boxmodel, core, entities, equipment, icons, items, shapes
from art.core import hexcolor

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot")
TEX_DIR = os.path.join(ASSETS, "textures", "item")
ENT_DIR = os.path.join(ASSETS, "textures", "entity")
EQP_DIR = os.path.join(ASSETS, "textures", "entity", "equipment", "humanoid")
MODEL_DIR = os.path.join(ASSETS, "models", "item")
ITEM_DIR = os.path.join(ASSETS, "items")

# 装備テクスチャ (防具セット = 本体色, 装飾色)
EQUIPMENT_SETS = {
    "abyssal": (0x4a3a68, 0xb28ce0),
    "blood": (0x5a1a26, 0xff4d6d),
    "ember": (0x5a2a18, 0xff6a2b),
    "frost": (0x2a4a5a, 0xaee9ff),
    "moon": (0x3a3a52, 0xa8e8ff),
    "rock": (0x4a4438, 0xc9a227),
    "shadow": (0x2a2038, 0x8a7cff),
    "star": (0x2a3a4a, 0x7ce8ff),
    "storm": (0x3a3a3a, 0xffe14a),
}


# ================================================================= 立体モデル

def _sw(**kw):
    """sword のラッパ (kwargs 差し替え)。"""
    return lambda m, p: shapes.sword(m, p, **kw)


def _kat(**kw):
    return lambda m, p: shapes.katana(m, p, **kw)


def _axe(**kw):
    return lambda m, p: shapes.axe(m, p, **kw)


def _spear(**kw):
    return lambda m, p: shapes.spear(m, p, **kw)


def _staff(**kw):
    return lambda m, p: shapes.staff(m, p, **kw)


def _hammer(**kw):
    return lambda m, p: shapes.hammer(m, p, **kw)


def _gem(**kw):
    return lambda m, p: shapes.gem(m, p, **kw)


# id -> (形状, accent 色, スタイル, 表示種別)
WEAPON_MODELS = {
    "abyss_sword": (_sw(), 0xe9e3d5, "steel", "weapon"),
    "abyss_greatsword": (shapes.greatsword, 0xe9e3d5, "steel", "weapon"),
    "abyss_rune_blade": (_sw(style="rune"), 0x7ce8ff, "steel", "weapon"),
    "abyss_rift_blade": (_sw(style="rune"), 0x8a5cff, "void", "weapon"),
    "abyss_wind_blade": (_sw(), 0x9fe8d8, "steel", "weapon"),
    "abyss_echo_katana": (_kat(), 0xa8e8ff, "steel", "weapon"),
    "abyss_katana": (_kat(), 0xff4d5e, "steel", "weapon"),
    "abyss_frost_katana": (_kat(), 0xaee9ff, "ice", "weapon"),
    "abyss_dagger": (lambda m, p: shapes.dagger(m, p), 0xffd23c, "steel", "weapon"),
    "abyss_fang_dagger": (lambda m, p: shapes.dagger(m, p), 0xffe14a, "bone", "weapon"),
    "abyss_twin_dagger": (lambda m, p: shapes.dagger(m, p, twin=True), 0xffe14a, "steel", "weapon"),
    "abyss_axe": (_axe(), 0xff8a2b, "steel", "weapon"),
    "abyss_blood_axe": (_axe(), 0xff2a55, "blood", "weapon"),
    "abyss_tool_axe": (_axe(), 0xb28ce0, "steel", "weapon"),
    "abyss_pickaxe": (shapes.pickaxe, 0xb28ce0, "steel", "weapon"),
    "abyss_shovel": (shapes.shovel, 0xb28ce0, "steel", "weapon"),
    "abyss_hoe": (shapes.hoe, 0xb28ce0, "steel", "weapon"),
    "abyss_spear": (_spear(), 0x5cc8ff, "steel", "weapon"),
    "abyss_storm_spear": (_spear(), 0x9fd8ff, "steel", "weapon"),
    "abyss_astral_lance": (_spear(head="astral"), 0xffe9a8, "steel", "weapon"),
    "abyss_meteor_lance": (_spear(head="astral"), 0xffd23c, "steel", "weapon"),
    "abyss_tide_trident": (_spear(head="trident"), 0x7cd8ff, "steel", "weapon"),
    "abyss_rod": (shapes.fishing_rod, 0x9fd8ff, "steel", "weapon"),
    "abyss_fishing_rod": (shapes.fishing_rod, 0x7ce8ff, "steel", "weapon"),
    "abyss_staff": (_staff(), 0xc58bff, "steel", "weapon"),
    "abyss_void_staff": (_staff(orb="void"), 0x8a5cff, "void", "weapon"),
    "abyss_wand": (shapes.wand, 0xd6b8ff, "steel", "weapon"),
    "abyss_hammer": (_hammer(), 0xff6a7d, "steel", "weapon"),
    "abyss_quake_hammer": (_hammer(size=6.0), 0xc9a227, "steel", "weapon"),
    "abyss_moon_mace": (shapes.mace, 0xd8e8ff, "steel", "weapon"),
    "abyss_bow": (shapes.bow, 0x7cffb2, "steel", "weapon"),
    "abyss_moon_bow": (shapes.bow, 0xd6f0ff, "steel", "weapon"),
    "abyss_scythe": (shapes.scythe, 0xa0e87c, "steel", "weapon"),
    "abyss_soul_scythe": (shapes.scythe, 0xc9a0ff, "void", "weapon"),
    "abyss_venom_whip": (shapes.whip, 0x8ff06e, "steel", "weapon"),
    "abyss_ember_flail": (shapes.flail, 0xff8a3c, "steel", "weapon"),
    "abyss_chakram": (shapes.chakram, 0x7ce8ff, "steel", "weapon"),
    "abyss_eclipse_chakram": (shapes.chakram, 0x5c8cff, "void", "weapon"),
    "abyss_tome": (shapes.tome, 0xe34bff, "steel", "weapon"),
    "abyss_chaos_tome": (shapes.tome, 0xff5c8a, "steel", "weapon"),
    "abyss_flame_grimoire": (shapes.tome, 0xff6a2b, "steel", "weapon"),
    "abyss_gauntlet": (shapes.gauntlet, 0xffb84a, "steel", "weapon"),
    "abyss_titan_gauntlet": (lambda m, p: shapes.gauntlet(m, p, big=True), 0xffb03c, "steel", "weapon"),
    "abyss_howling_claw": (shapes.claw, 0xffb03c, "bone", "weapon"),
    "abyss_war_horn": (shapes.horn, 0xffd092, "bone", "weapon"),
    "abyss_depth_gun": (shapes.depth_gun, 0xff4d6d, "steel", "weapon"),
    "abyss_shears": (shapes.shears, 0xb28ce0, "steel", "weapon"),
}

# 防具セット: (本体色, 装飾色) — GameData.ArmorSet の color / accent に対応
ARMOR_SETS = {
    "blood": (0xff4d6d, 0xffd0d8),
    "shadow": (0x8a7cff, 0xe0d8ff),
    "rock": (0xc9a227, 0xffe9a8),
    "star": (0x7ce8ff, 0xe0f8ff),
    "frost": (0xaee9ff, 0xe8f8ff),
    "storm": (0xffe14a, 0xfff2c9),
    "moon": (0xa8e8ff, 0xeaffff),
    "ember": (0xff6a2b, 0xffd0a0),
}
ARMOR_MODELS = {
    "abyss_helm": (shapes.helm, 0xb28ce0, 0xd8b0ff),
    "abyss_chest": (shapes.chest, 0xb28ce0, 0xd8b0ff),
    "abyss_legs": (shapes.legs, 0xb28ce0, 0xd8b0ff),
    "abyss_boots": (shapes.boots, 0xb28ce0, 0xd8b0ff),
}
for _set, (_body, _trim) in ARMOR_SETS.items():
    ARMOR_MODELS[f"abyss_{_set}_helm"] = (shapes.helm, _body, _trim)
    ARMOR_MODELS[f"abyss_{_set}_chest"] = (shapes.chest, _body, _trim)
    ARMOR_MODELS[f"abyss_{_set}_legs"] = (shapes.legs, _body, _trim)
    ARMOR_MODELS[f"abyss_{_set}_boots"] = (shapes.boots, _body, _trim)
del _set, _body, _trim

# 素材 (立体モデル化するもの)
MATERIAL_MODELS = {
    "soul_shard": (shapes.shard, 0x5cc8ff),
    "ancient_stone": (shapes.stone_chunk, 0xf5c542),
    "chaos_gem": (shapes.gem, 0xe34bff),
    "void_essence": (shapes.gem, 0x7c8cff),
    "abyss_blood_crystal": (shapes.gem, 0xff2a55),
    "abyss_shadow_stone": (shapes.gem, 0x4a2a6a),
    "abyss_star_dust": (shapes.shard, 0xbfe9ff),
    "abyss_wyrm_scale": (shapes.shard, 0x4bd07a),
    "abyss_ancient_core": (shapes.gem, 0xffd700),
    "abyss_void_piece": (shapes.shard, 0x2a0a3a),
    "abyss_teardrop": (shapes.shard, 0x7ce8ff),
    "abyss_ember": (shapes.shard, 0xff8a2b),
    "abyss_frost_prism": (shapes.gem, 0xaee9ff),
    "abyss_moonstone": (shapes.gem, 0xd8f0ff),
    "abyss_bone_charm": (shapes.shard, 0xe8e8f2),
    "abyss_bloodroot": (shapes.shard, 0xff4d6d),
    "abyss_thunder_thread": (shapes.shard, 0xffe14a),
    "abyss_venom_sac": (shapes.shard, 0x8ff06e),
    "abyss_ash_powder": (shapes.stone_chunk, 0x8a8a95),
    "abyss_void_splinter": (shapes.shard, 0x8a5cff),
    "abyss_gold_vein": (shapes.stone_chunk, 0xffd23c),
    "abyss_iron_rose": (shapes.shard, 0xd8b8c8),
    "abyss_night_pearl": (shapes.gem, 0x9a8ecf),
    "abyss_lava_pearl": (shapes.gem, 0xff5c2a),
    "abyss_mist_wisp": (shapes.shard, 0x9fd8ff),
    "abyss_star_seed": (shapes.shard, 0xffe9a8),
}


# ================================================================= 2D アイコン

CONSUMABLES = {
    "abyss_heal_s": (icons.flask, 0xff5c5c),
    "abyss_heal_m": (icons.flask, 0xff2a55),
    "abyss_heal_l": (icons.flask, 0xffd0d8),
    "abyss_soul_s": (icons.orb, 0xb14bff),
    "abyss_soul_l": (icons.orb, 0xe34bff),
    "abyss_pwr": (icons.vial, 0xff5c5c),
    "abyss_spd": (icons.vial, 0x7ce8ff),
    "abyss_irn": (icons.vial, 0x9aa5b5),
    "abyss_rgn": (icons.vial, 0x4bd07a),
    "abyss_abs": (icons.vial, 0xffd700),
    "abyss_fir": (icons.vial, 0xff8a2b),
    "abyss_owl": (icons.vial, 0x5cd8ff),
    "abyss_fth": (icons.vial, 0xe8e8f2),
    "abyss_lck": (icons.vial, 0x4ae88a),
    "abyss_cure": (icons.vial, 0xffffff),
    "abyss_berserk": (icons.vial, 0xd84a6a),
    "abyss_bomb_f": (icons.bomb, 0xff5c2a),
    "abyss_bomb_t": (icons.bomb, 0xffe14a),
    "abyss_bomb_i": (icons.bomb, 0xaee9ff),
    "abyss_bomb_h": (lambda c: icons.bomb(c, crown=True), 0xff4a2a),
    "abyss_smoke": (icons.bomb, 0x8a8a95),
    "abyss_tele_a": (icons.rune_stone, 0x7ce8ff),
    "abyss_tele_s": (icons.rune_stone, 0x7ec850),
    "abyss_magnet": (icons.lodestone, 0xff4d6d),
    "abyss_reveal": (icons.scroll, 0xffffff),
    "abyss_feast": (icons.feast, 0xffffff),
    "abyss_ration": (icons.ration, 0xffffff),
    "abyss_breacher": (icons.bomblet, 0xffa53c),
    "abyss_shardfling": (icons.bomblet, 0x7cd8ff),
    "abyss_netsnare": (icons.net_orb, 0x8ff06e),
    "abyss_reapple": (icons.scroll_tool, 0x4bd07a),
    "abyss_soundbell": (icons.bell, 0xffd23c),
    "abyss_carebox": (icons.firstaid, 0xececf2),
    "abyss_wardrums": (icons.drum, 0xff4d6d),
    "abyss_bulwark": (icons.aegis, 0x6a8cff),
    "abyss_fortune": (icons.pouch, 0xffd23c),
    # --- 追加の消費品 (効果名に対応する絵) ---
    "abyss_flask": (icons.flask, 0xff4d6d),
    "abyss_gut": (icons.feast, 0x9ac86a),
    "abyss_grandfeast": (icons.feast, 0xffd23c),
    "abyss_regenx": (icons.vial, 0x4bd07a),
    "abyss_strx": (icons.vial, 0xff8a2b),
    "abyss_resx": (icons.vial, 0x7ce8ff),
    "abyss_absx": (icons.vial, 0xffd700),
    "abyss_stride": (icons.vial, 0x5cd8ff),
    "abyss_nightward": (icons.rune_stone, 0x6a4a9a),
    "abyss_salamander": (icons.vial, 0xff6a2b),
    "abyss_pearldive": (icons.orb, 0x3aa8ff),
    "abyss_veilnote": (icons.scroll, 0xd8d0ff),
    "abyss_purity": (icons.orb, 0xffffff),
    "abyss_chainbomb": (icons.bomb, 0xff3a5a),
    "abyss_depthbomb": (icons.bomb, 0x3a4aff),
    "abyss_artscroll": (icons.scroll_tool, 0xb14bff),
    "abyss_skillreset": (icons.rune_stone, 0x7ce8ff),
    "abyss_lleap": (icons.scroll_tool, 0x8ff06e),
    "abyss_goldmist": (icons.vial, 0xffd23c),
    "abyss_leechcandy": (icons.ration, 0xff6a7d),
    "abyss_eagle": (icons.scroll_tool, 0xd8b878),
    "abyss_overcharge": (icons.vial, 0xff5cff),
    "abyss_pickvein": (icons.vial, 0xffe14a),
    "abyss_godspeed": (icons.scroll_tool, 0xffffff),
    # --- 职业技能の巻物 ---
    "abyss_scroll_samurai": (icons.scroll_tool, 0xe9e3d5),
    "abyss_scroll_mage": (icons.scroll_tool, 0xc58bff),
    "abyss_scroll_ninja": (icons.scroll_tool, 0x6a8cff),
    "abyss_scroll_paladin": (icons.scroll_tool, 0xffd23c),
    "abyss_scroll_gunner": (icons.scroll_tool, 0xff8a2b),
    "abyss_scroll_berserker": (icons.scroll_tool, 0xff4d5e),
    "abyss_scroll_monk": (icons.scroll_tool, 0x7cffb2),
    "abyss_scroll_archer": (icons.scroll_tool, 0x4bd07a),
}

SOUL_SHARD_STATES = ("soul_shard_pouch", "soul_shard_bale", "soul_shard_core")

# 宝石系の素材 (2D は gem アイコン)
GEM_MATERIALS = {
    "chaos_gem", "void_essence", "abyss_blood_crystal", "abyss_shadow_stone",
    "abyss_star_dust", "abyss_wyrm_scale", "abyss_ancient_core", "abyss_void_piece",
    "abyss_frost_prism", "abyss_moonstone", "abyss_night_pearl", "abyss_lava_pearl",
}

# 防具の 2D ドット絵 (部位で関数が違う)
ARMOR_ICONS = {
    "abyss_helm": icons.helm,
    "abyss_chest": icons.chest,
    "abyss_legs": icons.legs,
    "abyss_boots": icons.boots,
}
for _set in ARMOR_SETS:
    ARMOR_ICONS[f"abyss_{_set}_helm"] = icons.helm
    ARMOR_ICONS[f"abyss_{_set}_chest"] = icons.chest
    ARMOR_ICONS[f"abyss_{_set}_legs"] = icons.legs
    ARMOR_ICONS[f"abyss_{_set}_boots"] = icons.boots
del _set

# 縦持ちする道具 (GUI では upright・所持時は真っ直ぐ) の持ち方
VERTICAL_ITEMS = {
    "abyss_staff", "abyss_void_staff", "abyss_wand", "abyss_bow", "abyss_moon_bow",
    "abyss_rod", "abyss_fishing_rod", "abyss_pickaxe", "abyss_tool_axe",
    "abyss_shovel", "abyss_hoe", "abyss_ember_flail", "abyss_scythe", "abyss_soul_scythe",
    "abyss_tome", "abyss_chaos_tome", "abyss_flame_grimoire", "abyss_war_horn",
    "abyss_depth_gun", "abyss_gauntlet", "abyss_titan_gauntlet", "abyss_howling_claw",
}

# 3D モデルを持つアイテムの 2D ドット絵 (インベントリ表示用)
WEAPON_ICONS = {
    "abyss_sword": icons.sword,
    "abyss_greatsword": icons.greatsword,
    "abyss_rune_blade": icons.rune_blade,
    "abyss_rift_blade": icons.rift_blade,
    "abyss_wind_blade": icons.wind_blade,
    "abyss_echo_katana": icons.echo_katana,
    "abyss_katana": icons.katana,
    "abyss_frost_katana": icons.frost_katana,
    "abyss_dagger": icons.dagger,
    "abyss_fang_dagger": icons.fang_dagger,
    "abyss_twin_dagger": icons.twin_dagger,
    "abyss_axe": icons.axe,
    "abyss_blood_axe": icons.blood_axe,
    "abyss_tool_axe": icons.tool_axe,
    "abyss_pickaxe": icons.pickaxe,
    "abyss_shovel": icons.shovel,
    "abyss_hoe": icons.hoe,
    "abyss_spear": icons.spear,
    "abyss_storm_spear": icons.storm_spear,
    "abyss_astral_lance": icons.astral_lance,
    "abyss_meteor_lance": icons.meteor_lance,
    "abyss_tide_trident": icons.tide_trident,
    "abyss_rod": icons.rod,
    "abyss_fishing_rod": icons.fishing_rod,
    "abyss_staff": icons.staff,
    "abyss_void_staff": icons.void_staff,
    "abyss_wand": icons.wand,
    "abyss_hammer": icons.hammer,
    "abyss_quake_hammer": icons.quake_hammer,
    "abyss_moon_mace": icons.moon_mace,
    "abyss_bow": icons.bow,
    "abyss_moon_bow": icons.moon_bow,
    "abyss_scythe": icons.scythe,
    "abyss_soul_scythe": icons.soul_scythe,
    "abyss_venom_whip": icons.venom_whip,
    "abyss_ember_flail": icons.ember_flail,
    "abyss_chakram": icons.chakram,
    "abyss_eclipse_chakram": icons.eclipse_chakram,
    "abyss_tome": icons.tome,
    "abyss_chaos_tome": icons.chaos_tome,
    "abyss_flame_grimoire": icons.flame_grimoire,
    "abyss_gauntlet": icons.gauntlet,
    "abyss_titan_gauntlet": icons.titan_gauntlet,
    "abyss_howling_claw": icons.howling_claw,
    "abyss_war_horn": icons.war_horn,
    "abyss_depth_gun": icons.depth_gun,
    "abyss_shears": icons.shears,
}

# 素材 (2D)
MATERIAL_ICONS = {
    "soul_shard": icons.shard,
    "ancient_stone": icons.ancient_stone,
    "chaos_gem": None,       # gem() は (outer, inner) なので個別処理
    "void_essence": None,
    "abyss_blood_crystal": None,
    "abyss_shadow_stone": None,
    "abyss_star_dust": None,
    "abyss_wyrm_scale": None,
    "abyss_ancient_core": None,
    "abyss_void_piece": None,
}

# インベントリ (GUI) 用の 2D ドット絵
GUI_CONTEXTS = ["gui"]
HELD_CONTEXTS = [
    "firstperson_righthand", "firstperson_lefthand",
    "thirdperson_righthand", "thirdperson_lefthand",
    "ground", "fixed", "head", "on_shelf",
]


# ================================================================= 生成

def write_model(item_id: str, data: dict) -> None:
    with io.open(os.path.join(MODEL_DIR, f"{item_id}.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def write_item_def(item_id: str, model: dict | None = None) -> None:
    """アイテム定義。model を省略すると通常の models/item 参照。"""
    if model is None:
        model = {"type": "minecraft:model", "model": f"abyssalloot:item/{item_id}"}
    data = {"model": model}
    with io.open(os.path.join(ITEM_DIR, f"{item_id}.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def write_select_def(item_id: str, icon_model: str) -> None:
    """所持時=3D / インベントリ=2D ドット絵 にする定義。"""
    select = {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [
            {"when": GUI_CONTEXTS,
             "model": {"type": "minecraft:model", "model": f"abyssalloot:item/{icon_model}"}},
            {"when": HELD_CONTEXTS,
             "model": {"type": "minecraft:model", "model": f"abyssalloot:item/{item_id}"}},
        ],
        "fallback": {"type": "minecraft:model", "model": f"abyssalloot:item/{item_id}"},
    }
    write_item_def(item_id, select)


def gen_model(item_id: str, shape_fn, palette: dict, kind: str, texture_name: str | None = None) -> None:
    """立体モデル + テクスチャ + 定義を生成する。"""
    m = boxmodel.Model(item_id)
    shape_fn(m, palette)
    m.build_texture()
    tex = texture_name or item_id
    pose = items.VERTICAL_POSE if item_id in VERTICAL_ITEMS else None
    held_v = item_id not in items.GUI_UPRIGHT_ONLY
    m.texture.save(os.path.join(TEX_DIR, f"{tex}.png"))
    write_model(item_id, m.model_json(f"abyssalloot:item/{tex}", items.display(kind, pose, held_v)))
    write_item_def(item_id)


def gen_icon(item_id: str, fn, color) -> None:
    """2D アイコンを生成する (立体的な縁取り加工あり)。"""
    img = fn(hexcolor(color))
    img = core.emboss(img)
    img.save(os.path.join(TEX_DIR, f"{item_id}.png"))
    write_model(item_id, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"abyssalloot:item/{item_id}"},
    })
    write_item_def(item_id)


def gen_2d_icon(item_id: str, fn, color, trim=None) -> None:
    """3D アイテム用の 2D ドット絵 (インベントリ表示)。"""
    icon_id = f"{item_id}_icon"
    if trim is None:
        img = fn(hexcolor(color))
    else:
        img = fn(hexcolor(color), hexcolor(trim))
    img = core.emboss(img)
    img.save(os.path.join(TEX_DIR, f"{icon_id}.png"))
    write_model(icon_id, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"abyssalloot:item/{icon_id}"},
    })
    write_select_def(item_id, icon_id)


def gen_material_icon(item_id: str, color: int) -> None:
    """素材の 2D ドット絵 (インベントリ表示)。"""
    icon_id = f"{item_id}_icon"
    if item_id == "soul_shard":
        img = icons.shard()
    elif item_id == "ancient_stone":
        img = icons.ancient_stone(hexcolor(color))
    elif item_id in GEM_MATERIALS:
        img = icons.gem(hexcolor(color), (230, 240, 255, 255))
    else:
        img = icons._mats3_tex(hexcolor(color))
    img = core.emboss(img)
    img.save(os.path.join(TEX_DIR, f"{icon_id}.png"))
    write_model(icon_id, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"abyssalloot:item/{icon_id}"},
    })
    write_select_def(item_id, icon_id)


def main() -> None:
    for d in (TEX_DIR, ENT_DIR, MODEL_DIR, ITEM_DIR):
        os.makedirs(d, exist_ok=True)

    # --- 武器 (立体 + 2D ドット絵) ---
    for item_id, (fn, color, style, kind) in WEAPON_MODELS.items():
        pal = shapes.make_palette(color, style)
        gen_model(item_id, fn, pal, kind)
        icon_fn = WEAPON_ICONS.get(item_id)
        if icon_fn is not None:
            gen_2d_icon(item_id, icon_fn, color)

    # --- 防具 (立体 + 2D ドット絵) ---
    for item_id, (fn, base, trim) in ARMOR_MODELS.items():
        pal = shapes.make_palette(base, "steel")
        pal["steel"] = shapes.Material("metal", hexcolor(base), metal=0.9, rough=0.3)
        pal["gold"] = shapes.Material("metal", hexcolor(trim), metal=1.0, rough=0.2)
        pal["accent"] = shapes.Material("crystal", hexcolor(trim), emissive=True)
        gen_model(item_id, fn, pal, "armor")
        icon_fn = ARMOR_ICONS.get(item_id)
        if icon_fn is not None:
            gen_2d_icon(item_id, icon_fn, trim, base)

    # --- 素材 (立体 + 2D ドット絵) ---
    for item_id, (fn, color) in MATERIAL_MODELS.items():
        pal = shapes.make_palette(color)
        gen_model(item_id, fn, pal, "flat")
        gen_material_icon(item_id, color)

    # --- 消耗品 (2D) ---
    for item_id, (fn, color) in CONSUMABLES.items():
        gen_icon(item_id, fn, color)

    # --- 魂の欠片 ---
    for state, item_id in enumerate(SOUL_SHARD_STATES):
        icons.shard_bag(state).save(os.path.join(TEX_DIR, f"{item_id}.png"))
        write_model(item_id, {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"abyssalloot:item/{item_id}"},
        })
        write_item_def(item_id)

    # --- 奥義の断片 (WeaponStyle の色から生成) ---
    styles = read_weapon_styles()
    for sid, col in styles:
        gen_art_shard(sid, col)

    # --- 深淵MOB ---
    for ent_id, (base, accent, pattern) in entities.ENTITIES.items():
        img = entities.entity_tex(hexcolor(base), hexcolor(accent), pattern)
        img.save(os.path.join(ENT_DIR, f"{ent_id}.png"))

    # --- 装備 (プレイヤーの防具) ---
    for _set, (_base, _trim) in EQUIPMENT_SETS.items():
        base_c = hexcolor(_base)
        trim_c = hexcolor(_trim)
        acc_c = hexcolor(_trim)
        equipment.equipment_tex(base_c, trim_c, acc_c).save(
            os.path.join(EQP_DIR, f"{_set}.png"))
        equipment.equipment_tex(base_c, trim_c, acc_c, leggings=True).save(
            os.path.join(EQP_DIR, f"{_set}_leggings.png"))

    print(f"generated {len(WEAPON_MODELS)} weapon models, {len(ARMOR_MODELS)} armor models, "
          f"{len(MATERIAL_MODELS)} material models, {len(CONSUMABLES)} icons, "
          f"{len(entities.ENTITIES)} entity textures, {len(EQUIPMENT_SETS) * 2} equipment textures")


def read_weapon_styles() -> list[tuple[str, int]]:
    """GameData.WeaponStyle から (id, 色) を取り出す。"""
    path = os.path.join(ROOT, "src", "main", "java", "com", "abyssalloot", "GameData.java")
    with io.open(path, encoding="utf-8") as f:
        src = f.read()
    out = []
    for _name, sid, col in re.findall(
            r'(\w+)\("([\w_]+)", "[^"]+", "[^"]+", "[^"]+", (0x[0-9a-fA-F]+)\)', src):
        out.append((sid, int(col, 16)))
    return out


def gen_art_shard(sid: str, color: int) -> None:
    """奥義の断片 (2D の欠片)。"""
    c = hexcolor(color)
    img, d = icons.canvas()
    icons.poly(d, [(3, 5), (8, 2), (13, 5), (13, 9), (8, 13), (3, 9)], (60, 50, 90, 255))
    icons.poly(d, [(5, 6), (8, 4), (11, 6), (11, 9), (8, 11), (5, 9)], icons.OUTLINE)
    icons.poly(d, [(6, 7), (8, 5.8), (10, 7), (10, 8), (8, 9), (6, 8)], c)
    d.ellipse([6, 6, 10, 10], outline=(20, 14, 30, 255), width=1)
    d.point((8, 7), fill=icons.WHITE)
    core.emboss(img)
    img.save(os.path.join(TEX_DIR, f"art_shard_{sid}.png"))
    write_model(f"art_shard_{sid}", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"abyssalloot:item/art_shard_{sid}"},
    })
    write_item_def(f"art_shard_{sid}")


if __name__ == "__main__":
    main()
