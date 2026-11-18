"""MOB の骨組みジェネレータ (MCAssetGen の professional-minecraft-mob-maker を移植・改良したもの)。

骨組み (`mobgen_data.json`) は元アプリの 12 系統 (人型・四足・飛行・水棲・節足・スライム・蛇行・ゴーレム・多頭・
筒型・詠唱者・ボス) と、その体型違い (剛腕・細長・有角…) を焼き出したもの。各部位は箱 (原点・寸法・回転軸・回転・
色の役割)。

ここから次の 3 つを作る:
- テクスチャ: **バニラの箱 UV 配置** (上・下・右・前・左・後) で詰め、面ごとの明るさ・縦の陰影・縁・粒で塗る
- Java: この mod と同じ書き方 (`CubeListBuilder.create().texOffs(u, v).addBox(...)` + `PartPose`) の
  `createBodyLayer()`。AbyssModels に貼ればそのまま使える
- 見本: 回転を反映した等角投影の絵 (面の明るさ付き)

元実装からの改良点
- 元実装は面ごとにバラバラに詰めた独自配置で、Java の texOffs (箱 1 つにつき 1 か所) では使えなかった。
  バニラの箱 UV に揃えたので、そのまま MC のモデルに載る
- 面の塗りを平塗り + 枠線から、面の向きの明るさ × 上下の陰影 × 縁の暗さ × 決定的な粒 に (目は発光色のまま)
- 座標系を MC (y 下向き・地面 24) に変換して出力する (元実装は Fabric 向けの別コード生成)
- 体型違いの部位の伸縮は回転軸を中心に掛ける (元実装と同じ)。部位の親子関係は平らにする (各部位が絶対位置の
  回転軸を持つ。静止姿勢の見た目は同じ)

CLI: `python tools/abyss.py mobgen list | build <系統> [--variant] [--color slot=rrggbb]`
"""
from __future__ import annotations

import io
import json
import math
import os
import zlib

from PIL import Image, ImageDraw

_HERE = os.path.dirname(os.path.abspath(__file__))
with io.open(os.path.join(_HERE, "mobgen_data.json"), encoding="utf-8") as _f:
    ARCHETYPES: dict = json.load(_f)["archetypes"]

# 面の明るさ (元実装の FACE_LIGHT)。MC の箱 UV の面名で持つ
FACE_LIGHT = {"up": 1.05, "down": 0.45, "north": 0.82, "south": 0.62, "east": 0.74, "west": 0.58}


def _rgb(v):
    if isinstance(v, str):
        v = int(v.lstrip("#"), 16)
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


def _shade(c, k):
    return tuple(max(0, min(255, int(c[i] * k + 0.5))) for i in range(3)) + (255,)


def _hash(*k) -> float:
    return (zlib.crc32(",".join(map(str, k)).encode()) % 10000) / 10000


class Part:
    def __init__(self, pid, name, origin, size, pivot, rotation, slot):
        self.id, self.name = pid, name
        self.origin, self.size, self.pivot = origin, size, pivot
        self.rotation, self.slot = rotation, slot
        self.uv = (0, 0)

    @property
    def tex_dims(self):
        """テクスチャ上の箱の寸法 (整数)。0 幅の面も 1px は取る。"""
        return tuple(max(1, math.ceil(v - 1e-6)) for v in self.size)


