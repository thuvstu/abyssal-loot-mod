"""2D ドット絵の仕上げ処理 (全アイコン共通)。

描画関数が出した「平塗り + 黒い輪郭」の絵に、手描きのドット絵に近い質感を後から足す。

1. 領域の陰影: 同じ色でつながった領域ごとに、左上の光源を想定して
   右下の縁を 1 段暗く、左上の縁を 1 段明るくする。影は寒色側・光は暖色側へ色相をずらす
   (同じ色を明暗だけで塗るより鮮やかに見える)。細い線 (幅 2px 未満) と小さな領域は崩れるので触らない。
2. 色付き輪郭 (セルアウト): 真っ黒な輪郭を、隣の塗り色を深く沈めた色に置き換える。
   上・左の輪郭はやや明るく、下・右は暗く残して形の向きを出す。
3. 輪郭に接する外周の光と影 (旧 emboss の役割): 上端の塗りに薄い光、下端に薄い影。
"""
from __future__ import annotations

import colorsys

from PIL import Image

RGBA = tuple[int, int, int, int]

# 輪郭とみなす暗さ (相対輝度)。描画側の OUTLINE (24,18,36) などがここに入る
OUTLINE_LUM = 0.10
MIN_REGION = 6


def _lum(c) -> float:
    return (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / 255.0


def _shift(c, dv: float, hue_to: float, dh: float, ds: float) -> RGBA:
    """明度 dv だけ動かし、色相を hue_to へ dh だけ寄せ、彩度を ds 動かす。"""
    h, s, v = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
    if s > 0.08:  # 無彩色 (白・灰) は色相を持たないので回さない
        diff = ((hue_to - h + 0.5) % 1.0) - 0.5
        h = (h + max(-dh, min(dh, diff))) % 1.0
        s = max(0.0, min(1.0, s + ds))
    v = max(0.0, min(1.0, v + dv))
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return (int(round(r * 255)), int(round(g * 255)), int(round(b * 255)), c[3])


def darker(c, k: float = 0.16) -> RGBA:
    """影色: 暗く・青紫寄り・やや彩度高め。"""
    return _shift(c, -k, 0.70, 0.035, 0.08)


def lighter(c, k: float = 0.14) -> RGBA:
    """光色: 明るく・黄寄り・やや彩度低め。"""
    return _shift(c, k, 0.14, 0.03, -0.07)


def _regions(img: Image.Image) -> list[list[tuple[int, int]]]:
    """同じ色で 4 方向につながった不透明領域 (輪郭色は除く)。"""
    w, h = img.size
    px = img.load()
    seen = [[False] * w for _ in range(h)]
    out = []
    for y in range(h):
        for x in range(w):
            if seen[y][x]:
                continue
            c = px[x, y]
            seen[y][x] = True
            if c[3] < 128 or _lum(c) < OUTLINE_LUM:
                continue
            stack, reg = [(x, y)], []
            while stack:
                cx, cy = stack.pop()
                reg.append((cx, cy))
                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                    if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx] and px[nx, ny] == c:
                        seen[ny][nx] = True
                        stack.append((nx, ny))
            out.append(reg)
    return out


def finish(img: Image.Image, shade_regions: bool = True, selout: bool = True,
           rim: bool = True) -> Image.Image:
    img = img.convert("RGBA")
    w, h = img.size
    src = img.load()
    out = img.copy()
    op = out.load()

    def solid(x, y):
        return 0 <= x < w and 0 <= y < h and src[x, y][3] >= 128

    if shade_regions:
        for reg in _regions(img):
            if len(reg) < MIN_REGION:
                continue
            members = set(reg)
            c = src[reg[0]]
            for x, y in reg:
                inside = lambda dx, dy: (x + dx, y + dy) in members  # noqa: E731
                # 幅 2px 未満 (縦横どちらかで両側が外) の細線は塗り替えない
                if (not inside(-1, 0) and not inside(1, 0)) or (not inside(0, -1) and not inside(0, 1)):
                    continue
                shadow_edge = not inside(1, 0) or not inside(0, 1)
                light_edge = not inside(-1, 0) or not inside(0, -1)
                if shadow_edge and not light_edge:
                    op[x, y] = darker(c)
                elif light_edge and not shadow_edge and inside(1, 1) and inside(2, 2):
                    op[x, y] = lighter(c)
                elif not shadow_edge and not light_edge and not inside(1, 1):
                    op[x, y] = darker(c, 0.08)  # 右下の内角に浅い影

    if selout:
        snap = out.copy().load()
        for y in range(h):
            for x in range(w):
                c = src[x, y]
                if c[3] < 128 or _lum(c) >= OUTLINE_LUM:
                    continue
                # 輪郭: 外 (透明) に接していて、内側に塗りがある画素
                outer = any(not solid(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if not outer:
                    continue
                fills = [snap[x + dx, y + dy] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1),
                                                              (1, 1), (-1, -1), (1, -1), (-1, 1))
                         if solid(x + dx, y + dy) and _lum(src[x + dx, y + dy]) >= OUTLINE_LUM]
                if not fills:
                    continue
                avg = tuple(sum(f[i] for f in fills) // len(fills) for i in range(3)) + (255,)
                # 上・左の外縁は明るめ、下・右は暗め (形の向き)
                top_left = not solid(x, y - 1) or not solid(x - 1, y)
                k = 0.62 if top_left else 0.74
                deep = _shift(avg, -k, 0.72, 0.06, 0.12)
                if _lum(deep) > 0.16:
                    deep = _shift(deep, -0.15, 0.72, 0.0, 0.0)
                op[x, y] = (deep[0], deep[1], deep[2], c[3])

    if rim:
        snap = out.copy().load()
        for y in range(h):
            for x in range(w):
                c = snap[x, y]
                if c[3] < 128 or _lum(src[x, y]) < OUTLINE_LUM:
                    continue
                up = solid(x, y - 1) and _lum(src[x, y - 1]) >= OUTLINE_LUM
                dn = solid(x, y + 1) and _lum(src[x, y + 1]) >= OUTLINE_LUM
                if not up and _lum(c) > 0.2:
                    op[x, y] = lighter(c, 0.08)
                elif not dn:
                    op[x, y] = darker(c, 0.07)
    return out
