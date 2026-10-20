"""アイテムモデルの display 変換 (所持・GUI・地面...) と生成ヘルパー。"""
from __future__ import annotations

import os

from . import boxmodel, shapes
from .boxmodel import Model

# --- GUI: 45 度历程に傾けた「アイコン」見栄えにする ---
GUI_TILT = -30.0

# --- 一人称: 右手で構え、刃先が左上を向く ---
# 平行移動は「握り」の位置から自動計算する (全アイテムで手の位置が揃う)。
FP_ROT = (10.0, -10.0, 32.0)
FP_SCALE = 0.7
FP_OFFSET = (0.0, 0.0, 0.0)   # 全アイテム共通の微調整 (1/16 ブロック)

# --- 三人称: 腰高でやや前方に構える ---
TP_ROT = (0.0, -18.0, -22.0)
TP_SCALE = 0.8
TP_OFFSET = (0.0, 0.0, 0.0)

# 縦持ちする道具 (GUI では upright に、所持時は控えめの傾きで持つ)。
VERTICAL_POSE = {
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
    "fp_rot": (8.0, -8.0, 20.0),
    "fp_scale": 0.7,
}

# GUI だけ upright (所持時は通常の構え) にする品目
GUI_UPRIGHT_ONLY = {
    "abyss_scythe", "abyss_soul_scythe", "abyss_war_horn", "abyss_depth_gun",
}


def _anchor(rot, scale: float, grip, offset=(0.0, 0.0, 0.0)) -> list[float]:
    """握りが手の原点 (モデル空間の原点) に来る平行移動量を返す (1/16 ブロック)。

    MC の display 変換は  M = T(t/16) · R · S · T(-0.5)  なので、
    握り g (0..16) について  t = -scale · R · (g - 8)  となる。
    """
    if grip is None:
        return [round(v, 3) for v in offset]
    m = boxmodel._mat_rotate(rot[0], rot[1], rot[2])
    d = boxmodel._apply(m, (grip[0] - 8.0, grip[1] - 8.0, grip[2] - 8.0))
    return [round(offset[i] - scale * d[i], 3) for i in range(3)]


def display(kind: str = "weapon", pose: dict | None = None, held_vertical: bool = True,
            grip: tuple[float, float, float] | None = None) -> dict:
    """display ブロックを組み立てる。pose で所持の向きを上書きできる。

    grip を渡すと、その点が手の位置に来るように一人称・三人称の平行移動を計算する。
    """
    vertical = bool(pose)
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
        # 防具は前側 (-Z) に意匠があるため Y 軸 180° + 少し振って正面を見せる
        return {
            "gui": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "ground": {"rotation": [0, 180, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        }

    # 武器
    fp_rot, fp_scale = FP_ROT, FP_SCALE
    gui = {"rotation": [0, 0, GUI_TILT], "translation": [0, 0, 0], "scale": [1, 1, 1]}
    if vertical and pose:
        gui = pose.get("gui", gui)
        if held_vertical:
            fp_rot = pose.get("fp_rot", fp_rot)
            fp_scale = pose.get("fp_scale", fp_scale)
    fp_rot_l = (fp_rot[0], -fp_rot[1], -fp_rot[2])
    tp_rot_l = (TP_ROT[0], -TP_ROT[1], -TP_ROT[2])
    return {
        "gui": gui,
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.55, 0.55, 0.55]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {
            "rotation": list(TP_ROT),
            "translation": _anchor(TP_ROT, TP_SCALE, grip, TP_OFFSET),
            "scale": [TP_SCALE] * 3},
        "thirdperson_lefthand": {
            "rotation": list(tp_rot_l),
            "translation": _anchor(tp_rot_l, TP_SCALE, grip, TP_OFFSET),
            "scale": [TP_SCALE] * 3},
        "firstperson_righthand": {
            "rotation": list(fp_rot),
            "translation": _anchor(fp_rot, fp_scale, grip, FP_OFFSET),
            "scale": [fp_scale] * 3},
        "firstperson_lefthand": {
            "rotation": list(fp_rot_l),
            "translation": _anchor(fp_rot_l, fp_scale, grip, FP_OFFSET),
            "scale": [fp_scale] * 3},
    }


def build(model_id: str, shape_fn, palette: dict, kind: str = "weapon",
          texture: str | None = None) -> tuple[Model, dict]:
    """モデルを作り、テクスチャと JSON を返す。"""
    m = Model(model_id)
    shape_fn(m, palette)
    m.build_texture()
    tex = texture or f"abyssalloot:item/{model_id}"
    data = m.model_json(tex, display(kind, grip=m.grip_point()))
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
        data = m.model_json(f"abyssalloot:item/{tex_name}", display(kind, grip=m.grip_point()))
        with open(os.path.join(models_dir, f"{item_id}.json"), "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        itemdef = {"model": {"type": "minecraft:model", "model": f"abyssalloot:item/{item_id}"}}
        with open(os.path.join(items_dir, f"{item_id}.json"), "w", encoding="utf-8") as f:
            json.dump(itemdef, f, indent=2, ensure_ascii=False)
            f.write("\n")
