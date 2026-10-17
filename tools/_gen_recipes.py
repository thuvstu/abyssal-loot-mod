"""レシピ一括生成:
- art_shard_<style> + 古代石 + 欠片x2 -> その武器 (33種)
- 欠片圧縮 (9→袋→包→核) とその展開 (6種)
- 第3弾消耗品レシピ (24種)
"""
import io
import json
import os
import re

GAME = os.path.join("src", "main", "java", "com", "abyssalloot", "GameData.java")
REC = os.path.join("src", "main", "resources", "data", "abyssalloot", "recipe")
os.makedirs(REC, exist_ok=True)

styles = re.findall(
    r'(\w+)\("([\w_]+)", "[^"]*", "[^"]*", "[^"]+", 0x[0-9a-fA-F]+\)',
    io.open(GAME, encoding='utf-8').read())


def write(name, recipe):
    with io.open(os.path.join(REC, name), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(recipe, f, indent=2, ensure_ascii=False)
        f.write('\n')


def shapeless(ingredients, result, count=1):
    return {
        "type": "minecraft:crafting_shapeless",
        "group": "abyssal_loot",
        "ingredients": [{"item": i} for i in ingredients],
        "result": {"id": result, "count": count},
    }


# 1) 断片 -> 武器
for _, sid in styles:
    write(f"art_fuse_{sid}", shapeless(
        [f"abyssalloot:art_shard_{sid}", "abyssalloot:ancient_stone",
         "abyssalloot:soul_shard", "abyssalloot:soul_shard"],
        f"abyssalloot:abyss_{sid}"))

# 2) 欠片圧縮・展開
write("shard_pouch", shapeless(["abyssalloot:soul_shard"] * 9, "abyssalloot:soul_shard_pouch"))
write("shard_bale", shapeless(["abyssalloot:soul_shard_pouch"] * 9, "abyssalloot:soul_shard_bale"))
write("shard_core", shapeless(["abyssalloot:soul_shard_bale"] * 9, "abyssalloot:soul_shard_core"))
write("shard_unpack_pouch", shapeless(["abyssalloot:soul_shard_pouch"],
                                      "abyssalloot:soul_shard", count=9))
write("shard_unpack_bale", shapeless(["abyssalloot:soul_shard_bale"],
                                     "abyssalloot:soul_shard_pouch", count=9))
write("shard_unpack_core", shapeless(["abyssalloot:soul_shard_core"],
                                     "abyssalloot:soul_shard_bale", count=9))

# 3) 第3弾消耗品 (素材1 + 欠片1 が基本。バニラの爪足は difference )
S = "abyssalloot:soul_shard"
recipes = {
    "flask": ["abyssalloot:abyss_teardrop", S],
    "gut": [S, "minecraft:cooked_beef"],
    "grandfeast": [S, "abyssalloot:abyss_moonstone", "minecraft:bread"],
    "regenx": [S, "abyssalloot:abyss_frost_prism"],
    "strx": [S, "abyssalloot:abyss_bone_charm"],
    "resx": [S, "abyssalloot:abyss_iron_rose"],
    "absx": [S, "abyssalloot:abyss_gold_vein"],
    "stride": [S, "minecraft:sugar"],
    "nightward": [S, "abyssalloot:abyss_night_pearl"],
    "salamander": [S, "abyssalloot:abyss_ember"],
    "pearldive": [S, "abyssalloot:abyss_mist_wisp"],
    "veilnote": [S, "minecraft:phantom_membrane"],
    "purity": [S, "abyssalloot:abyss_venom_sac"],
    "chainbomb": [S, "abyssalloot:abyss_thunder_thread"],
    "artscroll": [S, "abyssalloot:abyss_void_splinter"],
    "skillreset": [S, "abyssalloot:abyss_void_splinter", "abyssalloot:abyss_teardrop"],
    "lleap": [S, "minecraft:rabbit_foot"],
    "goldmist": [S, "minecraft:golden_nugget"],
    "leechcandy": [S, "abyssalloot:abyss_bloodroot"],
    "depthbomb": [S, "abyssalloot:abyss_lava_pearl", "minecraft:gunpowder"],
    "eagle": [S, "minecraft glow"],  # placeholder很差
    "overcharge": [S, "abyssalloot:abyss_star_seed"],
    "pickvein": [S, "abyssalloot:abyss_ash_powder"],
    "godspeed": [S, "abyssalloot:abyss_thunder_thread"],
}
recipes["eagle"] = [S, "minecraft:glow_ink_sac"]
for key, ing in recipes.items():
    write(f"con_{key}", shapeless(ing, f"abyssalloot:abyss_{key}"))

print("recipes:", len(styles), "+6+", len(recipes))
