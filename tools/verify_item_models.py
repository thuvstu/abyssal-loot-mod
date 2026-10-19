#!/usr/bin/env python3
"""アイテムモデル (JSON) の静的検証。

- models/item/*.json が MC の elements 形式として妥当か
- UV がテクスチャ size を超えないか
- 参照しているテクスチャ / アイテム定義が存在するか
- 登録済みアイテムのうち、モデルが無いものがないか

使い方: python tools/verify_item_models.py
"""
from __future__ import annotations

import io
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot")
MODEL_DIR = os.path.join(ASSETS, "models", "item")
ITEM_DIR = os.path.join(ASSETS, "items")
TEX_DIR = os.path.join(ASSETS, "textures", "item")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "abyssalloot", "ModItems.java")

FACES = ("north", "south", "east", "west", "up", "down")


def main() -> int:
    errors: list[str] = []
    models = sorted(f[:-5] for f in os.listdir(MODEL_DIR) if f.endswith(".json"))
    print(f"models: {len(models)}")

    for item_id in models:
        path = os.path.join(MODEL_DIR, f"{item_id}.json")
        with io.open(path, encoding="utf-8") as f:
            data = json.load(f)

        # アイテム定義
        def_path = os.path.join(ITEM_DIR, f"{item_id}.json")
        if not os.path.exists(def_path):
            errors.append(f"{item_id}: アイテム定義が無い")
        else:
            with io.open(def_path, encoding="utf-8") as f:
                idef = json.load(f)
            if idef.get("model", {}).get("model") != f"abyssalloot:item/{item_id}":
                errors.append(f"{item_id}: アイテム定義の参照先が違う")

        # テクスチャの実在
        tex_refs = set()
        for key, ref in data.get("textures", {}).items():
            if not ref.startswith("abyssalloot:"):
                continue
            tex_refs.add(ref.split(":", 1)[1])
            tex_path = os.path.join(ASSETS, "textures", ref.split(":", 1)[1].replace("/", os.sep) + ".png")
            if not os.path.exists(tex_path):
                errors.append(f"{item_id}: テクスチャが無い {ref}")

        elements = data.get("elements")
        if not elements:
            # 2D アイコン (item/generated) は親と layer0 があれば良い
            if "layer0" not in data.get("textures", {}):
                errors.append(f"{item_id}: elements も textures.layer0 も無い")
            continue

        # UV が 0..16 の範囲に収まるか / テクスチャ内に収まるか
        for ei, el in enumerate(elements):
            frm, to = el.get("from"), el.get("to")
            if not frm or not to:
                errors.append(f"{item_id}[{ei}]: from/to が無い")
                continue
            if any(v < -16 or v > 32 for v in list(frm) + list(to)):
                errors.append(f"{item_id}[{ei}]: 座標が範囲外 {frm}..{to}")
            if any(frm[i] >= to[i] for i in range(3)):
                errors.append(f"{item_id}[{ei}]: from >= to {frm}..{to}")
            faces = el.get("faces", {})
            if not faces:
                errors.append(f"{item_id}[{ei}]: faces が空")
            for face, fd in faces.items():
                if face not in FACES:
                    errors.append(f"{item_id}[{ei}]: 未知の面 {face}")
                uv = fd.get("uv")
                if not uv or len(uv) != 4:
                    errors.append(f"{item_id}[{ei}].{face}: uv が不正 {uv}")
                    continue
                if uv[0] > uv[2] or uv[1] > uv[3]:
                    errors.append(f"{item_id}[{ei}].{face}: uv の順序が不正 {uv}")
                if min(uv) < 0 or max(uv) > 16:
                    errors.append(f"{item_id}[{ei}].{face}: uv が 0..16 外 {uv}")
                if fd.get("texture", "") not in ("#0", "#all", "#layer0"):
                    errors.append(f"{item_id}[{ei}].{face}: テクスチャ参照が不正 {fd.get('texture')}")
            rot = el.get("rotation")
            if rot:
                if rot.get("axis") not in ("x", "y", "z"):
                    errors.append(f"{item_id}[{ei}]: rotation.axis が不正")
                if not isinstance(rot.get("angle"), (int, float)):
                    errors.append(f"{item_id}[{ei}]: rotation.angle が不正")

        # display .transform チェック
        disp = data.get("display", {})
        for ctx, tr in disp.items():
            if "rotation" in tr and len(tr["rotation"]) != 3:
                errors.append(f"{item_id}: display.{ctx}.rotation が不正")
            if "scale" in tr and len(tr["scale"]) != 3:
                errors.append(f"{item_id}: display.{ctx}.scale が不正")
            if "translation" in tr and len(tr["translation"]) != 3:
                errors.append(f"{item_id}: display.{ctx}.translation が不正")

    # 登録済みアイテムにモデルがあるか
    if os.path.exists(JAVA):
        with io.open(JAVA, encoding="utf-8") as f:
            src = f.read()
        ids = set(re.findall(r'"(abyss_[\w]+|soul_shard[\w_]*|ancient_stone|chaos_gem|void_essence)"', src))
        missing = sorted(i for i in ids
                         if i not in models and not i.startswith("abyss_tab")
                         and not i.startswith("abyss_scroll_"))
        if missing:
            print(f"未生成のアイテム ({len(missing)}): " + " ".join(missing))

    if errors:
        print("ERRORS:")
        for e in errors:
            print("  " + e)
        return 1
    print("OK: 全アイテムモデルが正常")
    return 0


if __name__ == "__main__":
    sys.exit(main())
