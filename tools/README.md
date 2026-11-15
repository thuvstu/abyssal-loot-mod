# tools/ — Abyssal Loot のアセット / データ生成ツール

Python 3.10+ と Pillow (`pip install pillow`) が必要。どのディレクトリから実行してもよい。
入口は `tools/abyss.py` に一本化している。

| コマンド | 内容 |
|---|---|
| `python tools/abyss.py gen` | テクスチャ・3D モデル・アイテム定義を生成 (`generate_textures.py`) |
| `python tools/abyss.py data` | 進捗・GLM・レシピ・タグを生成 (`gen_data.py`) |
| `python tools/abyss.py verify` | リソース / データ / Java 定義の静的検証 (`verify.py`) |
| `python tools/abyss.py preview <種類>` | 見た目の確認画像を `build/model_preview/` に出力 (`preview.py`) |
| `python tools/abyss.py grips` | 武器の握り位置の一覧 |
| `python tools/abyss.py gametest` | **実サーバー**で結合テスト (`AbyssGameTests.java`)。レシピの全件読込・出撃・リタイア・層クリア・bossrush のレベル復元など |
| `python tools/abyss.py check` | **コミット前に**: 生成物が生成スクリプトと一致しているか + 全検証 |
| `python tools/abyss.py all` | 生成 + データ + 検証 |

## よく使う形

```sh
python tools/abyss.py gen --only katana -v          # id に katana を含むものだけ生成し、更新ファイルを表示
python tools/abyss.py gen --kind armor equipment    # 種別で絞る
python tools/abyss.py preview diff                  # HEAD から変わったテクスチャを [前|後] で並べる
python tools/abyss.py preview zoom abyss_sword --size 400
python tools/abyss.py preview hold abyss_katana     # 一人称 + 手の位置の十字
python tools/abyss.py verify recipes lang           # 指定チェックだけ
```

生成系は **内容が変わったファイルだけ** 書き換え、`--check` では何も書かずに差分の有無を返す
(差分ありで終了コード 1)。

## 構成

- `art/project.py` — パス・UTF-8 出力・差分書き込み (`Writer`)・Java 定義の抽出・モデル読込・シート作成
- `art/core.py` — 色と描画の共通処理
- `art/materials.py` — 素材 (鋼・金・木・革・布・水晶…) の塗り分け
- `art/boxmodel.py` — 箱モデル (MC elements JSON) の構築・UV・テクスチャ焼き込み・プレビュー描画
- `art/shapes.py` — 武器 / 防具 / 素材の立体形状
- `art/icons.py` — 2D ドット絵 / `art/entities.py` — MOB / `art/equipment.py` — 防具の着用テクスチャ
- `art/items.py` — display 変換 (握り位置の自動計算)

## 新しいアイテムを足すとき

1. Java (`ModItems`) に登録し、`lang/ja_jp.json`・`en_us.json` に名前を追加
2. `generate_textures.py` の表 (`WEAPON_MODELS` / `CONSUMABLES` など) に 1 行追加
3. レシピが必要なら `gen_data.py` に追加 (材料は `"namespace:id"` の **文字列**。`{"item": ...}` は 1.21.2+ で読込エラー)
4. `python tools/abyss.py all` → `preview` で目視 → コミット

## gametest が見ているもの

静的検証では分からない「実機で読み込まれるか・状態遷移が正しいか」を確認する。
テストは `src/main/java/com/abyssalloot/AbyssGameTests.java` の `SPECS` に 1 行足して追加する。
床の構造物 (`data/abyssalloot/structure/gametest_floor.nbt`) は `gen_data.py` が生成する。

## verify が見ているもの

拡張子の無いデータファイル / 旧書式のレシピ材料 / 未登録アイテムの参照 / 材料が同一のレシピ /
装備レイヤー (`humanoid`・`humanoid_leggings`) のテクスチャ実在 / 翻訳漏れ (Java が使うキー含む) /
進捗の親・アイコン / GLM の登録 / `AbyssModels` の部品名・UV / MOB テクスチャ / 奥義・小技の網羅。
