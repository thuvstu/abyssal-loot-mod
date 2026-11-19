"""アイコンのバリエーション (art/variants.py) の CLI。新武器・派生品の見た目の案出しに使う。

    python tools/abyss.py variants sheet abyss_katana            強化段階・特性・属性・素材・形態・状態の全一覧
    python tools/abyss.py variants sheet path/to/icon.png --accent ff4d5e
    python tools/abyss.py variants render abyss_axe --element ice --out build/variants/ice_axe.png
    python tools/abyss.py variants render abyss_axe --material gold --form serrated --mode blessed

元のアイコンは武器 id (64px の HD アイコン) か PNG のパス。出力の既定は build/variants/。
"""
from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw  # noqa: E402

from art import hd_weapons, variants as V  # noqa: E402
from art.project import ROOT, label_font, rel, setup_console  # noqa: E402

OUT_DIR = os.path.join(ROOT, "build", "variants")


def _base(src: str, accent: int | None) -> tuple[Image.Image, tuple[int, int, int], str]:
    if os.path.exists(src):
        img = Image.open(src).convert("RGBA")
        if img.height > img.width:  # アニメの縦帯なら 1 コマ目
            img = img.crop((0, 0, img.width, img.width))
        return img, V._hx(accent if accent is not None else 0xffffff), os.path.splitext(os.path.basename(src))[0]
    if src not in hd_weapons.ICONS:
        raise ValueError(f"'{src}' は PNG でも HD アイコンのある武器 id でもない")
    import generate_textures as G
    col = accent if accent is not None else G.WEAPON_MODELS[src][1]
    return hd_weapons.icon(src, col), V._hx(col), src


def _tinted_aura(img, color: int, t: float) -> Image.Image:
    au = V.aura(img, t)
    px = au.load()
    c = V._hx(color)
    for y in range(au.height):
        for x in range(au.width):
            if px[x, y][3]:
                px[x, y] = c + (px[x, y][3],)
    return au


def with_trait(img, tid: str, t: float = 0.3) -> Image.Image:
    _n, col, kind = V.TRAITS[tid]
    out = img.copy()
    out.alpha_composite(_tinted_aura(img, col, t))
    out.alpha_composite(V.trait_particles(kind, t).resize(img.size, Image.NEAREST))
    return out


def rows_for(img, acc, seed) -> list[tuple[str, list[tuple[str, Image.Image]]]]:
    return [
        ("強化段階 (鍛造レベル)", [(f"+{lv}", V.tier(img, V.tier_look(lv), acc, seed, 0.25)) for lv in (0, 1, 3, 5)]),
        ("特性 (気配 + 粒子)", [(n, with_trait(img, tid)) for tid, (n, _c, _k) in V.TRAITS.items()]),
        ("属性違い", [(n, V.recolor(img, *p)) for _id, (n, *p) in V.ELEMENTS.items()]),
        ("素材違い", [(n, V.recolor(img, *p)) for _id, (n, *p) in V.MATERIALS.items()]),
        ("限界突破", [("限界突破", V.limit_break(img, "break", acc, seed, 0.3)),
                  ("覚醒", V.limit_break(img, "awaken", acc, seed, 0.3))]),
        ("攻撃モーション", [(n, V.attack_pose(img, k, acc, seed)[3]) for k, n in
                     (("slash", "斬撃"), ("smash", "振り下ろし"), ("thrust", "突き"), ("guard", "防御"),
                      ("cast", "詠唱"), ("idle", "待機"))]),
        ("形態・状態", [("鋸刃", V.form(img, "serrated", acc, seed)), ("欠け", V.form(img, "broken", acc, seed)),
                    ("二振り", V.form(img, "twin", acc, seed)), ("双刃", V.form2(img, "dual")),
                    ("大剣", V.form2(img, "great")), ("短剣", V.form2(img, "dagger")),
                    ("エンチャント", V.enchanted(img, 0.3))]
         + [(n, V.mode(img, k, 0.3, seed)) for k, n in (("frozen", "凍結"), ("overheat", "過熱"),
                                                         ("blessed", "祝福"), ("stealth", "隠密"),
                                                         ("charged", "充填"))]),
    ]


def cmd_sheet(args) -> int:
    img, acc, name = _base(args.src, args.accent)
    rows = rows_for(img, acc, name)
    cell = img.width * args.scale
    cw, chh = cell + 8, cell + 18
    cols = max(len(r[1]) for r in rows)
    sheet = Image.new("RGBA", (cols * cw + 150, len(rows) * chh), (40, 38, 48, 255))
    d = ImageDraw.Draw(sheet)
    font = label_font(12)
    for r, (title, cells) in enumerate(rows):
        y = r * chh
        d.text((6, y + cell // 2), title, fill=(230, 220, 255, 255), font=font)
        for c, (label, im) in enumerate(cells):
            x = 150 + c * cw
            sheet.alpha_composite(im.resize((cell, cell), Image.NEAREST), (x, y + 2))
            d.text((x, y + cell + 1), label, fill=(210, 210, 210, 255), font=font)
    out = args.out or os.path.join(OUT_DIR, f"{name}_sheet.png")
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out)
    print(f"書き出し: {rel(os.path.abspath(out))}")
    return 0


def cmd_render(args) -> int:
    img, acc, name = _base(args.src, args.accent)
    if args.element:
        img = V.recolor(img, *V.ELEMENTS[args.element][1:])
    if args.material:
        img = V.recolor(img, *V.MATERIALS[args.material][1:])
    if args.form:
        img = V.form(img, args.form, acc, name)
    if args.tier:
        img = V.tier(img, V.tier_look(args.tier), acc, name)
    if args.mode:
        img = V.mode(img, args.mode, 0.0, name)
    if args.trait:
        img = with_trait(img, args.trait)
    out = args.out or os.path.join(OUT_DIR, f"{name}_variant.png")
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    img.save(out)
    print(f"書き出し: {rel(os.path.abspath(out))}")
    return 0


def main(argv: list[str] | None = None) -> int:
    setup_console()
    ap = argparse.ArgumentParser(prog="abyss.py variants", description="アイコンのバリエーション (art/variants.py)")
    sub = ap.add_subparsers(dest="cmd", required=True)

    def common(p):
        p.add_argument("src", help="武器 id か PNG のパス")
        p.add_argument("--accent", type=lambda v: int(v, 16), help="副色 rrggbb (省略で武器の色)")
        p.add_argument("--out")
    s = sub.add_parser("sheet", help="全バリエーションの一覧画像")
    common(s)
    s.add_argument("--scale", type=int, default=2)
    r = sub.add_parser("render", help="指定の組み合わせを 1 枚に (属性/素材→形態→強化→状態→特性 の順に重ねる)")
    common(r)
    r.add_argument("--element", choices=list(V.ELEMENTS))
    r.add_argument("--material", choices=list(V.MATERIALS))
    r.add_argument("--form", choices=["serrated", "broken", "twin"])
    r.add_argument("--tier", type=int, choices=range(0, 6))
    r.add_argument("--mode", choices=["frozen", "overheat", "blessed", "stealth", "charged"])
    r.add_argument("--trait", choices=list(V.TRAITS))
    args = ap.parse_args(argv)
    try:
        return {"sheet": cmd_sheet, "render": cmd_render}[args.cmd](args)
    except ValueError as e:
        print(e)
        return 2


if __name__ == "__main__":
    sys.exit(main())
