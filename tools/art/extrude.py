"""2D アイコンの立体押し出し (MCAssetGen の voxelforge-studio / compat/voxel-builder.ts を移植・改良したもの)。

ドット絵の各画素に厚みを与えて、MC の箱モデル (models/item/*.json の elements) にする。厚みの付け方は 5 通り:
- voxel    一定の厚み (バニラの item/generated と同じ板。ただし側面まで本物の箱)
- relief   明るい画素ほど厚い (浮き彫り)
- dome     縁から遠い画素ほど厚い (丸み)
- blade    対角の峰と縁からの距離で厚み (剣・槍向き。峰が高く刃が薄い)
- terraced 明るさを段に量子化した段々

同じ厚みの画素は貪欲法で長方形にまとめる (箱の数を減らす)。各箱の前後面はその長方形、側面は縁の 1 画素列の UV。

元実装からの改良点
- 出力をこの mod の書き出し (art/project.Writer の JSON) にそのまま渡せる dict にした。テクスチャは元の PNG を
  そのまま使う (元実装は減色したアトラスを作り直していた)
- まとめる長方形は「行方向に伸ばす → 同じ幅で下へ伸ばす」の 2 段で、元実装と同じ結果をより少ない走査で出す
- MC の要素数の目安 (既定 600) を超えたら厚みの段を減らして作り直す
- display は手持ち向けの既定値 (持ち手が右下に来るアイコン向け)。必要なら art/items.display で差し替える

CLI: `python tools/abyss.py extrude <png> --style blade --thickness 3 [--out model.json] [--preview]`
"""
from __future__ import annotations

import math

from PIL import Image, ImageDraw

STYLES = ("voxel", "relief", "dome", "blade", "terraced")


def _depths(img: Image.Image, style: str, thickness: int, invert: bool = False) -> dict[tuple[int, int], int]:
    w, h = img.size
    px = img.load()
    solid = {(x, y) for y in range(h) for x in range(w) if px[x, y][3] >= 128}
    # 縁からの距離 (4 近傍の BFS)
    dist = {}
    frontier = [p for p in solid if any((p[0] + dx, p[1] + dy) not in solid
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for p in frontier:
        dist[p] = 0
    i = 0
    while i < len(frontier):
        x, y = frontier[i]
        i += 1
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n in solid and n not in dist:
                dist[n] = dist[(x, y)] + 1
                frontier.append(n)
    maxd = max(dist.values(), default=1) or 1
    out = {}
    for (x, y) in solid:
        r, g, b, _a = px[x, y]
        lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
        lum = 1 - lum if invert else lum
        edge = dist[(x, y)] / maxd
        t = thickness
        if style == "voxel":
            d = t
        elif style == "relief":
            d = round(t * (0.22 + lum * 0.78))
        elif style == "dome":
            d = round(t * (0.25 + edge * 0.55 + lum * 0.2))
        elif style == "blade":
            u, v = x / max(1, w - 1), 1 - y / max(1, h - 1)
            spine = max(0.0, 1 - abs(u - v) * 1.65)
            d = round(t * (0.2 + edge * 0.45 + max(spine, 1 - abs(u - 0.5) * 1.8) * 0.35))
        elif style == "terraced":
            bands = max(2, min(6, t))
            d = round(min(bands, math.floor(lum * bands) + 1) / bands * t)
        else:
            raise ValueError(f"未知の押し出し方 '{style}'。使えるもの: {', '.join(STYLES)}")
        out[(x, y)] = max(1, min(16, d))
    return out


def _rects(depth: dict[tuple[int, int], int], w: int, h: int):
    """同じ厚みの画素を長方形にまとめる (行方向 → 下方向の貪欲法)。"""
    used = set()
    rects = []
    for y in range(h):
        x = 0
        while x < w:
            p = (x, y)
            if p not in depth or p in used:
                x += 1
                continue
            d = depth[p]
            x1 = x
            while (x1 + 1, y) in depth and (x1 + 1, y) not in used and depth[(x1 + 1, y)] == d:
                x1 += 1
            y1 = y
            while all((xx, y1 + 1) in depth and (xx, y1 + 1) not in used and depth[(xx, y1 + 1)] == d
                      for xx in range(x, x1 + 1)):
                y1 += 1
            for yy in range(y, y1 + 1):
                for xx in range(x, x1 + 1):
                    used.add((xx, yy))
            rects.append((x, y, x1 + 1, y1 + 1, d))
            x = x1 + 1
    return rects


DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0]},
}