def build(arch_id: str, variant: str | None = None, enable=(), disable=()) -> list[Part]:
    """骨組みを部位の一覧にする (体型違いの伸縮・追加部位の有効化を反映)。"""
    if arch_id not in ARCHETYPES:
        raise ValueError(f"未知の系統 '{arch_id}'。使えるもの: {', '.join(ARCHETYPES)}")
    arch = ARCHETYPES[arch_id]
    var = None
    if variant:
        var = next((v for v in arch["variants"] if v["id"] == variant), None)
        if var is None:
            raise ValueError(f"'{arch_id}' に体型 '{variant}' は無い。使えるもの: {', '.join(v['id'] for v in arch['variants'])}")
    on_extra = set(enable) | set(var["enable"] if var else ())
    off = set(disable) | set(var["disable"] if var else ())
    scales = var["partScale"] if var else {}
    out = []
    for d in arch["parts"]:
        pid = d["id"]
        on = (d["defaultOn"] if d["optional"] else True) or pid in on_extra
        if not on or pid in off:
            continue
        sc = scales.get(pid, [1, 1, 1])
        o, s, p = d["origin"], d["size"], d["pivot"]
        lo = [p[k] + (o[k] - p[k]) * sc[k] for k in range(3)]
        hi = [p[k] + (o[k] + s[k] - p[k]) * sc[k] for k in range(3)]
        mn = [min(lo[k], hi[k]) for k in range(3)]
        mx = [max(lo[k], hi[k]) for k in range(3)]
        out.append(Part(pid, d["name"], mn, [mx[k] - mn[k] for k in range(3)], list(p), list(d["rotation"]), d["slot"]))
    return out


def colors(arch_id: str, **over) -> dict[str, tuple]:
    c = {k: _rgb(v) for k, v in ARCHETYPES[arch_id]["colors"].items()}
    for k, v in over.items():
        if k not in c:
            raise ValueError(f"未知の色の役割 '{k}'。使えるもの: {', '.join(c)}")
        c[k] = _rgb(v)
    return c


# ---------------------------------------------------------------- テクスチャ (バニラの箱 UV)

def pack(parts: list[Part]) -> int:
    """箱 UV (幅 2(d+w) × 高さ d+h) を棚詰めし、各部位の uv を決める。テクスチャの一辺を返す。"""
    order = sorted(parts, key=lambda p: -(p.tex_dims[2] + p.tex_dims[1]))
    for size in (64, 128, 256):
        x = y = row_h = 0
        ok = True
        for p in order:
            w, h, d = p.tex_dims
            bw, bh = 2 * (d + w), d + h
            if x + bw > size:
                x, y, row_h = 0, y + row_h, 0
            if bw > size or y + bh > size:
                ok = False
                break
            p.uv = (x, y)
            x += bw
            row_h = max(row_h, bh)
        if ok:
            return size
    raise ValueError("部位が多すぎて 256px に収まらない")


def faces_of(p: Part):
    """MC の箱 UV の各面: (面名, u, v, 幅, 高さ)。"""
    w, h, d = p.tex_dims
    u, v = p.uv
    return [("up", u + d, v, w, d), ("down", u + d + w, v, w, d), ("east", u, v + d, d, h),
            ("north", u + d, v + d, w, h), ("west", u + d + w, v + d, d, h), ("south", u + 2 * d + w, v + d, w, h)]


def texture(parts: list[Part], cols: dict, size: int, seed: str = "") -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for p in parts:
        base = cols.get(p.slot, cols["primary"])
        glow = p.slot == "eye"
        for face, u, v, fw, fh in faces_of(p):
            light = 1.0 if glow else FACE_LIGHT[face]
            for yy in range(fh):
                for xx in range(fw):
                    k = light
                    if not glow:
                        if face not in ("up", "down"):
                            k *= 1.08 - 0.2 * (yy / max(1, fh - 1))  # 上ほど明るい
                        if xx in (0, fw - 1) or yy in (0, fh - 1):
                            k *= 0.86  # 箱の縁
                        n = _hash(seed, p.id, face, xx, yy)
                        k *= 0.94 if n < 0.18 else 1.05 if n > 0.86 else 1.0
                    elif (xx, yy) == (0, 0):
                        k = 1.25
                    px[u + xx, v + yy] = _shade(base, k)
    return img


# ---------------------------------------------------------------- Java (この mod の AbyssModels と同じ書き方)

def _f(v: float) -> str:
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return (s if s not in ("-0", "") else "0") + "f"


