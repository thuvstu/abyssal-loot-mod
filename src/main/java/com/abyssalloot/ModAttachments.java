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
    /** 属性配分 "g,r,b,n,u" (剛力,韻律,敏捷,忍耐,幸運) */
    public static final Supplier<AttachmentType<String>> ATTRS =
            reg("attrs", "0,0,0,0,0", Codec.STRING);
    /** 祝福 "id:lv,..." */
    public static final Supplier<AttachmentType<String>> BOONS =
            reg("boons", "", Codec.STRING);
    /** 提示中の祝福3択 "id,id,id" */
    public static final Supplier<AttachmentType<String>> OFFER =
            reg("offer", "", Codec.STRING);
    /** 反撃窓タイマー (回避後の初撃×1.5) */
    public static final Supplier<AttachmentType<Float>> COUNTER =
            reg("counter", 0f, Codec.FLOAT);
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
    /** この層の異変 ""/blood/frail/swift/rich/heavy */
    public static final Supplier<AttachmentType<String>> MUTATION =
            reg("mutation", "", Codec.STRING);
}
