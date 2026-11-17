#!/usr/bin/env python3
"""Abyssal Loot のアイテム / MOB アセットを生成する。

出力:
  src/main/resources/assets/abyssalloot/textures/item/*.png   アイテムのテクスチャ
  src/main/resources/assets/abyssalloot/textures/entity/*.png  深淵MOB のテクスチャ
  src/main/resources/assets/abyssalloot/models/item/*.json     立体モデル (elements)
  src/main/resources/assets/abyssalloot/items/*.json           アイテム定義 (1.21.4+)

使い方 (どのディレクトリからでも可):
    python tools/generate_textures.py                       # 全部
    python tools/generate_textures.py --only katana         # id に katana を含むものだけ
    python tools/generate_textures.py --kind weapons armor  # 種別で絞る
    python tools/generate_textures.py --check               # 書き込まず乖離だけ検出 (差分ありで exit 1)
    python tools/generate_textures.py -v                    # 更新したファイルを列挙

内容が変わったファイルだけを書き換える。Pillow が必要: pip install pillow
"""
from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from art import content, core, endgame, entities, equipment, finish, icons, items, potions, shapes  # noqa: E402
from art import boxmodel  # noqa: E402
from art.core import hexcolor  # noqa: E402
from art.project import ASSETS, Writer, matcher, read_weapon_styles, rel, setup_console  # noqa: E402
from art.project import ITEM_DEF as ITEM_DIR  # noqa: E402
from art.project import MODEL_ITEM as MODEL_DIR  # noqa: E402
from art.project import ROOT  # noqa: E402,F401
from art.project import TEX_ENTITY as ENT_DIR  # noqa: E402
from art.project import TEX_EQUIPMENT as EQP_DIR  # noqa: E402
from art.project import TEX_EQUIPMENT_LEGS as EQP_LEGS_DIR  # noqa: E402
from art.project import TEX_ITEM as TEX_DIR  # noqa: E402

KINDS = ("weapons", "armor", "materials", "consumables", "shards", "arts", "entities", "equipment", "content",
         "endgame")

# 追加コンテンツ (abyssalloot_content.json)。防具セットは下の表へ連結される
CONTENT = content.load()


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


def _scroll(glyph: str):
    """巻物 (紋章入り)。potions.scroll を参照。"""
    return lambda c: potions.scroll(c, glyph)


def _pot(form: str, glyph: str | None = None):
    """薬瓶 (形 + 効果の紋章)。potions.py を参照。"""
    return lambda c: potions.potion(c, form, glyph)


# id -> (形状, accent 色, スタイル, 表示種別)
WEAPON_MODELS = {
    "abyss_sword": (_sw(), 0xe9e3d5, "steel", "weapon"),
    "abyss_greatsword": (shapes.greatsword, 0xe9e3d5, "steel", "weapon"),
    "abyss_rune_blade": (_sw(style="rune"), 0x7ce8ff, "steel", "weapon"),
    "abyss_rift_blade": (_sw(style="rune", serrated=True, w=2.5), 0x8a5cff, "void", "weapon"),
    "abyss_wind_blade": (_sw(w=1.7, length=17.0, gem=False), 0x9fe8d8, "steel", "weapon"),
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
for _s in CONTENT["armor_sets"]:
    ARMOR_SETS[_s["id"]] = (content.color(_s, "body"), content.color(_s, "trim"))
    # 着用テクスチャ: 本体は暗めに落とし、装飾に本体色を使う (既存セットと同じ作り)
    EQUIPMENT_SETS[_s["id"]] = (core.to_hex(core.shade(hexcolor(content.color(_s, "body")), 0.36)),
                                content.color(_s, "body"))
del _s

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
    "soul_shard": (shapes.crystal, 0x5cc8ff),
    "ancient_stone": (shapes.stone_chunk, 0xf5c542),
    "chaos_gem": (shapes.gem, 0xe34bff),
    "void_essence": (shapes.crystal, 0x7c8cff),
    "abyss_blood_crystal": (shapes.gem, 0xff2a55),
    "abyss_shadow_stone": (shapes.stone_chunk, 0x4a2a6a),
    "abyss_star_dust": (shapes.splinter, 0xbfe9ff),
    "abyss_wyrm_scale": (shapes.scale_mat, 0x4bd07a),
    "abyss_ancient_core": (shapes.gem, 0xffd700),
    "abyss_void_piece": (shapes.splinter, 0x2a0a3a),
    "abyss_teardrop": (shapes.drop_mat, 0x7ce8ff),
    "abyss_ember": (shapes.drop_mat, 0xff8a2b),
    "abyss_frost_prism": (shapes.crystal, 0xaee9ff),
    "abyss_moonstone": (shapes.gem, 0xd8f0ff),
    "abyss_bone_charm": (shapes.bone_mat, 0xe8e8f2),
    "abyss_bloodroot": (shapes.rose_mat, 0xff4d6d),
    "abyss_thunder_thread": (shapes.thread_mat, 0xffe14a),
    "abyss_venom_sac": (shapes.drop_mat, 0x8ff06e),
    "abyss_ash_powder": (shapes.stone_chunk, 0x8a8a95),
    "abyss_void_splinter": (shapes.splinter, 0x8a5cff),
    "abyss_gold_vein": (shapes.stone_chunk, 0xffd23c),
    "abyss_iron_rose": (shapes.rose_mat, 0xd8b8c8),
    "abyss_night_pearl": (shapes.gem, 0x9a8ecf),
    "abyss_lava_pearl": (shapes.gem, 0xff5c2a),
    "abyss_mist_wisp": (shapes.wisp_mat, 0x9fd8ff),
    "abyss_star_seed": (shapes.drop_mat, 0xffe9a8),
}


