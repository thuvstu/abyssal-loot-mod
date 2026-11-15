#!/usr/bin/env python3
"""データパック (GLM・進捗・レシピ・タグ) を一括生成する。

旧 gen_vanilla_links.py と _gen_recipes.py を統合したもの。

生成物:
  data/neoforge/loot_modifiers/global_loot_modifiers.json
  data/abyssalloot/loot_modifiers/*.json          バニラ宝箱への欠片の混入
  data/abyssalloot/advancement/abyss/*.json        実績 (GameData.java の Achievement から抽出)
  data/abyssalloot/recipe/*.json                   断片→武器 / 欠片の圧縮・展開 / 消耗品 / バニラ連携
  data/minecraft/tags/item/beacon_payment_items.json
  data/abyssalloot/structure/gametest_floor.nbt    ゲームテスト用の床 (石 9x9 + 上空の空気)

使い方:
    python tools/gen_data.py           # 生成 (内容が変わったファイルだけ書き換える)
    python tools/gen_data.py --check   # 書き込まずに乖離だけ検出 (差分ありで exit 1)
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from art import content, nbt  # noqa: E402
from art.project import LANG, DATA, MOD_ID, Writer, java_source, read_weapon_styles, rel, setup_console  # noqa: E402

NS = f"{MOD_ID}:"
SHARD = f"{NS}soul_shard"

# ---------------------------------------------------------------- GLM
# (バニラの宝箱, 確率, 最小, 最大)
LOOT_MODIFIERS = [
    ("ancient_city", 1.0, 2, 4),
    ("ancient_city_ice_box", 0.5, 1, 2),
    ("simple_dungeon", 0.5, 1, 2),
    ("abandoned_mineshaft", 0.4, 1, 2),
    ("buried_treasure", 0.5, 1, 3),
    ("stronghold_library", 0.4, 1, 2),
]

# ---------------------------------------------------------------- 進捗
FRAME = {"bronze": "task", "silver": "goal", "gold": "goal", "mythic": "challenge"}
ICON = {"bronze": f"{NS}soul_shard", "silver": f"{NS}soul_shard_pouch",
        "gold": f"{NS}soul_shard_bale", "mythic": f"{NS}soul_shard_core"}
ROOT_ADVANCEMENT = {
    "display": {
        "announce_to_chat": False,
        "background": "minecraft:gui/advancements/backgrounds/end",
        "description": "深淵から滲み出る痕跡を追い、その系譜を刻む",
        "icon": {"count": 1, "id": "minecraft:echo_shard"},
        "show_toast": False,
        "title": "深淵の系譜",
    },
    "criteria": {"granted": {"trigger": "minecraft:impossible"}},
    "requirements": [["granted"]],
    "sends_telemetry_event": False,
}

# ---------------------------------------------------------------- 消耗品レシピ
# 結果 abyss_<key> <- 材料 (欠片 1 + 素材が基本)
CONSUMABLE_RECIPES = {
    "flask": [f"{NS}abyss_teardrop", SHARD],
    "gut": [SHARD, "minecraft:cooked_beef"],
    "grandfeast": [SHARD, f"{NS}abyss_moonstone", "minecraft:bread"],
    "regenx": [SHARD, f"{NS}abyss_frost_prism"],
    "strx": [SHARD, f"{NS}abyss_bone_charm"],
    "resx": [SHARD, f"{NS}abyss_iron_rose"],
    "absx": [SHARD, f"{NS}abyss_gold_vein"],
    "stride": [SHARD, "minecraft:sugar"],
    "nightward": [SHARD, f"{NS}abyss_night_pearl"],
    "salamander": [SHARD, f"{NS}abyss_ember"],
    "pearldive": [SHARD, f"{NS}abyss_mist_wisp"],
    "veilnote": [SHARD, "minecraft:phantom_membrane"],
    "purity": [SHARD, f"{NS}abyss_venom_sac"],
    "chainbomb": [SHARD, f"{NS}abyss_thunder_thread"],
    "artscroll": [SHARD, f"{NS}abyss_void_splinter"],
    "skillreset": [SHARD, f"{NS}abyss_void_splinter", f"{NS}abyss_teardrop"],
    "lleap": [SHARD, "minecraft:rabbit_foot"],
    "goldmist": [SHARD, "minecraft:gold_nugget"],
    "leechcandy": [SHARD, f"{NS}abyss_bloodroot"],
    "depthbomb": [SHARD, f"{NS}abyss_lava_pearl", "minecraft:gunpowder"],
    "eagle": [SHARD, "minecraft:glow_ink_sac"],
    "overcharge": [SHARD, f"{NS}abyss_star_seed"],
    "pickvein": [SHARD, f"{NS}abyss_ash_powder"],
    "godspeed": [SHARD, f"{NS}abyss_thunder_thread", "minecraft:feather"],
}


def shapeless(ingredients: list[str], result: str, count: int = 1) -> dict:
    # 1.21.2+ の Ingredient は ID 文字列 ("#" 始まりはタグ)。{"item": id} は読込エラーになる
    return {
        "type": "minecraft:crafting_shapeless",
        "group": "abyssal_loot",
        "ingredients": list(ingredients),
        "result": {"id": result, "count": count},
    }


def read_achievements() -> list[tuple[str, str, str, str, str]]:
    src = java_source("GameData.java")
    entry = re.compile(
        r'new Achievement\("([^"]+)", "([^"]+)", "((?:[^"\\]|\\.)*)", (\d+), "([^"]+)"\)')
    found = entry.findall(src)
    if not found:
        raise RuntimeError("GameData.java から Achievement を抽出できませんでした")
    return found


def generate(w: Writer) -> dict[str, int]:
    counts = {}

    # --- GLM
    glm_dir = os.path.join(DATA, MOD_ID, "loot_modifiers")
    for table, chance, mn, mx in LOOT_MODIFIERS:
        w.write_json(os.path.join(glm_dir, table + ".json"), {
            "type": f"{NS}shard_loot",
            "chance": chance,
            "min": mn,
            "max": mx,
            "conditions": [{"condition": "neoforge:loot_table_id",
                            "loot_table_id": f"minecraft:chests/{table}"}],
        })
    w.write_json(os.path.join(DATA, "neoforge", "loot_modifiers", "global_loot_modifiers.json"),
                 {"replace": False, "entries": [f"{NS}{t}" for t, *_ in LOOT_MODIFIERS]})
    counts["loot_modifiers"] = len(LOOT_MODIFIERS)

    # --- 進捗
    adv_dir = os.path.join(DATA, MOD_ID, "advancement", "abyss")
    w.write_json(os.path.join(adv_dir, "root.json"), ROOT_ADVANCEMENT)
    achievements = read_achievements()
    for aid, title, desc, _reward, tier in achievements:
        w.write_json(os.path.join(adv_dir, aid + ".json"), {
            "parent": f"{NS}abyss/root",
            "display": {
                "announce_to_chat": False,  # MOD 側のチャット告知と二重表示にならないようにする
                "description": desc,
                "frame": FRAME[tier],
                "icon": {"count": 1, "id": ICON[tier]},
                "title": title,
            },
            "criteria": {"granted": {"trigger": "minecraft:impossible"}},
            "requirements": [["granted"]],
            "sends_telemetry_event": False,
        })
    counts["advancements"] = len(achievements)

    # --- レシピ
    rec = os.path.join(DATA, MOD_ID, "recipe")
    n = 0

    def recipe(name: str, doc: dict) -> None:
        nonlocal n
        w.write_json(os.path.join(rec, name + ".json"), doc)
        n += 1

    # バニラ連携 (旧 gen_vanilla_links)
    recipe("shard_from_echo", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["minecraft:echo_shard"],
        "result": {"count": 1, "id": SHARD},
    })
    recipe("purity_from_potion", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [SHARD, "minecraft:potion"],
        "result": {"count": 1, "id": f"{NS}abyss_cure"},
    })
    # 断片 + 古代石 + 欠片x2 -> その武器
    for sid, _col in read_weapon_styles():
        recipe(f"art_fuse_{sid}", shapeless(
            [f"{NS}art_shard_{sid}", f"{NS}ancient_stone", SHARD, SHARD], f"{NS}abyss_{sid}"))
    # 欠片の圧縮 (9 -> 袋 -> 包 -> 核) と展開
    tiers = ["soul_shard", "soul_shard_pouch", "soul_shard_bale", "soul_shard_core"]
    for small, big in zip(tiers, tiers[1:]):
        recipe("shard_" + big.split("_")[-1], shapeless([NS + small] * 9, NS + big))
    for small, big in zip(tiers, tiers[1:]):
        recipe("shard_unpack_" + big.split("_")[-1], shapeless([NS + big], NS + small, count=9))
    # 消耗品
    for key, ing in CONSUMABLE_RECIPES.items():
        recipe(f"con_{key}", shapeless(ing, f"{NS}abyss_{key}"))
    # 追加コンテンツの消耗品 (abyssalloot_content.json の recipe)
    doc = content.load()
    for c in doc["consumables"]:
        recipe("x_" + c["id"].removeprefix("abyss_"), shapeless(c["recipe"], NS + c["id"]))
    # 素材の使い道 (交換・上位素材への変換)
    for r in doc.get("extra_recipes", []):
        recipe("x_" + r["name"], shapeless(r["ingredients"], r["result"], r.get("count", 1)))
    counts["recipes"] = n

    # --- 翻訳: 追加コンテンツの名前を lang へ反映 (手書きのキーはそのまま残す)
    for lang, entries in zip(("ja_jp", "en_us"), content.lang_entries(doc)):
        path = os.path.join(LANG, f"{lang}.json")
        with open(path, encoding="utf-8") as f:
            table = json.load(f)
        table.update(entries)
        w.write_json(path, table)
    counts["lang_keys"] = len(content.lang_entries(doc)[0])

    # --- タグ (ビーコンの燃料に欠片を認める)
    w.write_json(os.path.join(DATA, "minecraft", "tags", "item", "beacon_payment_items.json"),
                 {"replace": False, "values": [SHARD]})
    counts["tags"] = 1

    # --- ゲームテスト用の構造物 (src/main/java/.../AbyssGameTests が参照)
    size = (9, 4, 9)
    blocks = [((x, y, z), "minecraft:stone" if y == 0 else "minecraft:air")
              for y in range(size[1]) for z in range(size[2]) for x in range(size[0])]
    w.write_bytes(os.path.join(DATA, MOD_ID, "structure", "gametest_floor.nbt"),
                  nbt.structure_bytes(size, blocks))
    counts["structures"] = 1
    return counts


def main(argv: list[str] | None = None) -> int:
    setup_console()
    ap = argparse.ArgumentParser(description="データパック (GLM・進捗・レシピ・タグ) の生成")
    ap.add_argument("--check", action="store_true", help="書き込まずに差分の有無だけ調べる")
    ap.add_argument("-v", "--verbose", action="store_true", help="変更したファイルを列挙")
    args = ap.parse_args(argv)

    errs = content.validate()
    if errs:
        print("abyssalloot_content.json に問題があります:")
        for e in errs:
            print("  ", e)
        return 1
    w = Writer(check=args.check, verbose=args.verbose)
    counts = generate(w)
    w.flush()
    print("生成対象: " + ", ".join(f"{k} {v}" for k, v in counts.items()))
    print(w.summary("ファイル"))
    if args.check and w.changed:
        print("データ生成スクリプトと生成物が一致していません。python tools/gen_data.py を実行してください。")
        for p in w.changed[:20]:
            print("  ", rel(p))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
