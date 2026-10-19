"""旧 generate_textures.py を git から取り出して 2D 武器アイコンを tools/art/icons.py へ追記する。

    python tools/_append_weapon_icons.py
"""
import io
import os
import subprocess

OLD = r"C:\Users\gogok\AppData\Local\Temp\opencode\old_gen.py"
DST = os.path.join("tools", "art", "icons.py")

raw = subprocess.run(
    ["git", "show", "09a40cd~1:tools/generate_textures.py"],
    capture_output=True, check=True).stdout
with io.open(OLD, "wb") as f:
    f.write(raw)

with io.open(OLD, encoding="utf-8") as f:
    lines = f.read().split("\n")


def rng(a, b):
    return "\n".join(lines[a - 1:b])


HEADER = '''

# ===================================================================
# 2D 武器アイコン (インベントリ表示用。所持時は 3D モデル)
# ===================================================================


def _dia_blade(d, x0, y0, x1, y1, accent):
    """斜め刃の基本形: 幅3の鋼 + 樋 + 切先 + 二重煌き。"""
    line(d, [(x0, y0), (x1, y1)], STEEL_LIGHT, 3)
    line(d, [(x0, y0), (x1, y1)], STEEL, 1)
    d.line([(x0 + 1, y0), (x1, y1 - 1)], fill=STEEL_DARK, width=1)
    tx, ty = x1 + 1, y1 - 1
    poly(d, [(x1 - 1, y1 + 1), (tx + 1, ty - 1), (x1 + 1, y1 + 3)], STEEL_LIGHT)
    d.point((x0 + (x1 - x0) * 2 // 3, y0 + (y1 - y0) * 2 // 3), fill=WHITE)
    d.point((x0 + (x1 - x0) * 5 // 6, y0 + (y1 - y0) * 5 // 6), fill=WHITE)
    d.point((x0 + (x1 - x0) // 3 + 1, y0 + (y1 - y0) // 3), fill=accent)


def _guard_grip(d, gx, gy, accent):
    """鍔(金・斜め) + 柄(木) + 柄頭(金・種別色)。"""
    line(d, [(gx - 2, gy - 2), (gx + 2, gy + 2)], GOLD, 1)
    line(d, [(gx + 1, gy + 1), (gx - 2, gy + 4)], WOOD, 2)
    line(d, [(gx - 1, gy + 3), (gx - 2, gy + 4)], WOOD_DARK, 1)
    d.rectangle([gx - 3, gy + 3, gx - 1, gy + 5], fill=OUTLINE)
    d.rectangle([gx - 2, gy + 4, gx - 2, gy + 4], fill=accent)
    d.point((gx, gy), fill=accent)


'''

BODY = [
    rng(78, 231),     # 基本 12 種
    rng(233, 628),    # 派生武器 (rune_blade ~ howling_claw)
]

# 既に追記済みなら差し替える
with io.open(DST, encoding="utf-8") as f:
    cur = f.read()
marker = "# ===================================================================\n# 2D 武器アイコン"
if marker in cur:
    cur = cur[:cur.index(marker)]

with io.open(DST, "w", encoding="utf-8", newline="\n") as f:
    f.write(cur.rstrip("\n") + "\n")
    f.write(HEADER)
    f.write("\n\n".join(BODY))
    f.write("\n")
print("appended to", DST)
