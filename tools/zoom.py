"""指定アイテムのモデルを拡大表示する (GUI 視点)。

使い方: python tools/zoom.py id1 id2 ... [--size 320] [--out build/model_preview/zoom.png]
"""
import io
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

from art import boxmodel

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot", "models", "item")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssalloot", "textures", "item")


def load(item_id: str):
    path = os.path.join(MODELS, f"{item_id}.json")
    with io.open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    tex_ref = data["textures"]["0"]
    tex_name = tex_ref.split("/")[-1]
    tex = Image.open(os.path.join(TEX, f"{tex_name}.png")).convert("RGBA")
    return data, boxmodel.Model.from_json(data, tex)


def main(argv):
    size = 320
    out = os.path.join(ROOT, "build", "model_preview", "zoom.png")
    ids = []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--size":
            size = int(argv[i + 1])
            i += 2
            continue
        if a == "--out":
            out = argv[i + 1]
            i += 2
            continue
        ids.append(a)
        i += 1
    if not ids:
        print("usage: python tools/zoom.py id1 id2 ... [--size N] [--out path]")
        return 1
    cols = min(3, len(ids))
    rows = (len(ids) + cols - 1) // cols
    sheet = Image.new("RGBA", (size * cols, size * rows), (34, 34, 46, 255))
    for idx, item_id in enumerate(ids):
        data, model = load(item_id)
        tr = data.get("display", {}).get("gui") or {"rotation": [0, 0, 0]}
        im = boxmodel.render_preview(model, "gui", size, transform=tr)
        sheet.alpha_composite(im, ((idx % cols) * size, (idx // cols) * size))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.convert("RGB").save(out)
    print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
