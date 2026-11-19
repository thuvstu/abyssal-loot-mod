"""装備 (プレイヤーの防具) のテクスチャ生成。

64x32 のヒューマノイド装備 UV レイアウト (バニラ準拠):
  頭 (0..32, 0..16)  = [横 0..8:右側面][8..16:顔][16..24:左側面][24..32:後頭]
                         [上 0..8: 天面 8..16 / 底 16..24]
  兜 overlays (32..64, 0..16) = 頭と同じ並び
  胴 (16..40, 16..32) = [16..20:右側面][20..28:胸][28..32:左側面][32..40:背面]
                         [上 16..20][底 20..24]
  右腕 (40..56, 16..32) / 左腕 (48..64, 16..32, 反転)
  右脚 (0..16, 16..32) / 左脚 (16..32, 16..32, 反転)
"""
from __future__ import annotations

import math
import random

from PIL import Image, ImageDraw

from . import core
from .core import mix, shade

# 主要な面 (描画に使う矩形)
HEAD_SIDE_R = (0, 8, 8, 16)
HEAD_FACE = (8, 8, 16, 16)
HEAD_SIDE_L = (16, 8, 24, 16)
HEAD_BACK = (24, 8, 32, 16)
HEAD_TOP = (8, 0, 16, 8)
HEAD_BOTTOM = (16, 0, 24, 8)

BODY_SIDE_R = (16, 20, 20, 32)
BODY_FRONT = (20, 20, 28, 32)
BODY_SIDE_L = (28, 20, 32, 32)
BODY_BACK = (32, 20, 40, 32)
BODY_TOP = (20, 16, 28, 20)
BODY_BOTTOM = (28, 16, 36, 20)

ARM_R_SIDE = (40, 20, 44, 32)
ARM_R_FRONT = (44, 20, 48, 32)
ARM_R_SIDE_L = (48, 20, 52, 32)
ARM_R_BACK = (52, 20, 56, 32)
ARM_R_TOP = (44, 16, 48, 20)

ARM_L_FRONT = (56, 20, 60, 32)
ARM_L_BACK = (48, 20, 52, 32)
ARM_L_SIDE_R = (52, 20, 56, 32)
ARM_L_SIDE_L = (60, 20, 64, 32)
ARM_L_TOP = (56, 16, 60, 20)

LEG_R_SIDE = (0, 20, 4, 32)
LEG_R_FRONT = (4, 20, 8, 32)
LEG_R_SIDE_L = (8, 20, 12, 32)
LEG_R_BACK = (12, 20, 16, 32)
LEG_R_TOP = (4, 16, 8, 20)

LEG_L_FRONT = (28, 20, 32, 32)
LEG_L_BACK = (16, 20, 20, 32)
LEG_L_SIDE_R = (20, 20, 24, 32)
LEG_L_SIDE_L = (24, 20, 28, 32)
LEG_L_TOP = (28, 16, 32, 20)


def _grad(d, box, top, bottom):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        t = (y - y0) / max(1, y1 - y0 - 1)
        d.line([(x0, y), (x1 - 1, y)], fill=mix(top, bottom, t))


# ---------------------------------------------------------------- 素材の地模様
# MCAssetGen の minecraft-armor-asset-generator (generator.ts の patternXxx) を移植したもの。
# 元実装は面全体を模様で塗っていたが、ここでは「面の上下グラデ × 模様」にして、既存の装飾 (面頬・ベルト・
# 縁取り) をその上に描く。模様は素材、意匠は既存の線という分担。

STYLES = {"scale": "竜鱗", "plate": "板金", "chain": "鎖帷子", "leather": "革", "crystal": "結晶",
          "ember": "熔岩", "frost": "氷", "obsidian": "黒曜石"}
_CTX: dict = {}  # equipment_tex が描いている間の (style, 副色, 差し色, 種)


def _hash(x, y, seed):
    m = 0xFFFFFFFF
    h = ((x * 374761393) & m) ^ ((y * 668265263) & m) ^ ((seed * 2147483647) & m)
    h = ((h ^ (h >> 13)) * 1274126177) & m
    h ^= h >> 16
    return h / 4294967295


