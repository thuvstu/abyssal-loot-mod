# -*- coding: utf-8 -*-
"""バニラ連携データファイル (GLM・進捗・レシピ・タグ) を一括生成する。

生成物:
  data/neoforge/loot_modifiers/global_loot_modifiers.json
  data/abyssalloot/loot_modifiers/*.json            (6ファイル)
  data/abyssalloot/advancement/abyss/root.json      (進捗ルート)
  data/abyssalloot/advancement/abyss/<id>.json      (実績32件 - GameData.java から抽出)
  data/abyssalloot/recipe/*.json                    (2ファイル)
  data/minecraft/tags/item/beacon_payment_items.json
"""
import io
import json
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
DATA = os.path.join(ROOT, "src", "main", "resources", "data")

# ---------------- GLM ----------------
MODIFIERS = [
    ("ancient_city", 1.0, 2, 4),
    ("ancient_city_ice_box", 0.5, 1, 2),
    ("simple_dungeon", 0.5, 1, 2),
    ("abandoned_mineshaft", 0.4, 1, 2),
    ("buried_treasure", 0.5, 1, 3),
    ("stronghold_library", 0.4, 1, 2),
]

glm_dir = os.path.join(DATA, "abyssalloot", "loot_modifiers")
os.makedirs(glm_dir, exist_ok=True)
for table, chance, mn, mx in MODIFIERS:
    doc = {
        "type": "abyssalloot:shard_loot",
        "chance": chance,
        "min": mn,
        "max": mx,
        "conditions": [
            {"condition": "neoforge:loot_table_id",
             "loot_table_id": "minecraft:chests/%s" % table}
        ],
    }
    with io.open(os.path.join(glm_dir, table + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")

order = os.path.join(DATA, "neoforge", "loot_modifiers")
os.makedirs(order, exist_ok=True)
with io.open(os.path.join(order, "global_loot_modifiers.json"), "w", encoding="utf-8", newline="\n") as f:
    json.dump({"replace": False,
               "entries": ["abyssalloot:%s" % t for t, _, _, _ in MODIFIERS]},
              f, ensure_ascii=False, indent=2)
    f.write("\n")

# ---------------- 進捗 (GameData.java から実績定義を抽出) ----------------
gamedata = io.open(os.path.join(ROOT, "src", "main", "java", "com", "abyssalloot", "GameData.java"),
                   encoding="utf-8").read()
entry = re.compile(
    r'new Achievement\("([^"]+)", "([^"]+)", "((?:[^"\\]|\\.)*)", (\d+), "([^"]+)"\)')
ACH = entry.findall(gamedata)
assert len(ACH) == 32, "achievement parse failed: %d" % len(ACH)

FRAME = {"bronze": "task", "silver": "goal", "gold": "goal", "mythic": "challenge"}
ICON = {"bronze": "abyssalloot:soul_shard", "silver": "abyssalloot:soul_shard_pouch",
        "gold": "abyssalloot:soul_shard_bale", "mythic": "abyssalloot:soul_shard_core"}

CRITERIA = {"criteria": {"granted": {"trigger": "minecraft:impossible"}},
            "requirements": [["granted"]],
            "sends_telemetry_event": False}

adv_dir = os.path.join(DATA, "abyssalloot", "advancement", "abyss")
os.makedirs(adv_dir, exist_ok=True)

root = {
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
with io.open(os.path.join(adv_dir, "root.json"), "w", encoding="utf-8", newline="\n") as f:
    json.dump(root, f, ensure_ascii=False, indent=2)
    f.write("\n")

for aid, title, desc, reward, tier in ACH:
    child = {
        "parent": "abyssalloot:abyss/root",
        "display": {
            "announce_to_chat": False,  # MOD側のチャット告知と二重表示にならないようにする
            "description": desc,
            "frame": FRAME[tier],
            "icon": {"count": 1, "id": ICON[tier]},
            "title": title,
        },
        "criteria": {"granted": {"trigger": "minecraft:impossible"}},
        "requirements": [["granted"]],
        "sends_telemetry_event": False,
    }
    with io.open(os.path.join(adv_dir, aid + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(child, f, ensure_ascii=False, indent=2)
        f.write("\n")

# ---------------- レシピ ----------------
recipe_dir = os.path.join(DATA, "abyssalloot", "recipe")
os.makedirs(recipe_dir, exist_ok=True)
recipes = [
    ("shard_from_echo", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["minecraft:echo_shard"],
        "result": {"count": 1, "id": "abyssalloot:soul_shard"},
    }),
    ("purity_from_potion", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["abyssalloot:soul_shard", "minecraft:potion"],
        "result": {"count": 1, "id": "abyssalloot:abyss_cure"},
    }),
]
for name, doc in recipes:
    with io.open(os.path.join(recipe_dir, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")

# ---------------- タグ (ビーコンの燃料に欠片を認める) ----------------
tag_dir = os.path.join(DATA, "minecraft", "tags", "item")
os.makedirs(tag_dir, exist_ok=True)
with io.open(os.path.join(tag_dir, "beacon_payment_items.json"), "w", encoding="utf-8", newline="\n") as f:
    json.dump({"replace": False, "values": ["abyssalloot:soul_shard"]},
              f, ensure_ascii=False, indent=2)
    f.write("\n")

print("advancements: %d + root" % len(ACH))
print("loot modifiers: %d + order file" % len(MODIFIERS))
print("recipes: %d, tags: 1" % len(recipes))
