"""剣ジェネレータ (MCAssetGen の AegisBlade Studio = minecraft-sword-texture-maker を移植・改良したもの)。

剣を「解析的な形」で持つ。画像上の点 (x, y) を刃の軸座標 (d = 柄尻→切っ先, v = 刃に直交) に直し、
その点が 柄頭 / 柄 / 鍔 / 刃 / 刃縁 / 棘 / 結晶片 のどれで、刃の幅の中のどこ (across = -1 刃側 … +1 峰側) かを返す。
色はその部位・across・進み具合 (progress) から決め、刃の表面には 49 種の「地肌」(刃文・積層鋼・溶岩の亀裂…) を掛ける。

- 刃の輪郭 42 種 (`PROFILES`): 峰の曲がり・幅の包絡・切っ先の形・片刃の偏り のデータ
- 鍔 17 種 / 柄 11 種 / 柄頭 13 種 / 地肌 49 種 (`SURFACES`)

元実装からの改良点
- 仕上げをこの mod の HD アイコン (art/hd.py) と同じにした: 外周の selout + 外側 1px の濃い縁 + 発光の光暈。
  既存の武器アイコンと並べても浮かない
- 解像度は 16 単位の論理座標で持ち、出力は 64px (任意)。1 画素 = 1 標本で、アンチエイリアスの濁りが無い
- 乱数 (地肌の種) は名前から決める。同じ名前なら同じ剣になる
- 色は 3 色 (刃・鍔・柄) と差し色だけ渡せば、刃縁・刃の芯・輪郭などの残りを色相を保って導出する
- 未知の輪郭・鍔・地肌は一覧付きの ValueError

使い方:
    img = swordgen.render(swordgen.options(silhouette="katana", surface="choji", blade_color=0xd8e4f0, ...))
CLI: `python tools/abyss.py swordgen list | render | sheet`
"""
from __future__ import annotations

import colorsys
import io
import json
import math
import os
import random
import zlib

from PIL import Image

from . import hd

SQRT2 = math.sqrt(2)
RGB = tuple[int, int, int]


def _clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def _sinpi(p):
    return math.sin(math.pi * p)


# ---------------------------------------------------------------- 色の小道具 (元実装の color.ts)

def shade(c, f) -> RGB:
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def mix(a, b, t) -> RGB:
    t = _clamp(t)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def hash_noise(x, y) -> float:
    """-0.5..0.5 の決定的な値 (元実装と同じビット演算)。"""
    m = 0xFFFFFFFF
    a = ((int(x) + 0x9e3779b9) * 0x85ebca6b) & m
    b = ((int(y) + 0xc2b2ae35) * 0x27d4eb2f) & m
    n = (a ^ b) & m
    n = (n ^ (n >> 15)) & m
    return (n % 1000) / 1000 - 0.5


def vnoise(x, y, seed=0) -> float:
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    ux, uy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)

    def h(a, b):
        return hash_noise(a * 31 + seed, b * 17 - seed) + 0.5
    a, b, c, d = h(ix, iy), h(ix + 1, iy), h(ix, iy + 1), h(ix + 1, iy + 1)
    return (a * (1 - ux) + b * ux) * (1 - uy) + (c * (1 - ux) + d * ux) * uy


def fbm(x, y, seed=0) -> float:
    return vnoise(x, y, seed) * 0.5 + vnoise(x * 2.1, y * 2.1, seed + 7) * 0.3 + vnoise(x * 4.3, y * 4.3, seed + 13) * 0.2


def _hx(v) -> RGB:
    if isinstance(v, str):
        v = int(v.lstrip("#"), 16)
    if isinstance(v, int):
        return ((v >> 16) & 255, (v >> 8) & 255, v & 255)
    return tuple(v[:3])


def _hls(c, dh=0.0, dl=0.0, ds=0.0) -> RGB:
    h, l, s = colorsys.rgb_to_hls(*(v / 255 for v in c))
    r, g, b = colorsys.hls_to_rgb((h + dh) % 1, _clamp(l + dl), _clamp(s + ds))
    return (int(r * 255 + 0.5), int(g * 255 + 0.5), int(b * 255 + 0.5))


# ---------------------------------------------------------------- 刃の輪郭 42 種

class Profile:
    def __init__(self, curve, width, taper, tip, edge_bias=0.0, scale=1.0):
        self.curve, self.width, self.taper, self.tip = curve, width, taper, tip
        self.edge_bias, self.scale = edge_bias, scale


P = Profile
PROFILES: dict[str, Profile] = {
    "straight": P(lambda p: 0, lambda p: 1, 0.66, "point"),
    "claymore": P(lambda p: 0, lambda p: 1 + 0.08 * _sinpi(p), 0.7, "point", scale=1.1),
    "zweihander": P(lambda p: 0, lambda p: 0.9 + 0.12 * (1 if p > 0.22 else 0), 0.72, "point", scale=1.05),
    "gladius": P(lambda p: 0, lambda p: 0.86 + 0.32 * _sinpi(min(1, p * 1.15)), 0.6, "point", scale=1.05),
    "dagger": P(lambda p: 0, lambda p: 1 - 0.4 * p, 0.55, "point", scale=0.95),
    "estoc": P(lambda p: 0, lambda p: 1, 0.5, "needle", scale=0.55),
    "rapier": P(lambda p: 0.1 * p * p, lambda p: 1, 0.8, "needle", scale=0.55),
    "katana": P(lambda p: 1.05 * p ** 1.5, lambda p: 1, 0.9, "clip", 0.25, 0.7),
    "nodachi": P(lambda p: 1.5 * p ** 1.4, lambda p: 1, 0.9, "clip", 0.25, 0.72),
    "tanto": P(lambda p: 0.35 * p * p, lambda p: 1, 0.82, "clip", 0.3, 0.75),
    "sabre": P(lambda p: 0.85 * p ** 1.7, lambda p: 1 - 0.1 * p, 0.78, "point", 0.2, 0.85),
    "scimitar": P(lambda p: 1.5 * p ** 1.6, lambda p: 0.78 + 0.5 * _sinpi(p), 0.7, "point", 0.2),
    "cutlass": P(lambda p: 0.9 * p ** 1.8, lambda p: 0.85 + 0.35 * _sinpi(p), 0.7, "clip", 0.2),
    "falchion": P(lambda p: 0.45 * p * p, lambda p: 0.75 + 0.75 * p ** 1.5, 0.82, "round", 0.25),
    "khopesh": P(lambda p: 0 if p < 0.45 else 2.4 * ((p - 0.45) / 0.55) ** 1.5,
                 lambda p: 0.62 if p < 0.45 else 0.8 + 0.5 * _sinpi((p - 0.45) / 0.55), 0.86, "hook", 0.2),
    "cleaver": P(lambda p: 0, lambda p: 1.1 + 0.6 * min(1, p * 2.2), 0.9, "square", 0.35, 1.05),
    "macuahuitl": P(lambda p: 0, lambda p: 1.15, 0.9, "square", scale=1.05),
    "flamberge": P(lambda p: math.sin(p * math.pi * 5) * _sinpi(p) * 0.5, lambda p: 1, 0.7, "point"),
    "kris": P(lambda p: math.sin(p * math.pi * 6) * (1 - p) * 0.45, lambda p: 1 - 0.35 * p, 0.6, "point", scale=0.8),
    "twinblade": P(lambda p: 0, lambda p: 1.3, 0.7, "point"),
    "spear": P(lambda p: 0, lambda p: 0.45 + 0.75 * _sinpi(min(1, p * 1.05)), 0.62, "point", scale=0.95),
    "glaive": P(lambda p: 0.6 * p * p, lambda p: 0.7 + 0.5 * _sinpi(p), 0.75, "point", 0.3),
    "jian": P(lambda p: 0, lambda p: 1 - 0.18 * p, 0.55, "point", scale=0.85),
    "whip": P(lambda p: 1.9 * p ** 2.2, lambda p: 1, 0.4, "point", scale=0.56),
    "liuyedao": P(lambda p: 1.05 * p ** 1.9, lambda p: 0.82 + 0.4 * _sinpi(p), 0.68, "clip", 0.24, 0.9),
    "uchigatana": P(lambda p: 1.3 * p ** 1.45, lambda p: 1, 0.9, "clip", 0.26, 0.72),
    "nine_ring": P(lambda p: 0.75 * p ** 1.8, lambda p: 0.9 + 0.3 * _sinpi(p), 0.72, "clip", 0.22, 0.92),
    "trident": P(lambda p: 0, lambda p: 0.35 if p < 0.25 else 1.6 if p < 0.85 else 0.6, 0.8, "point", scale=1.1),
    "battleaxe": P(lambda p: 0, lambda p: 0.25 if p < 0.3 else 0.5 + 1.8 * _sinpi((p - 0.3) / 0.7), 0.85, "square", -0.4, 1.25),
    "warpick": P(lambda p: 0, lambda p: 0.25 if p < 0.4 else 0.4 + 1.4 * ((p - 0.4) / 0.6), 0.85, "needle", -0.6, 1.0),
    "mace": P(lambda p: 0, lambda p: 0.28 if p < 0.4 else 1.75 if p < 0.88 else 0.4, 0.9, "square", scale=1.25),
    "bow": P(lambda p: math.sin(p * math.pi) * 2.2, lambda p: 0.55, 0.8, "point", scale=0.8),
    "pickaxe": P(lambda p: 0, lambda p: 0.25 if p < 0.6 else 0.4 + 1.6 * _sinpi((p - 0.6) / 0.4), 0.9, "needle", -0.5, 1.2),
    # 型に囚われない形
    "essence": P(lambda p: 0, lambda p: 0.2 + 1.55 * math.sin(math.pi * min(1, p * 1.02)) ** 0.62, 0.94, "needle", scale=0.78),
    "lotus": P(lambda p: 0.22 * p * p, lambda p: 0.52 if p < 0.58 else 0.52 + 1.35 * ((p - 0.58) / 0.42) ** 0.8, 0.95, "round", 0.1, 0.92),
    "geode": P(lambda p: 0, lambda p: 0.62 + 0.95 * math.floor(math.sin(p * math.pi * 3.1) * 0.5 + 0.55), 0.92, "point", scale=1.05),
    "ribbon": P(lambda p: math.sin(p * math.pi * 2.0) * 0.88, lambda p: 0.98 - 0.14 * p, 0.68, "point", scale=0.7),
    "spine": P(lambda p: 0, lambda p: 0.78 + 0.62 * (1 if math.sin(p * math.pi * 8.5) > 0.3 else 0), 0.88, "point"),
    "fractured": P(lambda p: 0, lambda p: 0.22 if (0.42 < p < 0.46) or (0.74 < p < 0.78) else 1.05, 0.9, "point", scale=1.02),
    "obelisk": P(lambda p: 0, lambda p: 1.3 - 0.42 * p, 0.96, "square", scale=1.32),
    "fang": P(lambda p: 2.5 * p ** 2.15, lambda p: 1.05 - 0.35 * p, 0.7, "hook", 0.28, 0.95),
    "sakura": P(lambda p: 0.85 * p ** 1.55, lambda p: 0.72 + (1.25 * ((p - 0.78) / 0.22) if p > 0.78 else 0), 0.95, "round", 0.18, 0.84),
}
del P

