#!/usr/bin/env python3
"""MC を起動せずに見た目を確認するプレビュー (出力: build/model_preview/)。

旧 preview_*.py / zoom.py を統合したもの。

    python tools/preview.py gui [--only PAT]        全アイテムの GUI 表示 (3D は display.gui)
    python tools/preview.py icons                   インベントリ用 2D ドット絵 (*_icon)
    python tools/preview.py compare [id ...]        2D ドット絵と 3D モデルを並べる
    python tools/preview.py firstperson [id ...]    一人称視点
    python tools/preview.py hold [id ...]           一人称 + 手の位置に十字 (握りの確認)
    python tools/preview.py zoom id ... [--size N]  拡大表示
    python tools/preview.py textures [id ...]       テクスチャ画像そのものを拡大
    python tools/preview.py sheet weapons|armor|materials
    python tools/preview.py entities                MOB テクスチャ
    python tools/preview.py equipment               防具の着用テクスチャ (展開図)
    python tools/preview.py mockup                  着用テクスチャを人型に貼る
    python tools/preview.py diff [--ref HEAD]       git の基準から変わったテクスチャの前後比較

共通: --only PAT (id の部分一致 / glob) --cols N --size N --out PATH
"""
from __future__ import annotations

import argparse
import io
import math
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw  # noqa: E402

from art import boxmodel  # noqa: E402
from art.boxmodel import Box, Model  # noqa: E402
from art.project import (BG, FLAT, ROOT, TEX_ENTITY, TEX_EQUIPMENT, TEX_EQUIPMENT_LEGS,  # noqa: E402
                         TEX_ITEM, contact_sheet, draw_label, load_item_model, matcher, model_ids,
                         render_item, save_preview, setup_console)

HAND_POS = (0.56, -0.42, -0.72)  # MC の ItemInHandRenderer の手の位置 (少し上げて見やすく)
FOV = 70.0


def _ids(args, default: list[str]) -> list[str]:
    ids = list(args.ids) if args.ids else default
    want = matcher(args.only)
    return [i for i in ids if want(i)]


def _weapon_ids() -> list[str]:
    return [i for i in model_ids(include_icons=False)
            if os.path.exists(os.path.join(TEX_ITEM, f"{i}_icon.png"))]


def _png_sheet(paths: list[str], cell: tuple[int, int], cols: int, labels: bool = True) -> Image.Image:
    cells = []
    for p in paths:
        img = Image.new("RGBA", cell, BG)
        im = Image.open(p).convert("RGBA").resize(cell, Image.NEAREST)
        img.alpha_composite(im)
        if labels:
            draw_label(img, os.path.splitext(os.path.basename(p))[0])
        cells.append(img)
    return contact_sheet(cells, cols)


# ---------------------------------------------------------------- 各コマンド

def cmd_gui(args):
    ids = _ids(args, model_ids())
    cells = [render_item(i, args.size or 120) for i in ids]
    return save_preview(contact_sheet(cells, args.cols or 10), "contact_gui.png", args.out)


def cmd_icons(args):
    ids = _ids(args, [i for i in model_ids() if i.endswith("_icon")])
    paths = [os.path.join(TEX_ITEM, f"{i}.png") for i in ids]
    s = args.size or 96
    return save_preview(_png_sheet(paths, (s, s), args.cols or 12), "contact_icons.png", args.out)


def cmd_textures(args):
    ids = _ids(args, sorted(f[:-4] for f in os.listdir(TEX_ITEM) if f.endswith(".png")))
    paths = [os.path.join(TEX_ITEM, f"{i}.png") for i in ids]
    s = args.size or 192
    return save_preview(_png_sheet(paths, (s, s), args.cols or 6), "textures.png", args.out)


def cmd_compare(args):
    ids = _ids(args, _weapon_ids())
    s = args.size or 120
    cells = []
    for i in ids:
        pair = Image.new("RGBA", (s * 2, s), BG)
        icon = os.path.join(TEX_ITEM, f"{i}_icon.png")
        if os.path.exists(icon):
            pair.alpha_composite(Image.open(icon).convert("RGBA").resize((s, s), Image.NEAREST))
        pair.alpha_composite(render_item(i, s, label=False), (s, 0))
        draw_label(pair, i)
        cells.append(pair)
    return save_preview(contact_sheet(cells, args.cols or 5), "compare_2d_3d.png", args.out)


