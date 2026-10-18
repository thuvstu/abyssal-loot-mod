# -*- coding: utf-8 -*-
"""WeaponArts の奥義・小技スイッチが41武器種すべてを網羅しているか検証する。

GameData.WeaponStyle の id 列挙 (GameData.java) と WeaponArts.java の case ラベルを突き合わせる。
execute (奥義) は3グループ、executeLight (小技) は2グループに分かれているため、
それぞれ「奥義=41種」「小技=41種」の case があるはず。
"""
import io
import re

gd = io.open(r"src\main\java\com\abyssalloot\GameData.java", encoding="utf-8").read()
wa = io.open(r"src\main\java\com\abyssalloot\WeaponArts.java", encoding="utf-8").read()

# WeaponStyle enum の定義ブロックを切り出して id を抽出
style_block = gd[gd.index("public enum WeaponStyle"):gd.index("public static final WeaponStyle[] VALUES")]
ids = re.findall(r'^\s+([A-Z_]+)\("([a-z_]+)",', style_block, re.M)
print("WeaponStyle count: %d" % len(ids))

# WeaponArts.java を メソッド単位に分割
def method_slice(src, name):
    start = src.index("private static Boolean %s(" % name) if name.startswith("execute") and "Light" not in name \
        else src.index("private static void %s(" % name)
    depth = 0
    i = src.index("{", start)
    j = i
    while True:
        if src[j] == "{":
            depth += 1
        elif src[j] == "}":
            depth -= 1
            if depth == 0:
                break
        j += 1
    return src[start:j]

arts_groups = ["executeCoreArts", "executeExtendedArts", "executeEliteArts"]
light_groups = ["executeLightCore", "executeLightExtended"]

for label, groups, in (("奥義(execute)", arts_groups), ("小技(executeLight)", light_groups)):
    seen = {}
    for g in groups:
        body = method_slice(wa, g)
        for m in re.finditer(r'case ([A-Z_]+) ->', body):
            seen.setdefault(m.group(1), g)
    missing = [c for c, _ in ids if c not in seen]
    dup = {k: v for k, v in seen.items() if sum(1 for c, _ in ids if c == k) == 0}
    unknown = [k for k in seen if k not in {c for c, _ in ids}]
    print("%s: cases=%d missing=%s unknown=%s" % (label, len(seen), missing, unknown))
    if not missing and not unknown:
        print("  -> 41種すべて網羅 OK")