# ================================================================= 2D アイコン

CONSUMABLES = {
    "abyss_heal_s": (_pot("flask_s", "cross"), 0xff5c5c),
    "abyss_heal_m": (_pot("flask_m", "cross"), 0xff2a55),
    "abyss_heal_l": (_pot("flask", "cross"), 0xffd0d8),
    "abyss_soul_s": (icons.orb, 0xb14bff),
    "abyss_soul_l": (icons.orb, 0xe34bff),
    "abyss_pwr": (_pot("tonic", "sword"), 0xff5c5c),
    "abyss_spd": (_pot("vial", "speed"), 0x7ce8ff),
    "abyss_irn": (_pot("tonic", "shield"), 0x9aa5b5),
    "abyss_rgn": (_pot("phial", "heart"), 0x4bd07a),
    "abyss_abs": (_pot("vial", "star"), 0xffd700),
    "abyss_fir": (_pot("phial", "flame"), 0xff8a2b),
    "abyss_owl": (_pot("vial", "eye"), 0x5cd8ff),
    "abyss_fth": (_pot("vial", "feather"), 0xe8e8f2),
    "abyss_lck": (_pot("phial", "clover"), 0x4ae88a),
    "abyss_cure": (_pot("flask_m", "drop"), 0xffffff),
    "abyss_berserk": (_pot("tonic", "skull"), 0xd84a6a),
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
    "abyss_reapple": (_scroll("heart"), 0x4bd07a),
    "abyss_soundbell": (icons.bell, 0xffd23c),
    "abyss_carebox": (icons.firstaid, 0xececf2),
    "abyss_wardrums": (icons.drum, 0xff4d6d),
    "abyss_bulwark": (icons.aegis, 0x6a8cff),
    "abyss_fortune": (icons.pouch, 0xffd23c),
    # --- 追加の消費品 (効果名に対応する絵) ---
    "abyss_flask": (_pot("flask", "heart"), 0xff4d6d),
    "abyss_gut": (icons.feast, 0x9ac86a),
    "abyss_grandfeast": (icons.feast, 0xffd23c),
    "abyss_regenx": (_pot("elixir", "heart"), 0x4bd07a),
    "abyss_strx": (_pot("elixir", "sword"), 0xff8a2b),
    "abyss_resx": (_pot("elixir", "shield"), 0x7ce8ff),
    "abyss_absx": (_pot("elixir", "star"), 0xffd700),
    "abyss_stride": (_pot("tonic", "arrow"), 0x5cd8ff),
    "abyss_nightward": (icons.rune_stone, 0x6a4a9a),
    "abyss_salamander": (_pot("elixir", "flame"), 0xff6a2b),
    "abyss_pearldive": (icons.orb, 0x3aa8ff),
    "abyss_veilnote": (icons.scroll, 0xd8d0ff),
    "abyss_purity": (icons.orb, 0xffffff),
    "abyss_chainbomb": (icons.bomb, 0xff3a5a),
    "abyss_depthbomb": (icons.bomb, 0x3a4aff),
    "abyss_artscroll": (_scroll("bolt"), 0xb14bff),
    "abyss_skillreset": (icons.rune_stone, 0x7ce8ff),
    "abyss_lleap": (_scroll("feather"), 0x8ff06e),
    "abyss_goldmist": (_pot("phial", "coin"), 0xffd23c),
    "abyss_leechcandy": (icons.ration, 0xff6a7d),
    "abyss_eagle": (_scroll("eye"), 0xd8b878),
    "abyss_overcharge": (_pot("elixir", "bolt"), 0xff5cff),
    "abyss_pickvein": (_pot("tonic", "pick"), 0xffe14a),
    "abyss_godspeed": (_scroll("speed"), 0xffffff),
    # --- 职业技能の巻物 ---
    "abyss_scroll_samurai": (_scroll("sword"), 0xe9e3d5),
    "abyss_scroll_mage": (_scroll("star"), 0xc58bff),
    "abyss_scroll_ninja": (_scroll("shuriken"), 0x6a8cff),
    "abyss_scroll_paladin": (_scroll("shield"), 0xffd23c),
    "abyss_scroll_gunner": (_scroll("gun"), 0xff8a2b),
    "abyss_scroll_berserker": (_scroll("axe"), 0xff4d5e),
    "abyss_scroll_monk": (_scroll("swirl"), 0x7cffb2),
    "abyss_scroll_archer": (_scroll("arrow"), 0x4bd07a),
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



# インベントリ (GUI) 用の 2D ドット絵
GUI_CONTEXTS = ["gui"]
HELD_CONTEXTS = [
    "firstperson_righthand", "firstperson_lefthand",
    "thirdperson_righthand", "thirdperson_lefthand",
    "ground", "fixed", "head", "on_shelf",
]


# ================================================================= 生成

W = Writer()  # main() で差し替える


def _png(path: str, img) -> None:
    W.write_png(path, img)


def write_model(item_id: str, data: dict) -> None:
    W.write_json(os.path.join(MODEL_DIR, f"{item_id}.json"), data)


def write_flat_model(item_id: str) -> None:
    """2D ドット絵 (item/generated) のモデル。"""
    write_model(item_id, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"abyssalloot:item/{item_id}"},
    })