GUARDS = ("straight", "wings", "cross", "round", "crescent", "rapier", "jagged", "barbed", "dread", "tsuba",
          "swept", "shell", "hooked", "jian", "sigil", "asymmetric", "none")
HANDLES = ("smooth", "leather", "wire", "dragon_scale", "gold_ribbon", "bone", "wrapped_parchment", "ribbed",
           "cord", "studded", "chain")
POMMELS = ("round", "gem", "crescent", "skull", "ring", "fang", "spiked", "orb", "wing", "disc", "tassel",
           "chain", "flattened")


# ---------------------------------------------------------------- 設定 (元実装の SwordOptions と同じ 59 項目)

with io.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "swordgen_data.json"), encoding="utf-8") as _f:
    DATA = json.load(_f)
ELEMENTS: dict = DATA["elements"]            # 属性 12 種: [主色, 光の色]
EFFECT_PRESETS: dict = DATA["effect_presets"]  # 演出プリセット 14 種
PRESETS: dict = DATA["presets"]              # 名前付きの剣 65 種
PALETTE_KITS: list = DATA["palette_kits"]    # 配色キット 26 種
THEMES: list = DATA["themes"]                # 生成テーマ 18 種
BLADE_PROFILES: dict = DATA["blade_profiles"]  # 刀身ごとの寸法・相性 (鍔・柄・細身か)
DEFAULTS: dict = DATA["defaults"]
GEM_SHAPES = ("diamond", "circle", "teardrop", "hex", "marquise", "star")
DETAIL_LEVELS = ("minecraft", "rich", "cinematic", "anime")
OUTLINE_MODES = ("dark", "tinted", "gold", "none")
FINISHES = ("hd", "original")  # hd = この mod の HD アイコンの仕上げ / original = 元実装の方向別輪郭
SLIM_FORMS = {"ribbon", "whip", "essence", "sakura", "estoc", "rapier", "bow", "tanto", "spear"}

# この mod 側で使う短い名前 → 元実装の名前
ALIASES = {
    "blade_length": "bladeLength", "handle_length": "handleLength", "blade_width": "bladeWidth",
    "guard_width": "guardWidth", "guard": "guardStyle", "handle": "handleStyle", "pommel": "pommelStyle",
    "spur_count": "spurCount", "bone_spurs": "boneSpurs", "crystal_shards": "crystalShards",
    "fuller_length": "fullerLength", "edge_highlight": "edgeHighlight", "rune_inlay": "runeInlay",
    "gradient_blade": "gradientBlade", "edge_gradient": "edgeGradient", "pommel_band": "pommelBand",
    "bevel_line": "bevelLine", "tip_glow": "tipGlow", "gem_shape": "gemShape", "gem_glow": "gemGlow",
    "gem_color": "gemColor", "rune_color": "runeColor", "shard_color": "shardColor", "tip_color": "tipColor",
    "mid_color": "midGradientColor", "element_intensity": "elementIntensity", "effect_preset": "effectPreset",
    "sheen_speed": "sheenSpeed", "sheen_pos": "sheenPos", "sparkle_density": "sparkleDensity",
    "aura_bloom": "auraBloom", "rune_pulse": "runePulse", "detail_level": "detailLevel",
    "outline_mode": "outlineColorMode", "anti_alias": "antiAlias",
}
# 4 色指定 (刃・鍔・柄・差し色) から残りを導出する入口
COLOR_KEYS = ("blade_color", "guard_color", "handle_color", "accent")


def _hex(c) -> str:
    return "#%02x%02x%02x" % tuple(c[:3])


def palette_from(blade, guard=0xc9a24a, handle=0x4a3226, accent=0x7ce8ff) -> dict:
    """4 色から元実装の 10 色パレットを作る (色相を保って明暗だけ振る)。"""
    b, g, h, a = (_hx(v) for v in (blade, guard, handle, accent))
    return {"blade": _hex(b), "bladeEdge": _hex(_hls(b, 0, 0.26, -0.15)), "bladeCore": _hex(_hls(b, 0.01, -0.2, 0.05)),
            "guard": _hex(g), "guardAccent": _hex(_hls(g, 0, 0.22, 0.1)), "handle": _hex(h),
            "handleAccent": _hex(_hls(h, 0, 0.16, 0.05)), "pommel": _hex(_hls(g, 0, 0.05, 0.05)),
            "outline": _hex(_hls(b, 0, -0.45, 0)), "spur": _hex(_hls(b, 0, 0.12, 0)), "_accent": _hex(a)}


def _apply_form_rules(o: dict) -> dict:
    """細身の刀身には重い装飾を載せない (元実装の applyFormRules)。"""
    slim = o["silhouette"] in SLIM_FORMS or BLADE_PROFILES.get(o["silhouette"], {}).get("slim")
    if slim:
        o.update(particles=False, shatter=False, spurs=False, boneSpurs=False, lightning=False)
        if o["bladeWidth"] <= 1:
            o.update(fuller=False, horimono=False, runeInlay=False)
    if o["silhouette"] in ("obelisk", "mace"):
        o["horimono"] = False
    if o["silhouette"] == "fractured":
        o["shatter"] = False
    return o


def options(**kw) -> dict:
    """剣の設定。元実装の名前 (bladeWidth…) でもこの mod の短い名前 (blade_width…) でも書ける。
    4 色 (blade_color / guard_color / handle_color / accent) を渡すと 10 色パレットを導出する。
    effect_preset を渡すとその演出の既定値を先に敷く (個別の指定が優先)。"""
    o = json.loads(json.dumps(DEFAULTS))
    o["finish"] = "hd"
    cols = {k: kw.pop(k) for k in COLOR_KEYS if k in kw}
    kw = {ALIASES.get(k, k): v for k, v in kw.items()}
    fx = kw.get("effectPreset")
    if fx is not None:
        if fx not in EFFECT_PRESETS:
            raise ValueError(f"未知の演出プリセット '{fx}'。使えるもの: {', '.join(EFFECT_PRESETS)}")
        o.update(EFFECT_PRESETS[fx])
    unknown = set(kw) - set(o)
    if unknown:
        raise ValueError(f"未知の設定 {sorted(unknown)}")
    if "palette" in kw:
        o["palette"].update(kw.pop("palette"))
    o.update(kw)
    if cols:
        pal = palette_from(cols.get("blade_color", o["palette"]["blade"]), cols.get("guard_color", 0xc9a24a),
                           cols.get("handle_color", 0x4a3226), cols.get("accent", 0x7ce8ff))
        acc = pal.pop("_accent")
        o["palette"] = pal
        for k in ("runeColor", "gemColor", "shardColor", "tipColor"):
            if k not in kw:
                o[k] = acc
        if "midGradientColor" not in kw:
            o["midGradientColor"] = pal["blade"]
    for key, table in (("silhouette", PROFILES), ("guardStyle", GUARDS), ("handleStyle", HANDLES),
                       ("pommelStyle", POMMELS), ("surface", SURFACES), ("gemShape", GEM_SHAPES),
                       ("element", ELEMENTS), ("detailLevel", DETAIL_LEVELS), ("outlineColorMode", OUTLINE_MODES),
                       ("finish", FINISHES)):
        if o[key] not in table:
            raise ValueError(f"未知の {key} '{o[key]}'。使えるもの: {', '.join(table)}")
    return o


