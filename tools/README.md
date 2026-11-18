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
| `python tools/abyss.py matgen <list\|render\|sheet\|content>` | 素材ジェネレータ: 57 形 × 質感 × プリセットで素材アイコンを試す (`art/matgen.py`) |
| `python tools/abyss.py variants <sheet\|render> <武器id\|png>` | アイコンのバリエーション: 強化段階・特性・属性・素材・形態・状態の案出し (`art/variants.py`) |
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
- `art/matgen.py` + `art/matgen_shapes.json` — 素材ジェネレータ (MCAssetGen の素材テクスチャ生成器を移植・改良)。
  形を述語で書くと距離場で輪郭・光・影が付き、パレットと質感 (金属・宝石・古代・魔法・有機) で塗る
- `art/variants.py` — アイコンのバリエーション (MCAssetGen texcraft の evolution を移植・改良)。
  武器の鍛造レベルで育つアイコン・特性の気配と粒子、CLI 用の属性/素材違い・形態・状態

## 新しいアイテムを足すとき (推奨: JSON に 1 件足す)

素材・魔物素材・消耗品・防具セットは `src/main/resources/abyssalloot_content.json` に 1 件足して
`python tools/abyss.py all` を実行するだけでよい。Java (`Content.java` / `ModItems`) が同じ JSON を読んで
登録し、ツールがアイコン・モデル・レシピ・翻訳を生成する。

| 区分 | 主な項目 | ゲーム内での働き |
|---|---|---|
| `armor_sets` | `body` / `trim` (色), `bonus2` / `bonus4` | 4 部位の防具 + 着用テクスチャ + セット効果 |
| `trophies` | `mob` (敵 ID), `shape`, `color` | その敵が落とす (通常 10% / 精鋭 35% / 番人・層主は確定) |
| `zone_materials` | `zone` (区域 ID), `shape`, `color` | その区域の敵が落とす (通常 7% / 精鋭 30% / 層主 2 個) |
| `consumables` | `kind`, `icon`, `recipe` | 右クリックで使用。`kind` は `mix:heal=20;gauge=10;fx=speed,60,1` 形式 |
| `extra_recipes` | `ingredients`, `result`, `count` | 素材の交換・変換レシピ |

- `shape`: chunk / gem / drop / crystal / splinter / orb / fang / bone / eye / scale / ingot / feather / leaf、
  または `mat:<形>` (素材ジェネレータ。`abyss.py matgen list` で一覧。`accent` で副色・`style` で質感を指定可)
- `icon`: `icons.py` の関数名 (flask / vial / orb …) か `mask:<形>` (bowl / skewer / bread / candy / jelly など)、
  または `mat:<形>` (素材ジェネレータ)
- `fx` の効果名: strength / speed / resistance / regeneration / absorption / fire_resistance / haste /
  jump_boost / night_vision / luck / slow_falling / water_breathing / invisibility

## エンドコンテンツ武器 (最上位クラフト専用)

| 武器 | Java | 見た目 (`gen --kind endgame`) | レシピ (`gen_data.py` の `ENDGAME_RECIPES`) |
|---|---|---|---|
| 夜想曲ノクターン | `Nocturne.java` | 2D: `art/endgame.py` の `nocturne` (カウント 0〜4 で配色、1〜4 は 8 コマのアニメ) / 3D: `art/geo_weapons.py` | 終焉の核・零の結晶・超越の羽・魂の欠片の核 ほか |
| 相棒の剣 | `Aibou.java` | 2D: `art/endgame.py` の `aibou` (段階 0〜5、3 以上はアニメ) / 3D: `art/geo_weapons.py` | 終焉の核・虚無の残滓・魂の欠片の核・古核 ほか |
| 限界突破の楔 | `Aibou.java` | `art/endgame.py` の `limit_stone` | 魂の欠片の包・果ての鉄・虚空のエッセンス・混沌の宝石 |

- インベントリは 64px の 2D (陰影レンダラ `art/hd.py`)、手持ち・地面・額縁は GeckoLib の骨格付き 3D モデル
  (`items/*.json` は `select` の `gui` だけ 2D、ほかは `minecraft:special` + `geckolib:geckolib`)
- 段階は `CUSTOM_MODEL_DATA` の floats[0]。2D は `range_dispatch`、3D は `EndgameGeoRenderer` がテクスチャを差し替え、
  音符・翼・欠片の骨を出し入れする (規則は `geo_weapons.nocturne_hidden` / `aibou_hidden` と一致させる)
- 3D の資産: `geckolib/models/item/*.geo.json`・`geckolib/animations/item/*.animation.json`・
  `textures/item/geo/*_<段階>.png` (+ 発光部の `_glowmask.png`)。`art/geo.py` は見た目どおりの座標で組んで
  GeckoLib の規則 (x 反転・x/y 回転の符号反転) へ変換して書き出す。確認は `preview geo`
- GeckoLib は必須依存 (`gradle.properties` の `geckolib_version`、公式 Maven = cloudsmith)
- `GameData.WeaponStyle#isEndgame` の武器種はドロップ・商店・報酬・奥義の断片の抽選 (`DROPPABLE`) に出ない
- シールド・不死・オート反撃・クラフト時の神話化は `Endgame.java`。ゲーム内の説明は `/nocturnehelp`

## 新しいアイテムを足すとき (武器など、表に無い種類)

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

## 武器のインベントリ表示 (鍛造レベル・特性で変わる)

`gen --kind weapons` が武器ごとに `<id>_icon_t1..t3` (強化段階 +1〜2 / +3〜4 / +5。+5 はアニメ) と
`<id>_aura` (白い気配、アニメ)、全武器共通の `trait_fx_<粒子>` を作り、`items/<id>.json` の gui 表示を
`select (CUSTOM_MODEL_DATA strings[0] = 特性)` → `composite [段階の絵, 特性色で tints した気配, 粒子]` にする。
段階は `range_dispatch (floats[0] = 鍛造レベル)`。値は `GearData.syncLook` が鍛造時・lore 更新時に書く
(エンドコンテンツ武器は除外)。特性の色と粒子は `art/variants.py` の `TRAITS`。
