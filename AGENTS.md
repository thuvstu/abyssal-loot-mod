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