def preset(name: str, **over) -> dict:
    """名前付きの剣 (PRESETS) を設定にする (元実装の optionsForPreset と同じ手順)。"""
    if name not in PRESETS:
        raise ValueError(f"未知のプリセット '{name}'。使えるもの: {', '.join(PRESETS)}")
    p = PRESETS[name]
    fx = dict(EFFECT_PRESETS.get(p["effectPreset"], {}))
    base = dict(runeInlay=False, crystalShards=False, spurs=False, boneSpurs=False, fuller=False, surface="polished",
                filigree=False, runePulse=False, gemGlow=False)
    base.update(fx)
    base.update(palette=p["palette"], element=p["element"], elementIntensity=fx.get("elementIntensity", 0.35),
                tipColor=p["tipColor"], midGradientColor=p["midGradientColor"], gemColor=p["gemColor"],
                runeColor=p["runeColor"], shardColor=p["shardColor"], effectPreset=p["effectPreset"])
    style = p.get("style", {})
    base.update(style)
    prof = BLADE_PROFILES[base.get("silhouette", DEFAULTS["silhouette"])]
    base["bladeWidth"] = style.get("bladeWidth", prof["width"][0])
    base["bladeLength"] = style.get("bladeLength", prof["length"][0])
    base["guardWidth"] = style.get("guardWidth", prof["guard"])
    base["handleLength"] = style.get("handleLength", prof["handle"])
    base["guardStyle"] = style.get("guardStyle", prof["guards"][0])
    base["handleStyle"] = style.get("handleStyle", prof["grips"][0])
    base.update({ALIASES.get(k, k): v for k, v in over.items()})
    eff = base.pop("effectPreset")
    o = options(**base)
    o["effectPreset"] = eff
    if o["runeInlay"]:
        o["fuller"] = False
    if o["boneSpurs"]:
        o["spurs"] = o["crystalShards"] = False
    if o["spurs"]:
        o["crystalShards"] = False
    o["runePulse"] = o["runeInlay"]
    edge, core = _hx(o["palette"]["bladeEdge"]), _hx(o["palette"]["bladeCore"])
    if sum(edge) - sum(core) < 120:
        o["palette"]["bladeEdge"] = _hex(mix(_hx(o["palette"]["blade"]), (238, 231, 246), 0.68))
    return _apply_form_rules(o)


def generate(theme: str | None = None, seed: int | str = 0) -> dict:
    """テーマ (THEMES) の語彙から剣を 1 本組む (元実装の generateSword の自動選択部分)。同じ種なら同じ剣。"""
    rnd = random.Random(zlib.crc32(str(seed).encode()))
    th = next((t for t in THEMES if t["id"] == theme), None) if theme else rnd.choice(THEMES)
    if th is None:
        raise ValueError(f"未知のテーマ '{theme}'。使えるもの: {', '.join(t['id'] for t in THEMES)}")
    sil = rnd.choice(th["blades"])
    prof = BLADE_PROFILES[sil]
    kit = next((k for k in PALETTE_KITS if k["id"] == rnd.choice(th["palettes"])), PALETTE_KITS[0])
    guards = [g for g in th["guards"] if g in prof["guards"]] or prof["guards"]
    grips = [g for g in th["grips"] if g in prof["grips"]] or prof["grips"]
    surfaces = [s for s in th["surfaces"] if s in kit["surfaces"]] or th["surfaces"]
    element = rnd.choice(th["elements"])
    fx = next((k for k, v in EFFECT_PRESETS.items() if v.get("element") == element), "calm")
    o = options(silhouette=sil, palette=kit["palette"], element=element, effectPreset=fx,
                bladeWidth=rnd.randint(prof["width"][0], prof["width"][1]),
                bladeLength=rnd.randint(prof["length"][0], prof["length"][1]),
                guardWidth=prof["guard"], handleLength=prof["handle"], guardStyle=rnd.choice(guards),
                handleStyle=rnd.choice(grips), pommelStyle=rnd.choice(th["pommels"]), surface=rnd.choice(surfaces),
                runeColor=kit["accent"], gemColor=kit["accent"], shardColor=kit["accent"], tipColor=kit["accent"],
                midGradientColor=kit["palette"]["blade"], gemShape=rnd.choice(GEM_SHAPES),
                spurs=rnd.random() < 0.15, crystalShards=rnd.random() < 0.15, runeInlay=rnd.random() < 0.3,
                seed=rnd.randrange(1 << 30))
    o["surfaceSeed"] = o["effectSeed"] = o["seed"]
    return _apply_form_rules(o)


def palette(o: dict) -> dict[str, RGB]:
    p = {k: _hx(v) for k, v in o["palette"].items()}
    p["accent"] = _hx(o["runeColor"])
    p["gem"] = _hx(o["gemColor"])
    p["shard"] = _hx(o["shardColor"])
    p["tip"] = _hx(o["tipColor"])
    p["mid"] = _hx(o["midGradientColor"])
    el = ELEMENTS[o["element"] if o["element"] != "none" else "flame"]
    p["glow"] = tuple(el[1])
    p["element"] = tuple(ELEMENTS[o["element"]][0])
    return p


# ---------------------------------------------------------------- 形 (元実装の geometry.ts)

def _in_triangle(x, y, a, b, c) -> bool:
    def side(p, q):
        return (x - q[0]) * (p[1] - q[1]) - (p[0] - q[0]) * (y - q[1])
    s1, s2, s3 = side(a, b), side(b, c), side(c, a)
    return not ((s1 < 0 or s2 < 0 or s3 < 0) and (s1 > 0 or s2 > 0 or s3 > 0))