def _first_person(item_id: str, size: tuple[int, int], cross: bool) -> Image.Image:
    model, data = load_item_model(item_id)
    tr = data.get("display", {}).get("firstperson_righthand") or FLAT
    w, h = size
    if not model.boxes:  # 2D アイコンは平面として描けないので拡大表示
        img = Image.new("RGBA", size, BG)
        if model.texture is not None:
            s = min(w, h)
            img.alpha_composite(model.texture.resize((s, s), Image.NEAREST), ((w - s) // 2, 0))
    else:
        img = boxmodel.render_first_person(model, tr, size, fov=FOV,
                                           **({"item_pos": HAND_POS} if cross else {}))
    if cross:
        f = 1.0 / math.tan(math.radians(FOV / 2))
        sx = w / 2 + (HAND_POS[0] * f / (-HAND_POS[2])) / (w / h) * (w / 2)
        sy = h / 2 - (HAND_POS[1] * f / (-HAND_POS[2])) * (h / 2)
        d = ImageDraw.Draw(img)
        d.line([(sx - 10, sy), (sx + 10, sy)], fill=(255, 64, 64, 255))
        d.line([(sx, sy - 10), (sx, sy + 10)], fill=(255, 64, 64, 255))
    draw_label(img, item_id)
    return img


def cmd_firstperson(args):
    ids = _ids(args, model_ids(include_icons=False))
    s = args.size or 200
    cells = [_first_person(i, (s, s), cross=False) for i in ids]
    return save_preview(contact_sheet(cells, args.cols or 8), "contact_firstperson.png", args.out)


HOLD_DEFAULT = [
    "abyss_sword", "abyss_greatsword", "abyss_katana", "abyss_dagger",
    "abyss_axe", "abyss_spear", "abyss_staff", "abyss_bow",
    "abyss_scythe", "abyss_tome", "abyss_gauntlet", "abyss_chakram",
    "abyss_venom_whip", "abyss_hammer", "abyss_shears", "abyss_war_horn",
    "abyss_howling_claw", "abyss_fishing_rod",
]


def cmd_hold(args):
    ids = _ids(args, HOLD_DEFAULT)
    w = args.size or 480
    cells = [_first_person(i, (w, w * 9 // 16), cross=True) for i in ids]
    return save_preview(contact_sheet(cells, args.cols or 3), "hold_preview.png", args.out)


def cmd_zoom(args):
    if not args.ids and not args.only:
        raise SystemExit("zoom: アイテム id を指定してください (例: preview.py zoom abyss_sword)")
    ids = _ids(args, model_ids())
    cells = [render_item(i, args.size or 320) for i in ids]
    return save_preview(contact_sheet(cells, args.cols or 3), "zoom.png", args.out)


def cmd_sheet(args):
    import generate_textures as g

    table = {"weapons": g.WEAPON_MODELS, "armor": g.ARMOR_MODELS, "materials": g.MATERIAL_MODELS}
    cat = (args.ids or ["weapons"])[0]
    if cat not in table:
        raise SystemExit(f"sheet: weapons / armor / materials のいずれか (指定: {cat})")
    want = matcher(args.only)
    ids = [i for i in table[cat] if want(i)]
    cells = [render_item(i, args.size or 200) for i in ids]
    return save_preview(contact_sheet(cells, args.cols or 6), f"sheet_{cat}.png", args.out)


def cmd_entities(args):
    ids = _ids(args, sorted(f[:-4] for f in os.listdir(TEX_ENTITY) if f.endswith(".png")))
    paths = [os.path.join(TEX_ENTITY, f"{i}.png") for i in ids]
    s = args.size or 160
    return save_preview(_png_sheet(paths, (s, s), args.cols or 7), "contact_entities.png", args.out)


def _equipment_paths(want) -> list[str]:
    out = []
    for d in (TEX_EQUIPMENT, TEX_EQUIPMENT_LEGS):
        if os.path.isdir(d):
            out += [os.path.join(d, f) for f in sorted(os.listdir(d))
                    if f.endswith(".png") and want(f[:-4])]
    return sorted(out, key=os.path.basename)


def cmd_equipment(args):
    paths = _equipment_paths(matcher(args.ids or args.only))
    s = args.size or 256
    return save_preview(_png_sheet(paths, (s, s // 2), args.cols or 4), "contact_equipment.png", args.out)


def _net(u, v, w, h, d):
    """MC の箱ネット UV (texel 単位)。"""
    return {
        "down": (u + d, v, u + d + w, v + d),
        "up": (u + d + w, v, u + d + 2 * w, v + d),
        "east": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "west": (u + d + w, v + d, u + d + w + d, v + d + h),
        "south": (u + d + w + d, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def _humanoid(texture: Image.Image, leggings: bool) -> Model:
    """装備の上半身 (頭・胴・腕) または脚 (MC の HumanoidModel.createMesh と同じ配置)。"""
    m = Model("humanoid")
    if not leggings:
        parts = [("head", (0, 0), (-4, -8, -4), (8, 8, 8)),
                 ("body", (16, 16), (-4, 0, -2), (8, 12, 4)),
                 ("arm_r", (40, 16), (-8, 0, -2), (4, 12, 4)),
                 ("arm_l", (48, 16), (2, 0, -2), (4, 12, 4))]
    else:
        parts = [("leg_r", (0, 16), (-4, 12, -2), (4, 12, 4)),
                 ("leg_l", (16, 16), (0, 12, -2), (4, 12, 4))]
    for name, (u, v), org, (w, h, d) in parts:
        m.boxes.append(Box(name=name, frm=org, to=(org[0] + w, org[1] + h, org[2] + d), mat=None))
        for face, rect in _net(u, v, w, h, d).items():
            m.uv[(len(m.boxes) - 1, face)] = rect
    m.texture = texture
    m.texture_size = texture.width
    return m


def cmd_mockup(args):
    want = matcher(args.ids or args.only)
    sets = [f[:-4] for f in sorted(os.listdir(TEX_EQUIPMENT)) if f.endswith(".png") and want(f[:-4])]
    s = args.size or 300
    cells = []
    for name in sets:
        col = Image.new("RGBA", (s * 2 // 3, s * 2), BG)
        for row, (d, fname, legs) in enumerate(((TEX_EQUIPMENT, name, False),
                                                (TEX_EQUIPMENT_LEGS, f"{name}_leggings", True))):
            p = os.path.join(d, f"{fname}.png")
            if not os.path.exists(p):
                continue
            m = _humanoid(Image.open(p).convert("RGBA"), legs)
            img = boxmodel.render_preview(m, "front", s, transform=FLAT, background=BG)
            col.alpha_composite(img.crop((s // 6, 0, s // 6 + s * 2 // 3, s)), (0, row * s))
        draw_label(col, name)
        cells.append(col)
    return save_preview(contact_sheet(cells, args.cols or 5), "equipment_mockup.png", args.out)


def cmd_diff(args):
    """git の基準 (既定 HEAD) から変わった / 増えた PNG を [前 | 後] で並べる。"""
    ref = args.ref
    tex_root = os.path.relpath(os.path.join(TEX_ITEM, ".."), ROOT).replace(os.sep, "/")
    out = subprocess.run(["git", "diff", "--name-only", ref, "--", tex_root],
                         cwd=ROOT, capture_output=True, text=True, check=True).stdout.split()
    out += subprocess.run(["git", "ls-files", "--others", "--exclude-standard", "--", tex_root],
                          cwd=ROOT, capture_output=True, text=True, check=True).stdout.split()
    want = matcher(args.only)
    paths = sorted({p for p in out if p.endswith(".png")
                    and want(os.path.splitext(os.path.basename(p))[0])})
    if not paths:
        print(f"{ref} から変わったテクスチャはありません")
        return None
    s = args.size or 128
    cells = []
    for p in paths:
        pair = Image.new("RGBA", (s * 2 + 4, s), BG)
        old = subprocess.run(["git", "show", f"{ref}:{p}"], cwd=ROOT, capture_output=True)
        if old.returncode == 0:
            im = Image.open(io.BytesIO(old.stdout)).convert("RGBA")
            pair.alpha_composite(im.resize((s, s * im.height // im.width), Image.NEAREST))
        cur = os.path.join(ROOT, p)
        if os.path.exists(cur):
            im = Image.open(cur).convert("RGBA")
            pair.alpha_composite(im.resize((s, s * im.height // im.width), Image.NEAREST), (s + 4, 0))
        draw_label(pair, os.path.splitext(os.path.basename(p))[0])
        cells.append(pair)
    print(f"{len(paths)} 件変更 (左: {ref} / 右: 作業ツリー)")
    return save_preview(contact_sheet(cells, args.cols or 6), "diff.png", args.out)


COMMANDS = {
    "gui": cmd_gui, "icons": cmd_icons, "textures": cmd_textures, "compare": cmd_compare,
    "firstperson": cmd_firstperson, "hold": cmd_hold, "zoom": cmd_zoom, "sheet": cmd_sheet,
    "entities": cmd_entities, "equipment": cmd_equipment, "mockup": cmd_mockup, "diff": cmd_diff,
}


def main(argv: list[str] | None = None) -> int:
    setup_console()
    ap = argparse.ArgumentParser(description="アセットのプレビュー画像を作る",
                                 formatter_class=argparse.RawDescriptionHelpFormatter,
                                 epilog=__doc__.split("\n\n", 2)[2] if __doc__ else None)
    ap.add_argument("command", choices=list(COMMANDS), help="プレビューの種類")
    ap.add_argument("ids", nargs="*", help="対象 id (省略で既定の一覧)")
    ap.add_argument("--only", nargs="+", metavar="PAT", help="id で絞る (部分一致 / glob)")
    ap.add_argument("--cols", type=int, help="列数")
    ap.add_argument("--size", type=int, help="セルの大きさ (px)")
    ap.add_argument("--out", help="出力先 (既定: build/model_preview/<種類>.png)")
    ap.add_argument("--ref", default="HEAD", help="diff の比較基準 (コミット / ブランチ)")
    args = ap.parse_args(argv)
    COMMANDS[args.command](args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
