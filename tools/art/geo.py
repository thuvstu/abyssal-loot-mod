"""GeckoLib 用の骨格付き 3D モデル (Bedrock geometry 形式) を組み立てる。

座標は「見た目どおり」のワールド系 (x 右+, y 上+, z 手前+, 単位 = 1/16 ブロック) で書き、
書き出し時に GeckoLib の読み込み規則 (x 反転、x/y 回転の符号反転) へ変換する:
  BakedModelFactory: 頂点 x = -(origin.x + size.x), pivot.x = -pivot.x, rot = (-rx, -ry, rz)

原点はアイテム描画空間の中心 (GeoItemRenderer が (0.5, 0.51, 0.5) へ平行移動する)。
武器は +y 方向へまっすぐ立てて作り、傾きは手持ち表示 (display) の回転で付ける。

テクスチャは面ごとのアトラス (per-face UV)。配置は材質名だけで決まるので、
色違い (ノクターンのカウント / 相棒の段階) は同じ配置に別の配色で塗るだけで作れる。
発光する材質は GeckoLib の自動発光レイヤー用に *_glowmask.png へも塗る。
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

from PIL import Image, ImageDraw

from .hd import _hsv_shift, hexc, mix, ramp

TEX_SCALE = 2  # 1 単位あたりのテクセル数
FACES = ("north", "south", "east", "west", "up", "down")
WHITE = (255, 255, 255, 255)


@dataclass
class Cube:
    lo: tuple[float, float, float]
    size: tuple[float, float, float]
    mat: str
    rot: tuple[float, float, float] = (0.0, 0.0, 0.0)
    pivot: tuple[float, float, float] | None = None
    uv: dict = field(default_factory=dict)  # face -> (u, v, w, h)


@dataclass
class Bone:
    name: str
    pivot: tuple[float, float, float]
    parent: str | None = None
    rot: tuple[float, float, float] = (0.0, 0.0, 0.0)
    cubes: list[Cube] = field(default_factory=list)


@dataclass
class Material:
    """塗り方。kind: metal / gem / glow / cloth / rune / dark。"""
    color: int
    kind: str = "metal"
    emissive: bool = False
    accent: int | None = None  # rune の光 / cloth の縞の色


class Model:
    def __init__(self, ident: str):
        self.ident = ident
        self.bones: dict[str, Bone] = {}
        self.tex_w = 0
        self.tex_h = 0

    # ------------------------------------------------------------ 組み立て
    def bone(self, name: str, pivot=(0.0, 0.0, 0.0), parent: str | None = None, rot=(0.0, 0.0, 0.0)) -> str:
        assert name not in self.bones, name
        self.bones[name] = Bone(name, tuple(pivot), parent, tuple(rot))
        return name

    def box(self, bone: str, center, size, mat: str, rot=(0.0, 0.0, 0.0), pivot=None) -> None:
        """中心と大きさで直方体を置く。rot は度 (pivot 省略時は直方体の中心まわり)。"""
        cx, cy, cz = center
        sx, sy, sz = size
        lo = (cx - sx / 2, cy - sy / 2, cz - sz / 2)
        piv = tuple(pivot) if pivot is not None else (tuple(center) if any(rot) else None)
        self.bones[bone].cubes.append(Cube(lo, (sx, sy, sz), mat, tuple(rot), piv))

    def bar(self, bone: str, a, b, thick: float, depth: float, mat: str) -> None:
        """xy 平面上の 2 点 a→b を結ぶ角棒 (z 回転で傾ける)。"""
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dy)
        z = a[2] if len(a) > 2 else 0.0
        c = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, z)
        self.box(bone, c, (length, thick, depth), mat, rot=(0, 0, math.degrees(math.atan2(dy, dx))))

    # ------------------------------------------------------------ UV 配置
    def layout(self, width: int = 128) -> None:
        """全立方体の各面にアトラス上の領域を割り当てる (棚詰め)。"""
        rects = []
        for bone in self.bones.values():
            for cube in bone.cubes:
                sx, sy, sz = cube.size
                dims = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy),
                        "up": (sx, sz), "down": (sx, sz)}
                for f in FACES:
                    w = max(1, math.ceil(dims[f][0] * TEX_SCALE - 1e-6))
                    h = max(1, math.ceil(dims[f][1] * TEX_SCALE - 1e-6))
                    rects.append((cube, f, w, h))
        x = y = row_h = 0
        for cube, f, w, h in rects:
            if x + w > width:
                x, y, row_h = 0, y + row_h, 0
            cube.uv[f] = (x, y, w, h)
            x += w
            row_h = max(row_h, h)
        used = y + row_h
        self.tex_w = width
        self.tex_h = 1 << max(4, math.ceil(math.log2(max(1, used))))

    # ------------------------------------------------------------ 書き出し
    def geo_json(self) -> dict:
        if not self.tex_w:
            self.layout()
        bones = []
        for b in self.bones.values():
            out = {"name": b.name, "pivot": _bx(b.pivot)}
            if b.parent:
                out["parent"] = b.parent
            if any(b.rot):
                out["rotation"] = _brot(b.rot)
            cubes = []
            for c in b.cubes:
                cj = {
                    "origin": _r([-(c.lo[0] + c.size[0]), c.lo[1], c.lo[2]]),
                    "size": _r(list(c.size)),
                    "uv": {f: {"uv": [u, v], "uv_size": [w, h]} for f, (u, v, w, h) in c.uv.items()},
                }
                if any(c.rot):
                    cj["pivot"] = _bx(c.pivot)
                    cj["rotation"] = _brot(c.rot)
                cubes.append(cj)
            if cubes:
                out["cubes"] = cubes
            bones.append(out)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": f"geometry.{self.ident}",
                    "texture_width": self.tex_w,
                    "texture_height": self.tex_h,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 4,
                    "visible_bounds_offset": [0, 0.5, 0],
                },
                "bones": bones,
            }],
        }

    # ------------------------------------------------------------ テクスチャ
    def paint(self, palette: dict[str, Material]) -> tuple[Image.Image, Image.Image]:
        """配色からテクスチャと発光マスクを塗る。"""
        if not self.tex_w:
            self.layout()
        img = Image.new("RGBA", (self.tex_w, self.tex_h), (0, 0, 0, 0))
        glow = Image.new("RGBA", (self.tex_w, self.tex_h), (0, 0, 0, 0))
        px, gx = img.load(), glow.load()
        for b in self.bones.values():
            for c in b.cubes:
                m = palette[c.mat]
                for f, (u, v, w, h) in c.uv.items():
                    for j in range(h):
                        for i in range(w):
                            col = _shade(m, f, i, j, w, h)
                            px[u + i, v + j] = col
                            if m.emissive:
                                gx[u + i, v + j] = col
        return img, glow

    # ------------------------------------------------------------ 確認用
    def preview(self, palette: dict[str, Material], hidden: set[str] = frozenset(), size: int = 256,
                yaw: float = 25.0, pitch: float = 15.0, tilt: float = -45.0) -> Image.Image:
        """簡易な正投影プレビュー (面の平均色で塗る)。手持ちの傾き tilt を z 回転で再現。"""
        img = Image.new("RGBA", (size, size), (24, 22, 34, 255))
        d = ImageDraw.Draw(img)
        polys = []
        world = self._world_transforms()
        light = _norm((-0.4, 0.7, 0.6))
        for b in self.bones.values():
            if _is_hidden(self, b.name, hidden):
                continue
            mb = world[b.name]
            for c in b.cubes:
                col = _avg_color(palette[c.mat])
                for quad, n in _cube_quads(c):
                    q = [_apply(mb, p) for p in quad]
                    nn = _apply_dir(mb, n)
                    q = [_view(p, yaw, pitch, tilt) for p in q]
                    nn = _view(nn, yaw, pitch, tilt)
                    if nn[2] <= 0:
                        continue
                    lum = 0.45 + 0.55 * max(0.0, sum(a * b_ for a, b_ in zip(nn, light)))
                    fc = tuple(min(255, int(ch * lum)) for ch in col[:3]) + (255,)
                    depth = sum(p[2] for p in q) / 4
                    polys.append((depth, q, fc, palette[c.mat].emissive))
        polys.sort(key=lambda t: t[0])
        k = size / 40.0
        for _, q, fc, em in polys:
            pts = [(size / 2 + p[0] * k, size * 0.55 - p[1] * k) for p in q]
            d.polygon(pts, fill=fc, outline=(10, 8, 16, 255) if not em else None)
        return img

    def _world_transforms(self):
        out = {}

        def mat_of(name):
            if name in out:
                return out[name]
            b = self.bones[name]
            local = _compose(_translate(b.pivot), _rot_matrix(b.rot), _translate(tuple(-v for v in b.pivot)))
            m = local if b.parent is None else _compose(mat_of(b.parent), local)
            out[name] = m
            return m

        for n in self.bones:
            mat_of(n)
        return out


# ================================================================ アニメーション

def anim_json(name: str, length: float, bones: dict) -> dict:
    """bones: {bone: {"rotation"|"position"|"scale": {time: [x, y, z]}}}。
    回転は GeckoLib 規則に合わせ x/y を反転 (ワールド系で書けるように)、位置は x を反転。"""
    out = {}
    for bone, chans in bones.items():
        oc = {}
        for ch, keys in chans.items():
            conv = {}
            for t, v in sorted(keys.items()):
                if ch == "rotation":
                    v = [-v[0], -v[1], v[2]]
                elif ch == "position":
                    v = [-v[0], v[1], v[2]]
                conv[f"{t:.4g}"] = _r(list(v))
            oc[ch] = conv
        out[bone] = oc
    return {
        "format_version": "1.8.0",
        "animations": {name: {"loop": True, "animation_length": length, "bones": out}},
    }


def spin(length: float, axis: int = 1, turns: float = 1.0, steps: int = 4) -> dict:
    """一定速度の回転キーフレーム (線形補間なので細かめに刻む)。"""
    keys = {}
    for i in range(steps + 1):
        t = length * i / steps
        v = [0.0, 0.0, 0.0]
        v[axis] = 360.0 * turns * i / steps
        keys[t] = v
    return keys


def wave(length: float, amp, steps: int = 8, phase: float = 0.0, base=(0.0, 0.0, 0.0)) -> dict:
    """正弦の往復 (amp は軸ごとの振幅)。"""
    keys = {}
    for i in range(steps + 1):
        t = length * i / steps
        s = math.sin(2 * math.pi * i / steps + phase)
        keys[t] = [base[k] + amp[k] * s for k in range(3)]
    return keys


# ================================================================ 内部

def _r(v):
    return [round(float(x), 4) + 0.0 for x in v]


def _bx(p):
    return _r([-p[0], p[1], p[2]])


def _brot(r):
    return _r([-r[0], -r[1], r[2]])


def _shade(m: Material, face: str, i: int, j: int, w: int, h: int):
    """1 テクセルの色。面の向きで明暗、上から下へのグラデーション、縁取り、材質の模様。"""
    base = hexc(m.color)
    if m.kind == "glow":
        r = ramp(base, 5, spread=0.35)
    elif m.kind == "dark":
        r = ramp(base, 5, spread=0.5)
    else:
        r = ramp(base, 5, spread=0.75)
    level = {"up": 3.4, "north": 2.6, "south": 2.6, "east": 1.8, "west": 1.8, "down": 0.6}[face]
    if face not in ("up", "down") and h > 1:
        level += 0.7 * (0.5 - j / (h - 1))  # 上ほど明るい
    edge = (i == 0 or j == 0 or i == w - 1 or j == h - 1) and w >= 3 and h >= 3
    if edge and m.kind not in ("glow",):
        level -= 0.9
    if m.kind == "metal" and face in ("north", "south") and w >= 3:
        # 斜めの照り返し
        if abs((i - j * 0.6) - w * 0.35) < 0.9:
            level += 1.1
    elif m.kind == "gem":
        cx, cy = (w - 1) / 2, (h - 1) / 2
        dd = math.hypot((i - cx) / max(1, w / 2), (j - cy) / max(1, h / 2))
        level += 1.2 * max(0.0, 1 - dd) - 0.3
        if i == int(cx * 0.6) and j == int(cy * 0.6):
            return mix(r[4], WHITE, 0.7)
    elif m.kind == "cloth" and j % 3 == 2:
        level -= 1.0
    elif m.kind == "rune" and m.accent is not None and face in ("north", "south"):
        # 樋に刻まれたルーン (3 テクセルごとに光る印)
        if 1 <= i < w - 1 and j % 4 in (1, 2) and (j // 4 + i) % 2 == 0:
            return mix(hexc(m.accent), WHITE, 0.35 if j % 4 == 1 else 0.0)
    lv = max(0.0, min(4.0, level))
    lo = int(lv)
    hi = min(4, lo + 1)
    col = mix(r[lo], r[hi], lv - lo)
    return col


def _avg_color(m: Material):
    return ramp(hexc(m.color), 5)[2 if m.kind != "glow" else 3]


def _is_hidden(model: Model, name: str, hidden) -> bool:
    while name is not None:
        if name in hidden:
            return True
        name = model.bones[name].parent
    return False


def _cube_quads(c: Cube):
    x0, y0, z0 = c.lo
    x1, y1, z1 = x0 + c.size[0], y0 + c.size[1], z0 + c.size[2]
    v = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
         (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    faces = [((4, 5, 6, 7), (0, 0, 1)), ((1, 0, 3, 2), (0, 0, -1)), ((5, 1, 2, 6), (1, 0, 0)),
             ((0, 4, 7, 3), (-1, 0, 0)), ((7, 6, 2, 3), (0, 1, 0)), ((0, 1, 5, 4), (0, -1, 0))]
    m = None
    if any(c.rot):
        m = _compose(_translate(c.pivot), _rot_matrix(c.rot), _translate(tuple(-p for p in c.pivot)))
    for idx, n in faces:
        quad = [v[k] for k in idx]
        if m is not None:
            quad = [_apply(m, p) for p in quad]
            n = _apply_dir(m, n)
        yield quad, n


def _rot_matrix(rot):
    rx, ry, rz = (math.radians(a) for a in rot)
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    my = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    mz = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    return _compose(mz, my, mx)


def _translate(p):
    return [[1, 0, 0, p[0]], [0, 1, 0, p[1]], [0, 0, 1, p[2]], [0, 0, 0, 1]]


def _compose(*ms):
    out = [[1 if i == j else 0 for j in range(4)] for i in range(4)]
    for m in ms:
        out = [[sum(out[i][k] * m[k][j] for k in range(4)) for j in range(4)] for i in range(4)]
    return out


def _apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def _apply_dir(m, n):
    return tuple(m[i][0] * n[0] + m[i][1] * n[1] + m[i][2] * n[2] for i in range(3))


def _norm(v):
    s = math.sqrt(sum(a * a for a in v))
    return tuple(a / s for a in v)


def _view(p, yaw, pitch, tilt):
    m = _compose(_rot_matrix((pitch, 0, 0)), _rot_matrix((0, yaw, 0)), _rot_matrix((0, 0, tilt)))
    return _apply(m, p)


def glow_ramp_color(c: int, t: float) -> int:
    """主色を白へ寄せた発光色 (int)。"""
    col = mix(hexc(c), WHITE, t)
    return (col[0] << 16) | (col[1] << 8) | col[2]


def shift(c: int, dv: float, hue_to: float = 0.7, dh: float = 0.05, ds: float = 0.05) -> int:
    col = _hsv_shift(hexc(c), dv, hue_to, dh, ds)
    return (col[0] << 16) | (col[1] << 8) | col[2]