class Geometry:
    """部位の判定。sample(x, y) -> (部位, across, progress, d, v) / None。x, y は 0..16 の論理座標。"""

    def __init__(self, o: dict):
        self.o = o
        self.prof = PROFILES[o["silhouette"]]
        unit = 12 / max(14, o["bladeLength"] + o["handleLength"] + 1.2)
        self.pommel_d = 1.65
        self.handle_start = 2.05
        self.guard_d = self.handle_start + o["handleLength"] * unit
        self.blade_start = self.guard_d
        self.blade_end = self.blade_start + o["bladeLength"] * unit
        self.half_width = (0.38 + o["bladeWidth"] * 0.4) * self.prof.scale
        self.grip_width = 0.38 + o["handleLength"] * 0.035
        self.guard_length = min(0.9 + o["guardWidth"] * 0.43, (self.guard_d - 0.7) * SQRT2)
        self.bias = self.prof.edge_bias
        self.spikes = []
        if o["spurs"] or o["boneSpurs"]:
            n = o["spurCount"]
            for i in range(n):
                t = 0.2 + (i + 0.5) / n * 0.55
                d = self.blade_start + (self.blade_end - self.blade_start) * t
                for side in ((1,) if o["boneSpurs"] else (-1, 1)):
                    root = self.center(t) + self.width(t) * side * 0.5
                    reach = (1.3 if o["boneSpurs"] else 0.9) * side
                    lean = 0.55 if o["boneSpurs"] else 0.2
                    self.spikes.append((side, (d - 0.42, root), (d + 0.42, root), (d + lean, root + reach)))
        if o["silhouette"] == "zweihander":
            t = 0.16
            d = self.blade_start + (self.blade_end - self.blade_start) * t
            w = self.width(t)
            for side in (-1, 1):
                self.spikes.append((side, (d - 0.35, w * side * 0.6), (d + 0.35, w * side * 0.6),
                                    (d + 0.15, (w + 0.75) * side)))

    def center(self, t):
        return self.prof.curve(_clamp(t))

    def width(self, t):
        p = _clamp(t)
        w = self.half_width * self.prof.width(p)
        if p > self.prof.taper:
            k = (p - self.prof.taper) / (1 - self.prof.taper)
            tip = self.prof.tip
            if tip == "needle":
                w *= max(0, 1 - k) ** 0.55
            elif tip == "clip":
                w *= 1 - k * 0.15 if k < 0.6 else max(0, 1 - (k - 0.6) / 0.4) ** 0.8 * 0.91
            elif tip == "round":
                w *= math.sqrt(max(0, 1 - k * k))
            elif tip == "square":
                w *= 1 if k < 0.85 else max(0, 1 - (k - 0.85) / 0.15) ** 0.5
            elif tip == "hook":
                w *= max(0, 1 - k) ** 0.6
            else:
                w *= max(0, 1 - k) ** 0.75
        return max(0.05, w)

    def sample(self, x, y):
        o = self.o
        d = (x - y + 16) / 2
        v = (x + y - 16) / SQRT2
        t = (d - self.blade_start) / (self.blade_end - self.blade_start)
        vr = v - self.center(t)

        def surf(part, across, progress=t):
            return (part, _clamp(across, -1, 1), progress, d, v)

        gd, gl = self.guard_d, self.guard_length
        vn = v / gl
        gs = o["guardStyle"]
        guard = abs(d - gd) <= 0.3 and abs(v) <= max(self.grip_width + 0.15, self.width(0.02) + 0.1)
        in_bar = abs(vn) <= 1
        if gs == "none":
            guard = False
        elif gs == "straight":
            guard = guard or (in_bar and abs(d - gd) <= 0.28)
        elif gs in ("wings", "barbed", "dread"):
            guard = guard or (in_bar and abs(d - gd - 0.5 * vn * vn) <= 0.27)
        elif gs == "swept":
            guard = guard or (in_bar and abs(d - gd + 0.55 * vn * vn) <= 0.27)
        elif gs == "crescent":
            guard = guard or (in_bar and abs(d - gd + 0.45 * (1 - vn * vn) - 0.25) <= 0.26)
        elif gs == "cross":
            guard = guard or (in_bar and abs(d - gd) <= 0.28) or (abs(d - gd) < 0.75 and abs(v) < 0.34)
        elif gs == "round":
            guard = guard or ((d - gd) / 0.5) ** 2 + (vn / 0.82) ** 2 < 1
        elif gs == "tsuba":
            r = math.hypot((d - gd) / 0.42, vn / 0.85)
            guard = guard or (r < 1 and not (r < 0.62 and abs(math.sin(math.atan2(vn, d - gd) * 2)) > 0.72))
        elif gs == "shell":
            guard = guard or (-0.3 < d - gd < 0.95 and abs(v) < gl * 0.7 * math.sqrt(max(0, 1 - ((d - gd - 0.32) / 0.63) ** 2)))
        elif gs == "rapier":
            ring = math.hypot((d - gd + 0.35) / 1.0, v / (gl * 0.75))
            guard = guard or (1 > ring > 0.62) or (in_bar and abs(d - gd) <= 0.26)
        elif gs == "hooked":
            hook = 0.6 * (abs(vn) - 0.7) / 0.3 if abs(vn) > 0.7 else 0
            guard = guard or (in_bar and abs(d - gd - hook) <= 0.28)
        elif gs == "jian":
            lobe = max(0, 1 - abs(abs(vn) - 0.62) / 0.38)
            guard = guard or (in_bar and -0.35 < d - gd < 0.1 + lobe * 0.42)
        elif gs == "sigil":
            bar = in_bar and abs(d - gd) <= 0.2
            notch = 0.3 < abs(vn) < 0.62 and abs(d - gd) < 0.62
            tip = abs(vn) > 0.86 and abs(d - gd) < 0.3
            guard = guard or bar or notch or tip
        elif gs == "asymmetric":
            up = -1.5 < vn < -0.1 and -0.3 < d - gd < 0.1 + (1 + vn) * 0.5
            down = 0.05 < vn < 1.1 and -0.3 < d - gd < 0.75 - vn * 0.55
            guard = guard or up or down
        if gs in ("jagged", "barbed", "dread"):
            tooth = max(0, math.sin(v * 4.2 + 1)) * 0.3
            guard = guard or (in_bar and -0.28 < d - gd < 0.25 + tooth + (0.25 * vn * vn if gs == "dread" else 0))
        if guard:
            return surf("guard", vn, 0)

        grip_t = (d - self.handle_start) / (gd - self.handle_start)
        if -0.02 <= grip_t <= 1 and abs(v) < self.grip_width * (0.9 + 0.12 * math.sin(grip_t * math.pi)):
            return surf("handle", v / self.grip_width, _clamp(grip_t))

        pd = (d - self.pommel_d) * SQRT2
        radius = 0.66
        dist = math.hypot(pd, v)
        ps = o["pommelStyle"]
        pommel = dist < radius
        if ps == "ring":
            pommel = dist < radius * 1.1 and (dist > radius * 0.45 or pd > 0.1)
        elif ps == "crescent":
            pommel = dist < radius * 1.1 and math.hypot(pd + 0.45, v) > radius * 0.85
        elif ps == "fang":
            pommel = -1.15 < pd < 0.45 and abs(v) < 0.2 + 0.42 * (pd + 1.15) / 1.6
        elif ps == "spiked":
            pommel = dist < radius * (0.72 + 0.32 * max(0, math.cos(math.atan2(v, pd) * 4)))
        elif ps == "skull":
            pommel = abs(pd) < 0.7 and abs(v) < (0.36 if pd < -0.25 else 0.66)
        elif ps == "orb":
            pommel = dist < radius * 1.15
        elif ps == "wing":
            pommel = dist < radius * 0.7 or (-0.9 < pd < 0.3 and 0.25 < abs(v) < 0.5 + 0.55 * (0.3 - pd) / 1.2)
        elif ps == "disc":
            pommel = abs(pd) < 0.33 and abs(v) < 0.85
        elif ps == "tassel":
            pommel = math.hypot(pd, v) < radius * 0.75 or (-1.7 < pd < -0.15 and abs(v) < 0.54 - 0.1 * ((-0.15 - pd) / 1.55))
        elif ps == "chain":
            pommel = math.hypot(pd, v) < radius * 0.72 or (-1.9 < pd < -0.2 and abs(v) < 0.5 - 0.22 * abs(math.sin((pd + 0.2) * 4.2)))
        elif ps == "flattened":
            pommel = abs(pd) < 0.42 and abs(v) < 0.5 + 0.32 * max(0, math.cos(pd * 3))
        if pommel:
            return surf("pommel", v / radius, 0)

        for side, a, b, c in self.spikes:
            if _in_triangle(d, v, a, b, c):
                return surf("spur", side * 0.6)
        if t < 0 or t > 1:
            return None

        sil = o["silhouette"]
        w = self.width(t)
        if self.bias > 0 and vr > 0:
            w *= 1 - self.bias
        if sil == "glaive" and 0.35 < t < 0.92 and vr > 0:
            w += 0.9 * _sinpi((t - 0.35) / 0.57) * self.half_width
        if sil == "trident" and 0.35 < t < 0.78 and 0.32 < abs(vr) < 0.7:
            return None
        if sil == "battleaxe" and 0.32 < t < 0.88 and vr > 0.28 and \
                abs(d - (self.blade_start + (self.blade_end - self.blade_start) * 0.55)) < 0.35:
            return None
        if sil == "macuahuitl":
            teeth = abs(math.sin(t * math.pi * 6.5))
            if abs(vr) > w * 0.6:
                return surf("shard", 0.6 if vr > 0 else -0.6) if teeth > 0.3 and abs(vr) < w * (0.6 + 0.55 * teeth) else None
        if o["serrated"] and 0.15 < t < 0.85 and vr < 0:
            w *= 0.84 + max(0, math.sin(t * math.pi * 16)) * 0.16
        if o["crystalShards"] and 0.2 < t < 0.72:
            for ct in (0.3, 0.52, 0.68):
                cd = self.blade_start + (self.blade_end - self.blade_start) * ct
                cv = self.center(ct) + self.width(ct) * 0.35
                if abs((d - cd) * 1.5) + abs(v - cv) * 1.1 < 0.62:
                    return surf("shard", (v - cv) / 0.55)
        if abs(vr) > w:
            return None
        if sil == "twinblade" and 0.14 < t < 0.95 and abs(vr) < 0.22 + t * 0.12:
            return None
        across = vr / w
        part = "edge" if across < -0.78 else "bevel" if across > 0.78 and o["bevelLine"] else "blade"
        return surf(part, across)


# ---------------------------------------------------------------- 地肌 49 種 (元実装の surfaces.ts)

def _cells(d, a, size, seed):
    return hash_noise(math.floor(d / size) + seed, math.floor(a / size) * 7)


def _frac(v):
    return ((v % 1) + 1) % 1


def _jsmod(a, b):
    """JS の % (符号は被除数に従う)。"""
    return math.fmod(a, b)