def write_item_def(item_id: str, model: dict | None = None) -> None:
    """アイテム定義。model を省略すると通常の models/item 参照。"""
    if model is None:
        model = {"type": "minecraft:model", "model": f"abyssalloot:item/{item_id}"}
    W.write_json(os.path.join(ITEM_DIR, f"{item_id}.json"), {"model": model})


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


def build_model(item_id: str, shape_fn, palette: dict) -> boxmodel.Model:
    """形状関数から箱モデルを組み、テクスチャまで焼く (書き込みはしない)。"""
    m = boxmodel.Model(item_id)
    shape_fn(m, palette)
    m.build_texture()
    return m


def gen_model(item_id: str, shape_fn, palette: dict, kind: str, texture_name: str | None = None) -> None:
    """立体モデル + テクスチャ + 定義を生成する。"""
    m = build_model(item_id, shape_fn, palette)
    tex = texture_name or item_id
    pose = items.VERTICAL_POSE if item_id in VERTICAL_ITEMS else None
    held_v = item_id not in items.GUI_UPRIGHT_ONLY
    _png(os.path.join(TEX_DIR, f"{tex}.png"), m.texture)
    write_model(item_id, m.model_json(f"abyssalloot:item/{tex}",
                                      items.display(kind, pose, held_v, grip=m.grip_point())))
    write_item_def(item_id)


def gen_icon(item_id: str, fn, color) -> None:
    """2D アイコンを生成する (立体的な縁取り加工あり)。"""
    img = finish.finish(fn(hexcolor(color)))
    _png(os.path.join(TEX_DIR, f"{item_id}.png"), img)
    write_flat_model(item_id)
    write_item_def(item_id)


