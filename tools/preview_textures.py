import io
import os
import sys

from PIL import Image, ImageDraw

ASSETS = os.path.join("src", "main", "resources", "assets", "abyssalloot", "textures", "item")
ITEMS = [
    "abyss_wind_blade", "abyss_ember_flail", "abyss_astral_lance", "abyss_tide_trident",
    "abyss_berserk", "abyss_bulwark", "abyss_bomb_h", "abyss_fortune",
    "abyss_heal_l", "abyss_soul_l", "abyss_bomb_f", "abyss_pwr",
    "soul_shard", "ancient_stone", "abyss_katana", "abyss_greatsword",
]
S = 12  # 拡大率
pad = 6
w = 4 * 16 * S + pad * 5
h = 4 * 16 * S + pad * 5
sheet = Image.new("RGBA", (w, h), (40, 40, 52, 255))
d = ImageDraw.Draw(sheet)
for i, name in enumerate(ITEMS):
    img = Image.open(os.path.join(ASSETS, name + ".png")).convert("RGBA")
    img = img.resize((16 * S, 16 * S), Image.NEAREST)
    x = pad + (i % 4) * (16 * S + pad)
    y = pad + (i // 4) * (16 * S + pad)
    sheet.alpha_composite(img, (x, y))
out = os.path.join("build", "texture_preview.png")
os.makedirs("build", exist_ok=True)
sheet.convert("RGB").save(out)
print("saved", out)