def _surfaces():
    S = {}
    S["polished"] = lambda col, s, c: col

    def damascus(col, s, c):
        if not c["fine"]:
            return shade(col, c["shading"] * 0.5)
        wave = math.sin(s.d * 5.5 + 2.4 * math.sin(s.across * 2.8 + s.d * 1.3) + vnoise(s.d * 1.5, s.across, c["seed"]) * 3)
        return shade(col, 0.84) if wave > 0.55 else mix(col, c["edge"], 0.18) if wave < -0.7 else col
    S["damascus"] = damascus

    def hamon(col, s, c):
        line = -0.25 + math.sin(s.d * 3.1 + c["seed"] * 0.01) * 0.12 + vnoise(s.d * 2, 0, c["seed"]) * 0.16
        if s.across < line:
            return mix(col, c["edge"], 0.42 + 0.2 * max(0, line - s.across))
        return mix(col, c["edge"], 0.8) if abs(s.across - line) < 0.08 else col
    S["hamon"] = hamon

    def obsidian(col, s, c):
        base = shade(col, 0.72)
        if not c["fine"]:
            return base
        chip = _cells(s.d * 1.2, s.across * 1.6, 0.9, c["seed"])
        if chip > 0.36:
            return mix(base, c["edge"], 0.32)
        if chip < -0.38:
            return shade(base, 0.72)
        return mix(base, c["glow"], max(0, math.sin(s.d * 9 + s.across * 5)) * 0.08)
    S["obsidian"] = obsidian

    def crystal(col, s, c):
        if not c["fine"]:
            return col
        facet = math.floor(s.d * 1.6 + s.across * 1.1) + math.floor(s.d * 0.9 - s.across * 1.7)
        k = facet % 3
        out = mix(col, c["edge"], 0.3) if k == 0 else col if k == 1 else shade(col, 0.82)
        return mix(out, c["edge"], 0.5) if abs(_jsmod(s.d * 1.6 + s.across * 1.1, 1)) < 0.08 else out
    S["crystal"] = crystal

    def bone(col, s, c):
        warm = mix(col, (236, 226, 204), 0.4)
        if not c["fine"]:
            return shade(warm, c["shading"] * 0.4)
        pore = fbm(s.d * 3, s.across * 2.5, c["seed"])
        crack = abs(math.sin(s.d * 2.3 + vnoise(s.d, s.across * 2, c["seed"]) * 6)) < 0.04
        return shade(warm, 0.62) if crack else shade(warm, 0.9 + pore * 0.18)
    S["bone"] = bone

    def wood(col, s, c):
        if not c["fine"]:
            return col
        grain = math.sin((s.across * 2.4 + vnoise(s.d * 0.7, s.across, c["seed"]) * 3 + s.d * 0.25) * 6)
        w = mix(col, (140, 96, 62), 0.45)
        return shade(w, 0.78) if grain > 0.6 else shade(w, 0.9) if grain > 0.2 else shade(w, 1.05)
    S["wood"] = wood

    def scales(col, s, c):
        if not c["fine"]:
            return col
        freq = 2.6
        row = math.floor(s.d * freq)
        off = 0.5 if row % 2 else 0
        fx, fy = _frac(s.d * freq), _frac(s.across * 1.4 + off)
        r = math.hypot(fx - 0.5, (fy - 0.5) * 0.9)
        return shade(col, 0.72) if r > 0.46 else mix(col, c["edge"], 0.22) if r < 0.2 else col
    S["scales"] = scales

    def magma(col, s, c):
        n = fbm(s.d * (1.7 if c["fine"] else 1.1), s.across * 1.3, c["seed"])
        crust = shade(mix(col, (48, 34, 36), 0.5), 0.94 + vnoise(s.d * 3, s.across * 2.5, c["seed"]) * 0.12)
        if abs(n - 0.5) < 0.028:
            return mix(c["glow"], (255, 236, 190), 0.2)
        return mix(crust, c["glow"], 0.4) if abs(n - 0.5) < 0.06 else crust
    S["magma"] = magma

    def frost(col, s, c):
        pale = mix(col, (228, 244, 250), 0.32)
        if not c["fine"]:
            return pale
        fern = abs(math.sin(s.d * 7 + s.across * 9)) * abs(math.sin(s.d * 2.2 - s.across * 4 + vnoise(s.d, s.across, c["seed"]) * 4))
        return mix(pale, (255, 255, 255), 0.55) if fern > 0.86 else shade(pale, 0.86) if fern < 0.08 else pale
    S["frost"] = frost

    def hammered(col, s, c):
        if not c["fine"]:
            return col
        size = 0.7
        fx, fy = _frac(s.d / size) - 0.5, _frac(s.across * 1.2 / size) - 0.5
        r = math.hypot(fx, fy) + _cells(s.d, s.across, size, c["seed"]) * 0.15
        return shade(col, 0.86) if r < 0.16 else col if r < 0.3 else mix(col, c["edge"], 0.12)
    S["hammered"] = hammered

    def etched(col, s, c):
        if abs(s.across) >= 0.55:
            return col
        curl = math.sin(s.d * (4.5 if c["fine"] else 2.4)) * 0.28
        line = abs(s.across - curl) < 0.07 or (c["fine"] and abs(s.across + curl) < 0.07)
        dot = c["fine"] and abs(_jsmod(s.d * 2.25, 1) - 0.5) < 0.08 and abs(s.across) < 0.08
        return shade(mix(col, c["core"], 0.5), 0.8) if line or dot else col
    S["etched"] = etched

    def worn(col, s, c):
        out = shade(col, 0.88) if vnoise(s.d * 2.2, s.across * 2, c["seed"]) > 0.68 else col
        if c["fine"]:
            scratch = abs(hash_noise(math.floor(s.d * 9 + s.across * 3) + c["seed"], 3)) > 0.44 and \
                abs(math.sin(s.across * 11 + s.d * 40)) > 0.97
            chip = s.across < -0.72 and _cells(s.d, 0, 0.55, c["seed"]) > 0.32
            if scratch:
                out = mix(out, c["edge"], 0.4)
            if chip:
                out = shade(out, 0.7)
        return out
    S["worn"] = worn

    def banded(col, s, c):
        if not c["fine"]:
            return col
        b = math.sin(s.d * 3.4 + vnoise(s.d, s.across * 3, c["seed"]) * 1.5)
        return shade(col, 0.76) if b > 0.5 else mix(col, c["edge"], 0.16) if b < -0.6 else col
    S["banded"] = banded

    def gilded(col, s, c):
        if not c["fine"]:
            return col
        band = abs(_jsmod(s.d * 1.3, 1) - 0.5) < 0.07 and abs(s.across) < 0.72
        ridge = abs(s.across + 0.15) < 0.06 and math.sin(s.d * 6) > -0.2
        return mix((226, 184, 96), c["edge"], 0.15 if band else 0.35) if band or ridge else col
    S["gilded"] = gilded

    def circuit(col, s, c):
        if not c["fine"]:
            return col
        fq, fa = 2.2, 2
        gx, gy = math.floor(s.d * fq), math.floor(s.across * fa + 2)
        h = hash_noise(gx + c["seed"], gy)
        fx, fy = _frac(s.d * fq), _frac(s.across * fa + 2)
        trace = abs(fy - 0.5) < 0.09 if h > 0 else abs(fx - 0.5) < 0.09
        if math.hypot(fx - 0.5, fy - 0.5) < 0.14:
            return mix(c["glow"], (255, 255, 255), 0.3)
        return mix(col, c["accent"], 0.55) if trace else shade(col, 0.9)
    S["circuit"] = circuit

    def starfield(col, s, c):
        if not c["fine"]:
            return col
        deep = mix(col, c["core"], 0.55)
        haze = mix(deep, c["accent"], max(0, vnoise(s.d * 0.8, s.across, c["seed"]) - 0.55) * 0.9)
        star = hash_noise(math.floor(s.d * 6) + c["seed"], math.floor(s.across * 5)) > 0.44
        return mix(haze, (255, 255, 255), 0.75) if star else haze
    S["starfield"] = starfield

    def prismarine(col, s, c):
        if not c["fine"]:
            return mix(col, (98, 168, 160), 0.2)
        size = 0.85
        fx, fy = _frac(s.d / size), _frac(s.across * 1.3 / size)
        tile = mix(col, (98, 168, 160), 0.3)
        if fx < 0.12 or fy < 0.12:
            return shade(tile, 0.68)
        return shade(tile, 1 + _cells(s.d, s.across * 1.3, size, c["seed"]) * 0.16)
    S["prismarine"] = prismarine

    def amethyst(col, s, c):
        violet = mix(col, (150, 110, 205), 0.35)
        if not c["fine"]:
            return mix(col, (150, 110, 205), 0.3)
        k = math.floor(s.d * 2.4 + s.across * 0.8) % 4
        out = (mix(violet, c["edge"], 0.35), violet, shade(violet, 0.8), mix(violet, c["accent"], 0.3))[k]
        return shade(out, 0.6) if abs(_jsmod(s.d * 2.4 + s.across * 0.8, 1)) < 0.07 else out
    S["amethyst"] = amethyst

    def ender(col, s, c):
        dark = mix(col, (22, 18, 30), 0.6)
        if not c["fine"]:
            return dark
        if hash_noise(math.floor(s.d * 5) + c["seed"], math.floor(s.across * 4)) > 0.4:
            return mix(dark, (190, 140, 255), 0.7)
        return mix(dark, c["accent"], 0.25) if math.sin(s.d * 2 + vnoise(s.d, s.across, c["seed"]) * 5) > 0.7 else dark
    S["ender"] = ender

    def vanilla(col, s, c):
        t = s.across
        return shade(col, 1.3) if t < -0.7 else shade(col, 1.15) if t < -0.5 else shade(col, 0.6) if t > 0.7 \
            else shade(col, 0.75) if t > 0.5 else col
    S["vanilla"] = vanilla

    def bevel(col, s, c):
        hl = math.exp(-((s.across + 0.7) / 0.16) ** 2)
        sheen = math.exp(-((s.across + 0.32) / 0.08) ** 2) * 0.55
        sh = math.exp(-((s.across - 0.55) / 0.22) ** 2)
        out = mix(col, shade(col, 1.35), hl * 0.8)
        out = mix(out, shade(col, 1.1), sheen)
        return mix(out, shade(col, 0.6), sh)
    S["bevel"] = bevel

    def ifire(col, s, c):
        dark = shade(col, 0.78)
        if not c["fine"]:
            return dark
        bands = abs(math.sin(s.d * 4 + vnoise(s.d * 2, 0, c["seed"]) * 3)) > 0.94
        spine = abs(s.across) < 0.18 and _jsmod(s.d * 8, 1) < 0.4
        glow = mix(c["glow"], (180, 230, 255), 0.4) if bands or spine else dark
        return mix(glow, dark, 0.6 if bands else 0)
    S["ifire"] = ifire

    def stormforged(col, s, c):
        if not c["fine"]:
            return shade(col, 0.86)
        bolt = abs(math.sin(s.d * 9 + vnoise(s.d * 3, s.across, c["seed"]) * 4)) > 0.96
        fork = abs(s.across - math.sin(s.d * 3) * 0.4) < 0.07 and math.sin(s.d * 2.3) > 0.7
        return mix(c["glow"], (240, 245, 255), 0.5) if bolt or fork else shade(col, 0.86)
    S["stormforged"] = stormforged

    def skyblock(col, s, c):
        sheen = math.exp(-((s.across + 0.45) / 0.12) ** 2)
        pulse = 0.6 if _jsmod(s.d * 5.5, 1) < 0.05 and abs(s.across) < 0.4 else 0
        return mix(mix(col, shade(col, 1.5), sheen * 0.7), c["accent"], pulse)
    S["skyblock"] = skyblock

    def faithful(col, s, c):
        t = _clamp(s.across, -1, 1)
        return mix(col, c["edge"], 0.5) if t < -0.6 else col if t < 0.3 else shade(col, 0.7)
    S["faithful"] = faithful
    S["warmith"] = lambda col, s, c: mix(col, shade(col, 1.4), math.exp(-((s.across + 0.5) / 0.18) ** 2) * 0.6)

    def jade(col, s, c):
        green = mix(col, (72, 142, 110), 0.25)
        out = mix(green, shade(green, 1.45), math.exp(-((s.across + 0.55) / 0.14) ** 2) * 0.65)
        if c["fine"] and abs(s.d * 6 - round(s.d * 6)) < 0.04 and abs(s.across) < 0.3:
            out = shade(out, 0.78)
        return out
    S["jade"] = jade

    def temper(line_fn, inner, band_w, band_k):
        def f(col, s, c):
            line = line_fn(s, c)
            if s.across < line:
                return mix(col, c["edge"], inner)
            return mix(col, c["edge"], band_k) if abs(s.across - line) < band_w else col
        return f
    S["suguha"] = temper(lambda s, c: -0.22, 0.5, 0.06, 0.85)
    S["gunome"] = temper(lambda s, c: -0.22 + math.sin(s.d * 4.2) * 0.11, 0.5, 0.055, 0.85)
    S["choji"] = temper(lambda s, c: -0.18 - max(0, math.sin(s.d * 5.4)) ** 0.7 * 0.2, 0.52, 0.06, 0.88)
    S["notare"] = temper(lambda s, c: -0.22 + vnoise(s.d * 1.3, 0, c["seed"]) * 0.2 - 0.1, 0.48, 0.075, 0.8)

    def inazuma(col, s, c):
        base = -0.22 + math.sin(s.d * 3.6) * 0.1
        if s.across < base:
            return mix(col, c["edge"], 0.45)
        if abs(s.across - base + math.sin(s.d * 14) * 0.06) < 0.035:
            return mix(col, (255, 255, 255), 0.55)
        return mix(col, c["edge"], 0.8) if abs(s.across - base) < 0.05 else col
    S["inazuma"] = inazuma

    def kinsuji(col, s, c):
        base = -0.24 + math.sin(s.d * 3.1) * 0.09
        if s.across < base:
            return mix(col, c["edge"], 0.42)
        if abs(s.across - base * 0.6) < 0.028 and math.sin(s.d * 9 + c["seed"]) > 0.35:
            return mix(col, (235, 200, 120), 0.7)
        return mix(col, c["edge"], 0.78) if abs(s.across - base) < 0.05 else col
    S["kinsuji"] = kinsuji
    S["dualtone"] = lambda col, s, c: mix(col, c["accent"], 0.55) if s.across < 0 else mix(col, c["edge"], 0.4)

    def helix(col, s, c):
        tw = math.sin(s.d * 3.4 + s.across * 5.5)
        return mix(col, c["edge"], 0.35) if tw > 0.45 else shade(col, 0.78) if tw < -0.6 else col
    S["helix"] = helix

    def whorl(col, s, c):
        if not c["fine"]:
            return col
        a = math.sin(s.d * 6 + math.sin(s.across * 4) * 2)
        b = math.sin(s.across * 7 + math.sin(s.d * 3) * 2)
        return mix(col, c["edge"], 0.4) if a * b > 0.55 else shade(col, 0.84) if a * b < -0.75 else col
    S["whorl"] = whorl

    def divine(col, s, c):
        if not c["fine"]:
            return mix(col, c["edge"], 0.15)
        v, h = abs(math.sin(s.d * 4.5)), abs(math.sin(s.across * 6))
        if v < 0.09 or h < 0.09:
            return mix(col, c["edge"], 0.5)
        return mix(col, c["edge"], 0.28) if abs(v - h) < 0.07 else col
    S["divine"] = divine

    def nightflame(col, s, c):
        seam = math.sin(s.d * 5) * 0.15
        if s.across < seam:
            return mix(shade(col, 0.62), c["core"], 0.5)
        return mix(col, c["glow"], 0.45 + 0.2 * max(0, s.across))
    S["nightflame"] = nightflame
    S["rengoku"] = lambda col, s, c: mix(mix(col, (232, 186, 92), 0.55), mix(col, (186, 52, 42), 0.6), (s.across + 1) / 2)

    def dragonscale(col, s, c):
        if not c["fine"]:
            return mix(col, c["core"], 0.25)
        freq = 1.7
        row = math.floor(s.d * freq)
        fx, fy = _frac(s.d * freq), _frac(s.across * 1.1 + (0.5 if row % 2 else 0))
        r = math.hypot(fx - 0.5, (fy - 0.5) * 1.1)
        if r > 0.5:
            return shade(mix(col, c["core"], 0.3), 0.82)
        if r > 0.38:
            return shade(mix(col, c["core"], 0.2), 0.92)
        return mix(col, c["edge"], 0.22) if r < 0.2 else col
    S["dragonscale"] = dragonscale
    S["luminous"] = lambda col, s, c: mix(col, mix(c["edge"], (255, 252, 240), 0.4), math.exp(-(s.across / 0.45) ** 2) * 0.75)

    def ultimate_skyblock(col, s, c):
        rim = math.exp(-((s.across + 0.62) / 0.14) ** 2)
        relic = c["fine"] and abs(_jsmod(s.d * 4.2, 1) - 0.5) < 0.035 and abs(s.across) < 0.28
        out = mix(shade(col, 0.82), c["edge"], rim * 0.86)
        return mix(out, c["accent"], 0.5) if relic else out
    S["ultimate_skyblock"] = ultimate_skyblock

    def furfsky(col, s, c):
        rim = math.exp(-((s.across + 0.72) / 0.11) ** 2)
        out = mix(shade(col, 0.76), c["edge"], rim * 0.95)
        return mix(out, c["accent"], 0.22) if math.sin(s.d * 7 + s.across * 3) > 0.72 and c["fine"] else out
    S["furfsky"] = furfsky

    def hypixel_plus(col, s, c):
        t = _clamp(s.across, -1, 1)
        if t < -0.72:
            return mix(col, c["edge"], 0.72)
        if t > 0.68:
            return shade(col, 0.66)
        if c["fine"] and abs(s.d * 3.5 - round(s.d * 3.5)) < 0.05:
            return mix(col, c["edge"], 0.12)
        return col
    S["hypixel_plus"] = hypixel_plus

    def spartan_forge(col, s, c):
        file = math.sin(s.d * 26 + vnoise(s.d * 2, s.across, c["seed"]) * 2)
        out = shade(col, 1.05 if file > 0.5 else 0.91)
        return mix(out, c["edge"], math.exp(-((s.across + 0.48) / 0.2) ** 2) * 0.34)
    S["spartan_forge"] = spartan_forge

    def dragonsteel(col, s, c):
        if not c["fine"]:
            return shade(col, 0.78)
        vein = abs(math.sin(s.d * 6 + math.sin(s.across * 3) * 1.4 + c["seed"] * 0.01))
        return mix(c["glow"], c["edge"], 0.35) if vein > 0.965 else mix(shade(col, 0.76), col, 0.2)
    S["dragonsteel"] = dragonsteel

    def dragonbone(col, s, c):
        porous = fbm(s.d * 4, s.across * 3, c["seed"])
        bone_c = mix(col, (226, 215, 187), 0.48)
        return shade(bone_c, 0.68) if abs(math.sin(s.d * 5 + s.across * 2)) > 0.88 else shade(bone_c, 0.88 + porous * 0.15)
    S["dragonbone"] = dragonbone

    def simply_runic(col, s, c):
        lane = abs(s.across) < 0.24
        out = mix(col, c["core"], 0.12 if lane else 0)
        return mix(out, c["accent"], 0.7) if c["fine"] and lane and abs(math.sin(s.d * 13 + c["seed"] * 0.02)) > 0.72 else out
    S["simply_runic"] = simply_runic

    def vanilla_plus(col, s, c):
        t = _clamp(s.across, -1, 1)
        out = mix(col, c["edge"], 0.62) if t < -0.68 else shade(col, 0.74) if t > 0.62 else col
        out = mix(out, c["edge"], math.exp(-((t + 0.42) / 0.13) ** 2) * 0.3)
        if c["fine"]:
            out = shade(out, 1 + hash_noise(math.floor(s.d * 9) + c["seed"], math.floor(s.across * 9)) * 0.1)
        return out
    S["vanilla_plus"] = vanilla_plus
    return S


