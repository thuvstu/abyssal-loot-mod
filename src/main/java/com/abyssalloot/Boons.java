package com.abyssalloot;

/**
 * 恩寵 (原作 core.ts BOONS / BOON_GRADE) の定義表.
 * Minecraft のクラスに依存しない純粋データのため、GameDataTest から直接検証できる.
 * 効果の実装箇所: 攻撃=StatCalc.attackDamageOf / HP・速度・攻速=applyClass /
 * 被ダメ=CombatHandler.onDamage / 撃破=onKill / 定期=tickAbilityBoons / 奥義=tryArt / 跳躍=dash
 */
public final class Boons {
    private Boons() {}

    public record BoonDef(String id, String name, String desc, String grade) {}

    /**
     * 祝福プール 41 種 = 原作 BOONS 39 種 (ID から b_ 接頭語を除去) + 旧セーブ互換の簡易 2 種 (dmg/art)。
     * ID は一意。重みは原作 rollBoons と同じ (common 4.2 / rare 2.4 / epic 1.1)。
     */
    public static final BoonDef[] BOON_POOL = {
            // --- 原作 BOONS (core.ts の順) ---
            new BoonDef("dmg1", "剛力の刻印", "攻撃力 +18%", "common"),
            new BoonDef("dmg2", "破壊の刻印", "攻撃力 +12 / 攻撃力 +8%", "common"),
            new BoonDef("as", "迅風の刻印", "攻撃速度 +16%", "common"),
            new BoonDef("crit", "心眼の刻印", "クリティカル率 +5%/Lv", "common"),
            new BoonDef("critdmg", "致命の刻印", "クリティカル威力 +45%", "rare"),
            new BoonDef("hp", "巨躯の刻印", "最大HP +18% / HP回復 +0.6/秒", "common"),
            new BoonDef("armor", "鉄壁の刻印", "ダメージ軽減 +6%/Lv", "common"),
            new BoonDef("speed", "疾走の刻印", "移動速度 +12% / 回避短縮 12%", "common"),
            new BoonDef("area", "拡大の刻印", "攻撃範囲 +22%", "common"),
            new BoonDef("life", "吸血の刻印", "生命吸収 +4%", "rare"),
            new BoonDef("proj", "分裂の刻印", "追尾魔弾の弾数 +1", "epic"),
            new BoonDef("pierce", "貫通の刻印", "連鎖雷撃の飛移り +2", "rare"),
            new BoonDef("explode", "爆砕の刻印", "撃破時に18%で爆裂", "rare"),
            new BoonDef("freeze", "凍牙の刻印", "命中時に20%で氷結鈍足", "rare"),
            new BoonDef("thorns", "棘甲の刻印", "棘ダメージ +25", "common"),
            new BoonDef("energy", "魂の高揚", "魂ゲージ増加 +35%", "common"),
            new BoonDef("xp", "知識の渇望", "運 +20", "common"),
            new BoonDef("orbit", "回転刃", "周囲を刃が周回する", "rare"),
            new BoonDef("aura", "聖なる領域", "常時、周囲の敵を焼く", "rare"),
            new BoonDef("chain", "連鎖雷撃", "命中時に雷が連鎖する", "epic"),
            new BoonDef("missile", "追尾魔弾", "自動で魔弾を発射する", "rare"),
            new BoonDef("nova", "氷霜新星", "定期 Nova で敵を凍結", "epic"),
            new BoonDef("familiar", "影の眷属", "使い魔が共に戦う", "epic"),
            new BoonDef("execute", "処刑人", "瀕死の敵へ +90% ダメージ", "rare"),
            new BoonDef("blood", "血の契約", "撃破時にHP回復", "common"),
            new BoonDef("magnet", "貪欲なる磁場", "回収範囲 大幅拡大", "common"),
            new BoonDef("quake", "大地の怒り", "回避着地時に爆発", "rare"),
            new BoonDef("bladespin", "渦刃", "攻撃のたびに刃の渦が飛ぶ", "rare"),
            new BoonDef("vengeance", "復讐の刻印", "被弾時に周囲へ爆発", "rare"),
            new BoonDef("thunderfury", "天雷の刻印", "命中時に確率で落雷", "epic"),
            new BoonDef("echo", "残響の刻印", "奥義後に25%で魂ゲージが半分残る", "epic"),
            new BoonDef("greed", "強欲の刻印", "素材+55% / 被ダメ+10%", "common"),
            new BoonDef("warden", "障壁の刻印", "立ち止まると軽減が上がる", "common"),
            new BoonDef("harvest", "刈り取り", "強敵撃破で全ステータス強化", "epic"),
            new BoonDef("overflow", "過剰修復", "余ったHPを攻撃力へ変換", "epic"),
            new BoonDef("curse_eater", "呪喰らい", "技能の欠点を無効化し効果を2倍", "epic"),
            new BoonDef("lastlaugh", "最後の笑い", "HP30%以下で攻撃+70%", "rare"),
            new BoonDef("perfect", "完全主義", "見切り後3秒間、攻撃+90%", "epic"),
            new BoonDef("absolute_domain", "絶対領域", "魂ゲージ100%で発動。3秒間の無敵バリアを展開する", "epic"),
            // --- 旧セーブ互換 (簡易版のまま) ---
            new BoonDef("dmg", "剛力の刻印・旧", "攻撃+12%/Lv (互換)", "common"),
            new BoonDef("art", "武の刻印", "奥義・必殺+12%/Lv", "rare"),
            // --- 追加の祝福 (数値効果のみ。EXTRA_BOON_MODS で適用) ---
            new BoonDef("b_power", "剛腕の祝福", "攻撃力 +15%", "common"),
            new BoonDef("b_swift", "疾風の祝福", "攻撃速度 +12%", "common"),
            new BoonDef("b_guard", "鉄壁の祝福", "ダメージ軽減 +10%", "common"),
            new BoonDef("b_dash", "俊足の祝福", "移動速度 +10%", "common"),
            new BoonDef("b_eye", "鷹の目の祝福", "会心率 +6%", "common"),
            new BoonDef("b_fang", "牙の祝福", "会心威力 +30%", "rare"),
            new BoonDef("b_leech", "蛭の祝福", "生命吸収 +3%", "rare"),
            new BoonDef("b_mend", "修復の祝福", "HP自動回復 +1/秒", "common"),
            new BoonDef("b_luck", "幸運の祝福", "運 +20", "common"),
            new BoonDef("b_soul", "魂の祝福", "魂ゲージ増加 +20%", "common"),
            new BoonDef("b_wide", "広域の祝福", "攻撃範囲 +12%", "common"),
            new BoonDef("b_blast", "爆裂の祝福", "爆裂確率 +10%", "rare"),
            new BoonDef("b_frost", "凍結の祝福", "氷結確率 +12%", "rare"),
            new BoonDef("b_body", "強靭の祝福", "最大HP +12%", "common"),
            new BoonDef("b_pierce", "貫通の祝福", "貫通 +1", "rare"),
            new BoonDef("b_chain", "連鎖の祝福", "連鎖雷撃 +1", "epic"),
            new BoonDef("b_volley", "斉射の祝福", "追加弾数 +1", "epic"),
            new BoonDef("b_flat", "鉄槌の祝福", "攻撃力 +8", "common"),
            new BoonDef("b_vital", "生命の祝福", "最大HP +30", "common"),
            new BoonDef("b_drain", "吸魂の祝福", "生命吸収 +5% / 攻撃力 +10%", "epic"),
            new BoonDef("b_hawk", "鷲翼の祝福", "移動速度 +15% / 攻撃力 +8%", "common"),
            new BoonDef("b_aegis", "護りの祝福", "ダメージ軽減 +14%", "common"),
            new BoonDef("b_inferno", "灼熱の祝福", "爆裂確率 +18% / 攻撃力 +4%", "rare"),
            new BoonDef("b_squall", "嵐の祝福", "攻撃速度 +8% / 攻撃範囲 +10%", "common"),
            new BoonDef("b_luna", "月影の祝福", "会心率 +4% / 会心威力 +20%", "rare"),
            new BoonDef("b_midas", "黄金の祝福", "運 +15 / HP回復 +0.5/秒", "common"),
            new BoonDef("b_web", "蛛巣の祝福", "氷結確率 +15% / 連鎖雷撃 +1", "rare"),
            new BoonDef("b_giant", "巨人の祝福", "最大HP +18% / 最大HP +25", "common"),
            new BoonDef("b_reach", "望遠の祝福", "攻撃射程 +1", "common"),
            new BoonDef("b_sweep", "横薙の祝福", "横斬り (薙ぎ幅) +1", "rare"),
            new BoonDef("b_barrage", "連撃の祝福", "一打の連撃 +1", "rare"),
    };

    public static BoonDef boonById(String id) {
        for (var b : BOON_POOL) {
            if (b.id().equals(id)) return b;
        }
        return null;
    }

    /** 原作 rollBoons の等級重み */
    public static double weightOf(String grade) {
        return switch (grade) {
            case "epic" -> 1.1;
            case "rare" -> 2.4;
            default -> 4.2;
        };
    }

    /**
     * 原作 rollBoons の写し: 等級重みで n 択を引く (重複なし).
     * r は [0,1) を返す乱数.
     */
    public static java.util.List<BoonDef> rollBoons(java.util.function.DoubleSupplier r, int n) {
        var pool = new java.util.ArrayList<>(java.util.List.of(BOON_POOL));
        var pick = new java.util.ArrayList<BoonDef>();
        while (pick.size() < Math.min(n, pool.size()) && !pool.isEmpty()) {
            double total = 0;
            for (var b : pool) total += weightOf(b.grade());
            double x = r.getAsDouble() * total;
            BoonDef sel = pool.get(pool.size() - 1);
            for (var b : pool) {
                x -= weightOf(b.grade());
                if (x <= 0) {
                    sel = b;
                    break;
                }
            }
            pool.remove(sel);
            pick.add(sel);
        }
        return pick;
    }
}
