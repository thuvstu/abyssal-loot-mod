"""各武器の「握り」判定を一覧する (所持位置の確認用)。

どの箱を握りとみなしたか (base / prefix / wrap / haft / center) と座標を表示する。
握りがおかしいアイテムは shapes.py 側で `m.grip = (x, y, z)` を明示するとよい。

使い方:
    python tools/report_grips.py
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import generate_textures as g  # noqa: E402
from art import boxmodel  # noqa: E402


def _base(n: str) -> bool:
    for p in ("grip", "handle"):
        if n == p or (n.startswith(p) and n[len(p):].isdigit()):
            return True
    return False


TIERS = (
    ("base", _base),
    ("prefix", lambda n: n.startswith(("grip", "handle"))),
    ("wrap", lambda n: n.endswith("_wrap")),
    ("haft", lambda n: n.startswith(("haft", "shaft"))),
)


def picked(model: boxmodel.Model) -> tuple[str, str]:
    for label, hit in TIERS:
        cands = [b for b in model.boxes if hit(b.name)]
        if cands:
            def volume(b):
                return (max(0.0, b.to[0] - b.frm[0])
                        * max(0.0, b.to[1] - b.frm[1])
                        * max(0.0, b.to[2] - b.frm[2]))
            return label, max(cands, key=volume).name
    return "center", "-"


def main() -> None:
    for item_id, (fn, color, style, _kind) in g.WEAPON_MODELS.items():
        model = boxmodel.Model(item_id)
        fn(model, g.shapes.make_palette(color, style))
        gx, gy, gz = model.grip_point()
        label, name = picked(model)
        print(f"{item_id:24s} grip=({gx:5.2f},{gy:5.2f},{gz:5.2f})  {label:7s} {name}")


if __name__ == "__main__":
    main()
