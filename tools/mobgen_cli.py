"""MOB の骨組みジェネレータ (art/mobgen.py) の CLI。新しい敵の雛形 (テクスチャ・Java のモデル・見本) を作る。

    python tools/abyss.py mobgen list
    python tools/abyss.py mobgen build quadruped --variant wolf --color primary=5a4a6a eye=ff4d6d [--id abyss_newmob]
    python tools/abyss.py mobgen sheet                   12 系統の見本を 1 枚に

出力: build/mobgen/<id>/ に texture.png (箱 UV)・preview.png (等角投影)・Model.java (createBodyLayer)。
"""
from __future__ import annotations

import argparse
import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw  # noqa: E402

from art import mobgen  # noqa: E402
from art.project import ROOT, label_font, rel, setup_console  # noqa: E402


def main(argv=None) -> int:
    setup_console()
    ap = argparse.ArgumentParser(prog="abyss.py mobgen", description="MOB の骨組みジェネレータ (art/mobgen.py)")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list")
    b = sub.add_parser("build")
    b.add_argument("arch")
    b.add_argument("--variant")
    b.add_argument("--enable", nargs="+", default=[], help="追加部位 (角・翼・尾…) を有効にする")
    b.add_argument("--disable", nargs="+", default=[])
    b.add_argument("--color", nargs="+", default=[], metavar="slot=rrggbb")
    b.add_argument("--id", help="出力フォルダ名 (既定: 系統名)")
    sub.add_parser("sheet")
    a = ap.parse_args(argv)
    try:
        if a.cmd == "list":
            for k, v in mobgen.ARCHETYPES.items():
                opt = [p["id"] for p in v["parts"] if p["optional"]]
                print(f"{k} ({v['name']}): {v['tagline']}")
                print(f"    体型: {', '.join(x['id'] + '(' + x['name'] + ')' for x in v['variants'])}")
                if opt:
                    print(f"    追加部位: {', '.join(opt)}")
            print("色の役割: primary secondary accent skin eye detail")
            return 0
        if a.cmd == "sheet":
            font = label_font(14)
            cells = []
            for k in mobgen.ARCHETYPES:
                parts = mobgen.build(k)
                cells.append((k, mobgen.preview(parts, mobgen.colors(k), scale=4)))
            cw = max(im.width for _k, im in cells) + 10
            ch = max(im.height for _k, im in cells) + 24
            sheet = Image.new("RGBA", (cw * 4, ch * 3), (36, 34, 44, 255))
            d = ImageDraw.Draw(sheet)
            for i, (k, im) in enumerate(cells):
                x, y = (i % 4) * cw, (i // 4) * ch
                sheet.alpha_composite(im, (x + 5, y + 20))
                d.text((x + 6, y + 2), f"{k} / {mobgen.ARCHETYPES[k]['name']}", fill=(230, 230, 230, 255), font=font)
            out = os.path.join(ROOT, "build", "mobgen", "sheet.png")
            os.makedirs(os.path.dirname(out), exist_ok=True)
            sheet.save(out)
            print(f"書き出し: {rel(out)}")
            return 0
        over = dict(kv.split("=", 1) for kv in a.color)
        parts = mobgen.build(a.arch, a.variant, a.enable, a.disable)
        cols = mobgen.colors(a.arch, **over)
        size = mobgen.pack(parts)
        name = a.id or (a.arch + (f"_{a.variant}" if a.variant else ""))
        out = os.path.join(ROOT, "build", "mobgen", name)
        os.makedirs(out, exist_ok=True)
        mobgen.texture(parts, cols, size, seed=name).save(os.path.join(out, "texture.png"))
        mobgen.preview(parts, cols).save(os.path.join(out, "preview.png"))
        arch = mobgen.ARCHETYPES[a.arch]
        with io.open(os.path.join(out, "Model.java"), "w", encoding="utf-8", newline="\n") as f:
            f.write(mobgen.java(parts, size, f"{arch['name']} ({a.arch}{'/' + a.variant if a.variant else ''})") + "\n")
        print(f"書き出し: {rel(out)}/ (texture.png {size}x{size} / preview.png / Model.java, 部位 {len(parts)})")
        return 0
    except ValueError as e:
        print(e)
        return 2


if __name__ == "__main__":
    sys.exit(main())
