"""アセット生成スタジオ (ブラウザで操作する GUI)。Python 標準ライブラリ + Pillow だけで動く。

    python tools/abyss.py studio [--port 8765] [--no-browser]

生成器ごとのタブ (素材・剣・杖・アイコンのバリエーション・MOB・構造物・押し出し) があり、
選択肢を変えると見本がすぐ描き直される。「PNG を保存」で build/studio/ に書き出す。
フォームは各生成器の表 (形・地肌・型・属性…) から自動で組むので、表が増えれば選択肢も増える。
サーバーは 127.0.0.1 だけで待ち受ける (同じ PC のブラウザからだけ開ける)。
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys
import threading
import time
import traceback
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

from art import anim2d, decorate, palettes, extrude, hd_weapons, matgen, mobgen, staffgen, structgen, swordgen, texfx, variants  # noqa: E402
from art.project import ROOT, rel, setup_console  # noqa: E402

OUT = os.path.join(ROOT, "build", "studio")


def _sel(name, label, options, default=None):
    options = list(options)
    return {"name": name, "label": label, "type": "select", "options": options, "default": default or options[0]}


def _color(name, label, default):
    return {"name": name, "label": label, "type": "color", "default": default}


def _num(name, label, lo, hi, step, default):
    return {"name": name, "label": label, "type": "number", "min": lo, "max": hi, "step": step, "default": default}


def _check(name, label, default=False):
    return {"name": name, "label": label, "type": "check", "default": default}


def _weapon_icons():
    return sorted(hd_weapons.ICONS)


def schema() -> dict:
    """タブごとのフォーム定義。"""
    return {
        "matgen": {"title": "素材", "fields": [
            _sel("shape", "形", sorted(matgen.shapes()), "skull"),
            _sel("preset", "素材プリセット", ["(色を指定)"] + list(matgen.PRESETS)),
            _color("color", "地色", "#8a5cff"), _color("accent", "副色", "#e2c24c"),
            _sel("style", "質感", ["(形の既定)"] + list(matgen.STYLES)),
            _num("seed", "種", 0, 999, 1, 0), _check("halo", "発光の光暈"), _num("scale", "拡大", 1, 16, 1, 8)]},
        "sword": {"title": "剣", "fields": [
            _sel("preset", "名前付きプリセット", ["(使わない)"] + list(swordgen.PRESETS)),
            _sel("theme", "テーマで自動生成", ["(使わない)"] + [t["id"] for t in swordgen.THEMES]),
            _sel("silhouette", "輪郭", swordgen.PROFILES, "katana"), _sel("surface", "地肌", swordgen.SURFACES, "choji"),
            _sel("guard", "鍔", swordgen.GUARDS, "tsuba"), _sel("handle", "柄", swordgen.HANDLES, "cord"),
            _sel("pommel", "柄頭", swordgen.POMMELS, "gem"), _sel("element", "属性", swordgen.ELEMENTS),
            _sel("effect_preset", "演出", list(swordgen.EFFECT_PRESETS)),
            _color("blade_color", "刃", "#8a96ac"), _color("guard_color", "鍔", "#c9a24a"),
            _color("handle_color", "柄", "#4a3226"), _color("accent", "差し色", "#7ce8ff"),
            _num("blade_width", "刃の幅", 1, 5, 1, 3), _num("blade_length", "刃の長さ", 6, 13, 1, 10),
            _check("rune_inlay", "ルーン"), _check("crystal_shards", "結晶片"), _check("spurs", "棘"),
            _check("gem", "鍔の宝石"), _sel("detail_level", "細かさ", swordgen.DETAIL_LEVELS, "rich"),
            _sel("finish", "仕上げ", swordgen.FINISHES), _num("frame", "コマ", 0, 7, 1, 0), _num("scale", "拡大", 1, 8, 1, 4)]},
        "staff": {"title": "杖", "fields": [
            _sel("type", "型", staffgen.STAFF_TYPES, "lunar"), _sel("element", "属性", staffgen.ELEMENTS, "ice"),
            _sel("motif", "意匠", ["(使わない)"] + list(staffgen.DESIGN_MOTIFS)),
            _sel("effect", "演出", ["(使わない)"] + list(staffgen.EFFECT_PRESETS)),
            _sel("form", "形態", staffgen.FORMS), _sel("ornament", "装飾", staffgen.ORNAMENTS),
            _sel("gemCut", "カット", staffgen.GEM_CUTS), _sel("finish", "仕上げ", staffgen.FINISHES),
            _sel("rarity", "希少度", staffgen.RARITIES, "rare"), _num("frame", "コマ", 0, 7, 1, 1),
            _num("scale", "拡大", 1, 8, 1, 4)]},
        "variants": {"title": "バリエーション", "fields": [
            _sel("weapon", "元の武器", _weapon_icons(), "abyss_katana"),
            _sel("kind", "種類", ["強化段階", "特性", "属性違い", "素材違い", "形態", "状態", "限界突破", "攻撃モーション"]),
            _num("level", "段 / 番号", 0, 9, 1, 3), _num("frame", "コマ (0..7)", 0, 7, 1, 2), _num("scale", "拡大", 1, 8, 1, 4)]},
        "mob": {"title": "MOB", "fields": [
            _sel("arch", "系統", mobgen.ARCHETYPES, "humanoid"), _num("variant", "体型 (番号)", 0, 9, 1, 0),
            _color("primary", "主色", "#3d6b4f"), _color("eye", "目", "#f4ff8a"), _num("yaw", "向き", -180, 180, 15, 35),
            _check("texture", "テクスチャを表示")]},
        "struct": {"title": "構造物", "fields": [
            _sel("sample", "見本", list(structgen.SAMPLES) + list(structgen.SAMPLE_BUILDERS), "abyss_room"),
            _num("scale", "拡大", 4, 16, 1, 8)]},
        "texfx": {"title": "エフェクト", "fields": [
            _sel("source", "元の絵", ["(素材の形)"] + _weapon_icons(), "abyss_katana"),
            _sel("shape", "素材の形 (元の絵が素材のとき)", sorted(matgen.shapes()), "orb"),
            _sel("add", "足すエフェクト", ["(選んで追加)"] + list(texfx.EFFECTS)),
            {"name": "fx", "label": "重ねがけ (| 区切り)", "type": "text", "default": "iridescent:strength=0.4|sparkle:count=3"},
            _num("scale", "拡大", 1, 16, 1, 4)]},
        "decorate": {"title": "装飾・配色", "fields": [
            _sel("weapon", "アイコン", _weapon_icons(), "abyss_katana"),
            _sel("add", "足す装飾", ["(選んで追加)"] + list(decorate.BACK + decorate.FRONT)),
            {"name": "fx", "label": "装飾 (| 区切り)", "type": "text", "default": "halo|thorns:count=3"},
            _color("color", "装飾の色", "#7ce8ff"),
            _sel("material", "素材パレットで塗り替え", ["(使わない)"] + list(palettes.MATERIALS)),
            _num("seed", "種", 0, 99, 1, 1), _num("scale", "拡大", 1, 8, 1, 4)]},
        "anim2d": {"title": "2D アニメ", "fields": [
            _sel("weapon", "アイコン", _weapon_icons(), "abyss_sword"),
            _sel("add", "足すレイヤー", ["(選んで追加)"] + list(anim2d.LAYERS)),
            {"name": "fx", "label": "レイヤー (| 区切り)", "type": "text", "default": "sheen:width=3|gem|sparkle:phase=0.5"},
            _color("color", "色", "#7ce8ff"), _num("frame", "コマ (0..7)", 0, 7, 1, 0), _num("scale", "拡大", 1, 8, 1, 4)]},
        "extrude": {"title": "押し出し", "fields": [
            _sel("weapon", "アイコン", _weapon_icons(), "abyss_crystal_sword"),
            _sel("style", "厚みの付け方", extrude.STYLES, "blade"), _num("thickness", "厚み", 1, 6, 1, 3),
            _num("size", "縮める一辺", 8, 32, 8, 16)]},
    }


def _int(q, k, d):
    try:
        return int(float(q.get(k, d)))
    except ValueError:
        return d


def _hex(v):
    return int(str(v).lstrip("#"), 16)


def render(tool: str, q: dict) -> Image.Image:
    if tool == "matgen":
        preset = q.get("preset")
        style = q.get("style")
        img = matgen.render(q["shape"], base=None if preset and preset != "(色を指定)" else q["color"],
                            secondary=None if preset and preset != "(色を指定)" else q["accent"],
                            preset=preset if preset and preset != "(色を指定)" else None,
                            style=None if not style or style.startswith("(") else style,
                            seed=_int(q, "seed", 0), halo=q.get("halo") == "1")
        s = _int(q, "scale", 8)
        return img.resize((img.width * s, img.height * s), Image.NEAREST)
    if tool == "sword":
        if q.get("preset", "(使わない)") != "(使わない)":
            o = swordgen.preset(q["preset"])
        elif q.get("theme", "(使わない)") != "(使わない)":
            o = swordgen.generate(q["theme"], _int(q, "frame", 0) * 7919 + 1)
        else:
            o = swordgen.options(
                silhouette=q["silhouette"], surface=q["surface"], guard=q["guard"], handle=q["handle"],
                pommel=q["pommel"], element=q["element"], effect_preset=q["effect_preset"],
                blade_color=_hex(q["blade_color"]), guard_color=_hex(q["guard_color"]),
                handle_color=_hex(q["handle_color"]), accent=_hex(q["accent"]),
                blade_width=_int(q, "blade_width", 3), blade_length=_int(q, "blade_length", 10),
                rune_inlay=q.get("rune_inlay") == "1", crystal_shards=q.get("crystal_shards") == "1",
                spurs=q.get("spurs") == "1", gem=q.get("gem") == "1", detail_level=q["detail_level"],
                finish=q["finish"])
        img = swordgen.render(o, 64, _int(q, "frame", 0))
        s = _int(q, "scale", 4)
        return img.resize((64 * s, 64 * s), Image.NEAREST)
    if tool == "staff":
        kw = {k: q[k] for k in ("form", "ornament", "gemCut", "finish", "rarity") if k in q}
        motif = None if q.get("motif", "(").startswith("(") else q["motif"]
        effect = None if q.get("effect", "(").startswith("(") else q["effect"]
        cfg = staffgen.config(q["type"], element=q["element"], motif=motif, effect=effect, **kw)
        img = staffgen.render(cfg, _int(q, "frame", 1))
        s = _int(q, "scale", 4)
        return img.resize((64 * s, 64 * s), Image.NEAREST)
    if tool == "variants":
        wid = q["weapon"]
        base = hd_weapons.icon(wid, 0x7ce8ff)
        acc = variants._hx(0x7ce8ff)
        n, t = _int(q, "level", 3), _int(q, "frame", 2) / 8
        kind = q["kind"]
        if kind == "強化段階":
            img = variants.tier(base, variants.tier_look(min(5, n)), acc, wid, t)
        elif kind == "特性":
            tid = list(variants.TRAITS)[n % len(variants.TRAITS)]
            _nm, col, pk = variants.TRAITS[tid]
            img = base.copy()
            au = variants.aura(base, t)
            px = au.load()
            for y in range(au.height):
                for x in range(au.width):
                    if px[x, y][3]:
                        px[x, y] = variants._hx(col) + (px[x, y][3],)
            img.alpha_composite(au)
            img.alpha_composite(variants.trait_particles(pk, t).resize(base.size, Image.NEAREST))
        elif kind in ("属性違い", "素材違い"):
            table = variants.ELEMENTS if kind == "属性違い" else variants.MATERIALS
            _nm, *pal = list(table.values())[n % len(table)]
            img = variants.recolor(base, *pal)
        elif kind == "形態":
            k = ("serrated", "broken", "twin", "dual", "great", "dagger")[n % 6]
            img = variants.form(base, k, acc, wid) if k in ("serrated", "broken", "twin") else variants.form2(base, k)
        elif kind == "状態":
            k = ("frozen", "overheat", "blessed", "stealth", "charged", "enchanted")[n % 6]
            img = variants.enchanted(base, t) if k == "enchanted" else variants.mode(base, k, t, wid)
        elif kind == "限界突破":
            img = variants.limit_break(base, ("break", "awaken")[n % 2], acc, wid, t)
        else:
            k = list(variants.POSES)[n % len(variants.POSES)]
            img = variants.attack_pose(base, k, acc, wid)[_int(q, "frame", 2) % 8]
        s = _int(q, "scale", 4)
        return img.resize((img.width * s, img.height * s), Image.NEAREST)
    if tool == "mob":
        arch = q["arch"]
        vs = [v["id"] for v in mobgen.ARCHETYPES[arch]["variants"]]
        parts = mobgen.build(arch, vs[_int(q, "variant", 0) % len(vs)] if vs else None)
        cols = mobgen.colors(arch, primary=q["primary"], eye=q["eye"])
        if q.get("texture") == "1":
            size = mobgen.pack(parts)
            tex = mobgen.texture(parts, cols, size, seed=arch)
            return tex.resize((size * 4, size * 4), Image.NEAREST)
        return mobgen.preview(parts, cols, scale=7, yaw=_int(q, "yaw", 35))
    if tool == "struct":
        name = q["sample"]
        s = structgen.SAMPLE_BUILDERS[name]() if name in structgen.SAMPLE_BUILDERS else structgen.from_recipe(structgen.SAMPLES[name])
        return structgen.preview(s, _int(q, "scale", 8))
    if tool == "texfx":
        if q.get("source", "(").startswith("("):
            base = matgen.render(q.get("shape", "orb"), preset="mithril", seed=1)
        else:
            base = hd_weapons.icon(q["source"], 0x7ce8ff)
        img = texfx.apply(base, q.get("fx", ""))
        s = _int(q, "scale", 4)
        return img.resize((img.width * s, img.height * s), Image.NEAREST)
    if tool == "decorate":
        base = hd_weapons.icon(q["weapon"], _hex(q.get("color", "#7ce8ff")))
        mat = q.get("material", "(")
        if not mat.startswith("("):  # advanced の素材の 7 段色階へ明度で写す
            r = palettes.material(mat)["primary"]
            px = base.load()
            for y in range(base.height):
                for x in range(base.width):
                    c = px[x, y]
                    if c[3]:
                        v = r[min(6, int((0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 256 * 7))]
                        px[x, y] = ((v >> 16) & 255, (v >> 8) & 255, v & 255, c[3])
        img = decorate.decorate(base, q.get("fx", ""), _hex(q.get("color", "#7ce8ff")), _int(q, "seed", 1))
        s = _int(q, "scale", 4)
        return img.resize((img.width * s, img.height * s), Image.NEAREST)
    if tool == "anim2d":
        col = _hex(q.get("color", "#7ce8ff"))
        fs = anim2d.frames(hd_weapons.icon(q["weapon"], col), q.get("fx", ""), col, 8)
        img = fs[_int(q, "frame", 0) % 8]
        s = _int(q, "scale", 4)
        return img.resize((img.width * s, img.height * s), Image.NEAREST)
    if tool == "extrude":
        icon = hd_weapons.icon(q["weapon"], 0x7ce8ff)
        n = _int(q, "size", 16)
        small = icon.resize((n, n), Image.BOX)
        model = extrude.build(small, "x", q["style"], _int(q, "thickness", 3))
        return extrude.preview(small, model, scale=12)
    raise ValueError(f"未知のタブ {tool}")


PAGE = """<!doctype html><html lang="ja"><head><meta charset="utf-8"><title>Abyss Asset Studio</title>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
:root{--bg:#14121f;--panel:#1d1a2e;--edge:#3d3763;--text:#e8e6f4;--sub:#a8a4c8;--accent:#b48cff;--gold:#ffd23c}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:14px/1.5 system-ui,"Yu Gothic UI",sans-serif}
header{display:flex;gap:6px;align-items:center;padding:10px 16px;border-bottom:1px solid var(--edge);flex-wrap:wrap}
header h1{font-size:16px;margin:0 16px 0 0;color:var(--gold)}
.tab{background:none;border:1px solid var(--edge);color:var(--sub);padding:5px 12px;border-radius:6px;cursor:pointer}
.tab.on{background:var(--accent);color:#120e20;border-color:var(--accent);font-weight:600}
main{display:grid;grid-template-columns:minmax(260px,340px) 1fr;gap:16px;padding:16px}
form{background:var(--panel);border:1px solid var(--edge);border-radius:10px;padding:12px;display:grid;gap:8px;align-content:start}
label{display:grid;grid-template-columns:110px 1fr;gap:8px;align-items:center;color:var(--sub);font-size:13px}
select,input[type=number],input[type=text]{width:100%;background:#120f1f;color:var(--text);border:1px solid var(--edge);border-radius:5px;padding:4px}
input[type=color]{width:100%;height:28px;background:none;border:1px solid var(--edge);border-radius:5px}
.view{background:var(--panel);border:1px solid var(--edge);border-radius:10px;padding:12px;display:grid;gap:10px;align-content:start;justify-items:center}
.view img{image-rendering:pixelated;max-width:100%;background:repeating-conic-gradient(#221e36 0 25%,#1a172a 0 50%) 0 0/16px 16px;border-radius:6px}
.row{display:flex;gap:8px;flex-wrap:wrap;justify-content:center}button.act{background:var(--gold);border:0;border-radius:6px;padding:7px 14px;font-weight:600;cursor:pointer}
button.ghost{background:none;border:1px solid var(--edge);color:var(--sub);border-radius:6px;padding:7px 14px;cursor:pointer}
#msg{color:var(--sub);font-size:12px;min-height:1.4em}#err{color:#ff8a9a;white-space:pre-wrap;font-size:12px}
@media (max-width:760px){main{grid-template-columns:1fr}}
</style></head><body>
<header><h1>Abyss Asset Studio</h1><nav id="tabs"></nav></header>
<main><form id="form"></form><section class="view"><img id="img" alt="見本">
<div class="row"><button class="act" id="save" type="button">PNG を保存</button>
<button class="ghost" id="anim" type="button">コマを再生</button></div><div id="msg"></div><div id="err"></div></section></main>
<script>
let S={},tab=null,timer=null,playing=null;
const form=document.getElementById('form'),img=document.getElementById('img');
function params(){const p=new URLSearchParams();for(const el of form.elements){if(!el.name)continue;p.set(el.name,el.type==='checkbox'?(el.checked?'1':'0'):el.value)}return p}
function refresh(){clearTimeout(timer);timer=setTimeout(()=>{const p=params();p.set('tool',tab);p.set('t',Date.now());
 fetch('/api/render?'+p).then(r=>r.ok?r.blob():r.text().then(t=>{throw new Error(t)})).then(b=>{img.src=URL.createObjectURL(b);document.getElementById('err').textContent=''})
 .catch(e=>document.getElementById('err').textContent=e.message)},120)}
function build(name){tab=name;form.innerHTML='';for(const b of document.querySelectorAll('.tab'))b.classList.toggle('on',b.dataset.t===name);
 try{var saved=JSON.parse(localStorage.getItem('studio:'+name)||'{}')}catch(e){var saved={}}
 for(const f of S[name].fields){const l=document.createElement('label');l.textContent=f.label;let el;
  if(f.type==='select'){el=document.createElement('select');for(const o of f.options){const op=document.createElement('option');op.value=op.textContent=o;el.appendChild(op)}}
  else{el=document.createElement('input');el.type=f.type==='check'?'checkbox':f.type;if(f.type==='number'){el.min=f.min;el.max=f.max;el.step=f.step}}
  if(f.name==='add')el.addEventListener('change',()=>{const fx=form.elements['fx'];if(!el.value.startsWith('(')){fx.value=(fx.value?fx.value+'|':'')+el.value;el.value=el.options[0].value;fx.dispatchEvent(new Event('input'))}});
  el.name=f.name;const v=saved[f.name]??f.default;if(f.type==='check')el.checked=!!v&&v!=='0';else el.value=v;
  el.addEventListener('input',()=>{const s={};for(const e of form.elements)if(e.name)s[e.name]=e.type==='checkbox'?e.checked:e.value;
   try{localStorage.setItem('studio:'+name,JSON.stringify(s))}catch(e){}refresh()});l.appendChild(el);form.appendChild(l)}
 try{localStorage.setItem('studio:tab',name)}catch(e){}refresh()}
document.getElementById('save').onclick=()=>{const p=params();p.set('tool',tab);fetch('/api/save?'+p,{method:'POST'}).then(r=>r.json()).then(j=>document.getElementById('msg').textContent=j.message)};
document.getElementById('anim').onclick=()=>{const f=form.elements['frame'];if(!f){document.getElementById('msg').textContent='このタブにはコマがない';return}
 if(playing){clearInterval(playing);playing=null;return}playing=setInterval(()=>{f.value=(+f.value+1)%8;refresh()},260)};
fetch('/api/schema').then(r=>r.json()).then(s=>{S=s;const nav=document.getElementById('tabs');
 for(const k in s){const b=document.createElement('button');b.className='tab';b.dataset.t=k;b.textContent=s[k].title;b.onclick=()=>build(k);nav.appendChild(b)}
 let first=Object.keys(s)[0];try{first=localStorage.getItem('studio:tab')||first}catch(e){}build(s[first]?first:Object.keys(s)[0])});
</script></body></html>"""


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):  # 標準のアクセスログは出さない
        pass

    def _send(self, code, body: bytes, ctype: str):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _query(self):
        u = urlparse(self.path)
        return u.path, {k: v[-1] for k, v in parse_qs(u.query).items()}

    def do_GET(self):
        path, q = self._query()
        try:
            if path == "/":
                return self._send(200, PAGE.encode("utf-8"), "text/html; charset=utf-8")
            if path == "/api/schema":
                return self._send(200, json.dumps(schema(), ensure_ascii=False).encode("utf-8"), "application/json")
            if path == "/api/render":
                buf = io.BytesIO()
                render(q.pop("tool"), q).save(buf, "PNG")
                return self._send(200, buf.getvalue(), "image/png")
            self._send(404, b"not found", "text/plain")
        except Exception as e:  # 生成器の例外 (未知の値など) は画面に出す
            self._send(400, f"{e}\n{traceback.format_exc(limit=2)}".encode("utf-8"), "text/plain; charset=utf-8")

    def do_POST(self):
        path, q = self._query()
        if path != "/api/save":
            return self._send(404, b"not found", "text/plain")
        try:
            tool = q.pop("tool")
            img = render(tool, q)
            os.makedirs(OUT, exist_ok=True)
            name = f"{tool}_{time.strftime('%Y%m%d_%H%M%S')}.png"
            img.save(os.path.join(OUT, name))
            msg = {"message": f"保存: {rel(os.path.join(OUT, name))}"}
        except Exception as e:
            msg = {"message": f"保存できなかった: {e}"}
        self._send(200, json.dumps(msg, ensure_ascii=False).encode("utf-8"), "application/json")


def main(argv=None) -> int:
    setup_console()
    ap = argparse.ArgumentParser(prog="abyss.py studio", description="アセット生成スタジオ (ブラウザ GUI)")
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--no-browser", action="store_true")
    a = ap.parse_args(argv)
    srv = ThreadingHTTPServer(("127.0.0.1", a.port), Handler)
    url = f"http://127.0.0.1:{a.port}/"
    print(f"スタジオを開いた: {url}  (終了は Ctrl+C)")
    if not a.no_browser:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
