"""GeckoLib の着用防具モデル (セットごと 1 つ。兜・胴・腕・脚・靴の骨を持つ)。

GeckoLib の GeoArmorRenderer は骨の名前 (armorHead / armorBody / armorRightArm / armorLeftArm /
armorRightLeg / armorLeftLeg / armorRightBoot / armorLeftBoot) で部位を見つけ、MC の人型モデルの動きに追従させる。
骨の基準点は Bedrock の人型テンプレートの位置に置く (頭 (0,24,0)・右腕 (-5,22,0)・右脚 (-1.9,12,0) …。
右は -x、正面は -z)。art/geo.py は「見た目どおり」の座標を左右反転して書き出すので、ここでは x と
x/y 回転を先に反転して渡し、書き出し後に Bedrock 座標どおりになるようにしている。

意匠は兜の型 (equipment.HELMETS: drake / sentinel / crown) と地模様 (equipment.STYLES) で変える。
- 兜: 殻・額当て・光る目・頬当て + 型ごとの飾り (竜=後ろへ流れる角 / 番兵=稜線と羽飾り / 王冠=尖塔と回る光輪)
- 胴: 胸当て・光る核石・帯・揺れるマント (+ 魔法系の地模様なら背中で回る結晶片)
- 腕: 肩当て (棘付き)・手甲・手首の帯 / 脚: すね当て・膝当て・草摺 / 靴: 具足とつま先
アニメ: マントの揺れ・光輪と結晶片の回転 (idle をループ)。発光する材質は *_glowmask.png にも塗る。
"""
from __future__ import annotations

from . import geo
from .geo import Material, Model

MAGIC_STYLES = {"crystal", "obsidian", "frost", "ember"}


def _mix(a: int, b: int, t: float) -> int:
    return sum(int(((a >> sh) & 255) + (((b >> sh) & 255) - ((a >> sh) & 255)) * t) << sh for sh in (16, 8, 0))


def _shift(c: int, k: float) -> int:
    r, g, b = (c >> 16) & 255, (c >> 8) & 255, c & 255
    f = (lambda v: max(0, min(255, int(v * k)))) if k < 1 else (lambda v: max(0, min(255, int(v + (255 - v) * (k - 1)))))
    return (f(r) << 16) | (f(g) << 8) | f(b)


class _B:
    """Bedrock 座標で書くための薄い包み (x と x/y 回転を反転して geo.Model へ渡す)。"""

    def __init__(self, m: Model):
        self.m = m

    def bone(self, name, pivot, parent=None):
        return self.m.bone(name, pivot=(-pivot[0], pivot[1], pivot[2]), parent=parent)

    def box(self, bone, center, size, mat, rot=(0.0, 0.0, 0.0)):
        self.m.box(bone, (-center[0], center[1], center[2]), size, mat, rot=(-rot[0], -rot[1], rot[2]))

    def pair(self, bone_r, bone_l, center, size, mat, rot=(0.0, 0.0, 0.0)):
        """右 (x<0) に置いた部品を左にも鏡映して置く。"""
        self.box(bone_r, center, size, mat, rot)
        self.box(bone_l, (-center[0], center[1], center[2]), size, mat, (rot[0], -rot[1], -rot[2]))


