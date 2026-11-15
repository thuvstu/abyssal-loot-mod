"""構造物 (structure template) の NBT を標準ライブラリだけで書き出す。

HayateMod/tools/generate_structures.py の書き出し処理を移植・整理したもの。
gzip の mtime を 0 に固定し、再生成してもバイト単位で同じ出力になる。

    data = structure_bytes(size=(9, 2, 9), blocks=[((x, 0, z), "minecraft:stone"), ...])
"""
from __future__ import annotations

import gzip
import io
import struct

# net.minecraft.SharedConstants#WORLD_VERSION (1.21.11)。jar の version.json で確認済み
DATA_VERSION = 4671

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


def _payload(tag: int, value) -> bytes:
    if tag == BYTE:
        return struct.pack(">b", value)
    if tag == SHORT:
        return struct.pack(">h", value)
    if tag == INT:
        return struct.pack(">i", value)
    if tag == LONG:
        return struct.pack(">q", value)
    if tag == FLOAT:
        return struct.pack(">f", value)
    if tag == DOUBLE:
        return struct.pack(">d", value)
    if tag == STRING:
        raw = value.encode("utf-8")
        return struct.pack(">H", len(raw)) + raw
    if tag == LIST:
        item_tag, items = value
        if not items:
            return struct.pack(">Bi", END, 0)
        return struct.pack(">Bi", item_tag, len(items)) + b"".join(_payload(item_tag, i) for i in items)
    if tag == COMPOUND:
        out = b""
        for key, (item_tag, item) in value.items():
            raw = key.encode("utf-8")
            out += struct.pack(">BH", item_tag, len(raw)) + raw + _payload(item_tag, item)
        return out + b"\x00"
    if tag == INT_ARRAY:
        return struct.pack(">i", len(value)) + b"".join(struct.pack(">i", v) for v in value)
    raise ValueError(f"未対応の NBT タグ {tag}")


def _ints(values) -> tuple[int, tuple[int, list]]:
    return LIST, (INT, list(values))


def structure_bytes(size: tuple[int, int, int], blocks: list[tuple[tuple[int, int, int], str]]) -> bytes:
    """パレット形式の構造物 NBT (gzip 済み)。blocks に無い位置は構造物ボイド (置き換えない)。"""
    palette: list[str] = []
    entries = []
    for pos, name in blocks:
        if name not in palette:
            palette.append(name)
        entries.append({"pos": _ints(pos), "state": (INT, palette.index(name))})
    root = {
        "DataVersion": (INT, DATA_VERSION),
        "size": _ints(size),
        "palette": (LIST, (COMPOUND, [{"Name": (STRING, n)} for n in palette])),
        "blocks": (LIST, (COMPOUND, entries)),
        "entities": (LIST, (COMPOUND, [])),
    }
    body = struct.pack(">BH", COMPOUND, 0) + _payload(COMPOUND, root)
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as f:
        f.write(body)
    return buf.getvalue()
