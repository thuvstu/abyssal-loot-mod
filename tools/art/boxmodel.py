"""箱 (Box) ベース-model の構築・UV 割当・テクスチャ生成・プレビュー描画。

Minecraft の `models/item/*.json` の `elements` 形式 (軸parallel箱 + 任意の回転) を使い、
武器・防具などを立体として作る。テクスチャは 1 枚の PNG にまとめて焼き付ける。
"""
from __future__ import annotations

import json
import math
import os
from dataclasses import dataclass, field

from PIL import Image, ImageDraw

from . import core
from .core import RGBA
from .materials import DOWN, EAST, NORTH, SOUTH, UP, WEST, Material, paint_face

# 1 UV 単位あたりのテクセル数 (64x64 テクスチャ = 4 texel / unit)
TEXELS_PER_UNIT = 4

FACE_ORDER = [NORTH, SOUTH, EAST, WEST, UP, DOWN]

# 面の法線 (モデル空間, +Y 上)
FACE_NORMAL = {
    NORTH: (0, 0, -1),
    SOUTH: (0, 0, 1),
    EAST: (1, 0, 0),
    WEST: (-1, 0, 0),
    UP: (0, 1, 0),
    DOWN: (0, -1, 0),
}


@dataclass
class Box:
    """1 つの箱。座標はモデル空間 (1 単位 = 1/16 ブロック)。"""
    name: str
    frm: tuple[float, float, float]
    to: tuple[float, float, float]
    mat: Material
    rotation: dict | None = None      # {"origin":[x,y,z], "axis":"z", "angle":45}
    shade: bool = True
    light: int = 0
    hide: tuple = ()                  # 描かない面 (内部面など)

    def size(self):
        return (self.to[0] - self.frm[0], self.to[1] - self.frm[1], self.to[2] - self.frm[2])

    def corners(self, face):
        """面の 4 頂点 (モデル空間)。MC と同じ頂点順 (0=左上,1=左下,2=右下,3=右上)。"""
        x0, y0, z0 = self.frm
        x1, y1, z1 = self.to
        if face == NORTH:      # z = z0 (北から見る: 左が +X)
            return [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)]
        if face == SOUTH:      # z = z1 (南から見る: 左が -X)
            return [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)]
        if face == WEST:       # x = x0 (西から見る: 左が +Z)
            return [(x0, y1, z1), (x0, y0, z1), (x0, y0, z0), (x0, y1, z0)]
        if face == EAST:       # x = x1 (東から見る: 左が -Z)
            return [(x1, y1, z0), (x1, y0, z0), (x1, y0, z1), (x1, y1, z1)]
        if face == UP:         # y = y1 (上から: 画像下 = +Z)
            return [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)]
        return [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)]  # DOWN

    def face_size(self, face):
        w, h, d = self.size()
        if face in (NORTH, SOUTH):
            return (w, h)
        if face in (EAST, WEST):
            return (d, h)
        return (w, d)


def box(name, frm, to, mat, rotation=None, shade_=True, light=0, hide=()):
    """糖衣つきの箱生成 (座標は 0..16 のモデル単位)。"""
    return Box(name=name, frm=tuple(frm), to=tuple(to), mat=mat, rotation=rotation,
               shade=shade_, light=light, hide=tuple(hide))


# --------------------------------------------------------------------- UV 割当

class _Packer:
    """行ベースの単純パッカー (texel 単位)。"""

    def __init__(self, width: int, height: int):
        self.w = width
        self.h = height
        self.x = 0
        self.y = 0
        self.row_h = 0

    def place(self, w: int, h: int):
        if w > self.w or h > self.h:
            return None
        if self.x + w > self.w:
            self.x = 0
            self.y += self.row_h
            self.row_h = 0
        if self.y + h > self.h:
            return None
        r = (self.x, self.y, self.x + w, self.y + h)
        self.x += w
        self.row_h = max(self.row_h, h)
        return r