def armor_model(asset: str, helmet: str = "sentinel", style: str | None = None) -> Model:
    m = Model(f"armor_{asset}")
    b = _B(m)
    b.bone("armorHead", (0, 24, 0))
    b.bone("armorBody", (0, 24, 0))
    b.bone("armorRightArm", (-5, 22, 0))
    b.bone("armorLeftArm", (5, 22, 0))
    b.bone("armorRightLeg", (-1.9, 12, 0))
    b.bone("armorLeftLeg", (1.9, 12, 0))
    b.bone("armorRightBoot", (-1.9, 12, 0))
    b.bone("armorLeftBoot", (1.9, 12, 0))

    # ---- 兜
    b.box("armorHead", (0, 28.2, 0), (9.0, 8.6, 9.0), "plate")
    b.box("armorHead", (0, 29.9, -4.7), (9.4, 1.4, 0.8), "trim")
    b.box("armorHead", (0, 27.6, -4.75), (6.0, 0.8, 0.5), "glow" if helmet != "crown" else "dark")
    b.pair("armorHead", "armorHead", (-4.6, 25.9, -2.0), (0.8, 4.0, 5.0), "plate")
    b.box("armorHead", (0, 24.4, -4.6), (5.0, 1.0, 0.8), "trim")
    if helmet == "drake":
        b.bone("horns", (0, 31, 0), parent="armorHead")
        for k in range(5):  # 側頭から後ろ上へ反り上がる角 (重ねて途切れないように)
            t = k / 4
            b.pair("horns", "horns", (-4.5 - 0.6 * t, 31.0 + 4.2 * t * t + 0.6 * t, 0.6 + 4.6 * t),
                   (1.9 - 0.9 * t, 1.9 - 0.9 * t, 2.2), "bone", rot=(20 + 45 * t, 0, 10 * t))
        b.box("armorHead", (0, 32.6, -1.0), (1.2, 0.8, 7.0), "trim")
    elif helmet == "crown":
        import math
        for k in range(7):
            a = math.radians(-90 + (k - 3) * 30)
            x, z = 3.9 * math.cos(a), 3.9 * math.sin(a)
            h = 3.2 if k == 3 else 2.4
            b.box("armorHead", (x, 32.6 + h / 2, z), (1.0, h, 1.0), "trim")
        b.box("armorHead", (0, 33.4, -4.0), (1.4, 1.4, 0.6), "gem", rot=(0, 0, 45))
        b.bone("halo", (0, 37, 0), parent="armorHead")
        for k in range(8):
            a = math.radians(k * 45)
            b.box("halo", (3.6 * math.cos(a), 37.2, 3.6 * math.sin(a)), (1.2, 0.6, 1.2), "glow")
    else:  # sentinel
        b.box("armorHead", (0, 34.0, 0.6), (1.0, 3.2, 8.4), "trim")
        b.box("armorHead", (0, 35.0, 4.0), (0.8, 2.6, 3.0), "cloth", rot=(25, 0, 0))
        b.box("armorHead", (0, 30.9, -4.9), (9.8, 1.0, 1.2), "trim")

    # ---- 胴
    b.box("armorBody", (0, 18.2, 0), (9.0, 12.6, 5.0), "plate")
    b.box("armorBody", (0, 20.4, -2.8), (7.0, 6.0, 0.8), "trim")
    b.box("armorBody", (0, 20.4, -3.3), (2.0, 2.0, 0.6), "glow", rot=(0, 0, 45))
    b.box("armorBody", (0, 12.8, 0), (9.6, 1.4, 5.6), "dark")
    b.box("armorBody", (0, 12.8, -2.9), (2.0, 1.8, 0.6), "trim")
    b.box("armorBody", (0, 10.2, -2.9), (7.4, 4.0, 0.6), "plate", rot=(8, 0, 0))   # 前垂れ (草摺)
    b.box("armorBody", (0, 10.2, 2.9), (7.4, 4.0, 0.6), "plate", rot=(-8, 0, 0))
    b.box("armorBody", (0, 16.4, -2.8), (0.8, 4.0, 0.6), "glow")                   # 核石から下へ走る光
    b.pair("armorBody", "armorBody", (-3.0, 22.6, -2.9), (2.0, 1.0, 0.6), "glow", rot=(0, 0, 30))
    b.bone("cape", (0, 23.6, 2.8), parent="armorBody")
    b.box("cape", (0, 23.4, 2.9), (9.4, 1.4, 1.2), "filigree")  # 留め具の帯
    b.pair("cape", "cape", (-3.6, 23.4, 2.4), (1.6, 1.6, 0.6), "gem", rot=(0, 0, 45))
    b.box("cape", (0, 19.8, 3.1), (8.2, 6.0, 0.5), "cloth")
    b.bone("cape1", (0, 16.8, 3.1), parent="cape")
    b.box("cape1", (0, 13.8, 3.1), (8.6, 6.0, 0.45), "cloth")
    b.bone("cape2", (0, 10.8, 3.1), parent="cape1")
    b.box("cape2", (0, 8.6, 3.1), (9.0, 4.6, 0.4), "cloth")
    b.box("cape2", (0, 6.1, 3.1), (9.0, 0.6, 0.45), "filigree")  # 裾の金糸
    for k in range(3):  # 裾の三角の房
        b.box("cape2", (-3 + k * 3, 5.4, 3.1), (1.2, 1.2, 0.4), "filigree", rot=(0, 0, 45))
    if helmet == "crown" or style in MAGIC_STYLES:  # 背びれ (翼の骨)
        b.bone("fins", (0, 22, 3), parent="armorBody")
        for k in range(3):
            b.pair("fins", "fins", (-2.6 - k * 1.6, 23.0 + k * 1.4, 4.2 + k * 0.6), (0.8, 4.0 - k * 0.8, 0.8),
                   "trim", rot=(-20, 0, 25 + k * 15))
    if style in MAGIC_STYLES:
        b.bone("orbit", (0, 19, 6), parent="armorBody")
        for k, (x, y) in enumerate(((-3.5, 21), (3.5, 21), (0, 15.5))):
            b.box("orbit", (x, y, 6.0), (1.2, 2.6, 1.2), "glow", rot=(0, 45, 20 * (k - 1)))

    # ---- 腕
    b.pair("armorRightArm", "armorLeftArm", (-6.0, 17.0, 0), (4.8, 8.2, 4.8), "plate")
    b.pair("armorRightArm", "armorLeftArm", (-6.6, 23.4, 0), (5.8, 3.6, 6.2), "trim", rot=(0, 0, 14))
    b.pair("armorRightArm", "armorLeftArm", (-7.2, 25.0, 0), (4.2, 1.6, 5.0), "plate", rot=(0, 0, 18))
    b.pair("armorRightArm", "armorLeftArm", (-9.0, 22.6, 0), (0.6, 2.4, 6.4), "glow", rot=(0, 0, 14))
    b.pair("armorRightArm", "armorLeftArm", (-8.4, 26.4, 0), (1.0, 2.8, 1.0), "trim", rot=(0, 0, 32))
    b.pair("armorRightArm", "armorLeftArm", (-6.0, 13.0, 0), (5.0, 1.2, 5.0), "dark")
    b.pair("armorRightArm", "armorLeftArm", (-6.0, 15.4, -2.6), (3.2, 2.2, 0.6), "glow")  # 手甲の光る板

    # ---- 脚
    b.pair("armorRightLeg", "armorLeftLeg", (-1.9, 7.6, 0), (4.8, 7.0, 4.8), "plate")
    b.pair("armorRightLeg", "armorLeftLeg", (-1.9, 6.0, -2.6), (3.0, 2.4, 1.0), "trim")
    b.pair("armorRightLeg", "armorLeftLeg", (-2.1, 11.0, -2.6), (4.0, 3.0, 0.8), "trim", rot=(-8, 0, 0))
    b.pair("armorRightLeg", "armorLeftLeg", (-4.4, 7.6, 0), (0.6, 5.0, 1.0), "glow")  # 脚の外側のルーン線

    # ---- 靴
    b.pair("armorRightBoot", "armorLeftBoot", (-1.9, 2.3, 0), (4.9, 4.6, 5.2), "plate")
    b.pair("armorRightBoot", "armorLeftBoot", (-1.9, 0.9, -3.0), (4.6, 1.8, 1.6), "trim")
    if helmet == "drake":
        b.pair("armorRightBoot", "armorLeftBoot", (-4.6, 4.0, 1.0), (0.8, 2.4, 0.8), "bone", rot=(0, 0, -30))
    _details(b, helmet, style)
    m.layout(128)
    return m


