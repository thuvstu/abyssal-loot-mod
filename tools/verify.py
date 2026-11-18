#!/usr/bin/env python3
"""リソース / データ / Java 定義の静的検証 (MC を起動せずに壊れた参照を見つける)。

旧 verify_item_models.py / verify_models.py / verify_arts_coverage.py を統合し、
実機ログでしか分からなかった不具合 (拡張子の無いデータファイル、旧書式のレシピ材料、
装備レイヤーのテクスチャ欠落、翻訳漏れ) も検出する。

使い方:
    python tools/verify.py                 # 全チェック
    python tools/verify.py recipes lang    # 指定チェックのみ
    python tools/verify.py --list          # チェック一覧
    python tools/verify.py --strict        # 警告もエラー扱い

終了コード: エラーがあれば 1。
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

from art.project import (ASSETS, DATA, ITEM_DEF, JAVA_DIR, LANG, MOD_ID, MODEL_ITEM, RES, TEX_ENTITY,  # noqa: E402
                         TEX_ITEM, java_source, read_weapon_style_consts, rel, setup_console,
                         tex_file)

NS = f"{MOD_ID}:"
FACES = ("north", "south", "east", "west", "up", "down")
LANGS = ("ja_jp", "en_us")


class Report:
    def __init__(self):
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.notes: list[str] = []

    def err(self, msg: str) -> None:
        self.errors.append(msg)

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)

    def note(self, msg: str) -> None:
        self.notes.append(msg)


def load_json(path: str, rep: Report):
    try:
        with io.open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception as exc:  # noqa: BLE001
        rep.err(f"{rel(path)}: JSON として読めない ({exc})")
        return None


def item_ids() -> set[str]:
    return {f[:-5] for f in os.listdir(ITEM_DEF) if f.endswith(".json")}


def walk(base: str):
    for dirpath, _dirs, files in os.walk(base):
        for f in files:
            yield os.path.join(dirpath, f)


# ================================================================ files

def check_files(rep: Report) -> None:
    """拡張子の無い / 想定外のファイル (MC は黙って無視する) と JSON 構文。"""
    allowed = {".json", ".png", ".mcmeta", ".nbt", ".ogg", ".txt", ".fsh", ".vsh", ".glsl"}
    n = 0
    for path in walk(RES):
        n += 1
        ext = os.path.splitext(path)[1].lower()
        if os.path.dirname(path) == RES:
            continue  # pack.mcmeta / logo など
        if "META-INF" in path:
            continue
        if ext not in allowed:
            rep.err(f"{rel(path)}: 拡張子 '{ext or '(なし)'}' は読み込まれない")
        elif ext == ".json":
            load_json(path, rep)
    rep.note(f"{n} ファイル")


# ================================================================ item models

def check_item_models(rep: Report) -> None:
    """models/item と items/ の整合 (UV・テクスチャ実在・select 参照)。"""
    models = sorted(f[:-5] for f in os.listdir(MODEL_ITEM) if f.endswith(".json"))
    variants = _variant_models()
    for item_id in models:
        data = load_json(os.path.join(MODEL_ITEM, f"{item_id}.json"), rep)
        if data is None:
            continue
        is_icon = item_id.endswith("_icon")
        def_path = os.path.join(ITEM_DEF, f"{item_id}.json")
        if not os.path.exists(def_path):
            # 段階切替 (range_dispatch) の部品モデルは他のアイテム定義から参照される
            if not is_icon and item_id not in variants:
                rep.err(f"{item_id}: アイテム定義 (items/{item_id}.json) が無い")
        else:
            idef = load_json(def_path, rep) or {}
            _check_item_def(item_id, idef.get("model", {}), rep, is_icon)

        for _key, ref in data.get("textures", {}).items():
            if ref.startswith(NS) and not os.path.exists(tex_file(ref)):
                rep.err(f"{item_id}: テクスチャが無い {ref}")

        parent = data.get("parent", "")
        if parent.startswith(NS) and not os.path.exists(
                os.path.join(ASSETS, "models", parent.split(":", 1)[1] + ".json")):
            rep.err(f"{item_id}: 親モデルが無い {parent}")

        elements = data.get("elements")
        if not elements:
            if "layer0" not in data.get("textures", {}) and not parent:
                rep.err(f"{item_id}: elements も textures.layer0 も無い")
            continue
        for ei, el in enumerate(elements):
            _check_element(f"{item_id}[{ei}]", el, rep)
        for ctx, tr in data.get("display", {}).items():
            for k in ("rotation", "translation", "scale"):
                if k in tr and len(tr[k]) != 3:
                    rep.err(f"{item_id}: display.{ctx}.{k} の要素数が 3 でない")
    rep.note(f"models {len(models)}")

    # items/ 側だけにあるもの
    for item_id in sorted(item_ids()):
        if not os.path.exists(os.path.join(MODEL_ITEM, f"{item_id}.json")):
            idef = load_json(os.path.join(ITEM_DEF, f"{item_id}.json"), rep) or {}
            _check_item_def(item_id, idef.get("model", {}), rep, False)


def _model_exists(ref: str) -> bool:
    if not ref.startswith(NS):
        return True  # バニラ参照は検証しない
    return os.path.exists(os.path.join(ASSETS, "models", ref.split(":", 1)[1] + ".json"))


def _model_refs(mdl: dict) -> list[str]:
    """アイテム定義の模型ツリー (select / range_dispatch / special の入れ子) が参照する全モデル。"""
    out = []
    t = mdl.get("type")
    if t == "minecraft:model":
        out.append(mdl.get("model", ""))
    elif t == "minecraft:special":
        out.append(mdl.get("base", ""))
    for e in mdl.get("entries", []) + mdl.get("cases", []):
        out += _model_refs(e.get("model", {}))
    for m in mdl.get("models", []):  # composite (重ね描き)
        out += _model_refs(m)
    if "fallback" in mdl:
        out += _model_refs(mdl["fallback"])
    return out


def _variant_models() -> set[str]:
    """入れ子の定義 (段階切替・3D/2D 切替) から参照される部品モデル id。"""
    out: set[str] = set()
    for f in os.listdir(ITEM_DEF):
        if not f.endswith(".json"):
            continue
        with open(os.path.join(ITEM_DEF, f), encoding="utf-8") as fh:
            mdl = json.load(fh).get("model", {})
        if mdl.get("type") == "minecraft:model":
            continue
        out |= {r.split("/", 1)[-1] for r in _model_refs(mdl) if r.startswith(NS)}
    return out


def _check_item_def(item_id: str, mdl: dict, rep: Report, is_icon: bool) -> None:
    t = mdl.get("type")
    if t == "minecraft:range_dispatch":
        entries = mdl.get("entries", [])
        if not entries:
            rep.err(f"{item_id}: range_dispatch の entries が空")
        th = [e.get("threshold") for e in entries]
        if th != sorted(th):
            rep.err(f"{item_id}: range_dispatch の threshold が昇順でない {th}")
        for e in entries:
            _check_item_def(item_id, e.get("model", {}), rep, is_icon)
        if "fallback" not in mdl:
            rep.err(f"{item_id}: range_dispatch の fallback が無い")
        else:
            _check_item_def(item_id, mdl["fallback"], rep, is_icon)
    elif t == "minecraft:select":
        cases = mdl.get("cases", [])
        if not cases or (len(cases) < 2 and "fallback" not in mdl):
            rep.err(f"{item_id}: select の分岐が足りない")
        for case in cases:
            _check_item_def(item_id, case.get("model", {}), rep, is_icon)
        if "fallback" in mdl:
            _check_item_def(item_id, mdl["fallback"], rep, is_icon)
    elif t == "minecraft:model":
        ref = mdl.get("model", "")
        if not _model_exists(ref):
            rep.err(f"{item_id}: 参照モデルが無い {ref}")
    elif t == "minecraft:composite":
        if not mdl.get("models"):
            rep.err(f"{item_id}: composite の models が空")
        for m in mdl.get("models", []):
            _check_item_def(item_id, m, rep, is_icon)
    elif t == "minecraft:special":
        base = mdl.get("base", "")
        if not _model_exists(base):
            rep.err(f"{item_id}: special の基底モデルが無い {base}")
        if mdl.get("model", {}).get("type") == "geckolib:geckolib":
            _check_geckolib(item_id, rep)
    elif not is_icon:
        rep.warn(f"{item_id}: 未検証のアイテム定義 type={t}")


def _check_geckolib(item_id: str, rep: Report) -> None:
    """GeckoLib の骨格モデル・アニメ・テクスチャ (+ 発光マスク) が揃っているか。"""
    geo_dir = os.path.join(ASSETS, "geckolib")
    gm = load_json(os.path.join(geo_dir, "models", "item", f"{item_id}.geo.json"), rep)
    an = load_json(os.path.join(geo_dir, "animations", "item", f"{item_id}.animation.json"), rep)
    if gm is None or an is None:
        rep.err(f"{item_id}: GeckoLib のモデルかアニメが無い")
        return
    geom = gm.get("minecraft:geometry", [{}])[0]
    desc = geom.get("description", {})
    tw, th = desc.get("texture_width", 0), desc.get("texture_height", 0)
    bones = {b.get("name") for b in geom.get("bones", [])}
    for b in geom.get("bones", []):
        if b.get("parent") and b["parent"] not in bones:
            rep.err(f"{item_id}: 骨 {b['name']} の親 {b['parent']} が無い")
        for c in b.get("cubes", []):
            for face, uv in c.get("uv", {}).items():
                (u, v), (w, h) = uv["uv"], uv["uv_size"]
                if u < 0 or v < 0 or u + w > tw or v + h > th:
                    rep.err(f"{item_id}: 骨 {b['name']} の {face} 面の UV がテクスチャ外")
    for name, anim in an.get("animations", {}).items():
        for bone in anim.get("bones", {}):
            if bone not in bones:
                rep.err(f"{item_id}: アニメ {name} が存在しない骨 {bone} を動かす")
    tex = [f for f in os.listdir(os.path.join(TEX_ITEM, "geo")) if f.startswith(item_id + "_")]
    plain = [f for f in tex if not f.endswith("_glowmask.png")]
    if not plain:
        rep.err(f"{item_id}: GeckoLib のテクスチャが無い")
    for f in plain:
        if f.replace(".png", "_glowmask.png") not in tex:
            rep.err(f"{item_id}: 発光マスクが無い {f}")
        with Image.open(os.path.join(TEX_ITEM, "geo", f)) as im:
            if im.size != (tw, th):
                rep.err(f"{item_id}: {f} の大きさ {im.size} が geo の宣言 {(tw, th)} と違う")


def _check_element(label: str, el: dict, rep: Report) -> None:
    frm, to = el.get("from"), el.get("to")
    if not frm or not to:
        rep.err(f"{label}: from/to が無い")
        return
    if any(v < -16 or v > 32 for v in list(frm) + list(to)):
        rep.err(f"{label}: 座標が -16..32 の範囲外 {frm}..{to}")
    if any(frm[i] > to[i] for i in range(3)):
        rep.err(f"{label}: from > to {frm}..{to}")
    faces = el.get("faces", {})
    if not faces:
        rep.err(f"{label}: faces が空")
    for face, fd in faces.items():
        if face not in FACES:
            rep.err(f"{label}: 未知の面 {face}")
        uv = fd.get("uv")
        if not uv or len(uv) != 4:
            rep.err(f"{label}.{face}: uv が不正 {uv}")
            continue
        if min(uv) < 0 or max(uv) > 16:
            rep.err(f"{label}.{face}: uv が 0..16 外 {uv}")
        if not str(fd.get("texture", "")).startswith("#"):
            rep.err(f"{label}.{face}: テクスチャ参照が不正 {fd.get('texture')}")
    rot = el.get("rotation")
    if rot:
        if rot.get("axis") not in ("x", "y", "z"):
            rep.err(f"{label}: rotation.axis が不正")
        if not isinstance(rot.get("angle"), (int, float)):
            rep.err(f"{label}: rotation.angle が不正")


# ================================================================ textures

def check_textures(rep: Report) -> None:
    """どのモデルからも参照されない item テクスチャ (生成の取り残し) と画像サイズ。"""
    refs = set()
    for f in os.listdir(MODEL_ITEM):
        if f.endswith(".json"):
            with io.open(os.path.join(MODEL_ITEM, f), encoding="utf-8") as fh:
                for ref in json.load(fh).get("textures", {}).values():
                    refs.add(os.path.normcase(os.path.abspath(tex_file(ref))))
    orphans = []
    for f in sorted(os.listdir(TEX_ITEM)):
        p = os.path.join(TEX_ITEM, f)
        if not f.endswith(".png"):
            continue
        if os.path.normcase(os.path.abspath(p)) not in refs:
            orphans.append(f[:-4])
        with Image.open(p) as im:
            w, h = im.size
        if w != h and not os.path.exists(p + ".mcmeta"):
            rep.warn(f"{rel(p)}: 正方形でない ({w}x{h}) のに .mcmeta が無い")
    if orphans:
        rep.warn(f"どのモデルからも使われていない item テクスチャ {len(orphans)} 件: "
                 + " ".join(orphans[:12]) + (" ..." if len(orphans) > 12 else ""))


# ================================================================ equipment

def check_equipment(rep: Report) -> None:
    """equipment/*.json のレイヤー -> textures/entity/equipment/<layer>/<name>.png。"""
    eq_dir = os.path.join(ASSETS, "equipment")
    if not os.path.isdir(eq_dir):
        return
    used = set()
    for f in sorted(os.listdir(eq_dir)):
        data = load_json(os.path.join(eq_dir, f), rep) or {}
        for layer, entries in data.get("layers", {}).items():
            for e in entries:
                ref = e.get("texture", "")
                ns, _, name = ref.partition(":")
                if ns != MOD_ID:
                    continue
                p = os.path.join(TEX_ENTITY, "equipment", layer, name + ".png")
                used.add(os.path.normcase(p))
                if not os.path.exists(p):
                    rep.err(f"equipment/{f}: レイヤー {layer} のテクスチャが無い "
                            f"({rel(p)})")
    # 防具アイテムが参照する asset id
    src = java_source("ModItems.java")
    for asset in set(re.findall(r'armor\("[^"]+", Rarity\.\w+, EquipmentSlot\.\w+, "(\w+)"\)', src)):
        if not os.path.exists(os.path.join(eq_dir, asset + ".json")):
            rep.err(f"ModItems: 装備アセット equipment/{asset}.json が無い")
    rep.note(f"layers {len(used)}")


# ================================================================ recipes

def _ingredient_errors(ing, ids: set[str]) -> list[str]:
    out = []
    if isinstance(ing, str):
        if ing.startswith("#"):
            return out
        if ing.startswith(NS) and ing[len(NS):] not in ids:
            out.append(f"未登録のアイテム {ing}")
    elif isinstance(ing, list):
        for i in ing:
            out += _ingredient_errors(i, ids)
    elif isinstance(ing, dict):
        if "neoforge:ingredient_type" not in ing and "type" not in ing:
            out.append(f"旧書式の材料 {json.dumps(ing, ensure_ascii=False)} "
                       "(1.21.2+ は \"namespace:id\" 文字列で書く)")
    else:
        out.append(f"材料の型が不正 {ing!r}")
    return out


def check_recipes(rep: Report) -> None:
    rec_dir = os.path.join(DATA, MOD_ID, "recipe")
    ids = item_ids()
    n = 0
    results = {}
    for f in sorted(os.listdir(rec_dir)):
        if not f.endswith(".json"):
            continue  # files チェックが報告する
        n += 1
        d = load_json(os.path.join(rec_dir, f), rep)
        if d is None:
            continue
        name = f[:-5]
        ings = d.get("ingredients", [])
        if "ingredients" in d and not (1 <= len(ings) <= 9):
            rep.err(f"recipe/{f}: 材料数 {len(ings)} (1..9)")
        for ing in ings:
            for e in _ingredient_errors(ing, ids):
                rep.err(f"recipe/{f}: {e}")
        for k, ing in d.get("key", {}).items():
            for e in _ingredient_errors(ing, ids):
                rep.err(f"recipe/{f}: key '{k}': {e}")
        pattern = d.get("pattern")
        if pattern:
            used = {c for row in pattern for c in row if c != " "}
            missing = used - set(d.get("key", {}))
            if missing:
                rep.err(f"recipe/{f}: pattern の記号 {sorted(missing)} が key に無い")
        res = d.get("result", {})
        rid = res.get("id") if isinstance(res, dict) else None
        if not rid:
            rep.err(f"recipe/{f}: result.id が無い (1.21+ は 'item' でなく 'id')")
        elif rid.startswith(NS) and rid[len(NS):] not in ids:
            rep.err(f"recipe/{f}: 結果が未登録のアイテム {rid}")
        if d.get("type") == "minecraft:crafting_shapeless" and rid:
            sig = (tuple(sorted(json.dumps(i, sort_keys=True) for i in ings)))
            if sig in results:
                rep.warn(f"recipe/{f}: {results[sig]} と材料が同一 (どちらかしか作れない)")
            results[sig] = name
    rep.note(f"recipes {n}")


# ================================================================ advancements / loot

def check_advancements(rep: Report) -> None:
    adv_dir = os.path.join(DATA, MOD_ID, "advancement")
    ids = item_ids()
    names = set()
    paths = []
    for p in walk(adv_dir):
        if p.endswith(".json"):
            r = os.path.relpath(p, adv_dir)[:-5].replace(os.sep, "/")
            names.add(f"{NS}{r}")
            paths.append((r, p))
    for r, p in paths:
        d = load_json(p, rep) or {}
        parent = d.get("parent")
        if parent and parent.startswith(NS) and parent not in names:
            rep.err(f"advancement/{r}: 親 {parent} が無い")
        icon = d.get("display", {}).get("icon", {}).get("id", "")
        if icon.startswith(NS) and icon[len(NS):] not in ids:
            rep.err(f"advancement/{r}: アイコンが未登録のアイテム {icon}")
    rep.note(f"advancements {len(paths)}")


def check_loot_modifiers(rep: Report) -> None:
    order = os.path.join(DATA, "neoforge", "loot_modifiers", "global_loot_modifiers.json")
    if not os.path.exists(order):
        return
    d = load_json(order, rep) or {}
    for e in d.get("entries", []):
        ns, _, name = e.partition(":")
        p = os.path.join(DATA, ns, "loot_modifiers", name + ".json")
        if not os.path.exists(p):
            rep.err(f"global_loot_modifiers: {e} の定義ファイルが無い")
    lm_dir = os.path.join(DATA, MOD_ID, "loot_modifiers")
    listed = set(d.get("entries", []))
    for f in os.listdir(lm_dir) if os.path.isdir(lm_dir) else []:
        if f.endswith(".json") and f"{NS}{f[:-5]}" not in listed:
            rep.warn(f"loot_modifiers/{f}: global_loot_modifiers.json に登録されていない (無効)")


# ================================================================ lang

def check_lang(rep: Report) -> None:
    ids = item_ids()
    tables = {}
    for lang in LANGS:
        p = os.path.join(LANG, f"{lang}.json")
        if os.path.exists(p):
            tables[lang] = load_json(p, rep) or {}
    for lang, t in tables.items():
        missing = sorted(i for i in ids if f"item.{MOD_ID}.{i}" not in t)
        if missing:
            rep.err(f"{lang}: 翻訳の無いアイテム {len(missing)} 件: " + " ".join(missing[:15]))
        stale = sorted(k for k in t if k.startswith(f"item.{MOD_ID}.") and k.count(".") == 2
                       and k.split(".", 2)[2] not in ids)
        if stale:
            rep.warn(f"{lang}: 存在しないアイテムの翻訳 {len(stale)} 件 (例: {', '.join(stale[:4])})")
    if len(tables) == 2:
        a, b = (set(tables[l]) for l in LANGS)
        if a - b:
            rep.warn(f"en_us に無いキー {len(a - b)} 件: " + " ".join(sorted(a - b)[:8]))
        if b - a:
            rep.warn(f"ja_jp に無いキー {len(b - a)} 件: " + " ".join(sorted(b - a)[:8]))
    # Java が参照する翻訳キー
    keys_in_java = set()
    for f in os.listdir(JAVA_DIR):
        if f.endswith(".java"):
            keys_in_java |= set(re.findall(r'"((?:key|itemGroup|entity|gui|message)\.' + MOD_ID
                                           + r'[\w.]*)"', java_source(f)))
    for lang, t in tables.items():
        miss = sorted(k for k in keys_in_java if k not in t)
        if miss:
            rep.err(f"{lang}: Java が使う翻訳キーが無い: " + " ".join(miss[:10]))


# ================================================================ java: entity models

def _num(s: str) -> float:
    return float(s.strip().rstrip("f"))


def check_entity_models(rep: Report) -> None:
    """AbyssModels.java: getChild の対象が存在するか / UV が 64x64 に収まるか。"""
    try:
        src = java_source("AbyssModels.java")
    except FileNotFoundError:
        return
    classes = {}
    for m in re.finditer(
            r"public static class (\w+) extends \w+ \{(.*?)public static LayerDefinition "
            r"createBodyLayer\(\) \{(.*?)\n        \}", src, re.S):
        classes[m.group(1)] = (m.group(2), m.group(3))
    numpat = r"-?\d+(?:\.\d+)?f?"
    u_base = {"BU": 0, "LU": 48, "DU": 0, "WU": 16, "AU": 32, "FU": 0}
    v_base = {"BV": 0, "LV": 0, "DV": 32, "WV": 32, "AV": 32, "FV": 48}
    for cls, (ctor, body) in sorted(classes.items()):
        children = {"root": set()}
        varmap = {"root": "root"}
        for bm in re.finditer(
                r"(?:PartDefinition (\w+) = )?box(?:Rot)?\(\s*(\w+),\s*\"(\w+)\",\s*"
                r"([A-Z]+)(?:\s*\+\s*(\d+))?,\s*([A-Z]+)(?:\s*\+\s*(\d+))?,\s*"
                r"((?:" + numpat + r"\s*,\s*){8}" + numpat + r")", body):
            var, parent, name = bm.group(1), bm.group(2), bm.group(3)
            floats = [_num(x) for x in re.findall(numpat, bm.group(8))]
            if var:
                varmap[var] = name
            children.setdefault(varmap.get(parent, parent), set()).add(name)
            if len(floats) >= 6 and bm.group(4) in u_base and bm.group(6) in v_base:
                dx, dy, dz = floats[3], floats[4], floats[5]
                uu = u_base[bm.group(4)] + int(bm.group(5) or 0)
                vv = v_base[bm.group(6)] + int(bm.group(7) or 0)
                if uu + max(dx, dz) > 64 + 1e-6:
                    rep.err(f"{cls}.{name}: U がはみ出す (u={uu}, 幅 {max(dx, dz)})")
                if vv + max(dy, dz) > 64 + 1e-6:
                    rep.err(f"{cls}.{name}: V がはみ出す (v={vv}, 高さ {max(dy, dz)})")
                if dx <= 0 or dy <= 0 or dz <= 0:
                    rep.err(f"{cls}.{name}: 非正の寸法 {dx}x{dy}x{dz}")
        for rm in re.finditer(r'(\w+)\.addOrReplaceChild\("(\w+)"', body):
            parent = rm.group(1)
            if parent == "root" or parent in varmap:
                children.setdefault(varmap.get(parent, parent), set()).add(rm.group(2))
        for gm in re.finditer(r'(\w+(?:\.\w+)?)\.getChild\("(\w+)"\)', ctor):
            var, want = gm.group(1).replace("this.", ""), gm.group(2)
            base = varmap.get(var, var)
            if want in children.get(base, set()) or want in children.get("root", set()):
                continue
            if f'"{want}"' not in body:
                rep.err(f"{cls}: getChild(\"{want}\") の部品が定義に無い (実機の初回描画でクラッシュ)")
    rep.note(f"entity models {len(classes)}")


def check_entity_textures(rep: Report) -> None:
    """ClientGui の bind(..., "tex", ...) が参照する textures/entity/<tex>.png と、
    ModEntities で登録したエンティティにレンダラがあるか。"""
    try:
        gui = java_source("ClientGui.java")
        ents = java_source("ModEntities.java")
    except FileNotFoundError:
        return
    binds = re.findall(r'bind\(event, ModEntities\.(\w+), [^"]*"([a-z0-9_]+)"', gui)
    have = {f[:-4] for f in os.listdir(TEX_ENTITY) if f.endswith(".png")}
    for const, tex in binds:
        if tex not in have:
            rep.err(f"ModEntities.{const}: textures/entity/{tex}.png が無い")
    bound = {c for c, _t in binds}
    for const in re.findall(r'public static final Supplier<EntityType<\w+>> (\w+) = reg\(', ents):
        if const not in bound:
            rep.warn(f"ModEntities.{const}: ClientGui でレンダラが登録されていない")
    unused = sorted(have - {t for _c, t in binds})
    if unused:
        rep.warn(f"どのレンダラも使わない MOB テクスチャ: {' '.join(unused)}")
    rep.note(f"renderers {len(binds)}")


# ================================================================ java: weapon arts

def check_weapon_arts(rep: Report) -> None:
    """WeaponArts の奥義 / 小技スイッチが WeaponStyle を網羅しているか。"""
    try:
        wa = java_source("WeaponArts.java")
    except FileNotFoundError:
        return
    consts = [c for c, _sid, _col in read_weapon_style_consts()]

    def method_body(name: str) -> str | None:
        m = re.search(r"private static \w+ " + name + r"\(", wa)
        if not m:
            return None
        i = wa.index("{", m.end())
        depth = 0
        for j in range(i, len(wa)):
            if wa[j] == "{":
                depth += 1
            elif wa[j] == "}":
                depth -= 1
                if depth == 0:
                    return wa[i:j]
        return None

    groups = {
        "奥義 (execute)": ["executeCoreArts", "executeExtendedArts", "executeEliteArts"],
        "小技 (executeLight)": ["executeLightCore", "executeLightExtended"],
    }
    for label, names in groups.items():
        seen = set()
        for n in names:
            body = method_body(n)
            if body is None:
                rep.warn(f"WeaponArts.{n} が見つからない (メソッド名が変わった?)")
                continue
            seen |= set(re.findall(r"case ([A-Z_]+(?:\s*,\s*[A-Z_]+)*) ->", body))
        flat = {c.strip() for s in seen for c in s.split(",")}
        missing = [c for c in consts if c not in flat]
        unknown = sorted(flat - set(consts))
        if missing:
            rep.err(f"{label}: 未実装の武器種 {missing}")
        if unknown:
            rep.warn(f"{label}: WeaponStyle に無い case {unknown}")
    rep.note(f"weapon styles {len(consts)}")


# ================================================================ content

def check_content(rep: Report) -> None:
    """abyssalloot_content.json: 定義の矛盾 / アイテム定義・翻訳の欠け / 存在しない敵・素材の参照。"""
    from art import content

    doc = content.load()
    for e in content.validate(doc):
        rep.err(e)
    ids = item_ids()
    want = [e["id"] for g in ("trophies", "zone_materials", "consumables") for e in doc[g]]
    want += [f"abyss_{s['id']}_{p}" for s in doc["armor_sets"] for p in content.PIECES]
    missing = sorted(i for i in want if i not in ids)
    if missing:
        rep.err(f"アイテム定義が未生成 ({len(missing)} 件。python tools/abyss.py gen): " + " ".join(missing[:10]))
    ja, _en = content.lang_entries(doc)
    with io.open(os.path.join(LANG, "ja_jp.json"), encoding="utf-8") as f:
        table = json.load(f)
    stale = sorted(k for k, v in ja.items() if table.get(k) != v)
    if stale:
        rep.err(f"翻訳が未反映 ({len(stale)} 件。python tools/abyss.py data): " + " ".join(stale[:6]))
    mobs = set(re.findall(r'reg\("([a-z_]+)"', java_source("ModEntities.java")))
    for t in doc["trophies"]:
        if t["mob"] not in mobs:
            rep.err(f"{t['id']}: 存在しない敵 {t['mob']}")
    for m in sorted(mobs - {t["mob"] for t in doc["trophies"]}):
        rep.warn(f"敵 {m} に魔物素材が無い")
    zones = set(re.findall(r'new Zone\("([a-z_]+)"', java_source("GameData.java")))
    for m in doc["zone_materials"]:
        if m["zone"] not in zones:
            rep.err(f"{m['id']}: 存在しない区域 {m['zone']}")
    for zone, pool in doc.get("zone_existing_drops", {}).items():
        if zone not in zones:
            rep.err(f"zone_existing_drops: 存在しない区域 {zone}")
        for i in pool:
            if i not in ids:
                rep.err(f"zone_existing_drops[{zone}]: 未登録のアイテム {i}")
    for z in sorted(zones - {m["zone"] for m in doc["zone_materials"]}):
        rep.err(f"区域 {z} に素材が 1 つも無い")
    used = {i.split(":", 1)[1] for c in doc["consumables"] for i in c["recipe"] if i.startswith(NS)}
    used |= {i.split(":", 1)[1] for r in doc.get("extra_recipes", []) for i in r["ingredients"]
             if i.startswith(NS)}
    unused = sorted(e["id"] for g in ("trophies", "zone_materials") for e in doc[g] if e["id"] not in used)
    if unused:
        rep.warn(f"使い道 (レシピ) の無い素材 {len(unused)} 件: " + " ".join(unused[:8]))
    rep.note(f"追加アイテム {content.item_count(doc)}")


# ================================================================

CHECKS = {
    "files": (check_files, "拡張子・JSON 構文"),
    "item_models": (check_item_models, "アイテムモデル / 定義 / UV / テクスチャ実在"),
    "textures": (check_textures, "未使用テクスチャ・画像サイズ"),
    "equipment": (check_equipment, "装備レイヤーのテクスチャ"),
    "recipes": (check_recipes, "レシピの書式・参照・重複"),
    "advancements": (check_advancements, "進捗の親・アイコン"),
    "loot": (check_loot_modifiers, "グローバルルートモディファイア"),
    "lang": (check_lang, "翻訳 (ja_jp / en_us)"),
    "content": (check_content, "追加コンテンツ (abyssalloot_content.json)"),
    "entity_models": (check_entity_models, "AbyssModels の部品名・UV"),
    "entity_textures": (check_entity_textures, "MOB テクスチャの実在"),
    "weapon_arts": (check_weapon_arts, "奥義・小技の網羅"),
}


def run(names: list[str] | None = None, strict: bool = False, quiet: bool = False) -> int:
    names = names or list(CHECKS)
    total_e = total_w = 0
    for name in names:
        fn, desc = CHECKS[name]
        rep = Report()
        try:
            fn(rep)
        except Exception as exc:  # noqa: BLE001 - チェック自体の故障も報告
            rep.err(f"チェックが例外で停止: {type(exc).__name__}: {exc}")
        status = "NG" if rep.errors else ("注意" if rep.warnings else "OK")
        extra = f" ({', '.join(rep.notes)})" if rep.notes else ""
        print(f"[{status:>2}] {name:<16} {desc}{extra}")
        for e in rep.errors[:40]:
            print(f"      ✗ {e}")
        if len(rep.errors) > 40:
            print(f"      ... ほか {len(rep.errors) - 40} 件")
        if not quiet:
            for w in rep.warnings[:15]:
                print(f"      △ {w}")
        total_e += len(rep.errors)
        total_w += len(rep.warnings)
    print(f"\nエラー {total_e} / 警告 {total_w}")
    return 1 if total_e or (strict and total_w) else 0


def main(argv: list[str] | None = None) -> int:
    setup_console()
    ap = argparse.ArgumentParser(description="リソース / データ / Java 定義の静的検証")
    ap.add_argument("checks", nargs="*", help="実行するチェック名 (省略で全部)")
    ap.add_argument("--list", action="store_true", help="チェック一覧")
    ap.add_argument("--strict", action="store_true", help="警告もエラー扱い")
    ap.add_argument("-q", "--quiet", action="store_true", help="警告を表示しない")
    args = ap.parse_args(argv)
    if args.list:
        for k, (_fn, desc) in CHECKS.items():
            print(f"{k:<16} {desc}")
        return 0
    bad = [c for c in args.checks if c not in CHECKS]
    if bad:
        print("未知のチェック:", " ".join(bad), "(--list で一覧)")
        return 2
    return run(args.checks, args.strict, args.quiet)


if __name__ == "__main__":
    sys.exit(main())
