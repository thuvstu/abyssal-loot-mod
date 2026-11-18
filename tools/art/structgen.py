"""構造物ジェネレータ (MCAssetGen の minecraft-nbt-structure-generator を移植・改良したもの)。

ブロックを並べる操作 (箱・壁・楕円体・円柱・線・塗りつぶし・置換・回転/反転・複製) を **レシピ (JSON)** で書き、
構造物 NBT (data/<ns>/structure/*.nbt。/place template やジグソーで使う) と mcfunction (fill/setblock) に書き出す。
既存の .nbt を読んで中身を調べる・見本画像を出すこともできる。

レシピの例:
    {"size": [9, 6, 9], "ops": [
        {"op": "box", "from": [0, 0, 0], "to": [8, 0, 8], "block": "minecraft:deepslate_tiles"},
        {"op": "box", "from": [0, 1, 0], "to": [8, 4, 8], "block": "minecraft:polished_blackstone_bricks", "walls": true},
        {"op": "cylinder", "from": [3, 1, 3], "to": [5, 3, 5], "block": "minecraft:amethyst_block", "hollow": true},
        {"op": "set", "at": [4, 1, 0], "block": "minecraft:air"},
        {"op": "transform", "kind": "rotY"}]}

元実装からの改良点
- GUI の手作業ではなくレシピで書くので、再生成すると同じ構造物になる (差分管理できる)。gzip の時刻も 0 に固定
- blockstate (向き・軸・半ブロックの上下…) を持てる。回転・反転で facing / axis / 階段の shape / チェストの type を
  MC の規則どおりに書き換える (元実装と同じ規則)
- 書き出しは art/nbt.py の書式 (DataVersion 1.21.11) に揃え、Properties 付きのパレットに対応
- 読み込みは palette / palettes の両形式に対応
- 見本は等角投影の画像 (ブロック ID から決定的な色)

CLI: `python tools/abyss.py structgen build <レシピ.json> | sample <名前> | info <file.nbt> | preview <file>`
"""
from __future__ import annotations

import gzip
import io
import json
import math
import struct
import zlib
from collections import deque

from PIL import Image, ImageDraw

from . import nbt as N

AIR = "minecraft:air"
Block = tuple  # (id, ((key, value), ...))


def block(spec) -> Block | None:
    """"minecraft:oak_stairs[facing=east,half=top]" / {"id":..,"props":{..}} / None (= ボイド: 置き換えない)。
    くり抜くときは "minecraft:air" (空気を置く)、手を付けないなら null。"""
    if spec is None:
        return None
    if isinstance(spec, dict):
        bid, props = spec["id"], spec.get("props", {})
    else:
        bid, _, rest = spec.partition("[")
        props = dict(kv.split("=", 1) for kv in rest.rstrip("]").split(",") if kv) if rest else {}
    if ":" not in bid:
        bid = "minecraft:" + bid
    return (bid, tuple(sorted(props.items())))


def block_str(b: Block) -> str:
    return b[0] + ("[" + ",".join(f"{k}={v}" for k, v in b[1]) + "]" if b[1] else "")