def java(parts: list[Part], size: int, class_comment: str = "") -> str:
    lines = [f"    /** {class_comment} (tools/art/mobgen.py で生成) */" if class_comment else "",
             "    public static LayerDefinition createBodyLayer() {",
             "        MeshDefinition mesh = new MeshDefinition();",
             "        PartDefinition root = mesh.getRoot();"]
    for p in parts:
        # 元データ: y 上向き・地面 0 → MC: y 下向き・地面 24。回転は y 反転で x・z の符号が反転する
        px_, py_, pz_ = p.pivot[0], 24 - p.pivot[1], p.pivot[2]
        bx = p.origin[0] - p.pivot[0]
        by = (24 - (p.origin[1] + p.size[1])) - py_
        bz = p.origin[2] - p.pivot[2]
        rx, ry, rz = (math.radians(-p.rotation[0]), math.radians(p.rotation[1]), math.radians(-p.rotation[2]))
        pose = (f"PartPose.offsetAndRotation({_f(px_)}, {_f(py_)}, {_f(pz_)}, {_f(rx)}, {_f(ry)}, {_f(rz)})"
                if any(abs(a) > 1e-6 for a in (rx, ry, rz)) else f"PartPose.offset({_f(px_)}, {_f(py_)}, {_f(pz_)})")
        lines.append(f'        root.addOrReplaceChild("{p.id}", CubeListBuilder.create().texOffs({p.uv[0]}, {p.uv[1]})'
                     f".addBox({_f(bx)}, {_f(by)}, {_f(bz)}, {_f(p.size[0])}, {_f(p.size[1])}, {_f(p.size[2])}), "
                     f"{pose}); // {p.name}")
    lines += [f"        return LayerDefinition.create(mesh, {size}, {size});", "    }"]
    return "\n".join(x for x in lines if x)


# ---------------------------------------------------------------- 見本 (等角投影)

def _rot(pt, pivot, rot):
    x, y, z = (pt[i] - pivot[i] for i in range(3))
    rx, ry, rz = (math.radians(a) for a in rot)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return (x + pivot[0], y + pivot[1], z + pivot[2])


def preview(parts: list[Part], cols: dict, scale: int = 6, yaw: float = 35.0) -> Image.Image:
    """回転を反映した箱の等角投影 (奥から順に面を塗る)。"""
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    polys = []
    for p in parts:
        o, s = p.origin, p.size
        corner = lambda i: _rot((o[0] + (s[0] if i & 1 else 0), o[1] + (s[1] if i & 2 else 0),  # noqa: E731
                                 o[2] + (s[2] if i & 4 else 0)), p.pivot, p.rotation)
        cs = [corner(i) for i in range(8)]
        faces = {"up": (2, 3, 7, 6), "down": (0, 1, 5, 4), "north": (0, 1, 3, 2), "south": (4, 5, 7, 6),
                 "east": (1, 5, 7, 3), "west": (0, 4, 6, 2)}
        base = cols.get(p.slot, cols["primary"])
        for name, idx in faces.items():
            pts3 = [cs[i] for i in idx]
            proj = []
            depth = 0
            for x, y, z in pts3:
                xr, zr = x * cy - z * sy, x * sy + z * cy
                proj.append((xr, -y + zr * 0.45))
                depth += zr * 1.0 - y * 0.01
            light = 1.0 if p.slot == "eye" else FACE_LIGHT[name]
            polys.append((depth / 4, proj, _shade(base, light)))
    xs = [x for _d, pr, _c in polys for x, _y in pr]
    ys = [y for _d, pr, _c in polys for _x, y in pr]
    w = int((max(xs) - min(xs)) * scale) + 16
    h = int((max(ys) - min(ys)) * scale) + 16
    img = Image.new("RGBA", (w, h), (36, 34, 44, 255))
    d = ImageDraw.Draw(img)
    for _depth, pr, col in sorted(polys, key=lambda t: -t[0]):
        pts = [((x - min(xs)) * scale + 8, (y - min(ys)) * scale + 8) for x, y in pr]
        d.polygon(pts, fill=col, outline=_shade(col, 0.6))
    return img