def gen_2d_icon(item_id: str, fn, color, trim=None) -> None:
    """3D アイテム用の 2D ドット絵 (インベントリ表示)。"""
    icon_id = f"{item_id}_icon"
    img = fn(hexcolor(color)) if trim is None else fn(hexcolor(color), hexcolor(trim))
    _png(os.path.join(TEX_DIR, f"{icon_id}.png"), finish.finish(img))
    write_flat_model(icon_id)
    write_select_def(item_id, icon_id)


MATERIAL_ICON_SHAPES = {
    shapes.gem: "gem",
    shapes.crystal: "crystal",
    shapes.splinter: "splinter",
    shapes.drop_mat: "drop",
}


def material_icon(item_id: str, color: int):
    if item_id == "soul_shard":
        return icons.shard()
    if item_id == "ancient_stone":
        return icons.ancient_stone(hexcolor(color))
    # 3D モデルの形状に合わせて 2D の形を選ぶ (同じ塊ばかりにならないように)
    shape = MATERIAL_MODELS.get(item_id, (None,))[0]
    kind = MATERIAL_ICON_SHAPES.get(shape)
    if kind == "gem" or (kind is None and item_id in GEM_MATERIALS):
        return icons.gem(hexcolor(color), (230, 240, 255, 255))
    if kind is not None:
        return icons.mask_icon(kind, hexcolor(color))
    return icons._mats3_tex(hexcolor(color))


def gen_material_icon(item_id: str, color: int) -> None:
    """素材の 2D ドット絵 (インベントリ表示)。"""
    icon_id = f"{item_id}_icon"
    _png(os.path.join(TEX_DIR, f"{icon_id}.png"), finish.finish(material_icon(item_id, color)))
    write_flat_model(icon_id)
    write_select_def(item_id, icon_id)


def content_material_icon(shape: str, color):
    """追加素材の 2D ドット絵。chunk は粒入りの塊、それ以外はマスクをそのまま塗る。"""
    if shape == "chunk":
        return icons._mats3_tex(color)
    return icons.mask_icon(shape, color)


def armor_palette(base: int, trim: int) -> dict:
    """防具の配色: 本体色は「色付きの鋼」として薄く混ぜ、装飾色を縁・宝石に回す。"""
    body = hexcolor(base)
    tcol = hexcolor(trim)
    pal = shapes.make_palette(trim, "steel")
    pal["steel"] = shapes.Material("metal", core.mix((108, 116, 140, 255), body, 0.34), metal=0.95, rough=0.26)
    pal["dark"] = shapes.Material("metal", core.mix((58, 62, 80, 255), body, 0.30), metal=0.85, rough=0.38)
    pal["bright"] = shapes.Material("metal", core.mix((186, 196, 220, 255), tcol, 0.16), metal=1.0, rough=0.16)
    pal["cloth"] = shapes.Material("cloth", body)
    pal["leather"] = shapes.Material("leather", core.mix((78, 54, 38, 255), body, 0.35))
    pal["gold"] = shapes.Material("metal", tcol, metal=1.0, rough=0.2)
    pal["accent"] = shapes.Material("crystal", tcol, emissive=True)
    pal["accent_dim"] = shapes.Material("crystal", core.shade(tcol, 0.76))
    return pal


def art_shard_icon(sid: str, color: int):
    """奥義の断片: その武器のアイコンを刻印として封じた、割れた結晶の欠片。"""
    weapon = f"abyss_{sid}"
    icon_fn = WEAPON_ICONS.get(weapon)
    if icon_fn is None:
        return finish.finish(icons.crystal_splinter(hexcolor(color)))
    accent = WEAPON_MODELS[weapon][1] if weapon in WEAPON_MODELS else color
    return finish.finish(icons.art_shard(icon_fn(hexcolor(accent)), hexcolor(color)))


def gen_art_shard(sid: str, color: int) -> None:
    item_id = f"art_shard_{sid}"
    _png(os.path.join(TEX_DIR, f"{item_id}.png"), art_shard_icon(sid, color))
    write_flat_model(item_id)
    write_item_def(item_id)


def _handheld(model_id: str) -> None:
    write_model(model_id, {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": f"abyssalloot:item/{model_id}"},
    })


