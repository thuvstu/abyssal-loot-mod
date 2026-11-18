"""素材ジェネレータ (art/matgen.py) の CLI。新しい素材アイコンの形・色を試すのに使う。

    python tools/abyss.py matgen list                                   形・質感・プリセットの一覧
    python tools/abyss.py matgen render skull --color 8a5cff --out build/matgen/skull.png [--scale 8]
    python tools/abyss.py matgen render crown --preset gold --style ancient --seed 3
    python tools/abyss.py matgen sheet [--color 8a5cff | --preset void] [--shapes skull crown ...]
    python tools/abyss.py matgen content                                content.json の mat: 素材を一覧画像に

出力の既定は build/matgen/。
"""
from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw  # noqa: E402

from art import content, matgen  # noqa: E402
from art.project import ROOT, label_font, rel, setup_console  # noqa: E402

OUT_DIR = os.path.join(ROOT, "build", "matgen")


def _save(img: Image.Image, path: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    img.save(path)
    print(f"書き出し: {rel(os.path.abspath(path))}")


def _sheet(cells: list[tuple[str, Image.Image]], scale: int, cols: int = 10) -> Image.Image:
    size = cells[0][1].width * scale
    cw, ch = size + 8, size + 16
    rows = (len(cells) + cols - 1) // cols
    sheet = Image.new("RGBA", (min(cols, len(cells)) * cw, rows * ch), (40, 40, 48, 255))
    d = ImageDraw.Draw(sheet)
    font = label_font(12)
    for i, (label, img) in enumerate(cells):
        x, y = (i % cols) * cw + 4, (i // cols) * ch + 2
        sheet.alpha_composite(img.resize((size, size), Image.NEAREST), (x, y))
        d.text((x, y + size + 1), label[:14], fill=(220, 220, 220, 255), font=font)
    return sheet


def cmd_list(_args) -> int:
    by_cat: dict[str, list[str]] = {}
    for sid, s in matgen.shapes().items():
        by_cat.setdefault(s["category"], []).append(f"{sid}({s['name']})")
    print("形 (mat:<id>):")
    for cat, items in by_cat.items():
        print(f"  [{cat} / 既定の質感 {matgen.CATEGORY_STYLE[cat]}] " + " ".join(items))
    print("質感 (style): " + " ".join(f"{k}({v})" for k, v in matgen.STYLE_NAMES.items()))
    print("プリセット (preset): " + " ".join(f"{k}({v[0]})" for k, v in matgen.PRESETS.items()))
    return 0


def cmd_render(args) -> int:
    img = matgen.render(args.shape, base=args.color, secondary=args.accent, style=args.style,
                        preset=args.preset, seed=args.seed, halo=args.halo)
    out = args.out or os.path.join(OUT_DIR, f"{args.shape}.png")
    if args.scale > 1:
        img = img.resize((img.width * args.scale, img.height * args.scale), Image.NEAREST)
    _save(img, out)
    return 0


def cmd_sheet(args) -> int:
    ids = args.shapes or sorted(matgen.shapes())
    presets = list(matgen.PRESETS)
    cells = []
    for i, sid in enumerate(ids):
        if args.color or args.preset:
            img = matgen.render(sid, base=args.color, secondary=args.accent, style=args.style,
                                preset=args.preset, seed=sid)
        else:  # 色を指定しなければプリセットを順に当てて見本帳にする
            img = matgen.render(sid, preset=presets[i % len(presets)], style=args.style, seed=sid)
        cells.append((sid, img))
    _save(_sheet(cells, args.scale), args.out or os.path.join(OUT_DIR, "sheet.png"))
    return 0


def cmd_content(args) -> int:
    doc = content.load()
    cells = []
    for group, key in (("trophies", "shape"), ("zone_materials", "shape"), ("consumables", "icon")):
        for e in doc[group]:
            if matgen.spec_from_entry(e, key):
                cells.append((e["id"].removeprefix("abyss_"), matgen.entry_icon(e, key)))
    if not cells:
        print("content.json に mat: の項目がありません")
        return 0
    _save(_sheet(cells, args.scale, cols=8), args.out or os.path.join(OUT_DIR, "content.png"))
    return 0


def main(argv: list[str] | None = None) -> int:
    setup_console()
    ap = argparse.ArgumentParser(prog="abyss.py matgen", description="素材ジェネレータ (art/matgen.py)")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list", help="形・質感・プリセットの一覧")

    def colors(p):
        p.add_argument("--color", help="地色 rrggbb")
        p.add_argument("--accent", help="副色 rrggbb (省略で自動)")
        p.add_argument("--style", choices=list(matgen.STYLES))
        p.add_argument("--preset", choices=list(matgen.PRESETS))
        p.add_argument("--out")

    r = sub.add_parser("render", help="1 形を PNG に")
    r.add_argument("shape")
    colors(r)
    r.add_argument("--seed", default="0", help="粒・質感の種 (文字列可)")
    r.add_argument("--scale", type=int, default=1)
    r.add_argument("--halo", action="store_true", help="発光部の周りに半透明の光を付ける")
    s = sub.add_parser("sheet", help="形の見本帳")
    s.add_argument("--shapes", nargs="+")
    s.add_argument("--scale", type=int, default=4)
    colors(s)
    c = sub.add_parser("content", help="content.json の mat: 素材の一覧画像")
    c.add_argument("--scale", type=int, default=6)
    c.add_argument("--out")
    args = ap.parse_args(argv)
    if args.cmd == "render" and not (args.color or args.preset):
        ap.error("render には --color か --preset が必要")
    try:
        return {"list": cmd_list, "render": cmd_render, "sheet": cmd_sheet, "content": cmd_content}[args.cmd](args)
    except ValueError as e:
        print(e)
        return 2


if __name__ == "__main__":
    sys.exit(main())
