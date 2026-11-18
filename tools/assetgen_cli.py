"""構造物ジェネレータ (art/structgen.py) と 2D アイコンの立体押し出し (art/extrude.py) の CLI。

    python tools/abyss.py structgen sample abyss_room [--nbt out.nbt] [--mcfunction out.mcfunction]
    python tools/abyss.py structgen build recipe.json [--nbt ...] [--mcfunction ...]
    python tools/abyss.py structgen info src/main/resources/data/abyssalloot/structure/gametest_floor.nbt
    python tools/abyss.py extrude src/main/resources/assets/abyssalloot/textures/item/abyss_sword_icon.png \\
        --style blade --thickness 3 [--texture abyssalloot:item/abyss_sword_icon] [--out model.json]

既定の出力先は build/structgen/ ・ build/extrude/ (見本の画像 preview.png も出す)。
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

from art import extrude, structgen  # noqa: E402
from art.project import MOD_ID, ROOT, rel, setup_console  # noqa: E402


def _write(path, data, mode="w"):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    if mode == "wb":
        with open(path, "wb") as f:
            f.write(data)
    else:
        with io.open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(data)
    print(f"書き出し: {rel(os.path.abspath(path))}")


def struct_main(argv) -> int:
    setup_console()
    ap = argparse.ArgumentParser(prog="abyss.py structgen")
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ("sample", "build"):
        p = sub.add_parser(name)
        p.add_argument("src", help="見本名 (sample) / レシピ JSON のパス (build)")
        p.add_argument("--nbt")
        p.add_argument("--mcfunction")
    i = sub.add_parser("info")
    i.add_argument("nbt")
    sub.add_parser("list")
    a = ap.parse_args(argv)
    out_dir = os.path.join(ROOT, "build", "structgen")
    try:
        if a.cmd == "list":
            print("見本:", " ".join(structgen.SAMPLES))
            print("操作 (ops): box(hollow/walls) ellipsoid(hollow) cylinder(hollow) line set flood replace "
                  "transform(rotY/flipX/flipZ) paste(repeat)")
            return 0
        if a.cmd == "info":
            with open(a.nbt, "rb") as f:
                s = structgen.read_nbt(f.read())
            print(f"大きさ {s.size} / ブロック {len(s.cells)}")
            for bid, n in list(s.stats().items())[:20]:
                print(f"  {n:5d}  {bid}")
            os.makedirs(out_dir, exist_ok=True)
            structgen.preview(s).save(os.path.join(out_dir, "info_preview.png"))
            print(f"見本: {rel(os.path.join(out_dir, 'info_preview.png'))}")
            return 0
        if a.cmd == "sample":
            if a.src not in structgen.SAMPLES:
                raise ValueError(f"未知の見本 '{a.src}'。使えるもの: {', '.join(structgen.SAMPLES)}")
            doc, name = structgen.SAMPLES[a.src], a.src
        else:
            with io.open(a.src, encoding="utf-8") as f:
                doc = json.load(f)
            name = os.path.splitext(os.path.basename(a.src))[0]
        s = structgen.from_recipe(doc)
        _write(a.nbt or os.path.join(out_dir, f"{name}.nbt"), s.to_nbt(), "wb")
        _write(a.mcfunction or os.path.join(out_dir, f"{name}.mcfunction"), s.to_mcfunction())
        os.makedirs(out_dir, exist_ok=True)
        structgen.preview(s).save(os.path.join(out_dir, f"{name}_preview.png"))
        print(f"見本: {rel(os.path.join(out_dir, name + '_preview.png'))} / 大きさ {s.size} / ブロック {len(s.cells)}")
        return 0
    except (ValueError, OSError) as e:
        print(e)
        return 2


def extrude_main(argv) -> int:
    setup_console()
    ap = argparse.ArgumentParser(prog="abyss.py extrude")
    ap.add_argument("png")
    ap.add_argument("--style", default="voxel", choices=extrude.STYLES)
    ap.add_argument("--thickness", type=int, default=2)
    ap.add_argument("--size", type=int, default=16, help="押し出す前に縮める一辺 (既定 16。HD アイコンは 16 か 32 を推奨)")
    ap.add_argument("--texture", help="モデルが参照するテクスチャ名 (既定: PNG のファイル名から abyssalloot:item/<名前>)")
    ap.add_argument("--invert", action="store_true", help="relief / terraced で暗い画素ほど厚くする")
    ap.add_argument("--out")
    a = ap.parse_args(argv)
    try:
        img = Image.open(a.png).convert("RGBA")
        if img.height > img.width:
            img = img.crop((0, 0, img.width, img.width))
        small = img if img.width <= a.size else img.resize((a.size, a.size), Image.BOX)
        name = os.path.splitext(os.path.basename(a.png))[0]
        model = extrude.build(small, a.texture or f"{MOD_ID}:item/{name}", a.style, a.thickness, invert=a.invert)
        info = model.pop("_info")
        out = a.out or os.path.join(ROOT, "build", "extrude", f"{name}_{a.style}.json")
        _write(out, json.dumps(model, ensure_ascii=False, indent=1))
        extrude.preview(small, model).save(os.path.splitext(out)[0] + "_preview.png")
        print(f"箱 {info['elements']} 個 (厚み {info['thickness']}) / 見本: {rel(os.path.splitext(out)[0] + '_preview.png')}")
        if small.width != img.width:
            print(f"(形は {small.width}px に縮めて押し出し。UV は 0..16 の比率なので元の {img.width}px のテクスチャをそのまま使える)")
        return 0
    except (ValueError, OSError) as e:
        print(e)
        return 2
