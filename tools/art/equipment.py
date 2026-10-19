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


def _plate(d, box, base, top=1.18, bottom=0.72):
    """甲羅の面 (上ほど明るいグラデ)。"""
    _grad(d, box, shade(base, top), shade(base, bottom))


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


def equipment_tex(base, trim, accent, leggings: bool = False) -> Image.Image:
    """装備テクスチャ (64x32) を生成する。"""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if leggings:
        _legs(d, base, trim, accent)
    else:
        _helmet(d, base, trim, accent)
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
    return img