def _details(b, helmet: str, style: str | None):
    """繊細さを足す細部: 金の縁取り・重ね板 (札)・鋲・宝石の象嵌・喉当て・帯の留め金・膝当て・具足の段。"""
    import math
    # 兜: 縁の金線・頬当ての鋲・額の宝石・後頭部の重ね板
    b.box("armorHead", (0, 32.6, 0), (9.4, 0.5, 9.4), "filigree")
    b.box("armorHead", (0, 30.3, -5.15), (1.2, 1.2, 0.5), "gem", rot=(0, 0, 45))
    for k in range(3):
        b.box("armorHead", (0, 30.0 - k * 1.8, 4.6 + k * 0.2), (8.6 - k * 0.6, 1.6, 0.6), "plate")
        b.box("armorHead", (0, 29.3 - k * 1.8, 4.95 + k * 0.2), (8.6 - k * 0.6, 0.3, 0.3), "filigree")
    for y in (27.0, 25.4):
        b.pair("armorHead", "armorHead", (-5.05, y, -3.6), (0.4, 0.6, 0.6), "edge")
    # 胴: 喉当て・胸の縁取り・肋の重ね板・帯の留め金・腰の鋲
    b.box("armorBody", (0, 24.3, -0.2), (7.0, 1.4, 5.6), "plate")
    b.box("armorBody", (0, 24.9, -0.2), (7.2, 0.4, 5.8), "filigree")
    b.box("armorBody", (0, 23.5, -3.2), (7.4, 0.5, 0.3), "filigree")
    b.box("armorBody", (0, 17.3, -3.2), (7.4, 0.5, 0.3), "filigree")
    b.pair("armorBody", "armorBody", (-3.6, 20.4, -3.2), (0.5, 6.0, 0.3), "filigree")
    for k in range(3):
        b.box("armorBody", (0, 16.2 - k * 1.2, -2.75), (8.0 - k * 0.4, 1.0, 0.5), "plate", rot=(4, 0, 0))
        b.box("armorBody", (0, 15.75 - k * 1.2, -3.0), (8.0 - k * 0.4, 0.2, 0.3), "edge")
    b.box("armorBody", (0, 12.8, -3.3), (2.6, 2.4, 0.5), "filigree")
    b.box("armorBody", (0, 12.8, -3.6), (1.2, 1.2, 0.4), "gem", rot=(0, 0, 45))
    for x in (-4.0, -2.0, 2.0, 4.0):
        b.box("armorBody", (x, 12.8, -2.95), (0.5, 0.5, 0.4), "edge")
    # 背中の紋 (マントの上端に隠れない位置)
    for k in range(6):
        a = math.radians(k * 60)
        b.box("armorBody", (math.cos(a) * 1.4, 21.0 + math.sin(a) * 1.4, 2.65), (0.7, 0.7, 0.3), "filigree", rot=(0, 0, k * 60))
    # 腕: 肩当てを 3 枚の札に・肘当て・手甲の金線
    for k in range(3):
        b.pair("armorRightArm", "armorLeftArm", (-7.0 - k * 0.3, 22.2 - k * 1.5, 0), (5.0 - k * 0.4, 1.4, 5.8 - k * 0.4),
               "plate", rot=(0, 0, 18 + k * 6))
        b.pair("armorRightArm", "armorLeftArm", (-7.0 - k * 0.3, 21.6 - k * 1.5, 0), (5.0 - k * 0.4, 0.3, 5.9 - k * 0.4),
               "filigree", rot=(0, 0, 18 + k * 6))
    b.pair("armorRightArm", "armorLeftArm", (-8.6, 17.4, 0), (0.8, 2.2, 2.2), "trim", rot=(45, 0, 0))
    b.pair("armorRightArm", "armorLeftArm", (-6.0, 14.2, -2.65), (3.4, 0.3, 0.3), "filigree")
    b.pair("armorRightArm", "armorLeftArm", (-6.0, 16.6, -2.65), (3.4, 0.3, 0.3), "filigree")
    # 脚: 膝当ての菱・すねの稜線・腿の札
    b.pair("armorRightLeg", "armorLeftLeg", (-1.9, 6.4, -3.15), (1.4, 1.4, 0.5), "gem", rot=(0, 0, 45))
    b.pair("armorRightLeg", "armorLeftLeg", (-1.9, 8.8, -2.75), (0.5, 3.0, 0.5), "edge")
    for k in range(2):
        b.pair("armorRightLeg", "armorLeftLeg", (-2.0, 10.6 - k * 1.3, 2.65), (4.4, 1.1, 0.5), "plate", rot=(-6, 0, 0))
    # 靴: 甲の重ね板・踵の拍車
    for k in range(3):
        b.pair("armorRightBoot", "armorLeftBoot", (-1.9, 1.6 + k * 0.9, -3.0 + k * 0.25), (4.4, 0.8, 1.2), "plate", rot=(-10, 0, 0))
        b.pair("armorRightBoot", "armorLeftBoot", (-1.9, 1.25 + k * 0.9, -3.5 + k * 0.25), (4.4, 0.2, 0.3), "filigree")
    b.pair("armorRightBoot", "armorLeftBoot", (-1.9, 4.6, -0.2), (5.2, 0.6, 5.4), "filigree")
    b.pair("armorRightBoot", "armorLeftBoot", (-1.9, 1.4, 3.1), (0.6, 0.6, 1.4), "edge")
    if style in MAGIC_STYLES or helmet == "crown":
        # 浮遊する肩の宝珠 (腕の骨の子にして、腕と一緒に動く)
        b.bone("motesR", (-8.0, 26.0, 0), parent="armorRightArm")
        b.bone("motesL", (8.0, 26.0, 0), parent="armorLeftArm")
        for k in range(3):
            a = math.radians(k * 120)
            for bn, sx in (("motesR", -1), ("motesL", 1)):
                b.box(bn, (sx * 8.0 + math.cos(a) * 2.2, 27.4, math.sin(a) * 2.2), (0.8, 0.8, 0.8), "glow", rot=(45, 45, 0))


