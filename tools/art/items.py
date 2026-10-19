"""アイテムモデルの display 変換 (所持・GUI・地面...) と生成ヘルパー。"""
from __future__ import annotations

import os

from . import boxmodel, shapes
from .boxmodel import Model

# --- GUI: 45 度历程に傾けた「アイコン」見栄えにする ---
GUI_TILT = -30.0

# --- 一人称: 右手で構え、刃先が左上を向く ---
# 柄が手の位置 (0.62, -0.55, -0.70) に来るように平行移動し、刀身は画面左上へ向ける。
FP_ROT = (10.0, -10.0, 32.0)
FP_TRANS = (-0.5, 1.9, 0.5)
FP_SCALE = 0.7

# --- 三人称: 腰高でやや前方に構える ---
TP_ROT = (0.0, -18.0, -22.0)
TP_TRANS = (0.0, -5.5, 0.5)
TP_SCALE = 0.8


def display(kind: str = "weapon") -> dict:
    """display ブロックを組み立てる。"""
    if kind == "flat":
        # 2D アイコン風 (GUI では正面・斜め 45 度)
        return {
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        }

    if kind == "armor":
        return {
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        }

    # 武器
    return {
        "gui": {"rotation": [0, 0, GUI_TILT], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.55, 0.55, 0.55]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {
            "rotation": list(TP_ROT), "translation": list(TP_TRANS), "scale": [TP_SCALE] * 3},
        "thirdperson_lefthand": {
            "rotation": [TP_ROT[0], -TP_ROT[1], -TP_ROT[2]],
            "translation": list(TP_TRANS), "scale": [TP_SCALE] * 3},
        "firstperson_righthand": {
            "rotation": list(FP_ROT), "translation": list(FP_TRANS), "scale": [FP_SCALE] * 3},
        "firstperson_lefthand": {
            "rotation": [FP_ROT[0], -FP_ROT[1], -FP_ROT[2]],
            "translation": list(FP_TRANS), "scale": [FP_SCALE] * 3},
    }


def build(model_id: str, shape_fn, palette: dict, kind: str = "weapon",
          texture: str | None = None) -> tuple[Model, dict]:
    """モデルを作り、テクスチャと JSON を返す。"""
    m = Model(model_id)
    shape_fn(m, palette)
    m.build_texture()
    tex = texture or f"abyssalloot:item/{model_id}"
    data = m.model_json(tex, display(kind))
    return m, data


def write_all(models_dir: str, items_dir: str, texture_dir: str, entries: list) -> None:
    """entries: [(item_id, shape_fn, palette, kind, texture_name)] を書き出す。"""
    os.makedirs(models_dir, exist_ok=True)
    os.makedirs(items_dir, exist_ok=True)
    os.makedirs(texture_dir, exist_ok=True)
    import json
    for item_id, shape_fn, palette, kind, tex_name in entries:
        m = Model(item_id)
        shape_fn(m, palette)
        m.build_texture()
        m.texture.save(os.path.join(texture_dir, f"{tex_name}.png"))
        data = m.model_json(f"abyssalloot:item/{tex_name}", display(kind))
        with open(os.path.join(models_dir, f"{item_id}.json"), "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        itemdef = {"model": {"type": "minecraft:model", "model": f"abyssalloot:item/{item_id}"}}
        with open(os.path.join(items_dir, f"{item_id}.json"), "w", encoding="utf-8") as f:
            json.dump(itemdef, f, indent=2, ensure_ascii=False)
            f.write("\n")
