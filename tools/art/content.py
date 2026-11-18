"""追加コンテンツの定義 (src/main/resources/abyssalloot_content.json) を読む。

同じ JSON を Java (Content.java) も読む。アイテムを増やすときは JSON に 1 件足して
`python tools/abyss.py all` を実行すれば、アイコン・モデル・レシピ・翻訳まで揃う。
"""
from __future__ import annotations

import io
import json
import os

from .project import MOD_ID, RES

PATH = os.path.join(RES, f"{MOD_ID}_content.json")
PIECES = ("helm", "chest", "legs", "boots")
PIECE_NAMES = {"helm": ("兜", "Helm"), "chest": ("鎧", "Chestplate"),
               "legs": ("脚甲", "Leggings"), "boots": ("靴", "Boots")}
# Content.java の EFFECT_NAMES と同じキー
EFFECTS = {"strength", "speed", "resistance", "regeneration", "absorption", "fire_resistance", "haste",
           "jump_boost", "night_vision", "luck", "slow_falling", "water_breathing", "invisibility"}


def load() -> dict:
    with io.open(PATH, encoding="utf-8") as f:
        return json.load(f)


def color(entry: dict, key: str = "color") -> int:
    return int(entry[key], 16)


def lang_entries(doc: dict | None = None) -> tuple[dict, dict]:
    """(ja_jp, en_us) に入れる翻訳キー。"""
    doc = doc or load()
    ja, en = {}, {}
    for s in doc["armor_sets"]:
        for piece in PIECES:
            key = f"item.{MOD_ID}.abyss_{s['id']}_{piece}"
            ja[key] = f"{s['name']}の{PIECE_NAMES[piece][0]}"
            en[key] = f"{s['en']} {PIECE_NAMES[piece][1]}"
    for group in ("trophies", "zone_materials", "consumables"):
        for e in doc[group]:
            key = f"item.{MOD_ID}.{e['id']}"
            ja[key] = e["name"]
            en[key] = e["en"]
    return ja, en


def item_count(doc: dict | None = None) -> int:
    doc = doc or load()
    return (len(doc["armor_sets"]) * len(PIECES) + len(doc["trophies"])
            + len(doc["zone_materials"]) + len(doc["consumables"]))


def validate(doc: dict | None = None) -> list[str]:
    """定義の矛盾 (ID 重複・未知の効果・区域など) を列挙する。"""
    doc = doc or load()
    errs = []
    ids = [e["id"] for g in ("trophies", "zone_materials", "consumables") for e in doc[g]]
    ids += [f"abyss_{s['id']}_{p}" for s in doc["armor_sets"] for p in PIECES]
    for i in sorted({i for i in ids if ids.count(i) > 1}):
        errs.append(f"ID が重複: {i}")
    for c in doc["consumables"]:
        kind = c["kind"]
        if not kind.startswith("mix:"):
            errs.append(f"{c['id']}: kind は mix: で始める ({kind})")
            continue
        for part in kind[4:].split(";"):
            k, _, v = part.partition("=")
            if k in ("heal", "gauge"):
                if not v.isdigit():
                    errs.append(f"{c['id']}: {part} の値が数値でない")
            elif k == "fx":
                a = v.split(",")
                if len(a) != 3 or a[0] not in EFFECTS or not a[1].isdigit() or not a[2].isdigit():
                    errs.append(f"{c['id']}: fx の書式が不正 ({part})")
            else:
                errs.append(f"{c['id']}: 未知の効果種 {part}")
        if not (1 <= len(c.get("recipe", [])) <= 9):
            errs.append(f"{c['id']}: recipe の材料数が 1..9 でない")
    errs += _matgen_errors(doc)
    return errs


def _matgen_errors(doc: dict) -> list[str]:
    """"mat:<形>" の形・質感・副色が素材ジェネレータ (matgen.py) で描けるか。"""
    from . import matgen
    errs = []
    for group, key in (("trophies", "shape"), ("zone_materials", "shape"), ("consumables", "icon")):
        for e in doc[group]:
            sid = matgen.spec_from_entry(e, key)
            if sid is None:
                continue
            if sid not in matgen.shapes():
                errs.append(f"{e['id']}: 未知の形 mat:{sid}")
            if e.get("style") and e["style"] not in matgen.STYLES:
                errs.append(f"{e['id']}: 未知の質感 {e['style']} ({', '.join(matgen.STYLES)})")
            if e.get("accent") and len(e["accent"]) != 6:
                errs.append(f"{e['id']}: accent は rrggbb の 6 桁")
    return errs