class Structure:
    def __init__(self, sx: int, sy: int, sz: int):
        if min(sx, sy, sz) < 1 or max(sx, sy, sz) > 128:
            raise ValueError(f"大きさは 1..128 ({sx}, {sy}, {sz})")
        self.size = (sx, sy, sz)
        self.cells: dict[tuple[int, int, int], Block] = {}  # 無い位置 = 構造物ボイド (置き換えない)

    def inside(self, x, y, z):
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def set(self, x, y, z, b):
        if self.inside(x, y, z):
            if b is None:
                self.cells.pop((x, y, z), None)
            else:
                self.cells[(x, y, z)] = b

    # ------------------------------------------------------------ 操作 (元実装の opXxx)
    def box(self, a, c, b, hollow=False, walls=False):
        x0, x1 = sorted((a[0], c[0]))
        y0, y1 = sorted((a[1], c[1]))
        z0, z1 = sorted((a[2], c[2]))
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    edge_x, edge_z = x in (x0, x1), z in (z0, z1)
                    if walls and not (edge_x or edge_z):
                        continue
                    if hollow and not (edge_x or edge_z or y in (y0, y1)):
                        continue
                    self.set(x, y, z, b)

    def ellipsoid(self, a, c, b, hollow=False):
        x0, x1 = sorted((a[0], c[0]))
        y0, y1 = sorted((a[1], c[1]))
        z0, z1 = sorted((a[2], c[2]))
        cx, cy, cz = (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2
        rx, ry, rz = (max(0.5, (v1 - v0 + 1) / 2) for v0, v1 in ((x0, x1), (y0, y1), (z0, z1)))
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                    if d <= 1 and not (hollow and d < 0.55):
                        self.set(x, y, z, b)

    def cylinder(self, a, c, b, hollow=False):
        x0, x1 = sorted((a[0], c[0]))
        y0, y1 = sorted((a[1], c[1]))
        z0, z1 = sorted((a[2], c[2]))
        cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
        rx, rz = max(0.5, (x1 - x0 + 1) / 2), max(0.5, (z1 - z0 + 1) / 2)
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    d = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
                    if d > 1:
                        continue
                    if hollow and not (d > 0.55 or y in (y0, y1)):
                        continue
                    self.set(x, y, z, b)

    def line(self, a, c, b):
        n = max(abs(c[i] - a[i]) for i in range(3))
        for i in range(n + 1):
            k = i / n if n else 0
            self.set(*(round(a[j] + (c[j] - a[j]) * k) for j in range(3)), b)

    def flood(self, at, b):
        """at と同じブロック (ボイド含む) でつながった範囲を b で塗る (6 近傍)。"""
        target = self.cells.get(tuple(at))
        if target == b:
            return
        q, seen = deque([tuple(at)]), {tuple(at)}
        while q:
            p = q.popleft()
            self.set(*p, b)
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                n = (p[0] + dx, p[1] + dy, p[2] + dz)
                if n not in seen and self.inside(*n) and self.cells.get(n) == target:
                    seen.add(n)
                    q.append(n)

    def replace(self, frm: str | None, b):
        for p, c in list(self.cells.items()):
            if frm is None or c[0] == frm:
                self.set(*p, b)

    def transform(self, kind: str):
        """rotY (上から見て時計回り 90°) / flipX / flipZ。blockstate も MC の規則で書き換える。"""
        sx, sy, sz = self.size
        cells = {}
        for (x, y, z), b in self.cells.items():
            if kind == "rotY":
                np_ = (sz - 1 - z, y, x)
            elif kind == "flipX":
                np_ = (sx - 1 - x, y, z)
            elif kind == "flipZ":
                np_ = (x, y, sz - 1 - z)
            else:
                raise ValueError(f"未知の変換 '{kind}' (rotY / flipX / flipZ)")
            cells[np_] = (b[0], tuple(sorted(_map_props(dict(b[1]), kind).items())))
        if kind == "rotY":
            self.size = (sz, sy, sx)
        self.cells = cells

    def paste(self, other: "Structure", at, repeat=(1, 1, 1)):
        for ix in range(repeat[0]):
            for iy in range(repeat[1]):
                for iz in range(repeat[2]):
                    ox = at[0] + ix * other.size[0]
                    oy = at[1] + iy * other.size[1]
                    oz = at[2] + iz * other.size[2]
                    for (x, y, z), b in other.cells.items():
                        self.set(x + ox, y + oy, z + oz, b)

    def stats(self) -> dict[str, int]:
        out: dict[str, int] = {}
        for b in self.cells.values():
            out[b[0]] = out.get(b[0], 0) + 1
        return dict(sorted(out.items(), key=lambda kv: -kv[1]))

    # ------------------------------------------------------------ 書き出し
    def to_nbt(self) -> bytes:
        palette: list[Block] = []
        entries = []
        for pos in sorted(self.cells, key=lambda p: (p[1], p[2], p[0])):
            b = self.cells[pos]
            if b not in palette:
                palette.append(b)
            entries.append({"pos": N._ints(pos), "state": (N.INT, palette.index(b))})

        def pal(b):
            d = {"Name": (N.STRING, b[0])}
            if b[1]:
                d["Properties"] = (N.COMPOUND, {k: (N.STRING, v) for k, v in b[1]})
            return d
        root = {
            "DataVersion": (N.INT, N.DATA_VERSION),
            "size": N._ints(self.size),
            "palette": (N.LIST, (N.COMPOUND, [pal(b) for b in palette])),
            "blocks": (N.LIST, (N.COMPOUND, entries)),
            "entities": (N.LIST, (N.COMPOUND, [])),
        }
        body = struct.pack(">BH", N.COMPOUND, 0) + N._payload(N.COMPOUND, root)
        buf = io.BytesIO()
        with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as f:
            f.write(body)
        return buf.getvalue()

    def to_mcfunction(self) -> str:
        sx, sy, sz = self.size
        lines = [f"# tools/art/structgen.py で生成 / 大きさ {sx} {sy} {sz} / ブロック {len(self.cells)}",
                 "# 実行位置が構造物の原点 (0,0,0)。ボイドは置かない。x 方向に同じブロックが続く区間は fill にまとめる"]
        for y in range(sy):
            for z in range(sz):
                x = 0
                while x < sx:
                    b = self.cells.get((x, y, z))
                    if b is None:
                        x += 1
                        continue
                    x2 = x
                    while x2 + 1 < sx and self.cells.get((x2 + 1, y, z)) == b:
                        x2 += 1
                    s = block_str(b)
                    lines.append(f"setblock ~{x} ~{y} ~{z} {s}" if x == x2 else f"fill ~{x} ~{y} ~{z} ~{x2} ~{y} ~{z} {s}")
                    x = x2 + 1
        return "\n".join(lines) + "\n"


_ROT = {"north": "east", "east": "south", "south": "west", "west": "north"}
_FX = {"east": "west", "west": "east"}
_FZ = {"north": "south", "south": "north"}
_LR = {"inner_left": "inner_right", "inner_right": "inner_left", "outer_left": "outer_right",
       "outer_right": "outer_left", "left": "right", "right": "left"}


def _map_props(p: dict, kind: str) -> dict:
    if kind == "rotY":
        if p.get("facing") in _ROT:
            p["facing"] = _ROT[p["facing"]]
        if p.get("axis") in ("x", "z"):
            p["axis"] = "z" if p["axis"] == "x" else "x"
    else:
        m = _FX if kind == "flipX" else _FZ
        if p.get("facing") in m:
            p["facing"] = m[p["facing"]]
        for k in ("shape", "type"):
            if p.get(k) in _LR:
                p[k] = _LR[p[k]]
    return p


# ---------------------------------------------------------------- レシピ

def from_recipe(doc: dict) -> Structure:
    s = Structure(*doc["size"])
    for i, op in enumerate(doc.get("ops", [])):
        kind = op.get("op")
        b = block(op.get("block")) if "block" in op else None
        try:
            if kind == "box":
                s.box(op["from"], op["to"], b, op.get("hollow", False), op.get("walls", False))
            elif kind == "ellipsoid":
                s.ellipsoid(op["from"], op["to"], b, op.get("hollow", False))
            elif kind == "cylinder":
                s.cylinder(op["from"], op["to"], b, op.get("hollow", False))
            elif kind == "line":
                s.line(op["from"], op["to"], b)
            elif kind == "set":
                s.set(*op["at"], b)
            elif kind == "flood":
                s.flood(op["at"], b)
            elif kind == "replace":
                s.replace(op.get("from_id"), b)
            elif kind == "transform":
                s.transform(op["kind"])
            elif kind == "paste":
                s.paste(from_recipe(op["recipe"]), op["at"], tuple(op.get("repeat", (1, 1, 1))))
            else:
                raise ValueError(f"未知の操作 '{kind}'")
        except KeyError as e:
            raise ValueError(f"ops[{i}] ({kind}) に {e} が無い") from None
    return s


# ---------------------------------------------------------------- 読み込み

def _read(buf: io.BytesIO, tag: int):
    r = lambda fmt: struct.unpack(fmt, buf.read(struct.calcsize(fmt)))[0]  # noqa: E731
    if tag == N.BYTE:
        return r(">b")
    if tag == N.SHORT:
        return r(">h")
    if tag == N.INT:
        return r(">i")
    if tag == N.LONG:
        return r(">q")
    if tag == N.FLOAT:
        return r(">f")
    if tag == N.DOUBLE:
        return r(">d")
    if tag == N.BYTE_ARRAY:
        return list(buf.read(r(">i")))
    if tag == N.STRING:
        return buf.read(r(">H")).decode("utf-8")
    if tag == N.LIST:
        it, n = r(">B"), r(">i")
        return [_read(buf, it) for _ in range(n)]
    if tag == N.COMPOUND:
        out = {}
        while True:
            t = r(">B")
            if t == N.END:
                return out
            key = buf.read(r(">H")).decode("utf-8")
            out[key] = _read(buf, t)
    if tag == N.INT_ARRAY:
        return [r(">i") for _ in range(r(">i"))]
    if tag == N.LONG_ARRAY:
        return [r(">q") for _ in range(r(">i"))]
    raise ValueError(f"未対応の NBT タグ {tag}")


def read_nbt(data: bytes) -> Structure:
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    buf = io.BytesIO(data)
    if buf.read(1)[0] != N.COMPOUND:
        raise ValueError("構造物 NBT ではない (先頭が compound でない)")
    buf.read(struct.unpack(">H", buf.read(2))[0])
    root = _read(buf, N.COMPOUND)
    palette = root.get("palette") or (root.get("palettes") or [[]])[0]
    s = Structure(*root["size"])
    pal = [(p["Name"], tuple(sorted(p.get("Properties", {}).items()))) for p in palette]
    for e in root.get("blocks", []):
        b = pal[e["state"]]
        if b[0] not in ("minecraft:structure_void",):
            s.cells[tuple(e["pos"])] = b
    return s


# ---------------------------------------------------------------- 見本

_COLORS = {"stone": (125, 125, 125), "deepslate": (70, 70, 78), "blackstone": (48, 40, 48), "oak": (162, 130, 78),
           "spruce": (110, 80, 50), "glass": (190, 230, 240), "amethyst": (150, 100, 200), "gold": (240, 200, 70),
           "iron": (210, 210, 210), "lava": (230, 100, 20), "water": (60, 90, 220), "grass": (90, 160, 60),
           "dirt": (130, 90, 60), "cobble": (110, 110, 110), "brick": (150, 70, 60), "quartz": (235, 230, 220),
           "obsidian": (30, 20, 46), "sculk": (20, 50, 60), "lantern": (250, 210, 120), "torch": (250, 200, 90),
           "prismarine": (90, 170, 150), "netherrack": (110, 40, 40), "crying": (80, 30, 120), "air": (0, 0, 0)}


def _color(bid: str):
    name = bid.split(":")[-1]
    for k, c in _COLORS.items():
        if k in name:
            return c
    h = zlib.crc32(name.encode())
    return (90 + h % 120, 90 + (h >> 8) % 120, 90 + (h >> 16) % 120)


def preview(s: Structure, scale: int = 8) -> Image.Image:
    """等角投影 (奥から手前へ、上・左・右の 3 面を明るさを変えて塗る)。"""
    sx, sy, sz = s.size
    w = (sx + sz) * scale + 8
    h = (sx + sz) * scale // 2 + sy * scale + 8
    img = Image.new("RGBA", (w, h), (36, 34, 44, 255))
    d = ImageDraw.Draw(img)
    ox, oy = sz * scale + 4, sy * scale + 4

    def P(x, y, z):
        return (ox + (x - z) * scale, oy + (x + z) * scale / 2 - y * scale)
    for (x, y, z) in sorted(s.cells, key=lambda p: (p[0] + p[2], p[1])):
        if s.cells[(x, y, z)][0] == AIR:
            continue
        c = _color(s.cells[(x, y, z)][0])
        top = [P(x, y + 1, z), P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)]
        left = [P(x, y + 1, z + 1), P(x + 1, y + 1, z + 1), P(x + 1, y, z + 1), P(x, y, z + 1)]
        right = [P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1), P(x + 1, y, z + 1), P(x + 1, y, z)]
        for poly, k in ((top, 1.0), (left, 0.72), (right, 0.86)):
            col = tuple(int(v * k) for v in c) + (255,)
            d.polygon(poly, fill=col, outline=tuple(int(v * k * 0.7) for v in c) + (255,))
    return img


