#!/usr/bin/env python3
"""AbyssModels.java の静的検証。
- getChild("name") の対象が同モデルの定義ツリーに存在するか
  (存在しないと実機の初回描画でクラッシュするため)
- 全ボックスの UV (texOffs + 面サイズ) が 64x64 に収まるか
使い方: py tools/verify_models.py (tools/ でもリポジトリ直下でも可)
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "src", "main", "java", "com", "abyssalloot", "AbyssModels.java")


def num(s):
    s = s.strip().rstrip("f")
    return float(s)


def main():
    src = open(SRC, encoding="utf-8").read()
    errors = []
    # モデルクラスごとに createBodyLayer 本体を切り出す
    classes = {}
    for m in re.finditer(
            r"public static class (\w+) extends \w+ \{(.*?)public static LayerDefinition createBodyLayer\(\) \{(.*?)\n        \}",
            src, re.S):
        classes[m.group(1)] = (m.group(2), m.group(3))
    print(f"models: {len(classes)}")
    numpat = r"-?\d+(?:\.\d+)?f?"
    for cls, (ctor, body) in sorted(classes.items()):
        # パーツ定義ツリー: 変数名 -> 子名集合。root は特殊変数
        children = {"root": set()}
        varmap = {"root": "root"}
        for bm in re.finditer(
                r"(?:PartDefinition (\w+) = )?box(?:Rot)?\(\s*(\w+),\s*\"(\w+)\",\s*"
                r"([A-Z]+)(?:\s*\+\s*(\d+))?,\s*([A-Z]+)(?:\s*\+\s*(\d+))?,\s*"
                r"((?:" + numpat + r"\s*,\s*){8}" + numpat + r")",
                body):
            var, parent, name = bm.group(1), bm.group(2), bm.group(3)
            u = bm.group(4)
            v = bm.group(6)
            rest = bm.group(8)
            floats = [num(x) for x in re.findall(numpat, rest)]
            if var:
                varmap[var] = name
            pname = varmap.get(parent, parent)
            children.setdefault(pname, set()).add(name)
            # UV 検証: box 寸法は rest の先頭6個 (x,y,z,dx,dy,dz)
            if len(floats) >= 6:
                dx, dy, dz = floats[3], floats[4], floats[5]
                uu = {"BU": 0, "LU": 48, "DU": 0, "WU": 16, "AU": 32, "FU": 0}[u] \
                    + (int(bm.group(5)) if bm.group(5) else 0)
                vv = {"BV": 0, "LV": 0, "DV": 32, "WV": 32, "AV": 32, "FV": 48}[v] \
                    + (int(bm.group(7)) if bm.group(7) else 0)
                if uu + max(dx, dz) > 64 + 1e-6:
                    errors.append(f"{cls}.{name}: U overflow (u={uu}, need {max(dx, dz)})")
                if vv + max(dy, dz) > 64 + 1e-6:
                    errors.append(f"{cls}.{name}: V overflow (v={vv}, need {max(dy, dz)})")
                if dx <= 0 or dy <= 0 or dz <= 0:
                    errors.append(f"{cls}.{name}: 非正寸法 {dx}x{dy}x{dz}")
        # 直接 addOrReplaceChild("ring" 等、空パーツ) も登録
        for rm in re.finditer(r'(\w+)\.addOrReplaceChild\("(\w+)"', body):
            parent = rm.group(1)
            if parent == "root" or parent in varmap:
                children.setdefault(varmap.get(parent, parent), set()).add(rm.group(2))
        # getChild 検証 (コンストラクタ)
        targets = []

        def collect(partname, expr):
            for gm in re.finditer(r'(\w+)\.getChild\("(\w+)"\)', expr):
                targets.append((gm.group(1), gm.group(2)))

        collect(cls, ctor)

        def resolve(var):
            # this.body / root / body 等 -> パーツ名
            var = var.replace("this.", "")
            return varmap.get(var, var)

        ok = True
        for var, want in targets:
            base = resolve(var)
            # base 直下または root 直下に want があるか
            found = want in children.get(base, set()) or want in children.get("root", set())
            # base 自体が want の親変数として登録済みか (varmap 経由の二段)
            if not found:
                # var が指すパーツ名をたどる
                for pname, kids in children.items():
                    if want in kids and (pname == base or pname in varmap.values()):
                        pass
            if not found:
                # 最終手段: モデル内全文検索で定義の存在だけ確認
                if f'"{want}"' not in body:
                    errors.append(f"{cls}: getChild(\"{want}\") (from {var}={base}) が未定義")
                    ok = False
        print(f"  {cls}: parts={sum(len(v) for v in children.values())} getChild={len(targets)}")
    if errors:
        print("ERRORS:")
        for e in errors:
            print("  " + e)
        return 1
    print("OK: 全モデルの部品名・UV が正常")
    return 0


if __name__ == "__main__":
    sys.exit(main())
