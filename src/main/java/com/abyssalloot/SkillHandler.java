package com.abyssalloot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * スキル発動: 武器小技 (右クリック)・武器奥義 (R)・必殺技 (スニーク+右クリック/V)・回避ダッシュ (X)・魔弾.
 * CT管理は ModAttachments の LIGHT_CD / SKILL_CD / DASH_CD (CombatHandler.onServerTick で減衰).
 * CombatHandler から分離。
 */
public final class SkillHandler {
    private SkillHandler() {}

    /** 魔弾の威力倍率 (castWandBolt が hurtServer 直前に設定し、命中処理で消費) */
    public static double boltMult = 0;

    /** 右クリック=武器小技 / スニーク+右クリック=必殺技 / 魂の欠片=魂+25 */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel)) return;
        ItemStack stack = event.getItemStack();
        if (stack.is(ModItems.SOUL_SHARD.get())) {
            // すでに満タンなら消さない (欠片の無駄遣いを防ぐ)
            if (player.getData(ModAttachments.SOUL.get()) >= 100f) {
                player.displayClientMessage(Component.literal("§7魂ゲージは満タンだ"), true);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
            RunManager.addGauge(player, 25);
            float soul = player.getData(ModAttachments.SOUL.get());
            stack.shrink(1);
            player.displayClientMessage(Component.literal(String.format("§d魂+25 (%.0f/100)", soul)), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        GameData.WeaponStyle style = ModItems.styleOf(stack.getItem());
        if (style == null) return;
        // 武器を持ち替えたらその武器種の小技になる
        if (player.isShiftKeyDown()) {
            trySkill(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (tryLightSkill(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** 武器小技の発動 (右クリック共通。CT1~3秒で連発できる) */
    public static boolean tryLightSkill(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel)) return false;
        GameData.WeaponStyle style = ModItems.styleOf(player.getMainHandItem().getItem());
        if (style == null) {
            player.displayClientMessage(Component.literal("§cAbyss武器を手に持ってね"), true);
            return false;
        }
        float cd = player.getData(ModAttachments.LIGHT_CD.get());
        if (cd > 0) {
            player.displayClientMessage(
                    Component.literal(String.format("§7小技CT %.1fs", cd)), true);
            return false;
        }
        GameData.HeroClass cls =
                GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        WeaponArts.executeLight(player, style, StatCalc.attackDamageOf(player));
        player.setData(ModAttachments.LIGHT_CD.get(),
                (float) GameData.lightCooldown(cls, style));
        return true;
    }

    /** 武器奥義の発動判定 (Rキー / コマンド共通) */
    public static boolean tryArt(ServerPlayer player) {
        GameData.WeaponStyle style = ModItems.styleOf(player.getMainHandItem().getItem());
        if (style == null) {
            player.displayClientMessage(Component.literal("§cAbyss武器を手に持ってね"), true);
            return false;
        }
        float soul = player.getData(ModAttachments.SOUL.get());
        if (soul < 100f) {
            player.displayClientMessage(
                    Component.literal(String.format("§7魂ゲージ %.0f/100", soul)), true);
            return false;
        }
        if (WeaponArts.execute(player, style,
                StatCalc.attackDamageOf(player) * StatCalc.artMultOf(player)
                        * GameData.skillTierMult(player.getData(ModAttachments.LEVEL.get())))) {
            // 恩寵「残響の刻印」: 奥義後に25%/Lvで魂ゲージが半分残る
            if (player.getRandom().nextFloat() < 0.25 * RunManager.boonLv(player, "echo")) {
                player.setData(ModAttachments.SOUL.get(), 50f);
                player.displayClientMessage(Component.literal("§d残響！ 魂ゲージが半分残った"), true);
            } else {
                player.setData(ModAttachments.SOUL.get(), 0f);
            }
            int uses = player.getData(ModAttachments.ART_USES.get()) + 1;
            player.setData(ModAttachments.ART_USES.get(), uses);
            String artTier = GameData.skillTierName(player.getData(ModAttachments.LEVEL.get()));
            if (!artTier.isEmpty()) {
                player.displayClientMessage(Component.literal("§d奥義解放" + artTier + "！"), true);
            }
            if (uses >= 10) {
                var a = GameData.achievementById("art-master");
                Achievements.grantAch(player, "art-master", a.title(), GameData.shardsForReward(a.reward()));
            }
            return true;
        }
        return false;
    }

    /** 必殺技の発動 (スニーク+右クリック / Vキー / コマンド共通。CT25秒) */
    public static void trySkill(ServerPlayer player) {
        float cd = player.getData(ModAttachments.SKILL_CD.get());
        if (cd > 0) {
            player.displayClientMessage(
                    Component.literal(String.format("§7必殺技CT %.1fs", cd)), true);
            return;
        }
        GameData.HeroClass cls =
                GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        int lv = player.getData(ModAttachments.LEVEL.get());
        WeaponArts.executeSkill(player, cls,
                StatCalc.attackDamageOf(player) * StatCalc.artMultOf(player)
                        * GameData.skillTierMult(lv));
        player.setData(ModAttachments.SKILL_CD.get(), 25f);
        String tierName = GameData.skillTierName(lv);
        if (!tierName.isEmpty()) {
            player.displayClientMessage(Component.literal("§d必殺技解放" + tierName + "！"), true);
        }
    }

    /** 回避ダッシュ: 移動方向へ6m瞬動 + 0.5秒無敵 (原作dashの簡易版)。静止時は前方 */
    public static void dash(ServerPlayer player) {
        float cd = player.getData(ModAttachments.DASH_CD.get());
        if (cd > 0) {
            player.displayClientMessage(
                    Component.literal(String.format("§7回避CT %.1fs", cd)), true);
            return;
        }
        Vec3 mot = player.getDeltaMovement();
        Vec3 dir;
        if (mot.horizontalDistanceSqr() > 0.004) {
            dir = new Vec3(mot.x, 0, mot.z).normalize();
        } else {
            Vec3 look = player.getLookAngle();
            dir = new Vec3(look.x, 0, look.z).normalize();
        }
        Vec3 dest = player.position().add(dir.scale(6));
        player.teleportTo(dest.x, dest.y, dest.z);
        player.setData(ModAttachments.IFRAME.get(), 0.5f);
        // 原作: 職業別の回避CT. 敏捷1点につき-1% (下限0.28秒)。
        // 疾走の刻印: 回避短縮12%/Lv (原作 b_speed.dashCdPct)
        var cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        double dashCd = GameData.dashCooldown(cls, (int) StatCalc.attrEff(player, 2));
        int dashPct = RunManager.boonLv(player, "speed") * 12;
        if (dashPct > 0) dashCd *= Math.max(0.5, 1 - dashPct / 100.0);
        // 防具スキル「敏捷の靴」: 回避CT-10%
        if ("b_vigor".equals(GearData.skillOf(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)))) {
            dashCd *= 0.9;
        }
        player.setData(ModAttachments.DASH_CD.get(), (float) dashCd);
        player.setData(ModAttachments.COUNTER.get(), 1.0f);
        player.displayClientMessage(Component.literal("§b回避！"), true);
        ((ServerLevel) player.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                dest.x, dest.y + 1, dest.z, 12, 0.8, 0.6, 0.8, 0.05);
        // 大地の怒り: 回避着地で爆発
        if (RunManager.boonLv(player, "quake") > 0) {
            WeaponArts.pulse((ServerLevel) player.level(), player,
                    new Vec3(dest.x, dest.y + 1, dest.z), 5, 1.5, false,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION);
        }
    }

    /** 魔弾: 照準錐内の敵に通常攻撃を遠隔命中させる。
     * 通常命中の全処理 (会心・特性・連鎖・ゲージ) を通し、最終威力に mult を掛ける */
    public static void castWandBolt(ServerPlayer player, double range, double mult, int maxTargets) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) return;
        Vec3 origin = player.position().add(0, 1.5, 0);
        Vec3 look = player.getLookAngle();
        var cands = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(origin.x - range, origin.y - range / 2, origin.z - range,
                        origin.x + range, origin.y + range / 2, origin.z + range),
                e -> e.isAlive() && !(e instanceof ServerPlayer));
        var hits = new java.util.ArrayList<net.minecraft.world.entity.LivingEntity>();
        for (var e : cands) {
            Vec3 d = e.position().add(0, e.getBbHeight() / 2, 0).subtract(origin);
            double dist = d.length();
            if (dist > range + 1 || dist < 0.5) continue;
            double cos = Math.max(-1, Math.min(1, d.normalize().dot(look)));
            if (Math.acos(cos) > 0.22) continue;
            hits.add(e);
        }
        hits.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(origin)));
        int n = 0;
        for (var t : hits) {
            if (n++ >= Math.max(1, maxTargets)) break;
            boltMult = mult;
            try {
                t.hurtServer(level, level.damageSources().playerAttack(player),
                        (float) StatCalc.attackDamageOf(player));
            } finally {
                boltMult = 0;
            }
            Vec3 tp = t.position().add(0, t.getBbHeight() / 2, 0);
            int steps = Math.max(1, (int) (origin.distanceTo(tp) * 2));
            for (int i = 1; i <= steps; i++) {
                Vec3 p = origin.lerp(tp, (double) i / steps);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                        p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }
}