SURFACES = _surfaces()
SURFACE_NAMES = {
    "polished": "研磨鋼", "damascus": "積層鋼", "hamon": "刃文", "obsidian": "黒曜石", "crystal": "水晶", "bone": "骨",
    "wood": "木", "scales": "鱗", "magma": "溶岩", "frost": "霜", "hammered": "槌目", "etched": "彫刻",
    "worn": "使い込み", "banded": "縞岩", "gilded": "金象嵌", "circuit": "回路", "starfield": "星空",
    "prismarine": "プリズマリン", "amethyst": "アメジスト", "ender": "エンダー", "vanilla": "バニラ",
    "bevel": "面取り", "ifire": "氷炎", "stormforged": "嵐鍛え", "skyblock": "スカイブロック", "faithful": "フェイスフル",
    "warmith": "温鋼", "jade": "翡翠", "suguha": "直刃", "gunome": "互の目", "choji": "丁子", "notare": "湾れ",
    "inazuma": "稲妻", "kinsuji": "金筋", "dualtone": "二色", "helix": "螺旋", "whorl": "渦織", "divine": "黄金律",
    "nightflame": "夜炎", "rengoku": "煉獄", "dragonscale": "竜鱗", "luminous": "内光", "ultimate_skyblock": "遺物縞",
    "furfsky": "宝飾縁", "hypixel_plus": "三階調", "spartan_forge": "鍛造鑢目", "dragonsteel": "竜鋼",
    "dragonbone": "竜骨", "simply_runic": "ルーン道", "vanilla_plus": "バニラ+",
}


class _S:
    __slots__ = ("part", "across", "progress", "d", "v")

    def __init__(self, t):
        self.part, self.across, self.progress, self.d, self.v = t


_GLYPHS = (0b101010111010101, 0b111101010101111, 0b010111101111010, 0b101111010111101)


# ---------------------------------------------------------------- 塗り (元実装の shading.ts)