class Model:
    """箱の集合。テクスチャ焼き付けと JSON 出力まで担う。"""

    def __init__(self, name: str):
        self.name = name
        self.boxes: list[Box] = []
        self.texture_size = 64
        self.tpu = TEXELS_PER_UNIT          # 1 モデル単位あたりのテクセル数
        self.uv: dict[tuple[int, str], tuple[int, int, int, int]] = {}  # (box index, face) -> texel rect
        self.texture: Image.Image | None = None
        self._culled = False

    # -- 構築 --
    def add(self, b: Box) -> Box:
        self.boxes.append(b)
        return b

    def box(self, name, frm, to, mat, **kw) -> Box:
        return self.add(box(name, frm, to, mat, **kw))

    # -- UV / テクスチャ --
    def _face_texel_size(self, b: Box, face):
        fw, fh = b.face_size(face)
        return (max(1, int(round(fw * self.tpu))),
                max(1, int(round(fh * self.tpu))))

    # -- 面カリング (完全に隠れる面を除外してテクスチャを節約) --
    def cull_hidden_faces(self) -> int:
        """他の箱に完全に覆われる面を hide に追加する。除外した面数を返す。"""
        if self._culled:
            return 0
        axis_of = {NORTH: 2, SOUTH: 2, EAST: 0, WEST: 0, UP: 1, DOWN: 1}
        out_of = {NORTH: -1, SOUTH: 1, EAST: 1, WEST: -1, UP: 1, DOWN: -1}
        eps = 0.02
        removed = 0
        for i, a in enumerate(self.boxes):
            if a.rotation:
                continue
            for face in FACE_ORDER:
                if face in a.hide:
                    continue
                axis = axis_of[face]
                out = out_of[face]
                plane = a.frm[axis] if out < 0 else a.to[axis]
                u, v = [k for k in (0, 1, 2) if k != axis]
                lo = (a.frm[u], a.frm[v])
                hi = (a.to[u], a.to[v])
                for j, o in enumerate(self.boxes):
                    if j == i or o.rotation:
                        continue
                    if out > 0:
                        if abs(o.frm[axis] - plane) > eps:
                            continue
                    else:
                        if abs(o.to[axis] - plane) > eps:
                            continue
                    if (o.frm[u] <= lo[0] + eps and o.to[u] >= hi[0] - eps
                            and o.frm[v] <= lo[1] + eps and o.to[v] >= hi[1] - eps):
                        a.hide = a.hide + (face,)
                        removed += 1
                        break
        self._culled = True
        return removed

    def build_texture(self, size: int | None = None, max_size: int = 256) -> Image.Image:
        """UV を割り当ててテクスチャを焼き付ける。

        テクセル密度 (tpu) 6 → 5 → 4 の順に、より高い密度を優先して試す。
        小さいモデルは 128px に収まり、密度の必要な大きなモデルは 256px になる。
        """
        self.cull_hidden_faces()
        if size:
            combos = [(size, TEXELS_PER_UNIT)]
        else:
            combos = [(128, 6), (192, 6)]
            if max_size >= 256:
                combos += [(256, 6), (128, 5), (192, 5), (256, 5), (128, TEXELS_PER_UNIT),
                           (192, TEXELS_PER_UNIT), (256, 4), (256, 3), (256, 2)]
            elif max_size >= 128:
                combos += [(128, 5), (128, TEXELS_PER_UNIT)]
        for cand, tpu in combos:
            self.tpu = tpu
            img, uv = self._pack(cand)
            if img is not None:
                self.texture_size = cand
                self.uv = uv
                self.texture = img
                return img
        raise RuntimeError(f"{self.name}: テクスチャに収まりません")

    def _pack(self, size: int, pad: int = 1):
        packer = _Packer(size, size)
        uv = {}
        placed = []
        # 大きな面から先に詰める (シェルフパッカーの充填率を上げる)
        items = []
        for bi, b in enumerate(self.boxes):
            for face in FACE_ORDER:
                if face in b.hide:
                    continue
                tw, th = self._face_texel_size(b, face)
                items.append((th, tw, bi, face))
        items.sort(reverse=True)
        for th, tw, bi, face in items:
            r = packer.place(tw + pad, th + pad)
            if r is None:
                return None, None
            r = (r[0], r[1], r[0] + tw, r[1] + th)
            uv[(bi, face)] = r
            placed.append((bi, face, r))

        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        for bi, face, r in placed:
            b = self.boxes[bi]
            seed = (bi * 31 + FACE_ORDER.index(face) * 7 + hash(b.name) % 97) & 0xFFFF
            paint_face(img, r, b.mat, face, seed, b.size())
        return img, uv

    def uv_json(self, bi: int, face: str):
        r = self.uv.get((bi, face))
        if r is None:
            return None
        x0, y0, x1, y1 = r
        # UV 空間は 0..16 (テクスチャ全体 = 16 単位)。テクスチャ解像度に合わせ換算する。
        k = 16.0 / float(self.texture_size)
        return [round(x0 * k, 4), round(y0 * k, 4), round(x1 * k, 4), round(y1 * k, 4)]

    # -- JSON --
    @staticmethod
    def from_json(data: dict, texture: Image.Image | None = None) -> "Model":
        """生成済みのモデル JSON から Model を復元する (プレビュー用)。"""
        m = Model("loaded")
        for el in data.get("elements", []):
            b = Box(
                name="el",
                frm=tuple(el["from"]),
                to=tuple(el["to"]),
                mat=Material("flat", (255, 255, 255, 255)),
                rotation=el.get("rotation"),
                shade=el.get("shade", True),
                light=el.get("light_emission", 0),
            )
            m.boxes.append(b)
        # UV を JSON から復元 (UV 空間 0..16 を texel に換算)
        scale = (texture.width / 16.0) if texture is not None else TEXELS_PER_UNIT
        for bi, el in enumerate(data.get("elements", [])):
            for face, fd in el.get("faces", {}).items():
                uv = fd.get("uv")
                if not uv:
                    continue
                m.uv[(bi, face)] = (int(round(uv[0] * scale)), int(round(uv[1] * scale)),
                                    int(round(uv[2] * scale)), int(round(uv[3] * scale)))
        if texture is not None:
            m.texture = texture
            m.texture_size = texture.width
        return m

    def elements(self) -> list:
        out = []
        for bi, b in enumerate(self.boxes):
            el = {
                "from": [round(v, 3) for v in b.frm],
                "to": [round(v, 3) for v in b.to],
            }
            if b.rotation:
                el["rotation"] = {
                    "origin": [round(v, 3) for v in b.rotation["origin"]],
                    "axis": b.rotation["axis"],
                    "angle": b.rotation["angle"],
                }
            if not b.shade:
                el["shade"] = False
            if b.light:
                el["light_emission"] = b.light
            faces = {}
            for face in FACE_ORDER:
                if face in b.hide:
                    continue
                uv = self.uv_json(bi, face)
                if uv is None:
                    continue
                faces[face] = {"uv": uv, "texture": "#0"}
            if not faces:
                continue
            el["faces"] = faces
            out.append(el)
        return out

    def model_json(self, texture: str, display: dict | None = None, parent: str | None = None) -> dict:
        data = {
            "textures": {"0": texture, "particle": texture},
            "elements": self.elements(),
        }
        if parent:
            data["parent"] = parent
        if display:
            data["display"] = display
        return data


