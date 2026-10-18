# -*- coding: utf-8 -*-
"""grantAch(定型2行) -> Achievements.grant(id) への一括置換。

対象パターン (2行ペア・変数名は任意):
    var <v> = GameData.achievementById("<id>");
    Achievements.grantAch(player, "<id>", <v>.title(), GameData.shardsForReward(<v>.reward()));
変換後:
    Achievements.grant(player, "<id>");
"""
import io
import re
import sys

FILES = [
    r"src\main\java\com\abyssalloot\BossHandler.java",
    r"src\main\java\com\abyssalloot\FloorEvents.java",
    r"src\main\java\com\abyssalloot\KillHandler.java",
    r"src\main\java\com\abyssalloot\RunManager.java",
    r"src\main\java\com\abyssalloot\WeaponHitHandler.java",
    r"src\main\java\com\abyssalloot\SkillHandler.java",
]

PAT = re.compile(
    r"var (\w+) = GameData\.achievementById\(\"([^\"]+)\"\);\n"
    r"(\s*)Achievements\.grantAch\(player, \"\2\", \1\.title\(\), "
    r"GameData\.shardsForReward\(\1\.reward\(\)\)\);"
)

total = 0
for path in FILES:
    with io.open(path, "r", encoding="utf-8") as f:
        src = f.read()
    out, n = PAT.subn(lambda m: 'Achievements.grant(player, "%s");' % m.group(2), src)
    if n:
        with io.open(path, "w", encoding="utf-8", newline="") as f:
            f.write(out)
        print("%s: %d replaced" % (path, n))
        total += n
    else:
        print("%s: 0 replaced" % path)
print("total: %d" % total)
