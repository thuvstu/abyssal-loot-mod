#!/usr/bin/env python3
"""Abyssal Loot 開発ツールの入口。

    python tools/abyss.py gen [--only PAT] [--kind ...] [--check] [-v]   テクスチャ / モデル生成
    python tools/abyss.py data [--check] [-v]                           データパック生成
    python tools/abyss.py verify [チェック名 ...] [--strict]               静的検証
    python tools/abyss.py preview <種類> [id ...] [--only PAT]           プレビュー画像
    python tools/abyss.py grips                                         武器の握り位置の一覧
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
    if cmd == "check":
        return _check(write=False)
    if cmd == "all":
        return _check(write=True)
    print(f"未知のコマンド: {cmd}\n{__doc__}")
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