def _color(o, p, ctx, s: _S, x, y):
    across, t = s.across, _clamp(s.progress)
    seed = ctx["seed"]
    noise = 0.0
    if o["noise"] > 0.001 and o["detailLevel"] != "minecraft":
        noise = hash_noise(math.floor(x * 5) + seed, math.floor(y * 5)) * o["noise"] * 0.14
    f = 1 + (across + 0.2) * 0.32 * o["shading"] + noise
    if o["detailLevel"] in ("anime", "minecraft"):  # 陰影を 5 段に量子化
        f = round(f * 5) / 5
    if s.part in ("blade", "edge", "bevel"):
        col = p["blade"]
        if o["gradientBlade"]:
            col = mix(col, p["mid"] if t < 0.5 else p["tip"], t * 0.3 if t < 0.5 else (t - 0.5) * 0.95)
        if o["edgeGradient"]:
            k = (1 - across) / 2
            col = mix(col, p["bladeCore"], (0.5 - k) * 2 * 0.45) if k < 0.5 else mix(col, p["bladeEdge"], (k - 0.5) * 2 * 0.5)
        col = shade(col, f)
        if ctx["blade_px"] < 5 and o["surface"] != "polished":  # 細い刃は模様を載せきれないので色味だけ
            tint = SURFACES[o["surface"]](col, _S((s.part, 0.0, s.progress, s.d, s.v)), ctx)
            col = mix(col, tint, 0.5)
        else:
            col = SURFACES[o["surface"]](col, s, ctx)
        if o["fuller"] and 0.08 < t < o["fullerLength"] and ctx["blade_px"] >= 5:
            if abs(across) < 0.18:
                col = mix(col, p["bladeCore"], 0.68)
            elif -0.27 < across < -0.18:
                col = mix(col, p["bladeEdge"], 0.4)
        hl = math.exp(-((across + 0.62) / 0.14) ** 2)
        spec = math.exp(-((across + 0.35) / 0.10) ** 2) * 0.5
        sh = math.exp(-((across - 0.55) / 0.18) ** 2)
        if o["edgeHighlight"]:
            col = mix(col, p["bladeEdge"], hl * 0.75)
            col = mix(col, p["bladeEdge"], spec * o["shading"])
        if s.part == "edge":
            col = mix(col, p["bladeEdge"], 0.72 if o["edgeHighlight"] else 0.3)
        if s.part == "bevel":
            col = mix(col, p["bladeCore"], 0.58)
        col = mix(col, shade(col, 0.65), sh * 0.55)
        if _rune_at(o, ctx, s):
            col = mix(p["accent"], p["bladeEdge"], 0.15)
        if o["ricasso"] and 0.04 < t < 0.17:
            col = mix(col, p["bladeCore"], 0.35)
            if t < 0.06 or t > 0.15:
                col = shade(col, 0.78)
        if o["horimono"] and ctx["blade_px"] >= 7 and 0.18 < t < 0.72 and 0.25 < across < 0.55:
            col = shade(col, 0.68 if abs(_jsmod(t * 7, 1) - 0.5) < 0.22 else 0.86)
        if t < 0.06:
            col = shade(col, 0.72 + t * 4.5)
        return col
    if s.part == "handle":
        col = p["handle"]
        cross = math.sin(t * 44 + across * 3) * math.sin(t * 44 - across * 3)
        hs = o["handleStyle"]
        acc = 0.0
        if hs == "leather":
            acc = 0.48 if cross > 0.5 else 0.12
        elif hs == "wire":
            acc = 0.65 if math.sin(t * 70 + across * 3) > 0.4 else 0.05
        elif hs == "dragon_scale":
            acc = 0.5 if math.cos(t * 38 + (math.floor(across * 3) % 2) * math.pi) > 0.3 else 0
        elif hs == "gold_ribbon":
            return shade(mix(col, p["guardAccent"], 0.8 if math.sin(t * 30 + across * 4) > 0.3 else 0), f)
        elif hs == "bone":
            return shade(mix(p["handleAccent"], p["bladeEdge"], 0.55), (0.6 if _jsmod(t * 7, 1) < 0.15 else 1) * f)
        elif hs == "wrapped_parchment":
            acc = 0.9 if abs(math.sin(t * 38)) > 0.45 + abs(across) * 0.4 else 0.05
        elif hs == "ribbed":
            on = _jsmod(t * 8, 1) < 0.35
            acc = 0.55 if on else 0
            f *= 1.08 if on else 0.92
        elif hs == "cord":
            acc = 0.5 if math.sin(t * 52 + across * 6) > 0.2 else 0.1
        elif hs == "studded":
            acc = 0.9 if math.hypot(_jsmod(t * 6, 1) - 0.5, across * 0.6) < 0.16 else 0.08
        elif hs == "chain":
            acc = 0.85 if abs(math.hypot(_jsmod(t * 7, 1) - 0.5, across * 0.55) - 0.3) < 0.09 else 0.05
        else:
            f *= 0.98
        col = mix(col, p["handleAccent"], acc)
        if o["pommelBand"] and (t > 0.9 or t < 0.08):
            col = p["guard"]
        return shade(col, f)
    if s.part == "guard":
        col = p["guard"]
        if o["filigree"]:
            if abs(_jsmod(abs(s.v) * 1.6 + 0.2, 1) - 0.5) < 0.17 and abs(s.v) > 0.35:
                col = mix(col, p["guardAccent"], 0.6)
            elif abs(math.sin(s.v * 2.6)) < 0.12 and abs(s.v) > 0.25:
                col = shade(col, 0.8)
        if across < -0.45:
            col = mix(col, p["guardAccent"], 0.45)
        if across > 0.55:
            col = shade(col, 0.78)
        return shade(col, f)
    if s.part == "shard":
        return shade(p["shard"], 1.22 if across < 0 else 0.68)
    if s.part == "spur":
        return shade(mix(p["spur"], (232, 222, 200), 0.6) if o["boneSpurs"] else p["spur"], f)
    col = p["pommel"]
    if o["pommelStyle"] in ("orb", "round"):
        col = mix(col, p["guardAccent"], 0.35 if across < -0.5 else 0)
    return shade(col, f)


def _rune_at(o, ctx, s) -> bool:
    if not o["runeInlay"] or s.part not in ("blade", "edge", "bevel") or s.progress < 0.13 or s.progress > 0.78:
        return False
    if ctx["blade_px"] < 7:
        return abs(s.across) < 0.3 and _jsmod(s.progress * 9, 1) < 0.55
    if abs(s.across) > 0.4:
        return False
    pos = (s.progress - 0.13) * 6
    local = _jsmod(pos, 1)
    if local > 0.72:
        return False
    gy = min(4, math.floor(local / 0.72 * 5))
    gx = min(2, math.floor((s.across + 0.4) / 0.8 * 3))
    return bool(_GLYPHS[math.floor(pos) % len(_GLYPHS)] & (1 << (gy * 3 + gx)))


def _gem_distance(shape, x, y):
    if shape == "diamond":
        return abs(x) + abs(y)
    if shape == "hex":
        return max(abs(x) * 0.866 + abs(y) * 0.5, abs(y))
    if shape == "marquise":
        return math.hypot(x, y / 1.3)
    if shape == "teardrop":
        return math.hypot(x, y * 0.85) * (1 + max(0, -y) * 0.5)
    if shape == "star":
        return math.hypot(x, y) / (0.8 + 0.2 * math.cos(math.atan2(y, x) * 5))
    return math.hypot(x, y)


_BAYER4 = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))


def _bayer8(x, y):
    return ((_BAYER4[y % 4][x % 4] * 16) + _BAYER4[(y >> 1) % 4][(x >> 1) % 4]) / 256 - 0.5


def _mulberry(a):
    a &= 0xFFFFFFFF

    def nxt():
        nonlocal a
        a = (a + 0x6d2b79f5) & 0xFFFFFFFF
        t = ((a ^ (a >> 15)) * (1 | a)) & 0xFFFFFFFF
        t = ((t + (((t ^ (t >> 7)) * (61 | t)) & 0xFFFFFFFF)) ^ t) & 0xFFFFFFFF
        return ((t ^ (t >> 14)) & 0xFFFFFFFF) / 4294967296
    return nxt


def _hue_shift(c, deg):
    h, l, s = colorsys.rgb_to_hls(*(v / 255 for v in c))
    r, g, b = colorsys.hls_to_rgb((h + deg / 360) % 1, l, s)
    return (int(r * 255 + 0.5), int(g * 255 + 0.5), int(b * 255 + 0.5))


class _Prepared:
    """静止部分 (形・塗り・宝石・ディザ) を一度だけ作り、アニメの各コマで使い回す。"""

    def __init__(self, o, size):
        self.o = o
        aa = o["antiAlias"]
        self.width = w = size * (2 if aa else 1)
        self.scale = scale = w / 16
        self.geo = geo = Geometry(o)
        self.p = p = palette(o)
        seed = int(o["surfaceSeed"]) % 9973
        blade_px = (0.38 + o["bladeWidth"] * 0.4) * 2 * scale
        self.ctx = ctx = {"seed": seed, "fine": blade_px >= 12, "blade_px": blade_px, "edge": p["bladeEdge"],
                          "core": p["bladeCore"], "accent": p["accent"], "glow": p["glow"], "shading": o["shading"]}
        self.col: dict[tuple[int, int], RGB] = {}
        self.flags: dict[tuple[int, int], int] = {}
        self.part: dict[tuple[int, int], str] = {}
        coarse = o["detailLevel"] == "minecraft"
        mode = o["outlineColorMode"]
        outline = (shade(p["bladeCore"], 0.52) if mode == "tinted" else shade(p["guard"], 0.7) if mode == "gold"
                   else p["outline"])
        probe = 0.55 if coarse else max(0.18, min(0.45, 1.2 / scale))
        original = o["finish"] == "original"
        for y in range(w):
            for x in range(w):
                lx = (math.floor(x / scale) + 0.5) if coarse else (x + 0.5) / scale
                ly = (math.floor(y / scale) + 0.5) if coarse else (y + 0.5) / scale
                sm = geo.sample(lx, ly)
                if sm is None:
                    continue
                s = _S(sm)
                color = _color(o, p, ctx, s, lx, ly)
                fl = 0
                if s.part in ("blade", "edge", "bevel"):
                    fl |= 1
                if _rune_at(o, ctx, s):
                    fl |= 2
                if original and o["outline"] and mode != "none":
                    blanks = sum(geo.sample(lx + dx, ly + dy) is None
                                 for dx, dy in ((probe, 0), (-probe, 0), (0, probe), (0, -probe)))
                    if 0 < blanks < 3:
                        color = mix(color, outline, 0.32 if fl & 1 and s.across < -0.5 else 0.72)
                self.col[(x, y)] = color
                self.flags[(x, y)] = fl
                self.part[(x, y)] = s.part
        if o["gem"]:
            self._gem(geo.guard_d, min(0.72, 0.32 + o["guardWidth"] * 0.07), outline)
        if o["pommelStyle"] == "gem":
            self._gem(geo.pommel_d, 0.38, outline)
        if o["detailLevel"] == "minecraft" or (o["detailLevel"] == "rich" and w <= 32):
            for (x, y), c in self.col.items():
                t = _bayer8(x, y) * 0.18 * 14
                self.col[(x, y)] = tuple(max(0, min(255, int(v + t))) for v in c)

    def _gem(self, d, radius, outline):
        o, p, scale, w = self.o, self.p, self.scale, self.width
        cx, cy = (d + 0 / SQRT2) * scale, (16 - d) * scale
        r = radius * scale
        for y in range(max(0, int(cy - r * 1.6)), min(w, int(cy + r * 1.6) + 1)):
            for x in range(max(0, int(cx - r * 1.6)), min(w, int(cx + r * 1.6) + 1)):
                dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
                dist = _gem_distance(o["gemShape"], dx, dy)
                if dist > 1.18:
                    continue
                c = outline if dist > 1 else shade(p["gem"], 1.15 if dx + dy < 0 else 0.64)
                if dist < 0.75 and dx < 0 and dy < 0:
                    c = mix(c, (235, 248, 255), 0.55)
                if scale > 4:
                    fac = hash_noise(math.floor(x / (scale * 0.4)), math.floor(y / (scale * 0.4)) + int(o["effectSeed"]) % 1000) + 0.5
                    c = mix(c, shade(p["gem"], 1.25), max(0, fac - 0.6))
                self.col[(x, y)] = c
                self.flags[(x, y)] = 4 if dist < 1 else 0
                self.part[(x, y)] = "gem"


