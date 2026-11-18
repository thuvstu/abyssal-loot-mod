"""プロジェクト共通の入出力ユーティリティ。

- パス定義 (ツールをどのディレクトリから実行しても同じ場所を指す)
- コンソールの UTF-8 化 (Windows の cp932 で日本語が化けるのを防ぐ)
- 差分を見て書き込む Writer (変更件数の報告・--check による乖離検出)
- Java ソースからの定義抽出 (WeaponStyle など)
- 生成済みモデルの読込・コンタクトシートの作成
"""
from __future__ import annotations

import fnmatch
import io
import json
import os
import re
import sys

from PIL import Image, ImageDraw

MOD_ID = "abyssalloot"

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", MOD_ID)
DATA = os.path.join(RES, "data")
JAVA_DIR = os.path.join(ROOT, "src", "main", "java", "com", MOD_ID)

TEX_ITEM = os.path.join(ASSETS, "textures", "item")
TEX_ENTITY = os.path.join(ASSETS, "textures", "entity")
TEX_EQUIPMENT = os.path.join(TEX_ENTITY, "equipment", "humanoid")
# equipment/*.json の "humanoid_leggings" レイヤーはこちらを参照する
TEX_EQUIPMENT_LEGS = os.path.join(TEX_ENTITY, "equipment", "humanoid_leggings")
MODEL_ITEM = os.path.join(ASSETS, "models", "item")
ITEM_DEF = os.path.join(ASSETS, "items")
LANG = os.path.join(ASSETS, "lang")
PREVIEW_OUT = os.path.join(ROOT, "build", "model_preview")

BG = (34, 34, 46, 255)
FLAT = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}