# --------------------------------------------------------------------- プレビュー

def _mat_mul(a, b):
    """4x4 行列 (行優先 flattened 16) の積。"""
    out = [0.0] * 16
    for i in range(4):
        for j in range(4):
            out[i * 4 + j] = sum(a[i * 4 + k] * b[k * 4 + j] for k in range(4))
    return out


def _mat_identity():
    return [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]


def _mat_translate(x, y, z):
    m = _mat_identity()
    m[3], m[7], m[11] = x, y, z
    return m


def _mat_scale(x, y, z):
    m = _mat_identity()
    m[0], m[5], m[10] = x, y, z
    return m


def _mat_rotate(rx, ry, rz):
    """degrees の回転行列。JOML の rotationXYZ と同じ意味 (Z → Y → X の順に適用)。"""
    ax, ay, az = math.radians(rx), math.radians(ry), math.radians(rz)
    cx, sx = math.cos(ax), math.sin(ax)
    cy, sy = math.cos(ay), math.sin(ay)
    cz, sz = math.cos(az), math.sin(az)
    rx_m = [1, 0, 0, 0, 0, cx, -sx, 0, 0, sx, cx, 0, 0, 0, 0, 1]
    ry_m = [cy, 0, sy, 0, 0, 1, 0, 0, -sy, 0, cy, 0, 0, 0, 0, 1]
    rz_m = [cz, -sz, 0, 0, sz, cz, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
    return _mat_mul(rx_m, _mat_mul(ry_m, rz_m))


def _apply(m, p):
    x, y, z = p
    return (m[0] * x + m[1] * y + m[2] * z + m[3],
            m[4] * x + m[5] * y + m[6] * z + m[7],
            m[8] * x + m[9] * y + m[10] * z + m[11])


def display_matrix(transform: dict) -> list:
    """MC の ItemTransform を再現 (モデルを中心へ寄せてから回転・スケール)。"""
    rot = transform.get("rotation", [0, 0, 0])
    tr = transform.get("translation", [0, 0, 0])
    sc = transform.get("scale", [1, 1, 1])
    m = _mat_translate(tr[0] / 16.0, tr[1] / 16.0, tr[2] / 16.0)
    m = _mat_mul(m, _mat_rotate(rot[0], rot[1], rot[2]))
    m = _mat_mul(m, _mat_scale(sc[0], sc[1], sc[2]))
    m = _mat_mul(m, _mat_translate(-0.5, -0.5, -0.5))
    return m


def _rotate_box_corners(b: Box, corners):
    """要素の rotation を適用 (MC と同じ: 原点まわりに axis 回転)。"""
    if not b.rotation:
        return corners
    ax = b.rotation["axis"]
    ang = math.radians(b.rotation["angle"])
    o = b.rotation["origin"]
    c, s = math.cos(ang), math.sin(ang)
    out = []
    for (x, y, z) in corners:
        dx, dy, dz = x - o[0], y - o[1], z - o[2]
        if ax == "x":
            dy, dz = dy * c - dz * s, dy * s + dz * c
        elif ax == "y":
            dx, dz = dx * c + dz * s, -dx * s + dz * c
        else:
            dx, dy = dx * c - dy * s, dx * s + dy * c
        out.append((dx + o[0], dy + o[1], dz + o[2]))
    return out


def render_preview(model: Model, view: str = "gui", size: int = 256, background=(38, 38, 50, 255),
                   transform: dict | None = None) -> Image.Image:
    """モデルのプレビューを描画 (GUI 視点 / 三人称視点)。"""
    tex = model.texture
    if tex is None:
        raise RuntimeError("build_texture() を先に呼ぶこと")
    img = Image.new("RGBA", (size, size), background)

    if view == "gui":
        tr = transform or {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
        m = display_matrix(tr)
        quads = _collect_quads(model, m, scale_to_blocks=True)

        def proj(p):
            return ((p[0] + 0.5) * size, (0.5 - p[1]) * size)
    elif view == "front":
        m = display_matrix({"rotation": [0, 0, 0], "scale": [1, 1, 1]})
        quads = _collect_quads(model, m, scale_to_blocks=True)
        xs = [p[0] for q in quads for p in q[0]]
        ys = [p[1] for q in quads for p in q[0]]
        x0, x1 = (min(xs), max(xs)) if xs else (0, 1)
        y0, y1 = (min(ys), max(ys)) if ys else (0, 1)
        sx = size / max(1e-6, (x1 - x0))
        sy = size / max(1e-6, (y1 - y0))
        s = min(sx, sy)
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2

        def proj(p):
            return (size / 2 + (p[0] - cx) * s, size / 2 - (p[1] - cy) * s)
    else:
        m = display_matrix({"rotation": [22, 34, 0], "scale": [1, 1, 1]})
        quads = _collect_quads(model, m, scale_to_blocks=True)

        def proj(p):
            return ((p[0] + 0.5) * size, (0.5 - p[1]) * size)

    # 奥から順に描画 (painter's algorithm)。視点は +Z 側なので z の小さい順。
    quads.sort(key=lambda q: sum(p[2] for p in q[0]) / 4.0)
    for pts, uvs in quads:
        sp = [proj(p) for p in pts]
        _draw_textured_quad(img, sp, uvs, tex)
    return img


def _collect_quads(model: Model, m, scale_to_blocks: bool = True, offset=(0, 0, 0)):
    """変換を適用した面 information を集める。"""
    quads = []
    for bi, b in enumerate(model.boxes):
        for face in FACE_ORDER:
            if face in b.hide:
                continue
            r = model.uv.get((bi, face))
            if r is None:
                continue
            corners = _rotate_box_corners(b, b.corners(face))
            pts = []
            for c in corners:
                p = (c[0] / 16.0, c[1] / 16.0, c[2] / 16.0) if scale_to_blocks else c
                p = _apply(m, p)
                pts.append((p[0] + offset[0], p[1] + offset[1], p[2] + offset[2]))
            # 頂点順 (左上, 左下, 右下, 右上) にUV を合わせる (MC の FaceInfo と同じ)
            uvs = [(r[0], r[1]), (r[0], r[3]), (r[2], r[3]), (r[2], r[1])]
            quads.append((pts, uvs))
    return quads


def render_first_person(model: Model, transform: dict, size=(640, 360), fov: float = 70.0,
                        item_pos=(0.56, -0.52, -0.72), background=(38, 38, 50, 255)) -> Image.Image:
    """一人称視点のプレビュー (カメラは -Z 方向を向く)。"""
    tex = model.texture
    if tex is None:
        raise RuntimeError("build_texture() を先に呼ぶこと")
    w, h = size
    img = Image.new("RGBA", size, background)
    m = display_matrix(transform)
    m = _mat_mul(_mat_translate(*item_pos), m)
    quads = _collect_quads(model, m, scale_to_blocks=True, offset=(0, 0, 0))
    f = 1.0 / math.tan(math.radians(fov / 2))
    aspect = w / h
    # 奥から順に描画 (視点は -Z 方向なので z の小さい順 = 遠い順)
    quads.sort(key=lambda q: sum(p[2] for p in q[0]) / 4.0)
    for pts, uvs in quads:
        sp = []
        ok = True
        for p in pts:
            if -p[2] <= 0.02:
                ok = False
                break
            sp.append((w / 2 + (p[0] * f / (-p[2])) / aspect * (w / 2),
                       h / 2 - (p[1] * f / (-p[2])) * (h / 2)))
        if ok:
            _draw_textured_quad(img, sp, uvs, tex)
    return img


def _ortho(size):
    def f(p):
        return (p[0] / 16.0 * size, (16.0 - p[1]) / 16.0 * size)
    return f


def _draw_textured_quad(img: Image.Image, sp, uvs, tex):
    """重心座標でテクスチャを貼り付ける 1 枚ポリゴン (三角形 2 つに分割)。"""
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = sp
    (u0, v0), (u1, v1), (u2, v2), (u3, v3) = uvs
    minx = max(0, int(min(x0, x1, x2, x3)))
    maxx = min(img.width - 1, int(max(x0, x1, x2, x3)) + 1)
    miny = max(0, int(min(y0, y1, y2, y3)))
    maxy = min(img.height - 1, int(max(y0, y1, y2, y3)) + 1)
    if maxx < minx or maxy < miny:
        return
    px = tex.load()

    def bary(pxc, pyc, ax, ay, bx, by, cx, cy):
        det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(det) < 1e-9:
            return None
        a = ((by - cy) * (pxc - cx) + (cx - bx) * (pyc - cy)) / det
        b = ((cy - ay) * (pxc - cx) + (ax - cx) * (pyc - cy)) / det
        c = 1.0 - a - b
        return a, b, c

    for y in range(miny, maxy + 1):
        for x in range(minx, maxx + 1):
            pxc, pyc = x + 0.5, y + 0.5
            w = bary(pxc, pyc, x0, y0, x1, y1, x2, y2)
            if w is not None and w[0] >= -0.02 and w[1] >= -0.02 and w[2] >= -0.02:
                a, b, c = w
                u = a * u0 + b * u1 + c * u2
                v = a * v0 + b * v1 + c * v2
            else:
                w = bary(pxc, pyc, x0, y0, x2, y2, x3, y3)
                if w is None or w[0] < -0.02 or w[1] < -0.02 or w[2] < -0.02:
                    continue
                a, b, c = w
                u = a * u0 + b * u2 + c * u3
                v = a * v0 + b * v2 + c * v3
            tx = int(min(tex.width - 1, max(0, round(u - 0.5))))
            ty = int(min(tex.height - 1, max(0, round(v - 0.5))))
            col = px[tx, ty]
            if col[3] > 0:
                img.putpixel((x, y), col)


def write_model(path: str, data: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
