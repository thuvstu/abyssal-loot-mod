"""剣ジェネレータ (art/swordgen.py) と杖ジェネレータ (art/staffgen.py) の CLI。新武器の見た目の案出しに使う。

    python tools/abyss.py swordgen list
    python tools/abyss.py swordgen render --silhouette katana --surface choji --guard tsuba --blade 8a96ac --accent ef8fb2
    python tools/abyss.py swordgen sheet [--by silhouette|surface|guard|pommel|handle] [--surface damascus]
    python tools/abyss.py staffgen list
    python tools/abyss.py staffgen render lunar --element ice [--frames]      (--frames で 8 コマの縦帯)
    python tools/abyss.py staffgen sheet [--element fire] [--by type|element]

出力の既定は build/swordgen/ ・ build/staffgen/。
"""
from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw  # noqa: E402

from art import staffgen, swordgen  # noqa: E402
from art.project import ROOT, label_font, rel, setup_console  # noqa: E402


def _save(img, path):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    img.save(path)
    print(f"書き出し: {rel(os.path.abspath(path))}")


def _sheet(cells, scale=2, cols=8):
    size = cells[0][1].width * scale
    cw, ch = size + 8, size + 18
    sheet = Image.new("RGBA", (min(cols, len(cells)) * cw, ((len(cells) + cols - 1) // cols) * ch), (40, 38, 48, 255))
    d = ImageDraw.Draw(sheet)
    font = label_font(12)
    for i, (label, img) in enumerate(cells):
        x, y = (i % cols) * cw + 4, (i // cols) * ch + 2
        sheet.alpha_composite(img.resize((size, size), Image.NEAREST), (x, y))
        d.text((x, y + size + 1), label[:16], fill=(220, 220, 220, 255), font=font)
    return sheet


def _hex(v):
    return int(v, 16)


def sword_main(argv) -> int:
    ap = argparse.ArgumentParser(prog="abyss.py swordgen")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list")

    def opts(p):
        p.add_argument("--silhouette", default="straight")
        p.add_argument("--surface", default="polished")
        p.add_argument("--guard", default="straight")
        p.add_argument("--handle", default="leather")
        p.add_argument("--pommel", default="round")
        p.add_argument("--blade", type=_hex, default=0xc8d2e0, help="刃の色 rrggbb")
        p.add_argument("--guard-color", type=_hex, default=0xc9a24a)
        p.add_argument("--handle-color", type=_hex, default=0x4a3226)
        p.add_argument("--accent", type=_hex, default=0x7ce8ff)
        for flag in ("runes", "shards", "spurs", "serrated", "gem"):
            p.add_argument(f"--{flag}", action="store_true")
        p.add_argument("--out")
    r = sub.add_parser("render")
    opts(r)
    r.add_argument("--scale", type=int, default=1)
    s = sub.add_parser("sheet")
    opts(s)
    s.add_argument("--by", default="silhouette", choices=["silhouette", "surface", "guard", "pommel", "handle"])
    a = ap.parse_args(argv)
    if a.cmd == "list":
        print("輪郭 (silhouette):", " ".join(swordgen.PROFILES))
        print("地肌 (surface):", " ".join(f"{k}({swordgen.SURFACE_NAMES.get(k, k)})" for k in swordgen.SURFACES))
        print("鍔 (guard):", " ".join(swordgen.GUARDS))
        print("柄 (handle):", " ".join(swordgen.HANDLES))
        print("柄頭 (pommel):", " ".join(swordgen.POMMELS))
        return 0

    def build(**over):
        kw = dict(silhouette=a.silhouette, surface=a.surface, guard=a.guard, handle=a.handle, pommel=a.pommel,
                  blade_color=a.blade, guard_color=a.guard_color, handle_color=a.handle_color, accent=a.accent,
                  rune_inlay=a.runes, crystal_shards=a.shards, spurs=a.spurs, serrated=a.serrated, gem=a.gem)
        kw.update(over)
        return swordgen.render(swordgen.options(**kw))
    try:
        if a.cmd == "render":
            img = build()
            if a.scale > 1:
                img = img.resize((img.width * a.scale, img.height * a.scale), Image.NEAREST)
            _save(img, a.out or os.path.join(ROOT, "build", "swordgen", f"{a.silhouette}_{a.surface}.png"))
            return 0
        table = {"silhouette": swordgen.PROFILES, "surface": swordgen.SURFACES, "guard": swordgen.GUARDS,
                 "pommel": swordgen.POMMELS, "handle": swordgen.HANDLES}[a.by]
        cells = [(k, build(**{a.by: k})) for k in table]
        _save(_sheet(cells), a.out or os.path.join(ROOT, "build", "swordgen", f"sheet_{a.by}.png"))
        return 0
    except ValueError as e:
        print(e)
        return 2


def staff_main(argv) -> int:
    ap = argparse.ArgumentParser(prog="abyss.py staffgen")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list")
    r = sub.add_parser("render")
    r.add_argument("type")
    r.add_argument("--element")
    r.add_argument("--frames", action="store_true", help="8 コマの縦帯で出す")
    r.add_argument("--scale", type=int, default=1)
    r.add_argument("--out")
    s = sub.add_parser("sheet")
    s.add_argument("--element")
    s.add_argument("--type", default="wizard")
    s.add_argument("--by", default="type", choices=["type", "element"])
    s.add_argument("--out")
    a = ap.parse_args(argv)
    if a.cmd == "list":
        print("杖の型 (type):", " ".join(f"{k}({v['label']})" for k, v in staffgen.STAFF_TYPES.items()))
        print("属性 (element):", " ".join(f"{k}({v['label']})" for k, v in staffgen.ELEMENTS.items()))
        print("素材 (shaftMaterial / trimMaterial):", " ".join(staffgen.MATERIALS))
        return 0
    try:
        if a.cmd == "render":
            cfg = staffgen.config(a.type, element=a.element)
            img = staffgen.render(cfg, 1) if not a.frames else Image.new("RGBA", (64, 64 * staffgen.FRAMES))
            if a.frames:
                for i, f in enumerate(staffgen.frames(cfg)):
                    img.alpha_composite(f, (0, 64 * i))
            if a.scale > 1:
                img = img.resize((img.width * a.scale, img.height * a.scale), Image.NEAREST)
            _save(img, a.out or os.path.join(ROOT, "build", "staffgen", f"{a.type}_{a.element or 'default'}.png"))
            return 0
        if a.by == "type":
            cells = [(k, staffgen.render(staffgen.config(k, element=a.element), 1)) for k in staffgen.STAFF_TYPES]
        else:
            cells = [(k, staffgen.render(staffgen.config(a.type, element=k), 1)) for k in staffgen.ELEMENTS]
        _save(_sheet(cells, cols=9), a.out or os.path.join(ROOT, "build", "staffgen", f"sheet_{a.by}.png"))
        return 0
    except ValueError as e:
        print(e)
        return 2


if __name__ == "__main__":
    setup_console()
    sys.exit(sword_main(sys.argv[1:]))