def setup_console() -> None:
    """標準出力を UTF-8 にする (PowerShell / Git Bash どちらでも日本語を読めるように)。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass


def rel(path: str) -> str:
    """表示用のリポジトリ相対パス。"""
    return os.path.relpath(path, ROOT).replace(os.sep, "/")


# ---------------------------------------------------------------- 書き込み

def json_text(data) -> str:
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


class Writer:
    """内容が変わったファイルだけ書き込み、件数を数える。

    check=True のときは書き込まず「書き換わるはずのファイル」を記録するだけ
    (生成物と生成スクリプトの乖離検出に使う)。
    """

    def __init__(self, check: bool = False, verbose: bool = False):
        self.check = check
        self.verbose = verbose
        self.changed: list[str] = []
        self.unchanged = 0
        self._staged: dict[str, bytes] = {}

    def write_bytes(self, path: str, data: bytes) -> None:
        """書き込みを予約する (同じパスは後勝ち)。実際の反映は flush()。"""
        self._staged[os.path.abspath(path)] = data

    def write_json(self, path: str, data) -> None:
        # 改行は LF 固定 (Windows でも生成物の差分を出さない)
        self.write_bytes(path, json_text(data).encode("utf-8"))

    def write_png(self, path: str, img: Image.Image) -> None:
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        self.write_bytes(path, buf.getvalue())

    def remove(self, path: str) -> None:
        """不要になった生成物を消す (無ければ何もしない。check 時は差分として数える)。"""
        if not os.path.exists(path):
            return
        self.changed.append(os.path.abspath(path))
        if self.verbose:
            print(("  差分(削除) " if self.check else "  削除 ") + rel(path))
        if not self.check:
            os.remove(path)

    def flush(self) -> "Writer":
        """予約した内容を、既存ファイルと異なるものだけ書き出す。"""
        for path, data in self._staged.items():
            try:
                with open(path, "rb") as f:
                    old = f.read()
            except FileNotFoundError:
                old = None
            # autocrlf で CRLF に展開されたテキストは同一内容とみなす
            if old == data or (old is not None and old.replace(b"\r\n", b"\n") == data):
                self.unchanged += 1
                continue
            self.changed.append(path)
            if self.verbose:
                print(("  差分 " if self.check else "  更新 ") + rel(path))
            if not self.check:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                with open(path, "wb") as f:
                    f.write(data)
        self._staged.clear()
        return self

    def summary(self, label: str = "") -> str:
        head = f"{label}: " if label else ""
        verb = "差分あり" if self.check else "更新"
        return f"{head}{verb} {len(self.changed)} / 変更なし {self.unchanged}"


# ---------------------------------------------------------------- 絞り込み

def matcher(patterns: list[str] | None):
    """--only のパターン (fnmatch / 部分一致) から判定関数を作る。"""
    if not patterns:
        return lambda _id: True
    pats = [p if any(ch in p for ch in "*?[") else f"*{p}*" for p in patterns]
    return lambda item_id: any(fnmatch.fnmatchcase(item_id, p) for p in pats)


# ---------------------------------------------------------------- Java ソース

def java_source(name: str) -> str:
    with io.open(os.path.join(JAVA_DIR, name), encoding="utf-8") as f:
        return f.read()


# エンドコンテンツ武器 (GameData.WeaponStyle#isEndgame)。奥義の断片・断片レシピを持たない
ENDGAME_STYLES = ("nocturne", "aibou")


def read_weapon_styles(include_endgame: bool = False) -> list[tuple[str, int]]:
    """GameData.WeaponStyle の (id, 色) 一覧 (定義順)。既定ではエンドコンテンツ武器を除く。"""
    return [(sid, col) for _const, sid, col in read_weapon_style_consts()
            if include_endgame or sid not in ENDGAME_STYLES]


def read_weapon_style_consts() -> list[tuple[str, str, int]]:
    """GameData.WeaponStyle の (定数名, id, 色) 一覧 (定義順)。"""
    src = java_source("GameData.java")
    start = src.index("public enum WeaponStyle")
    end = src.index("public static final WeaponStyle[] VALUES", start)
    out = []
    for const, sid, col in re.findall(
            r'^\s+([A-Z_]+)\("([a-z_]+)",.*?(0x[0-9a-fA-F]{6})\)[,;]', src[start:end], re.M):
        out.append((const, sid, int(col, 16)))
    if not out:
        raise RuntimeError("GameData.WeaponStyle を解析できませんでした")
    return out


# ---------------------------------------------------------------- 生成物の読込

def tex_file(ref: str) -> str:
    """"abyssalloot:item/foo" -> textures/item/foo.png の絶対パス。"""
    ns, _, path = ref.partition(":")
    if not path:
        ns, path = "minecraft", ns
    return os.path.join(RES, "assets", ns, "textures", path.replace("/", os.sep) + ".png")


def model_ids(include_icons: bool = True) -> list[str]:
    ids = sorted(f[:-5] for f in os.listdir(MODEL_ITEM) if f.endswith(".json"))
    return ids if include_icons else [i for i in ids if not i.endswith("_icon")]


def load_model_json(item_id: str) -> dict:
    with io.open(os.path.join(MODEL_ITEM, f"{item_id}.json"), encoding="utf-8") as f:
        return json.load(f)


def load_item_model(item_id: str):
    """生成済みの models/item/<id>.json を boxmodel.Model として読む。

    戻り値: (Model, json)。2D アイコン (item/generated) は boxes が空で texture のみ。
    """
    from . import boxmodel  # 循環 import 回避

    data = load_model_json(item_id)
    texs = data.get("textures", {})
    ref = texs.get("0") or texs.get("layer0")
    tex = None
    if ref:
        p = tex_file(ref)
        if os.path.exists(p):
            tex = Image.open(p).convert("RGBA")
    return boxmodel.Model.from_json(data, tex), data


def render_item(item_id: str, size: int, view: str = "gui", label: bool = True) -> Image.Image:
    """1 アイテムを GUI 表示 (2D は拡大・3D は display.gui 変換) で描く。"""
    from . import boxmodel

    model, data = load_item_model(item_id)
    if model.texture is None:
        img = Image.new("RGBA", (size, size), BG)
        ImageDraw.Draw(img).text((4, size // 2 - 6), "NO TEXTURE", fill=(255, 80, 80, 255))
    elif not model.boxes:
        img = Image.new("RGBA", (size, size), BG)
        img.alpha_composite(model.texture.resize((size, size), Image.NEAREST))
    else:
        tr = data.get("display", {}).get(view if view != "front" else "gui", FLAT)
        img = boxmodel.render_preview(model, view, size, transform=tr, background=BG)
    if label:
        draw_label(img, item_id)
    return img


def draw_label(img: Image.Image, text: str) -> None:
    d = ImageDraw.Draw(img)
    t = text.replace("abyss_", "")
    d.text((4, 3), t, fill=(0, 0, 0, 200))
    d.text((3, 2), t, fill=(235, 235, 245, 230))


# ---------------------------------------------------------------- シート

def contact_sheet(images: list[Image.Image], cols: int, bg=BG) -> Image.Image:
    """同サイズ想定の画像を格子状に並べる (サイズが違えば最大セルに合わせる)。"""
    if not images:
        return Image.new("RGBA", (64, 64), bg)
    cw = max(i.width for i in images)
    ch = max(i.height for i in images)
    cols = max(1, min(cols, len(images)))
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cw, rows * ch), bg)
    for n, im in enumerate(images):
        sheet.alpha_composite(im.convert("RGBA"), ((n % cols) * cw, (n // cols) * ch))
    return sheet


def save_preview(img: Image.Image, name: str, out: str | None = None) -> str:
    path = out or os.path.join(PREVIEW_OUT, name)
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    img.convert("RGB").save(path)
    print("保存:", rel(path) if path.startswith(ROOT) else path)
    return path


def label_font(size: int = 12):
    """見本帳の文字用フォント。日本語が出るフォントを探し、無ければ Pillow 既定 (日本語は化ける)。"""
    from PIL import ImageFont
    for path in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc",
                 "/System/Library/Fonts/ヒラギノ角ゴシック W3.ttc", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()