def _vnoise(x, y, seed, scale):
    gx, gy = x / scale, y / scale
    x0, y0 = int(gx // 1), int(gy // 1)
    fx, fy = gx - x0, gy - y0
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = _hash(x0, y0, seed), _hash(x0 + 1, y0, seed)
    c, e = _hash(x0, y0 + 1, seed), _hash(x0 + 1, y0 + 1, seed)
    top, bot = a + (b - a) * sx, c + (e - c) * sx
    return top + (bot - top) * sy


def _lt(c, t):
    return mix(c, (255, 255, 255, 255), t)


def _dk(c, t):
    return mix(c, (0, 0, 0, 255), t)


def _pattern(style, x, y, fx, fy, fw, fh, P, Q, A, seed, detail=3):
    if style == "scale":
        s = max(2, round(detail / 2) + 1)
        row = fy // s
        sx = fx + (row % 2) * (s // 2)
        lx, ly, cell = sx % s, fy % s, sx // s
        if ly == s - 1 and lx != 0:
            return _dk(P, 0.32)
        if lx == 0 and ly > 0:
            return _dk(Q, 0.17)
        base = mix(P, _lt(P, 0.26), 0.8 if ly == 0 else 0.34)
        return mix(base, Q, _hash(cell + x, row + y, seed) * 0.12)
    if style == "plate":
        s = detail + 2
        gx, gy = x // s, y // s
        cx, cy = x - gx * s, y - gy * s
        base = mix(_dk(P, 0.08), _lt(P, 0.1), _hash(gx, gy, seed))
        if cx == 0 or cy == 0:
            return _dk(Q, 0.3)
        if cx == 1 and cy == 1:
            return A
        return _lt(base, 0.22) if cx == 1 or cy == 1 else base
    if style == "chain":
        s = detail + 1
        row = y // s
        off = (row & 1) * (s / 2)
        col = int((x + off) // s)
        lx, ly = x + off - col * s - s / 2 + 0.5, y - row * s - s / 2 + 0.5
        odd = (col + row) & 1
        kx, ky = (1, 0.55) if odd else (0.55, 1)
        rr = math.hypot(lx * kx, ly * ky)
        if rr > s * 0.42 or rr < s * 0.2:
            return _dk(Q, 0.45)
        return mix(_lt(P, 0.3) if lx + ly < 0 else _dk(P, 0.15), Q, _hash(col, row, seed) * 0.2)
    if style == "leather":
        n = _vnoise(x, y, seed, 3)
        base = shade(P, 0.82 + 0.3 * n)
        inset = 1
        seam = (fx in (inset, fw - 1 - inset) or fy in (inset, fh - 1 - inset)) and fw > 3 and fh > 3
        if seam and (fx + fy + detail) % 4 < 2:
            return mix(A, Q, 0.3)
        return _dk(base, 0.12) if (x + y) % 9 == 0 and n > 0.6 else base
    if style == "crystal":
        s = detail + 2
        gx, gy = x // s, y // s
        lx, ly = x - gx * s, y - gy * s
        facet = _hash(gx, gy, seed)
        col = mix(P, _lt(P, 0.35), facet) if lx > ly else mix(_dk(P, 0.25), P, facet)
        if lx == ly:
            col = _lt(col, 0.4)
        return A if _hash(x, y, seed + 7) > 0.985 else col
    if style == "ember":
        n1, n2 = _vnoise(x, y, seed, 4), _vnoise(x, y, seed + 5, 2)
        v = abs(n1 - 0.5) * 2
        thr = 0.06 + detail * 0.01
        if v < thr:
            return mix(A, (255, 255, 220, 255), 0.4)
        if v < thr * 2.2:
            return mix(_dk(P, 0.2), A, 0.5 * (1 - v / (thr * 2.2)))
        return mix(_dk(P, 0.45), P, n2)
    if style == "frost":
        n = _vnoise(x, y, seed, 3)
        col = mix(P, (255, 255, 255, 255), 0.12 + 0.35 * n)
        if (x + y * 2) % (detail + 5) == 0:
            col = _lt(col, 0.25)
        return mix(col, A, 0.8) if _hash(x, y, seed + 3) > 0.94 else col
    if style == "obsidian":
        n = _vnoise(x, y, seed, 6)
        t = math.sin((x * 0.45 + y * 0.9) * (1 + detail * 0.12) + n * 6) * 0.5 + 0.5
        col = mix(_dk(P, 0.35), _lt(P, 0.22), t)
        return A if _hash(x, y, seed + 11) > 0.99 else col
    raise ValueError(f"未知の防具の地模様 '{style}'。使えるもの: {', '.join(STYLES)}")


def _plate(d, box, base, top=1.18, bottom=0.72):
    """甲羅の面 (上ほど明るいグラデ)。地模様 (style) があれば模様 × グラデで塗る。"""
    style = _CTX.get("style")
    if not style:
        _grad(d, box, shade(base, top), shade(base, bottom))
        return
    x0, y0, x1, y1 = box
    fw, fh = x1 - x0, y1 - y0
    for y in range(y0, y1):
        k = top + (bottom - top) * ((y - y0) / max(1, fh - 1))
        for x in range(x0, x1):
            c = _pattern(style, x, y, x - x0, y - y0, fw, fh, base, _CTX["second"], _CTX["accent"], _CTX["seed"])
            d.point((x, y), fill=shade(c, 0.55 + 0.45 * k))


def _trim(d, box, color):
    d.rectangle(box, fill=color)


def _rivets(d, box, color, step=3):
    x0, y0, x1, y1 = box
    for y in range(y0, y1, step):
        for x in range(x0, x1, step):
            d.point((x, y), fill=color)


def _helmet(d, base, trim, accent, off=0):
    """兜 (頭 + overlays 領域)。"""
    for shift in (0, 32):
        face = (HEAD_FACE[0] + shift, HEAD_FACE[1], HEAD_FACE[2] + shift, HEAD_FACE[3])
        back = (HEAD_BACK[0] + shift, HEAD_BACK[1], HEAD_BACK[2] + shift, HEAD_BACK[3])
        side_r = (HEAD_SIDE_R[0] + shift, HEAD_SIDE_R[1], HEAD_SIDE_R[2] + shift, HEAD_SIDE_R[3])
        side_l = (HEAD_SIDE_L[0] + shift, HEAD_SIDE_L[1], HEAD_SIDE_L[2] + shift, HEAD_SIDE_L[3])
        top = (HEAD_TOP[0] + shift, HEAD_TOP[1], HEAD_TOP[2] + shift, HEAD_TOP[3])
        bottom = (HEAD_BOTTOM[0] + shift, HEAD_BOTTOM[1], HEAD_BOTTOM[2] + shift, HEAD_BOTTOM[3])
        for box in (face, back, side_r, side_l, top, bottom):
            _plate(d, box, base, 1.22, 0.66)
        # 面の目隠し (横一本のスリット)
        d.rectangle([face[0] + 1, face[1] + 2, face[2] - 2, face[1] + 3], fill=(14, 12, 20, 255))
        d.point((face[0] + 2, face[1] + 2), fill=accent)
        # 面の顎当て
        d.rectangle([face[0], face[3] - 2, face[2] - 1, face[3] - 1], fill=shade(base, 0.5))
        # 天面の兜のこび (縦線)
        d.line([(top[0] + 3, top[1]), (top[0] + 3, top[3] - 1)], fill=shade(base, 1.35))
        d.line([(top[0] + 4, top[1]), (top[0] + 4, top[3] - 1)], fill=shade(base, 0.6))
        # 装飾の縁
        d.line([(face[0], face[1]), (face[2] - 1, face[1])], fill=trim)


HELMETS = {"drake": "竜 (後方へ流れる角 / 顎当て)", "sentinel": "番兵 (立ち上がる稜線 / 重い額当て)",
           "crown": "王冠 (尖塔 / 宝石の額飾り)"}
VISORS = {"embers": "面頬に熾火の目", "open": "面頬を開ける"}


def _helmet_profile(img, profile, visor, base, trim, accent):
    """兜の意匠 (MCAssetGen の防具ジェネレータの designedPixel を移植)。顔・天面・側面・後頭に描く。"""
    px = img.load()
    white = (255, 255, 255, 255)
    rim = mix(base, accent, 0.68)
    gilt = mix(rim, white, 0.28)
    dark = shade(trim, 0.33)
    inset = shade(trim, 0.64)
    bright = mix(base, white, 0.42)
    for shift in (0, 32):
        def put(box, x, y, c):
            if c is not None:
                px[box[0] + shift + x, box[1] + y] = c
        for y in range(8):
            for x in range(8):
                m = min(x, 7 - x)
                c = None
                if y == 0:
                    c = gilt if (profile == "crown" and x in (1, 3, 4, 6)) or x in (3, 4) else trim if x in (0, 7) else bright
                elif y == 1:
                    c = bright if profile == "sentinel" and x in (3, 4) else rim if x in (3, 4) else trim if m == 0 else None
                elif y == 2:
                    c = gilt if m == 0 or x in (3, 4) else rim
                elif y in (3, 4):
                    if m == 0:
                        c = rim
                    elif visor == "open" and 2 <= x <= 5:
                        c = (0, 0, 0, 0) if shift else (20, 16, 26, 255)
                    elif y == 3 and x in (2, 5) and visor == "embers":
                        c = mix(accent, white, 0.5)
                    else:
                        c = dark
                elif y == 5:
                    c = bright if m <= 1 else rim if x in (3, 4) else inset
                elif y == 6:
                    c = trim if m == 0 else gilt if x in (3, 4) else None
                elif y == 7:
                    c = trim if m <= 1 else rim if x in (3, 4) else inset
                put(HEAD_FACE, x, y, c)
                c = None
                if x in (3, 4):
                    c = gilt if y % 3 == 0 else rim
                elif x in (1, 6) and y % 2 == 0:
                    c = bright
                elif x in (0, 7) or y in (0, 7):
                    c = trim
                if profile == "crown" and y in (0, 7) and x % 2 == 0:
                    c = gilt
                put(HEAD_TOP, x, y, c)
                c = None
                if y in (0, 2):
                    c = rim if y == 2 else bright
                elif y == 3 and 2 <= x <= 5:
                    c = inset
                elif y == 4 and 2 <= x <= 5:
                    c = dark
                elif y == 5 and x <= 2:
                    c = gilt
                elif y == 7:
                    c = gilt if x % 3 == 0 else trim
                if profile == "drake" and y in (1, 2) and x >= 5:
                    c = mix(accent, white, 0.2)
                put(HEAD_SIDE_R, x, y, c)
                put(HEAD_SIDE_L, 7 - x, y, c)
                c = rim if y in (2, 7) else (gilt if y % 3 == 0 else bright) if x in (3, 4) else None
                if profile == "sentinel" and x in (3, 4):
                    c = gilt
                put(HEAD_BACK, x, y, c)


def _chest(d, base, trim, accent):
    """胸当て (胴)。"""
    for box in (BODY_FRONT, BODY_BACK, BODY_SIDE_R, BODY_SIDE_L):
        _plate(d, box, base, 1.2, 0.68)
    _plate(d, BODY_TOP, base, 1.3, 0.9)
    _plate(d, BODY_BOTTOM, base, 0.9, 0.55)
    # 胸の装飾 (縦のライン + 中央の紋章)
    d.line([(BODY_FRONT[0] + 3, BODY_FRONT[1] + 1), (BODY_FRONT[0] + 3, BODY_FRONT[3] - 3)], fill=trim)
    d.line([(BODY_FRONT[0] + 4, BODY_FRONT[1] + 1), (BODY_FRONT[0] + 4, BODY_FRONT[3] - 3)], fill=shade(base, 0.55))
    d.rectangle([BODY_FRONT[0] + 1, BODY_FRONT[1] + 2, BODY_FRONT[0] + 2, BODY_FRONT[1] + 5], fill=accent)
    # 肩のライン
    d.line([(BODY_FRONT[0], BODY_FRONT[1]), (BODY_FRONT[2] - 1, BODY_FRONT[1])], fill=trim)
    d.line([(BODY_BACK[0], BODY_BACK[1]), (BODY_BACK[2] - 1, BODY_BACK[1])], fill=trim)
    # 腰のベルト
    d.rectangle([BODY_FRONT[0], BODY_FRONT[3] - 2, BODY_FRONT[2] - 1, BODY_FRONT[3] - 1], fill=trim)
    d.rectangle([BODY_BACK[0], BODY_BACK[3] - 2, BODY_BACK[2] - 1, BODY_BACK[3] - 1], fill=trim)
    _rivets(d, (BODY_FRONT[0] + 1, BODY_FRONT[3] - 2, BODY_FRONT[2] - 1, BODY_FRONT[3]), shade(trim, 1.3), 3)


def _arms(d, base, trim, accent):
    """腕当て (両腕)。"""
    boxes = [ARM_R_FRONT, ARM_R_BACK, ARM_R_SIDE, ARM_R_SIDE_L, ARM_R_TOP,
             ARM_L_FRONT, ARM_L_BACK, ARM_L_SIDE_R, ARM_L_SIDE_L, ARM_L_TOP]
    for box in boxes:
        _plate(d, box, base, 1.16, 0.66)
    # 肘の当て
    for box in (ARM_R_FRONT, ARM_R_BACK, ARM_L_FRONT, ARM_L_BACK):
        d.rectangle([box[0], box[3] - 4, box[2] - 1, box[3] - 3], fill=trim)
    d.rectangle([ARM_R_FRONT[0], ARM_R_FRONT[1], ARM_R_FRONT[2] - 1, ARM_R_FRONT[1] + 1], fill=trim)
    d.rectangle([ARM_L_FRONT[0], ARM_L_FRONT[1], ARM_L_FRONT[2] - 1, ARM_L_FRONT[1] + 1], fill=trim)


def _legs(d, base, trim, accent):
    """脚当て (両脚)。"""
    boxes = [LEG_R_FRONT, LEG_R_BACK, LEG_R_SIDE, LEG_R_SIDE_L, LEG_R_TOP,
             LEG_L_FRONT, LEG_L_BACK, LEG_L_SIDE_R, LEG_L_SIDE_L, LEG_L_TOP]
    for box in boxes:
        _plate(d, box, base, 1.16, 0.66)
    # 膝当て
    for box in (LEG_R_FRONT, LEG_R_BACK, LEG_L_FRONT, LEG_L_BACK):
        d.rectangle([box[0] + 1, box[1] + 4, box[2] - 2, box[1] + 6], fill=trim)
    # 裾
    for box in (LEG_R_FRONT, LEG_R_BACK, LEG_L_FRONT, LEG_L_BACK):
        d.rectangle([box[0], box[3] - 2, box[2] - 1, box[3] - 1], fill=shade(base, 0.55))


def equipment_tex(base, trim, accent, leggings: bool = False, style: str | None = None,
                  seed: int = 7, helmet: str | None = None, visor: str = "embers") -> Image.Image:
    """装備テクスチャ (64x32) を生成する。style で地模様 (STYLES)、helmet で兜の意匠 (HELMETS) を付ける。"""
    if helmet and helmet not in HELMETS:
        raise ValueError(f"未知の兜 '{helmet}'。使えるもの: {', '.join(HELMETS)}")
    if visor not in VISORS:
        raise ValueError(f"未知の面頬 '{visor}'。使えるもの: {', '.join(VISORS)}")
    _CTX.clear()
    if style:
        if style not in STYLES:
            raise ValueError(f"未知の防具の地模様 '{style}'。使えるもの: {', '.join(STYLES)}")
        _CTX.update(style=style, second=shade(base, 0.6), accent=accent, seed=seed)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if leggings:
        _legs(d, base, trim, accent)
    else:
        _helmet(d, base, trim, accent)
        if helmet:
            _helmet_profile(img, helmet, visor, base, trim, accent)
        _chest(d, base, trim, accent)
        _arms(d, base, trim, accent)
    # 全体の微細な weathered 感 (不透明な部分だけ)
    rnd = random.Random(7)
    px = img.load()
    for _ in range(160):
        x = rnd.randrange(64)
        y = rnd.randrange(32)
        if px[x, y][3] > 0:
            px[x, y] = shade(px[x, y], rnd.choice([0.9, 1.08]))
    _CTX.clear()
    return img