def _animate(pr: _Prepared, phase: float) -> dict:
    """元実装の animate: 属性の脈動・ホログラム・光沢・切っ先の光・ルーン/宝石の脈動・粒・雷・砕片。"""
    o, p, geo, scale, w = pr.o, pr.p, pr.geo, pr.scale, pr.width
    tau = 2 * math.pi
    col = dict(pr.col)
    alpha: dict[tuple[int, int], float] = {}
    sheen_pos = (phase * max(1, round(o["sheenSpeed"] * 2)) + o["sheenPos"]) % 1
    tip = geo.blade_start + (geo.blade_end - geo.blade_start) * 0.97
    tipx, tipy = tip + geo.center(0.97) / SQRT2, 16 - tip + geo.center(0.97) / SQRT2
    for (x, y), c in pr.col.items():
        d = (x - y) / scale
        fl = pr.flags[(x, y)]
        if fl & 1:
            if o["element"] != "none":
                c = mix(c, p["element"], o["elementIntensity"] * (0.1 + math.sin(phase * tau + d) * 0.04))
            if o["holographic"]:
                c = _hue_shift(c, math.sin(phase * tau + d * 0.4) * 24)
            if o["sheen"]:
                dist = abs((d + 16) / 32 - sheen_pos)
                if dist < 0.08:
                    c = mix(c, (235, 243, 250), (1 - dist / 0.08) * 0.35)
            if o["tipGlow"]:
                dist = math.hypot(x / scale - tipx, y / scale - tipy)
                if dist < 2.4:
                    c = mix(c, p["accent"], (1 - dist / 2.4) * 0.3)
        if fl & 2 and o["runePulse"]:
            c = mix(c, (232, 242, 255), (0.5 + 0.5 * math.sin(phase * tau + d)) * 0.3)
        if fl & 4 and o["gemGlow"]:
            c = mix(c, (231, 247, 255), (0.5 + 0.5 * math.sin(phase * tau)) * 0.18)
        col[(x, y)] = c

    def blend(fx, fy, c, a):
        k = (int(round(fx)), int(round(fy)))
        if not (0 <= k[0] < w and 0 <= k[1] < w):
            return
        a = _clamp(a)
        if k in col:
            col[k] = mix(col[k], c, a)
        else:
            old = alpha.get(k, 0.0)
            prev = col.get(k, c)
            col[k] = mix(prev, c, a if old else 1.0)
            alpha[k] = a + old * (1 - a)

    def point(t, off=0.0):
        dd = geo.blade_start + (geo.blade_end - geo.blade_start) * t
        vv = geo.center(t) + off
        return dd + vv / SQRT2, 16 - dd + vv / SQRT2
    if o["sparkle"] or o["particles"]:
        rnd = _mulberry(int(o["effectSeed"]) + 97)
        count = round(o["sparkleDensity"] * 15) + (8 if o["particles"] else 0)
        for i in range(count):
            t = 0.13 + rnd() * 0.73
            off = (rnd() - 0.5) * (5 if o["particles"] else geo.width(t))
            ang = rnd() * tau
            px_, py_ = point(t, off)
            fx = px_ * scale + (math.sin(phase * tau + ang) * scale * 0.4 if o["particles"] else 0)
            fy = py_ * scale + (math.cos(phase * tau + ang) * scale * 0.65 if o["particles"] else 0)
            inten = max(0.0, math.sin(phase * tau + ang))
            if inten < 0.3:
                continue
            blend(fx, fy, p["accent"], inten * 0.85)
            if o["sparkle"] and i % 3 == 0:
                r = max(1, round(scale * 0.18))
                for dx, dy in ((-r, 0), (r, 0), (0, -r), (0, r)):
                    blend(fx + dx, fy + dy, p["accent"], inten * 0.38)
    if o["lightning"]:
        rnd = _mulberry(int(o["effectSeed"]) + 701 + math.floor(phase * 12))
        prev = point(0.05)
        for i in range(1, 16):
            pt = point(i / 16, (rnd() - 0.5) * 1.4)
            steps = max(1, math.ceil(math.hypot(pt[0] - prev[0], pt[1] - prev[1]) * scale))
            for k in range(steps + 1):
                blend((prev[0] + (pt[0] - prev[0]) * k / steps) * scale,
                      (prev[1] + (pt[1] - prev[1]) * k / steps) * scale, p["accent"], 0.85)
            prev = pt
    if o["shatter"]:
        for i in range(5):
            ang = phase * tau + i / 5 * tau
            px_, py_ = point(0.5 + math.cos(ang) * 0.28, math.sin(ang) * 3.2)
            r = max(1, round(scale * 0.23))
            for yy in range(-r, r + 1):
                for xx in range(-r, r + 1):
                    if abs(xx) + abs(yy) <= r:
                        blend(px_ * scale + xx, py_ * scale + yy, p["accent"], 0.65)
    return col, alpha


def render(o: dict, size: int = 64, frame: int = 0, frames: int = 8, glow: bool = True,
           _cache: dict = {}) -> Image.Image:  # noqa: B006
    """剣を size px で 1 コマ描く。frame / frames はアニメの位相 (元実装は 4 秒周期)。

    finish="hd" (既定): この mod の HD アイコンと同じ selout + 外縁 + 光暈 (auraBloom で光暈を強める)。
    finish="original": 元実装の方向別の輪郭 (outlineColorMode) をそのまま使う。
    """
    o = options(**o) if "palette" not in o or "finish" not in o else o
    key = json.dumps(o, sort_keys=True) + f"|{size}"
    pr = _cache.get(key)
    if pr is None:
        if len(_cache) > 8:
            _cache.clear()
        pr = _cache[key] = _Prepared(o, size)
    col, alpha = _animate(pr, (frame / max(1, frames)) % 1)
    w = pr.width
    if o["finish"] == "original":
        img = Image.new("RGBA", (w, w), (0, 0, 0, 0))
        px = img.load()
        for (x, y), c in col.items():
            px[x, y] = c + (int(255 * alpha[(x, y)]) if (x, y) in alpha else 255,)
        if o["antiAlias"]:
            img = img.resize((size, size), Image.BOX)
        return img
    c = hd.Canvas(w, w)
    pid = {"blade": 1, "edge": 1, "bevel": 1, "guard": 2, "handle": 3, "pommel": 4, "spur": 5, "shard": 6, "gem": 7}
    for (x, y), cc in col.items():
        if (x, y) in alpha and alpha[(x, y)] < 0.5:
            continue
        c.put(x, y, cc + (255,), pid.get(pr.part.get((x, y)), 8))
        fl = pr.flags.get((x, y), 0)
        part = pr.part.get((x, y))
        if part == "shard" or fl & 2 or fl & 4:
            c.add_glow(x, y, 0.7)
        if o["auraBloom"] and fl & 1:
            c.add_glow(x, y, 0.35)
        if o["tipGlow"] and part in ("blade", "edge"):
            c.add_glow(x, y, 0.0)
    img = c.render(outline=o["outline"] and o["outlineColorMode"] != "none",
                   glow_color=(p_glow(pr) + (255,)) if glow else None, glow_radius=max(1, w // 32))
    if o["antiAlias"]:
        img = img.resize((size, size), Image.BOX)
    return img


def p_glow(pr) -> RGB:
    return pr.p["glow"] if pr.o["element"] != "none" else pr.p["accent"]


def frames(o: dict, n: int = 8, size: int = 64) -> list[Image.Image]:
    return [render(o, size, f, n) for f in range(n)]


def is_animated(o: dict) -> bool:
    o = options(**o) if "finish" not in o else o
    return any(o[k] for k in ("sheen", "holographic", "sparkle", "particles", "lightning", "shatter", "runePulse",
                              "gemGlow")) or (o["element"] != "none" and o["elementIntensity"] > 0)


def silhouettes() -> list[str]:
    return list(PROFILES)