def build(img: Image.Image, texture: str, style: str = "voxel", thickness: int = 2,
          max_elements: int = 600, invert: bool = False) -> dict:
    """押し出したモデル (models/item/*.json の中身)。texture は "abyssalloot:item/xxx" の参照名。"""
    img = img.convert("RGBA")
    if img.height > img.width:  # アニメの縦帯なら 1 コマ目
        img = img.crop((0, 0, img.width, img.width))
    w, h = img.size
    t = thickness
    while True:
        depth = _depths(img, style, t, invert)
        rects = _rects(depth, w, h)
        if len(rects) <= max_elements or t <= 1:
            break
        t -= 1
    sx, sy = 16 / w, 16 / h
    elements = []
    for x0, y0, x1, y1, d in rects:
        z0, z1 = 8 - d / 2 * (16 / w), 8 + d / 2 * (16 / w)
        fx0, fx1 = x0 * sx, x1 * sx
        fy0, fy1 = 16 - y1 * sy, 16 - y0 * sy
        u0, v0, u1, v1 = x0 * 16 / w, y0 * 16 / h, x1 * 16 / w, y1 * 16 / h
        cw, ch = 16 / w, 16 / h
        elements.append({
            "from": [round(fx0, 4), round(fy0, 4), round(z0, 4)],
            "to": [round(fx1, 4), round(fy1, 4), round(z1, 4)],
            "faces": {
                "north": {"uv": [u1, v0, u0, v1], "texture": "#0"},
                "south": {"uv": [u0, v0, u1, v1], "texture": "#0"},
                "up": {"uv": [u0, v0, u1, v0 + ch], "texture": "#0"},
                "down": {"uv": [u0, v1 - ch, u1, v1], "texture": "#0"},
                "west": {"uv": [u0, v0, u0 + cw, v1], "texture": "#0"},
                "east": {"uv": [u1 - cw, v0, u1, v1], "texture": "#0"},
            },
        })
    return {"textures": {"0": texture, "particle": texture}, "elements": elements, "display": DISPLAY,
            "_info": {"style": style, "thickness": t, "elements": len(elements)}}


def preview(img: Image.Image, model: dict, scale: int = 10, yaw: float = 40.0) -> Image.Image:
    """押し出した箱を斜めから見た絵 (面の色は前面の平均色 × 面の明るさ)。"""
    img = img.convert("RGBA")
    if img.height > img.width:
        img = img.crop((0, 0, img.width, img.width))
    w = img.width
    px = img.load()
    cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    polys = []
    for el in model["elements"]:
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        u0, v0, u1, v1 = el["faces"]["south"]["uv"]
        cols = [px[int(u * w / 16), int(v * w / 16)] for u in (u0, u1 - 0.01) for v in (v0, v1 - 0.01)]
        base = tuple(sum(c[i] for c in cols) // len(cols) for i in range(3))
        corners = {(i, j, k): (x1 if i else x0, y1 if j else y0, z1 if k else z0)
                   for i in (0, 1) for j in (0, 1) for k in (0, 1)}
        faces = {"south": ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)), "up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
                 "east": ((1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1))}
        for name, idx in faces.items():
            pts, depth = [], 0
            for key in idx:
                x, y, z = corners[key]
                xr, zr = x * cy - z * sy_, x * sy_ + z * cy
                pts.append((xr, -y + zr * 0.35))
                depth += zr
            k = {"south": 1.0, "up": 1.15, "east": 0.7}[name]
            polys.append((depth / 4, pts, tuple(min(255, int(v * k)) for v in base) + (255,)))
    xs = [x for _d, p, _c in polys for x, _y in p]
    ys = [y for _d, p, _c in polys for _x, y in p]
    out = Image.new("RGBA", (int((max(xs) - min(xs)) * scale) + 16, int((max(ys) - min(ys)) * scale) + 16), (36, 34, 44, 255))
    d = ImageDraw.Draw(out)
    for _depth, pts, col in sorted(polys, key=lambda t: t[0]):
        d.polygon([((x - min(xs)) * scale + 8, (y - min(ys)) * scale + 8) for x, y in pts], fill=col)
    return out
