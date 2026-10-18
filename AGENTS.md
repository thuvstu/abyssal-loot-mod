# 作業ルール

対象: Abyssal Loot (NeoForge 1.21.11 / Java 21)

## Gradle (デーモン残留・ハング防止)

- 常に `.\gradlew.bat --no-daemon --console=plain <タスク>` の形式で実行する
- すべてのコマンドにタイムアウトを設定する (目安: コンパイル 5 分 / 全体ビルド 10 分)
- `runClient` など終了しないタスクを実行しない (ユーザーの明示指示があるときのみ)
- 実行後は `.\gradlew.bat --stop` でデーモン・残留プロセスが無いことを確認する
- `TASKKILL /F /IM java.exe` は禁止 (無関係の Java プロセスを巻き込む)。どうしても必要な場合は対象 PID のみを終了する

## コミット & プッシュ

- 変更を加えたら毎回コミットしてプッシュする (`git commit` → `git push`)

## JDK

- `C:\Users\gogok\.jdks\temurin-21.0.12.1` を使う (`JAVA_HOME` に設定済み)
- `C:\Users\gogok\.jdks\latest` は JDK 8 へのリンクなので使わない

## コンテキストウィンドウ

- 1M (1000K) を前提に作業する。モデル側の `limit` は `~/.config/opencode/opencode.jsonc` で指定済み

## 使用言語

- 絶対に日本語にしてください!!!間違っても母国語は出さないように

## ツール利用 (OpenCode の仕様に基づく運用ルール)

OpenCode V2 の tool 仕様 (https://opencode.ai/v2/docs/tools/) に基づく運用上の注意。
tool calling は引数を完全に組み立てて 1 回で渡すのが基本。失敗したら原因を分類して対処する
(同じ引数の盲目的な再送はしない)。

- **edit / write は `path` が必須**。「path: Missing key」が出た場合は引数の組み立てミスなので、
  **同じ呼び出しをそのまま再送してよい** (再送で復帰する事例が多い)。2 連続で失敗したら別手段に切り替える
  (write でスクリプトファイルを作って実行する等)。
- **edit の `oldString` は空白・コメント込みで完全一致かつ一意**。日本語を含む置換は edit が最も安定。
  一意にならない場合は前後の行を含めて広げるか、`replaceAll: true` を使う。
- **大きな `newString` は複数回の小さな edit に分割する** (長い引数は組み立てに失敗しやすい)。
- **`python -c` のインライン実行は禁止** (PowerShell の引数解析でコードが壊れる)。
  長い処理は write でスクリプトファイルに書いてから実行する。
- **日本語リテラルを含む Python の文字列置換は化けることがある**。
  置換対象の行特定は ASCII マーカー (変数名・定数名など) で行い、必要なら `\uXXXX` エスケープで表記する。
- **編集後は grep で適用結果を検証する** (ツールの成功表示・スクリプトの "patched" 表示を信用しない)。
  まとまった編集の後は括弧整合・文字化けチェック (`health_check.py` 相当) と `build` を実行する。
- **副作用のある操作 (置換・削除) は冪等にする**: 再実行しても壊れない手順にし、
  中断された場合は git diff で現状を確認してから続ける。
- **subagent 動作中は編集系 tool が不安定になることがある** → 検証系 (explore) は並行で可、
  編集は subagent 完了後に一括で行う。subagent への作業委譲は「触ってよいファイル」を明示して競合を避ける。
- **websearch が cancelled を返す場合は webfetch (直接 URL) に代替する**。
  公式ドキュメントが必要なときは `https://opencode.ai/v2/docs/tools/` と `llms.txt` を起点にする。
- 新しい操作形態 (patch / execute / Code Mode) を使う前に公式仕様を確認する
  (`patch` は GPT 系モデル向け。他モデルには edit/write が割り当てられる)。