def write_stage_def(item_id: str, model_ids: list[str]) -> None:
    """CUSTOM_MODEL_DATA の floats[0] (段階) で見た目を切り替えるアイテム定義。"""
    write_item_def(item_id, {
        "type": "minecraft:range_dispatch",
        "property": "minecraft:custom_model_data",
        "index": 0,
        "entries": [{"threshold": float(i),
                     "model": {"type": "minecraft:model", "model": f"abyssalloot:item/{m}"}}
                    for i, m in enumerate(model_ids) if i > 0],
        "fallback": {"type": "minecraft:model", "model": f"abyssalloot:item/{model_ids[0]}"},
    })


def _content_form(e: dict) -> str:
    """追加コンテンツの薬の瓶の形 (名前から: elixir / tonic / draught / dew …)。"""
    name = e["id"]
    for key, form in (("elixir", "elixir"), ("tonic", "tonic"), ("oil", "tonic"), ("wine", "tonic"),
                      ("draught", "flask"), ("brew", "phial"), ("serum", "phial"), ("blood", "flask_m"),
                      ("dew", "vial"), ("drops", "vial"), ("tea", "flask_m")):
        if key in name:
            return form
    return "vial" if e["icon"] == "vial" else "flask"


def gen_endgame(want) -> int:
    """夜想曲ノクターン (カウント0〜4、1〜4 は光が流れるアニメ) と相棒の剣 (3段階) と限界突破の楔。"""
    n = 0
    if want("abyss_nocturne"):
        ids = []
        for c in range(endgame.NOCTURNE_STAGES):
            mid = f"abyss_nocturne_c{c}"
            img = endgame.source(f"nocturne_{c}")
            path = os.path.join(TEX_DIR, f"{mid}.png")
            if c == 0:
                _png(path, img)
            else:
                _png(path, endgame.pulse_frames(img))
                W.write_json(path + ".mcmeta", endgame.anim_meta())
            _handheld(mid)
            ids.append(mid)
        write_stage_def("abyss_nocturne", ids)
        n += 1
    if want("abyss_aibou"):
        ids = []
        for s in range(endgame.AIBOU_LOOKS):
            mid = f"abyss_aibou_s{s}"
            _png(os.path.join(TEX_DIR, f"{mid}.png"), endgame.source(f"aibou_{s}"))
            _handheld(mid)
            ids.append(mid)
        write_stage_def("abyss_aibou", ids)
        n += 1
    if want("aibou_limit_stone"):
        _png(os.path.join(TEX_DIR, "aibou_limit_stone.png"), endgame.limit_stone())
        write_flat_model("aibou_limit_stone")
        write_item_def("aibou_limit_stone")
        n += 1
    return n