# ---------------------------------------------------------------- 見本のレシピ

SAMPLES = {
    # 深淵の小部屋: 床・壁・柱・紫水晶の祭壇・灯り。アリーナの部屋の雛形
    "abyss_room": {"size": [11, 6, 11], "ops": [
        {"op": "box", "from": [0, 0, 0], "to": [10, 0, 10], "block": "minecraft:deepslate_tiles"},
        {"op": "box", "from": [2, 0, 2], "to": [8, 0, 8], "block": "minecraft:polished_blackstone"},
        {"op": "box", "from": [0, 1, 0], "to": [10, 4, 10], "block": "minecraft:polished_blackstone_bricks", "walls": True},
        {"op": "box", "from": [4, 1, 0], "to": [6, 3, 0], "block": AIR},
        {"op": "cylinder", "from": [4, 1, 4], "to": [6, 1, 6], "block": "minecraft:amethyst_block"},
        {"op": "set", "at": [5, 2, 5], "block": "minecraft:budding_amethyst"},
        *[{"op": "box", "from": [x, 1, z], "to": [x, 4, z], "block": "minecraft:crying_obsidian"}
          for x, z in ((1, 1), (9, 1), (1, 9), (9, 9))],
        *[{"op": "set", "at": [x, 5, z], "block": "minecraft:soul_lantern[hanging=false]"}
          for x, z in ((1, 1), (9, 1), (1, 9), (9, 9))]]},
    # 塔: 円柱の壁・螺旋の床・屋根の楕円体
    "tower": {"size": [9, 16, 9], "ops": [
        {"op": "cylinder", "from": [0, 0, 0], "to": [8, 11, 8], "block": "minecraft:stone_bricks", "hollow": True},
        {"op": "ellipsoid", "from": [0, 11, 0], "to": [8, 15, 8], "block": "minecraft:deepslate_tiles", "hollow": True},
        *[{"op": "set", "at": [4 + round(3 * math.cos(i * 0.8)), i, 4 + round(3 * math.sin(i * 0.8))],
           "block": "minecraft:stone_brick_slab[type=bottom]"} for i in range(1, 11)],
        {"op": "box", "from": [4, 1, 0], "to": [4, 2, 0], "block": AIR}]},
    # 地下牢: 部屋 + 鉄格子の房 + 溶岩の溝
    "dungeon": {"size": [13, 5, 9], "ops": [
        {"op": "box", "from": [0, 0, 0], "to": [12, 4, 8], "block": "minecraft:cobbled_deepslate", "hollow": True},
        {"op": "box", "from": [1, 1, 1], "to": [11, 3, 7], "block": AIR},
        *[{"op": "box", "from": [x, 1, 5], "to": [x, 3, 5], "block": "minecraft:iron_bars"} for x in range(1, 12)],
        *[{"op": "box", "from": [x, 1, 6], "to": [x, 3, 7], "block": "minecraft:cobbled_deepslate"} for x in (4, 8)],
        {"op": "box", "from": [1, 0, 2], "to": [11, 0, 2], "block": "minecraft:lava"}]},
}
