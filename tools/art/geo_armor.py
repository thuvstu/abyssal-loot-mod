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
    b.box("cape", (0, 15.2, 3.1), (8.2, 16.6, 0.5), "cloth")
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
    m.layout(128)
    return m


def armor_palette(body: int, trim: int) -> dict[str, Material]:
    """着用防具の材質。本体色は少し暗く・装飾色は金属・発光は装飾色を明るく。"""
    return {
        "plate": Material(_shift(body, 0.82), "metal"),
        "trim": Material(trim, "metal"),
        "dark": Material(_shift(body, 0.42), "dark"),
        "cloth": Material(_shift(body, 0.6), "cloth", accent=trim),
        "bone": Material(0xe8e0cc, "metal"),
        "gem": Material(_shift(trim, 1.25), "gem", emissive=True),
        "glow": Material(_shift(trim, 1.35), "glow", emissive=True),
    }


def armor_anim(asset: str, helmet: str, style: str | None) -> dict:
    bones = {"cape": {"rotation": geo.wave(3.0, (8.0, 0.0, 0.0), steps=8, base=(10.0, 0.0, 0.0))}}
    if helmet == "crown":
        bones["halo"] = {"rotation": geo.spin(3.0, axis=1, turns=1.0)}
    if style in MAGIC_STYLES:
        bones["orbit"] = {"rotation": geo.spin(3.0, axis=2, turns=1.0)}
    return geo.anim_json("animation.armor.idle", 3.0, bones)