def generate(kinds: set[str], want) -> dict[str, int]:
    """kinds の種別のうち want(id) が真のものを生成し、種別ごとの件数を返す。"""
    counts = {k: 0 for k in KINDS}

    if "weapons" in kinds:  # 立体 + 2D ドット絵
        for item_id, (fn, color, style, kind) in WEAPON_MODELS.items():
            if not want(item_id):
                continue
            gen_model(item_id, fn, shapes.make_palette(color, style), kind)
            icon_fn = WEAPON_ICONS.get(item_id)
            if icon_fn is not None:
                gen_2d_icon(item_id, icon_fn, color)
            counts["weapons"] += 1

    if "armor" in kinds:  # 立体 + 2D ドット絵
        for item_id, (fn, base, trim) in ARMOR_MODELS.items():
            if not want(item_id):
                continue
            gen_model(item_id, fn, armor_palette(base, trim), "armor")
            icon_fn = ARMOR_ICONS.get(item_id)
            if icon_fn is not None:
                gen_2d_icon(item_id, icon_fn, trim, base)
            counts["armor"] += 1

    if "materials" in kinds:  # 立体 + 2D ドット絵
        for item_id, (fn, color) in MATERIAL_MODELS.items():
            if not want(item_id):
                continue
            gen_model(item_id, fn, shapes.make_palette(color), "flat")
            gen_material_icon(item_id, color)
            counts["materials"] += 1

    if "consumables" in kinds:  # 2D
        for item_id, (fn, color) in CONSUMABLES.items():
            if want(item_id):
                gen_icon(item_id, fn, color)
                counts["consumables"] += 1

    if "shards" in kinds:  # 魂の欠片 (袋・包・核)
        for state, item_id in enumerate(SOUL_SHARD_STATES):
            if not want(item_id):
                continue
            _png(os.path.join(TEX_DIR, f"{item_id}.png"), icons.shard_bag(state))
            write_flat_model(item_id)
            write_item_def(item_id)
            counts["shards"] += 1

    if "arts" in kinds:  # 奥義の断片 (WeaponStyle の色から生成)
        for sid, col in read_weapon_styles():
            if want(f"art_shard_{sid}"):
                gen_art_shard(sid, col)
                counts["arts"] += 1

    if "entities" in kinds:  # 深淵MOB
        for ent_id, (base, accent, pattern) in entities.ENTITIES.items():
            if want(ent_id):
                img = entities.entity_tex(hexcolor(base), hexcolor(accent), pattern)
                _png(os.path.join(ENT_DIR, f"{ent_id}.png"), img)
                counts["entities"] += 1

    if "content" in kinds:  # 追加コンテンツ: 魔物素材・区域素材・消耗品 (2D) と装備定義
        for e in CONTENT["trophies"] + CONTENT["zone_materials"]:
            if want(e["id"]):
                gen_icon(e["id"], lambda c, _k=e["shape"]: content_material_icon(_k, c), content.color(e))
                counts["content"] += 1
        for e in CONTENT["consumables"]:
            if want(e["id"]):
                # icon: icons.py の関数名、または "mask:<形>" (色付きマスク)
                if e["icon"].startswith("mask:"):
                    fn = lambda c, _k=e["icon"][5:]: icons.mask_icon(_k, c)  # noqa: E731
                elif e["icon"] in ("vial", "flask"):
                    fn = _pot(_content_form(e), potions.glyph_for_kind(e["kind"]))
                else:
                    fn = getattr(icons, e["icon"])
                gen_icon(e["id"], fn, content.color(e))
                counts["content"] += 1
        for s in CONTENT["armor_sets"]:
            if want(s["id"]):
                W.write_json(os.path.join(ASSETS, "equipment", f"{s['id']}.json"), {"layers": {
                    "humanoid": [{"texture": f"abyssalloot:{s['id']}"}],
                    "humanoid_leggings": [{"texture": f"abyssalloot:{s['id']}_leggings"}],
                }})

    if "endgame" in kinds:  # エンドコンテンツ武器 (元絵 tools/art/src + CUSTOM_MODEL_DATA で段階切替)
        counts["endgame"] += gen_endgame(want)

    if "equipment" in kinds:  # 装備 (プレイヤーの防具の着用テクスチャ)
        for set_id, (base, trim) in EQUIPMENT_SETS.items():
            if not want(set_id):
                continue
            base_c, trim_c = hexcolor(base), hexcolor(trim)
            _png(os.path.join(EQP_DIR, f"{set_id}.png"), equipment.equipment_tex(base_c, trim_c, trim_c))
            _png(os.path.join(EQP_LEGS_DIR, f"{set_id}_leggings.png"),
                 equipment.equipment_tex(base_c, trim_c, trim_c, leggings=True))
            counts["equipment"] += 2

    return counts


def main(argv: list[str] | None = None) -> int:
    global W
    setup_console()
    ap = argparse.ArgumentParser(description="Abyssal Loot のテクスチャ / モデル生成")
    ap.add_argument("--only", nargs="+", metavar="PAT",
                    help="id で絞る (部分一致。* ? も可)。例: --only katana 'abyss_*_helm'")
    ap.add_argument("--kind", nargs="+", choices=KINDS, help="生成する種別")
    ap.add_argument("--check", action="store_true", help="書き込まずに差分の有無だけ調べる")
    ap.add_argument("-v", "--verbose", action="store_true", help="変更したファイルを列挙")
    args = ap.parse_args(argv)

    W = Writer(check=args.check, verbose=args.verbose)
    kinds = set(args.kind or KINDS)
    counts = generate(kinds, matcher(args.only))
    W.flush()
    made = ", ".join(f"{k} {v}" for k, v in counts.items() if v)
    print(f"生成対象: {made or 'なし'}")
    print(W.summary("ファイル"))
    if args.check and W.changed:
        print("生成スクリプトと生成物が一致していません。python tools/generate_textures.py を実行してください。")
        for p in W.changed[:20]:
            print("  ", rel(p))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
