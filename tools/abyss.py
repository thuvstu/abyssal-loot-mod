#!/usr/bin/env python3
"""Abyssal Loot 開発ツールの入口。

    python tools/abyss.py gen [--only PAT] [--kind ...] [--check] [-v]   テクスチャ / モデル生成
    python tools/abyss.py data [--check] [-v]                           データパック生成
    python tools/abyss.py verify [チェック名 ...] [--strict]               静的検証
    python tools/abyss.py preview <種類> [id ...] [--only PAT]           プレビュー画像
    python tools/abyss.py grips                                         武器の握り位置の一覧
    python tools/abyss.py matgen <list|render|sheet|content>            素材ジェネレータ (新素材の形・色を試す)
    python tools/abyss.py variants <sheet|render> <武器id|png>           アイコンのバリエーション (強化・特性・属性…)
    python tools/abyss.py swordgen <list|render|sheet>                  剣ジェネレータ (輪郭42・地肌49…)
    python tools/abyss.py staffgen <list|render|sheet>                  杖ジェネレータ (型45・属性28…)
    python tools/abyss.py gametest                                      実サーバーで結合テスト (GameTest。1 分弱)
    python tools/abyss.py check                                         生成物の乖離 + 全検証 (コミット前に)
    python tools/abyss.py all                                           生成 + データ + 検証

各サブコマンドの詳細は `python tools/abyss.py <cmd> -h`。
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from art.project import setup_console  # noqa: E402


def _check(write: bool) -> int:
    import gen_data
    import generate_textures
    import verify

    flag = [] if write else ["--check"]
    print("== テクスチャ / モデル")
    rc = generate_textures.main(flag)
    print("\n== データパック")
    rc |= gen_data.main(flag)
    print("\n== 検証")
    rc |= verify.main(["-q"])
    print("\n" + ("すべて OK" if rc == 0 else "問題があります (上の出力を確認)"))
    return rc


def _gametest() -> int:
    """runGameTestServer を実行し、結果行だけを表示する (AGENTS.md の Gradle 運用に従う)。"""
    import re
    import subprocess

    from art.project import ROOT
    gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
    log_dir = os.path.join(ROOT, "build")
    os.makedirs(log_dir, exist_ok=True)
    log_path = os.path.join(log_dir, "gametest.log")
    print("GameTest サーバーを起動しています (全テスト後に自動終了)...")
    try:
        with open(log_path, "wb") as log:
            proc = subprocess.run([gradlew, "--no-daemon", "--console=plain", "runGameTestServer"],
                                  cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=600)
        rc = proc.returncode
    except subprocess.TimeoutExpired:
        print("タイムアウト (10 分)")
        rc = 1
    finally:
        subprocess.run([gradlew, "--stop"], cwd=ROOT, capture_output=True, timeout=120)
    text = open(log_path, encoding="utf-8", errors="replace").read()
    pat = re.compile(r"(failed at .*|required tests .*|All \d+ required tests passed.*|GAME TESTS COMPLETE.*"
                     r"|Couldn't parse data file .*|error: .*|BUILD (?:SUCCESSFUL|FAILED).*)")
    for line in text.splitlines():
        m = pat.search(line)
        if m:
            print("  " + m.group(1)[:300])
    print(f"ログ全文: build/gametest.log (終了コード {rc})")
    return 0 if rc == 0 else 1


def main(argv: list[str]) -> int:
    setup_console()
    if not argv or argv[0] in ("-h", "--help", "help"):
        print(__doc__)
        return 0
    cmd, rest = argv[0], argv[1:]
    if cmd == "gen":
        import generate_textures
        return generate_textures.main(rest)
    if cmd == "data":
        import gen_data
        return gen_data.main(rest)
    if cmd == "verify":
        import verify
        return verify.main(rest)
    if cmd == "preview":
        import preview
        return preview.main(rest)
    if cmd == "grips":
        import report_grips
        report_grips.main()
        return 0
    if cmd == "matgen":
        import matgen_cli
        return matgen_cli.main(rest)
    if cmd in ("swordgen", "staffgen"):
        import weapongen_cli
        return (weapongen_cli.sword_main if cmd == "swordgen" else weapongen_cli.staff_main)(rest)
    if cmd == "variants":
        import variants_cli
        return variants_cli.main(rest)
    if cmd == "gametest":
        return _gametest()
    if cmd == "check":
        return _check(write=False)
    if cmd == "all":
        return _check(write=True)
    print(f"未知のコマンド: {cmd}\n{__doc__}")
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
