package com.abyssalloot;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * 戦闘対象の共通判定.
 * ダメージ数字・囮のアーマースタンドは表示用の実体であり、攻撃対象・自動照準に含めてはいけない.
 */
public final class CombatUtil {
    private CombatUtil() {}

    /** 表示用の実体 (ダメージ数字・囮) か */
    public static boolean isUtility(Entity entity) {
        if (entity == null) return false;
        return entity.getTags().contains(DamageNumbers.TAG)
                || entity.getTags().contains(SubSkills.DECOY_TAG);
    }

    /**
     * アーマースタンドをマーカー化する (当たり判定 0・攻撃や照準の対象外).
     * 1.21.11 で ArmorStand#setMarker が private になったため、同じフラグを直接立てる.
     */
    public static void makeMarker(ArmorStand stand) {
        var data = stand.getEntityData();
        byte flags = data.get(ArmorStand.DATA_CLIENT_FLAGS);
        data.set(ArmorStand.DATA_CLIENT_FLAGS, (byte) (flags | ArmorStand.CLIENT_FLAG_MARKER));
        stand.refreshDimensions();
    }

    /** プレイヤー攻撃の対象にできる実体か (プレイヤー自身と表示用の実体を除く) */
    public static boolean isValidTarget(LivingEntity entity) {
        return entity != null && entity.isAlive()
                && !(entity instanceof ServerPlayer)
                && !isUtility(entity);
    }
}