def armor_palette(body: int, trim: int) -> dict[str, Material]:
    """着用防具の材質。本体色は少し暗く・装飾色は金属・発光は装飾色を明るく。"""
    return {
        "plate": Material(_shift(body, 0.82), "metal"),
        "trim": Material(trim, "metal"),
        "dark": Material(_shift(body, 0.42), "dark"),
        "cloth": Material(_shift(body, 0.6), "cloth", accent=trim),
        "bone": Material(0xe8e0cc, "metal"),
        "gem": Material(_shift(trim, 1.25), "gem", emissive=True),
        "filigree": Material(_mix(trim, 0xd9b04c, 0.45), "gold"),
        "edge": Material(_shift(trim, 1.4), "edge"),
        "glow": Material(_shift(trim, 1.35), "glow", emissive=True),
    }


def armor_anim(asset: str, helmet: str, style: str | None) -> dict:
    bones = {"cape": {"rotation": geo.wave(3.0, (5.0, 0.0, 1.5), steps=12, base=(8.0, 0.0, 0.0))},
             "cape1": {"rotation": geo.wave(3.0, (7.0, 0.0, 2.0), steps=12, phase=0.8, base=(4.0, 0.0, 0.0))},
             "cape2": {"rotation": geo.wave(3.0, (9.0, 0.0, 3.0), steps=12, phase=1.6, base=(3.0, 0.0, 0.0))}}
    if style in MAGIC_STYLES or helmet == "crown":
        bones["motesR"] = {"rotation": geo.spin(3.0, axis=1, turns=1.0, steps=8)}
        bones["motesL"] = {"rotation": geo.spin(3.0, axis=1, turns=-1.0, steps=8)}
    if helmet == "crown":
        bones["halo"] = {"rotation": geo.spin(3.0, axis=1, turns=1.0)}
    if style in MAGIC_STYLES:
        bones["orbit"] = {"rotation": geo.spin(3.0, axis=2, turns=1.0)}
    return geo.anim_json("animation.armor.idle", 3.0, bones)
