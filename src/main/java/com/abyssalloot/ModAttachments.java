package com.abyssalloot;

import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * プレイヤー進行データ (Attachment, ワールド保存される).
 * シンプルな型のみで構成し Codec 永続化する.
 */
public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AbyssalLootMod.MODID);

    private static <T> Supplier<AttachmentType<T>> reg(String id, T def, Codec<T> codec) {
        return ATTACHMENTS.register(id, () -> AttachmentType.builder(() -> def).serialize(codec.fieldOf("value")).build());
    }

    public static final Supplier<AttachmentType<String>> HERO_CLASS =
            reg("hero_class", "samurai", Codec.STRING);
    public static final Supplier<AttachmentType<Integer>> LEVEL =
            reg("hero_level", 1, Codec.INT);
    public static final Supplier<AttachmentType<Float>> SOUL =
            reg("soul_gauge", 0f, Codec.FLOAT);
    public static final Supplier<AttachmentType<Integer>> FLOOR =
            reg("floor", 1, Codec.INT);
    public static final Supplier<AttachmentType<Integer>> KILLS =
            reg("kills", 0, Codec.INT);
    public static final Supplier<AttachmentType<String>> SIGILS =
            reg("sigils", "", Codec.STRING);
    public static final Supplier<AttachmentType<Float>> DASH_CD =
            reg("dash_cd", 0f, Codec.FLOAT);
    public static final Supplier<AttachmentType<Float>> IFRAME =
            reg("iframe", 0f, Codec.FLOAT);
    public static final Supplier<AttachmentType<Integer>> COMBO =
            reg("combo", 0, Codec.INT);
    /** この層の湧き総数 (層クリア判定用) */
    public static final Supplier<AttachmentType<Integer>> WAVE_TOTAL =
            reg("wave_total", 0, Codec.INT);
    /** この層での撃破数 */
    public static final Supplier<AttachmentType<Integer>> FLOOR_KILLS =
            reg("floor_kills", 0, Codec.INT);
    /** 転移門オープン済みか (0/1) */
    public static final Supplier<AttachmentType<Integer>> PORTAL_OPEN =
            reg("portal_open", 0, Codec.INT);
    /** 属性配分 "8値" (剛力,韻律,敏捷,忍耐,幸運,集中,活力,技巧) */
    public static final Supplier<AttachmentType<String>> ATTRS =
            reg("attrs", "0,0,0,0,0,0,0,0", Codec.STRING);
    /** 祝福 "id:lv,..." */
    public static final Supplier<AttachmentType<String>> BOONS =
            reg("boons", "", Codec.STRING);
    /** 提示中の祝福3択 "id,id,id" */
    public static final Supplier<AttachmentType<String>> OFFER =
            reg("offer", "", Codec.STRING);
    /** 未選択の祝福選択の残数 (原作 engine.ts pendingBoons。レベルアップごとに+1) */
    public static final Supplier<AttachmentType<Integer>> PENDING_BOONS =
            reg("pending_boons", 0, Codec.INT);
    /** 反撃窓タイマー (回避後の初撃×1.5) */
    public static final Supplier<AttachmentType<Float>> COUNTER =
            reg("counter", 0f, Codec.FLOAT);
    /** 必殺技の残りCT (秒)。発動で25秒、毎tick減衰 */
    public static final Supplier<AttachmentType<Float>> SKILL_CD =
            reg("skill_cd", 0f, Codec.FLOAT);
    /** 武器小技の残りCT (秒)。右クリック発動・1~3秒、毎tick減衰 */
    public static final Supplier<AttachmentType<Float>> LIGHT_CD =
            reg("light_cd", 0f, Codec.FLOAT);
    /** 進行モード standard/bossrush/endless/covenant */
    public static final Supplier<AttachmentType<String>> MODE =
            reg("mode", "standard", Codec.STRING);
    /** 難易度 normal/hard/inferno */
    public static final Supplier<AttachmentType<String>> DIFF =
            reg("diff", "normal", Codec.STRING);
    /** 撃破した層主 "5,10,..." */
    public static final Supplier<AttachmentType<String>> BOSSES =
            reg("bosses", "", Codec.STRING);
    /** 継承装備 (次回出撃時に受け取る1点) */
    public static final Supplier<AttachmentType<net.minecraft.world.item.ItemStack>> HEIRLOOM =
            ATTACHMENTS.register("heirloom", () -> AttachmentType.builder(
                    () -> net.minecraft.world.item.ItemStack.EMPTY.copy())
                    .serialize(net.minecraft.world.item.ItemStack.CODEC.fieldOf("value")).build());
    /** 層間イベント使用済み (0/1) */
    public static final Supplier<AttachmentType<Integer>> EVENT_DONE =
            reg("event_done", 0, Codec.INT);
    /** 誓いの累積攻撃加算 (勝利ごとに+1) */
    public static final Supplier<AttachmentType<Integer>> OATH_DMG =
            reg("oath_dmg", 0, Codec.INT);
    /** 商店品揃え "style:price,..." */
    public static final Supplier<AttachmentType<String>> SHOP =
            reg("shop", "", Codec.STRING);
    /** 罠位置 "x,y,z;..." */
    public static final Supplier<AttachmentType<String>> TRAPS =
            reg("traps", "", Codec.STRING);
    /** 破壊物 "kind,x,y,z;..." */
    public static final Supplier<AttachmentType<String>> DESTRUCT =
            reg("destruct", "", Codec.STRING);
    /** 不壊タイマー (命中で10秒・被ダメ-10%) */
    public static final Supplier<AttachmentType<Float>> FORTIFY =
            reg("fortify", 0f, Codec.FLOAT);
    /** 実績 "id,..." */
    public static final Supplier<AttachmentType<String>> ACH =
            reg("ach", "", Codec.STRING);
    /** この層の異変ID (GameData.MUTATIONS の id。空=なし) */
    public static final Supplier<AttachmentType<String>> MUTATION =
            reg("mutation", "", Codec.STRING);
    /** この層のダンジョン特性ID (GameData.DUNGEON_TRAITS の id。空=なし) */
    public static final Supplier<AttachmentType<String>> DUNGEON =
            reg("dungeon", "", Codec.STRING);
    /** アリーナ中心 "x,y,z" (MOBの脱走収容用。buildArenaで保存) */
    public static final Supplier<AttachmentType<String>> ARENA =
            reg("arena", "", Codec.STRING);
    /** 実行遺物 (原作 RELICS・ラン中最大2つ・"id,...") */
    public static final Supplier<AttachmentType<String>> RELICS =
            reg("relics", "", Codec.STRING);
    /** 技能 (原作 PASSIVES・"id,...") */
    public static final Supplier<AttachmentType<String>> PASSIVES =
            reg("passives", "", Codec.STRING);
    /** 提示中の遺物3択 "id,id,id" */
    public static final Supplier<AttachmentType<String>> RELIC_OFFER =
            reg("relic_offer", "", Codec.STRING);
    /** 提示中の技能3択 "id,id,id" */
    public static final Supplier<AttachmentType<String>> PASSIVE_OFFER =
            reg("passive_offer", "", Codec.STRING);
    /** fate効果の終了tick (呪いの霧・時の歪み). ワールド時間ではなくサーバtick基準 */
    public static final Supplier<AttachmentType<Long>> FATE_MIST =
            reg("fate_mist", 0L, Codec.LONG);
    public static final Supplier<AttachmentType<Long>> FATE_TIME =
            reg("fate_time", 0L, Codec.LONG);
    /** 武器奥義の使用回数 (実績「武の極致」) */
    public static final Supplier<AttachmentType<Integer>> ART_USES =
            reg("art_uses", 0, Codec.INT);
    /** 見切り回数 (実績「刹那の見切り」) */
    public static final Supplier<AttachmentType<Integer>> PERFECT =
            reg("perfect", 0, Codec.INT);
    /** 層間イベントの完了回数 (実績「祭壇の巡礼者」) */
    public static final Supplier<AttachmentType<Integer>> EVENT_TOTAL =
            reg("event_total", 0, Codec.INT);
    /** 呪われた鏡の使用済み (0/1) */
    public static final Supplier<AttachmentType<Integer>> MIRROR_USED =
            reg("mirror_used", 0, Codec.INT);
    /** 技能「刈り取り」の蓄積 (強敵撃破ごとに+1) */
    public static final Supplier<AttachmentType<Integer>> HARVEST =
            reg("harvest", 0, Codec.INT);
    /** 見切りの最終成立時刻 (gameTime。技能「完全主義」用) */
    public static final Supplier<AttachmentType<Long>> PARRY_TICK =
            reg("parry_tick", -1000L, Codec.LONG);
    /** 出撃中の条件固定 (0/1。呪印・モード・難易度の変更禁止) */
    public static final Supplier<AttachmentType<Integer>> RUN_LOCK =
            reg("run_lock", 0, Codec.INT);
    /** ラン中の最大コンボ (スコア用) */
    public static final Supplier<AttachmentType<Integer>> BEST_COMBO =
            reg("best_combo", 0, Codec.INT);
    /** ラン中の累積与ダメ (スコア用。切り捨ての合計) */
    public static final Supplier<AttachmentType<Integer>> DMG_DEALT =
            reg("dmg_dealt", 0, Codec.INT);
    /** チュートリアル表示済み (0/1)。初出撃時に操作案内を出す */
    public static final Supplier<AttachmentType<Integer>> TUTOR =
            reg("tutor", 0, Codec.INT);
    /** 鎌の収穫スタック (撃破ごとに+1・最大10・攻速+6%/枚) */
    public static final Supplier<AttachmentType<Integer>> SCYTHE =
            reg("scythe", 0, Codec.INT);
    /** 拳套の連撃数 (3連撃ごとに衝撃波) */
    public static final Supplier<AttachmentType<Integer>> GCNT =
            reg("gcnt", 0, Codec.INT);
    /** 運命の祭壇の効果ID (rage/gold/haste/armor/scourge・空=なし) */
    public static final Supplier<AttachmentType<String>> SHRINE_FATE =
            reg("shrine_fate", "", Codec.STRING);
    /** 運命の祭壇の効果終了時刻 (gameTime) */
    public static final Supplier<AttachmentType<Long>> SHRINE_UNTIL =
            reg("shrine_until", 0L, Codec.LONG);
    /** 放浪の討伐カウンタ (試練外でのバニラ敵討伐数。25体ごとに欠片) */
    public static final Supplier<AttachmentType<Integer>> ROAM_KILLS =
            reg("roam_kills", 0, Codec.INT);
    /** 武器の左クリック追撃カウンタ (WEAPON_FOLLOWUP 用) */
    public static final Supplier<AttachmentType<Integer>> FOLLOW_HIT =
            reg("follow_hit", 0, Codec.INT);
}
