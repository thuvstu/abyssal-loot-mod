package com.abyssalloot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * ダメージイベント処理: 被ダメの各種軽減/反射と武器命中の原作式威力上書き.
 * 護衛兵・エリート魔盾・見切り・接辞/祝福/技能/呪い/防具スキルの補正、
 * 一打の連撃・横斬り・追撃・状態異常・連鎖雷などをここに集約 (CombatHandler から分離).
 */
public final class WeaponHitHandler {
    private WeaponHitHandler() {}

    /**
     * 鉄壁の護衛兵: 前方±60°からの被ダメ30%、側面・背面は135% (原作 guardMultiplier).
     * 他の補正の後に掛けるため LOW 優先度.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGuardHit(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer || !victim.getTags().contains(Enemies.GUARD_TAG)) return;
        Vec3 src = event.getSource().getSourcePosition();
        if (src == null) return;
        Vec3 toSource = new Vec3(src.x - victim.getX(), 0, src.z - victim.getZ());
        Vec3 facing = victim.getLookAngle();
        facing = new Vec3(facing.x, 0, facing.z);
        double a = toSource.length();
        double b = facing.length();
        if (a < 1e-6 || b < 1e-6) return;
        double cos = (toSource.x * facing.x + toSource.z * facing.z) / (a * b);
        double mult = cos >= Math.cos(Math.PI / 3) ? 0.30 : 1.35;
        event.setNewDamage(event.getNewDamage() * (float) mult);
    }

    /** 無敵時間の適用 + 武器ダメージの原作式上書き */
    @SubscribeEvent
    public static void onDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
        // エリート「魔盾」: 盾持ちの被ダメ軽減 (原作のシールドエリート)
        if (event.getEntity().getTags().contains(Enemies.ET_SHIELD)
                && !(event.getEntity() instanceof ServerPlayer)) {
            event.setNewDamage(event.getNewDamage() * 0.7f);
        }
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            float iframe = player.getData(ModAttachments.IFRAME.get());
            if (iframe > 0) {
                event.setNewDamage(0f);
                // 見切り: 無敵中に受けた攻撃は魂+14 (兜の直感で+6)
                RunManager.addGauge(player, 14);
                if (ModItems.slotOf(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem()) != null) {
                    RunManager.addGauge(player, 6);
                }
                int perfect = player.getData(ModAttachments.PERFECT.get()) + 1;
                player.setData(ModAttachments.PERFECT.get(), perfect);
                if (perfect >= 10) {
                    var a = GameData.achievementById("perfect-ten");
                    Achievements.grantAch(player, "perfect-ten", a.title(), GameData.shardsForReward(a.reward()));
                }
                // 見切りの成立時刻 (恩寵「完全主義」の3秒窓)
                player.setData(ModAttachments.PARRY_TICK.get(), player.level().getGameTime());
                // 遺物「嵐召喚者の冠」: 見切りのたび落雷が3体を焼く
                if (RunManager.relicFlagsOf(player).contains("storm")) {
                    stormStrike(level, player);
                }
                player.displayClientMessage(Component.literal("§a見切り！"), true);
                return;
            }
            // 特殊スキル「棘の盾」: 被ダメ-30% + 攻撃者へ反射
            if (player.getData(ModAttachments.SUB_SHIELD.get()) > 0
                    && event.getSource().getEntity() instanceof LivingEntity refAtk && refAtk.isAlive()) {
                event.setNewDamage(event.getNewDamage() * 0.7f);
                refAtk.hurtServer(level, level.damageSources().playerAttack(player),
                        event.getNewDamage() * 0.5f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_OFF,
                        player.getX(), player.getY() + 1, player.getZ(), 10, 0.6, 0.8, 0.6, 0.1);
            }
            // 鉄壁の刻印: 被ダメ軽減 (上限50%)
            int armor = RunManager.boonLv(player, "armor");
            if (armor > 0) {
                event.setNewDamage(event.getNewDamage() * (float) Math.max(0.5, 1 - 0.06 * armor));
            }
            // 呪い武器 (armorPct): 防御低下 (呪喰らいで無効化)
            var heldDef = player.getMainHandItem();
            if (GearData.curseActive(player, heldDef) && "armorPct".equals(GearData.curseOf(heldDef))) {
                event.setNewDamage(event.getNewDamage()
                        * (float) (1 - GearData.curseValueOf(heldDef) / 100.0));
            }
            // 接辞 (armorPct): 被ダメ軽減
            double affixArmor = StatCalc.affixOf(player, "armorPct")
                    + StatCalc.extraStat(player, "armorPct") + StatCalc.attrEff(player, 6) * 0.5;
            if (affixArmor != 0) {
                event.setNewDamage(event.getNewDamage()
                        * (float) Math.max(0.3, 1 - affixArmor / 100.0));
            }
            // 防具スキル: 荊棘の鎧 (反射20%) / 落法 (落下半減)
            var chest = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            if ("c_thorns".equals(GearData.skillOf(chest))
                    && event.getSource().getEntity() instanceof LivingEntity atk && atk.isAlive()) {
                atk.hurtServer(level, level.damageSources().magic(), event.getNewDamage() * 0.2f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        atk.getX(), atk.getY() + 1, atk.getZ(), 6, 0.4, 0.6, 0.4, 0.1);
            }
            // 防具スキル「霜の鎧」: 被弾時に周囲を凍結
            if ("c_frost".equals(GearData.skillOf(chest))) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(4),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                }
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                        player.getX(), player.getY() + 1, player.getZ(), 12, 1.2, 0.8, 1.2, 0.1);
            }
            // 防具スキル「魂導の鎧」: 被弾で魂ゲージ+4
            if ("c_soul".equals(GearData.skillOf(chest))) {
                RunManager.addGauge(player, 4);
            }
            // 防具スキル「鉄壁の鎧」: 被ダメ追加軽減 -8%
            if ("c_guard".equals(GearData.skillOf(chest))) {
                event.setNewDamage(event.getNewDamage() * 0.92f);
            }
            var legsF = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
            if ("l_rock".equals(GearData.skillOf(legsF))
                    && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                event.setNewDamage(event.getNewDamage() * 0.2f);
            }
            // 全武器の固有能力 (GUARD系は被弾時反射)
            var gheld = player.getMainHandItem();
            var gstyle = ModItems.styleOf(gheld.getItem());
            var gab = gstyle != null ? GameData.WEAPON_ABILITIES.get(gstyle.id) : null;
            if (gab != null && "GUARD".equals(gab.kind())
                    && player.getRandom().nextFloat() < gab.chance()
                    && event.getSource().getEntity() instanceof LivingEntity gatk && gatk.isAlive()) {
                gatk.hurtServer(level, level.damageSources().magic(),
                        event.getNewDamage() * (float) gab.value());
            }
            var legs = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
            if ("l_leap".equals(GearData.skillOf(legs))
                    && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                event.setNewDamage(event.getNewDamage() * 0.5f);
            }
            // 防具スロット能力: 鎧=重装 (被ダメ-5%) / 脚=軽業 (落下-25%)
            if (ModItems.slotOf(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()) != null) {
                event.setNewDamage(event.getNewDamage() * 0.95f);
            }
            if (ModItems.slotOf(legs.getItem()) != null
                    && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                event.setNewDamage(event.getNewDamage() * 0.75f);
            }
            // 跳躍属性: 落下軽減 (最大-40%)
            if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                event.setNewDamage(event.getNewDamage()
                        * (float) (1 - Math.min(0.4, StatCalc.attrEff(player, 9) * 0.02)));
            }
            // 恩寵「障壁の刻印」: 立ち止まると軽減が上がる (動いていなければ +8%/Lv)
            if (RunManager.boonLv(player, "warden") > 0
                    && player.getDeltaMovement().horizontalDistanceSqr() < 0.0015) {
                event.setNewDamage(event.getNewDamage()
                        * (float) Math.max(0.4, 1 - 0.08 * RunManager.boonLv(player, "warden")));
            }
            // 恩寵「強欲の刻印」: 被ダメ+10%/Lv
            int greedLv = RunManager.boonLv(player, "greed");
            if (greedLv > 0) {
                event.setNewDamage(event.getNewDamage() * (float) (1 + 0.10 * greedLv));
            }
            // 弾幕の獄: 被弾は軽い (敵の弾が濃いかわりに)
            if ("danmaku".equals(player.getData(ModAttachments.MODE.get()))) {
                event.setNewDamage(event.getNewDamage() * 0.6f);
            }
            // 技能の被ダメ補正 (原作 passiveMods 対応分)
            var passives = RunManager.passivesOf(player);
            double armorPct = GameData.passiveArmorPct(passives);
            if (passives.contains("last_stand")
                    && player.getHealth() / Math.max(1, player.getMaxHealth()) < 0.3) {
                armorPct += 25;
            }
            if (armorPct != 0) {
                event.setNewDamage(event.getNewDamage() * (float) Math.max(0.3, 1 - armorPct / 100.0));
            }
            // 技能「勢い」: コンボが切れると被ダメ+15%
            if (passives.contains("momentum") && player.getData(ModAttachments.COMBO.get()) <= 0) {
                event.setNewDamage(event.getNewDamage() * 1.15f);
            }
            // 不壊: 命中から10秒は被ダメ-10%
            if (player.getData(ModAttachments.FORTIFY.get()) > 0) {
                event.setNewDamage(event.getNewDamage() * 0.9f);
            }
            // fate「呪いの霧」: 被ダメ+30%
            if (RunManager.hasFate(player, "mist")) {
                event.setNewDamage(event.getNewDamage() * 1.3f);
            }
            // 祭壇「怒り」: 被ダメ+20% / 「大地」: 被ダメ-50%
            String shrine = RunManager.activeShrine(player);
            if ("rage".equals(shrine)) event.setNewDamage(event.getNewDamage() * 1.2f);
            if ("armor".equals(shrine)) event.setNewDamage(event.getNewDamage() * 0.5f);
            // 致死回避: 不滅の誓い (確定・1ラン1回) / 呪われた鏡 (40%・1ラン1回)
            if (event.getNewDamage() >= player.getHealth() && player.getHealth() > 0
                    && player.getData(ModAttachments.MIRROR_USED.get()) == 0) {
                boolean undying = passives.contains("undying");
                boolean mirror = RunManager.relicFlagsOf(player).contains("mirror");
                if (undying || (mirror && player.getRandom().nextFloat() < 0.40)) {
                    player.setData(ModAttachments.MIRROR_USED.get(), 1);
                    event.setNewDamage(0f);
                    player.setHealth(player.getMaxHealth() * 0.5f);
                    player.displayClientMessage(Component.literal(
                            undying ? "§f不滅の誓いが発動！ HP50%で復活" : "§b呪われた鏡が身代わりになった！"), false);
                    return;
                }
            }
            // 技能「棘の王」: 棘+80を反射
            if (passives.contains("thorn_lord") && event.getSource().getEntity() instanceof LivingEntity atk) {
                WeaponArts.artHurt((ServerLevel) player.level(), atk,
                        player.level().damageSources().playerAttack(player), 80f);
            }
            // 精鋭「吸魂」: 攻撃命中で自身のHPを吸収する
            if (event.getSource().getEntity() instanceof LivingEntity leech
                    && leech.getTags().contains(Enemies.ET_VAMPIRE)) {
                leech.heal(Math.min(event.getNewDamage() * 0.5f, leech.getMaxHealth() * 0.1f));
            }
            // 恩寵「復讐の刻印」: 被弾時に周囲へ爆発
            int vengeance = RunManager.boonLv(player, "vengeance");
            if (vengeance > 0 && event.getNewDamage() > 0) {
                WeaponArts.pulse(level, player, player.position(), 4, 0.5 * vengeance, true,
                        net.minecraft.core.particles.ParticleTypes.EXPLOSION);
            }
            // 恩寵「棘甲の刻印」: 棘ダメージ+25/Lv (攻撃者へ反射)
            int thorns = RunManager.boonLv(player, "thorns");
            if (thorns > 0 && event.getSource().getEntity() instanceof LivingEntity atk) {
                WeaponArts.artHurt(level, atk,
                        level.damageSources().playerAttack(player), 25f * thorns);
            }
            // 重圧の層: 被ダメ+15%
            // 被ダメ由来の奥義ゲージ (原作: 残HPまでの被害 / 最大HP * 35)
            float taken = Math.min(event.getNewDamage(), player.getHealth());
            RunManager.addGauge(player, GameData.damageTakenCharge(taken, player.getMaxHealth()));
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level) {
            // 奥義・罠・二次反応の命中は威力を上書きせず、ゲージも増やさない
            if (WeaponArts.inArtHit()) {
                // スコア用: 奥義等の与ダメも累積する
                player.setData(ModAttachments.DMG_DEALT.get(),
                        player.getData(ModAttachments.DMG_DEALT.get()) + (int) event.getNewDamage());
                return;
            }
            ItemStack held = player.getMainHandItem();
            if (ModItems.styleOf(held.getItem()) != null) {
                double atk = StatCalc.attackDamageOf(player);
                boolean crit = player.getRandom().nextFloat() < StatCalc.critOf(player);
                // 致命の刻印: 会心威力 +45%/Lv
                float dmg = (float) (atk * (crit ? StatCalc.critDamageMult(player) : 1.0));
                // レアリティ倍率
                dmg *= (float) GearData.rarityMult(GearData.rarityOf(held));
                // 魔弾 (ロッド・ワンドの自動射撃): 最終威力に倍率
                if (SkillHandler.boltMult > 0) dmg *= (float) SkillHandler.boltMult;
                String trait = GearData.traitOf(held);
                // 反撃窓: 回避直後の初撃×1.5 (技能「完全主義」は×1.9・原作カウンター)
                if (player.getData(ModAttachments.COUNTER.get()) > 0) {
                    boolean perfection = RunManager.passivesOf(player).contains("perfection");
                    dmg *= perfection ? 1.9f : 1.5f;
                    player.setData(ModAttachments.COUNTER.get(), 0f);
                    player.displayClientMessage(Component.literal("§e反撃！"), true);
                }
                LivingEntity target = event.getEntity();
                if ("sharp".equals(trait)) dmg *= 1.08f;
                if ("terminator".equals(trait)
                        && target.getHealth() / Math.max(1, target.getMaxHealth()) < 0.3f) {
                    dmg *= 1.5f;
                }
                // 祝福「処刑人」: 瀕死の敵へ+90%
                if (RunManager.boonLv(player, "execute") > 0
                        && target.getHealth() / Math.max(1, target.getMaxHealth()) < 0.3f) {
                    dmg *= 1.9f;
                }
                // 全武器の固有能力 (EXECUTE系はここで威力に反映)
                var wstyle = ModItems.styleOf(held.getItem());
                var wab = wstyle != null ? GameData.WEAPON_ABILITIES.get(wstyle.id) : null;
                if (wab != null && "EXECUTE".equals(wab.kind())
                        && target.getHealth() / Math.max(1, target.getMaxHealth()) < 0.3f) {
                    dmg *= (float) (1 + wab.value());
                }
                if ("frenzy".equals(trait) && player.getData(ModAttachments.COMBO.get()) >= 30) {
                    dmg *= 1.25f;
                }
                // 技能「巨人殺し」: エリート・層主へ+45%、雑魚へ-15%
                var pflags = RunManager.passiveFlagsOf(player);
                if (pflags.contains("giant_slayer")) {
                    boolean big = target.getTags().contains(Enemies.WARDEN_TAG)
                            || RunManager.BOSS_BARS.containsKey(target)
                            || (target.getCustomName() != null
                                    && target.getCustomName().getString().contains("エリート"));
                    dmg *= big ? 1.45f : 0.85f;
                }
                // 技能「群れの克星」: 標的の周囲8mに3体以上いれば+35%
                if (pflags.contains("swarm_bane")) {
                    var tp = target.position();
                    long near = level.getEntitiesOfClass(LivingEntity.class,
                            new net.minecraft.world.phys.AABB(tp.x - 8, tp.y - 4, tp.z - 8,
                                    tp.x + 8, tp.y + 4, tp.z + 8),
                            e -> e.isAlive() && !(e instanceof ServerPlayer)).size();
                    if (near >= 3) dmg *= 1.35f;
                }
                event.setNewDamage(dmg);
                // 魔盾の軽減を再適用 (本メソッドの威力上書きで打ち消されないように)
                if (target.getTags().contains(Enemies.ET_SHIELD)) {
                    event.setNewDamage(event.getNewDamage() * 0.7f);
                }
                // 一打の連撃・横斬り (通常武器命中のみ。奥義ヒット側は inArtHit で対象外)
                applyShapeStrikes(player, level, target, dmg);
                // 武器ごとの左クリック追撃 (N打ごとの固有ボーナス)
                var styleOfHit = ModItems.styleOf(held.getItem());
                if (styleOfHit != null) {
                    applyFollowup(player, level, target, dmg, styleOfHit.id);
                }
                // スコア用: 累積与ダメ (切り捨て)
                player.setData(ModAttachments.DMG_DEALT.get(),
                        player.getData(ModAttachments.DMG_DEALT.get()) + (int) dmg);
                // 与ダメ由来の奥義ゲージ (残HPまで=オーバーキル除外)
                float actual = Math.min(dmg, target.getHealth());
                RunManager.addGauge(player, GameData.artChargeGain(actual, atk, false));
                // 吸血の刻印 (祝福) / 血の盟約 (技能・+6%/自然回復喪失は回復量で近似) / 接辞
                int life = RunManager.boonLv(player, "life");
                if (life > 0 && dmg > 0) RunManager.heal(player, dmg * 0.04 * life);
                double affixLeech = StatCalc.affixOf(player, "lifestealPct")
                        + StatCalc.extraStat(player, "lifestealPct");
                if (affixLeech > 0 && dmg > 0) RunManager.heal(player, dmg * affixLeech / 100.0);
                if (RunManager.passivesOf(player).contains("blood_pact") && dmg > 0) {
                    RunManager.heal(player, dmg * 0.06);
                }
                // 不壊の付与
                if ("fortify".equals(trait)) player.setData(ModAttachments.FORTIFY.get(), 10f);
                // 状態異常 (業火/猛毒/裂傷/業火の刻/氷縛の理)
                var rand = player.getRandom();
                if ("infernus".equals(trait) && rand.nextFloat() < 0.15f) {
                    target.setRemainingFireTicks(60);
                }
                if (RunManager.passivesOf(player).contains("slow_burn") && rand.nextFloat() < 0.35f) {
                    target.setRemainingFireTicks(40);
                }
                // 恩寵「凍牙の刻印」・接辞: 命中時に氷結鈍足
                double freezeChance = RunManager.boonLv(player, "freeze") * 0.20
                        + (StatCalc.affixOf(player, "freezePct") + StatCalc.extraStat(player, "freezePct")) / 100.0;
                if (freezeChance > 0 && rand.nextFloat() < freezeChance) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                    target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 40, 0));
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                            target.getX(), target.getY() + 1, target.getZ(), 8, 0.4, 0.6, 0.4, 0.05);
                }
                if (RunManager.passivesOf(player).contains("frostbound") && rand.nextFloat() < 0.40f) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                }
                if ("venomous".equals(trait) && rand.nextFloat() < 0.12f) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
                }
                if ("rend".equals(trait) && rand.nextFloat() < 0.10f) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                }
                // 連鎖雷 (武器特性 / 祝福): 他の敵へ飛び移る
                int chainLv = RunManager.boonLv(player, "chain");
                boolean weaponChain = "chain_l".equals(trait) || StatCalc.affixOf(player, "chain") > 0
                        || StatCalc.extraStat(player, "chain") > 0;
                if ((weaponChain || chainLv > 0) && rand.nextFloat() < (weaponChain ? 1f : 0.25f)) {
                    float chainDmg = (float) (atk * (weaponChain ? 0.5 : 0.6));
                    // 貫通の刻印・弓・輪刃: 飛移り +2/個
                    int jumps = 2 + RunManager.boonLv(player, "pierce") * 2
                            + StatCalc.wbonusOf(player).pierce() * 2
                            + (int) (StatCalc.affixOf(player, "pierce") + StatCalc.extraStat(player, "pierce")) * 2;
                    chainStrike(level, player, target, chainDmg, jumps);
                }
                // 恩寵「渦刃」: 命中のたびに刃の渦が飛ぶ (周囲に小斬撃)
                if (RunManager.boonLv(player, "bladespin") > 0) {
                    WeaponArts.pulse(level, player, target.position(), 3, 0.35, false,
                            net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK);
                }
                // 恩寵「天雷の刻印」: 命中時に8%/Lvで落雷
                int thunder = RunManager.boonLv(player, "thunderfury");
                if (thunder > 0 && rand.nextFloat() < 0.08 * thunder) {
                    thunderStrike(level, player, target);
                }
                // 技能「残響の型」: 15%でフィニッシャーが再放される
                if (RunManager.passiveFlagsOf(player).contains("echo_strike")
                        && rand.nextFloat() < 0.15f) {
                    WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player), dmg);
                }
                // 拳套: 3連撃ごとに衝撃波 (原作 gauntlet の連打型ボーナス)
                if (ModItems.styleOf(player.getMainHandItem().getItem()) == GameData.WeaponStyle.GAUNTLET) {
                    int hits = player.getData(ModAttachments.GCNT.get()) + 1;
                    player.setData(ModAttachments.GCNT.get(), hits);
                    if (hits % 3 == 0) {
                        WeaponArts.pulse(level, player, target.position(), 3, 1.0, true,
                                net.minecraft.core.particles.ParticleTypes.CRIT);
                    }
                }
                // 全武器の固有能力 (確率発動系)
                if (wab != null) {
                    switch (wab.kind()) {
                        case "CHAIN" -> {
                            if (rand.nextFloat() < wab.chance()) {
                                chainStrike(level, player, target, (float) (atk * wab.value()), 2);
                            }
                        }
                        case "FROST" -> {
                            if (rand.nextFloat() < wab.chance()) {
                                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                                WeaponArts.artHurt(level, target,
                                        level.damageSources().playerAttack(player),
                                        (float) (atk * wab.value()));
                                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                                        target.getX(), target.getY() + 1, target.getZ(),
                                        8, 0.4, 0.6, 0.4, 0.05);
                            }
                        }
                        case "SOUL" -> {
                            if (rand.nextFloat() < wab.chance()) RunManager.addGauge(player, wab.value());
                        }
                        case "BURST" -> {
                            if (rand.nextFloat() < wab.chance()) {
                                WeaponArts.pulse(level, player, target.position(), 3, wab.value(), false,
                                        net.minecraft.core.particles.ParticleTypes.EXPLOSION);
                            }
                        }
                        case "POISON" -> {
                            if (rand.nextFloat() < wab.chance()) {
                                target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                                level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                                        target.getX(), target.getY() + 1, target.getZ(), 6, 0.4, 0.6, 0.4, 0.05);
                            }
                        }
                        default -> {
                        }
                    }
                }
                // モブの固有能力 (被弾時): THORNS=反射 / WARP=転移
                if (target instanceof net.minecraft.world.entity.Mob mob) {
                    String mab = Enemies.mobAbility(mob);
                    if ("THORNS".equals(mab)) {
                        player.hurtServer(level, level.damageSources().magic(), dmg * 0.15f);
                    } else if ("WARP".equals(mab) && rand.nextFloat() < 0.3f) {
                        Vec3 mp = mob.position();
                        mob.teleportTo(mp.x + (rand.nextDouble() - 0.5) * 10, mp.y + 1,
                                mp.z + (rand.nextDouble() - 0.5) * 10);
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                                mp.x, mp.y + 1, mp.z, 20, 1, 1, 1, 0.3);
                    }
                    // 悪夢「幻影」: 被弾25%で攻撃者の背後へ瞬間移動し短時間攻撃上昇
                    if (target.getTags().contains(Enemies.NT_VANISH) && rand.nextFloat() < 0.25f) {
                        Vec3 look = player.getLookAngle();
                        target.teleportTo(player.getX() - look.x * 4, target.getY(),
                                player.getZ() - look.z * 4);
                        target.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 1));
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                                target.getX(), target.getY() + 1, target.getZ(), 20, 0.8, 1, 0.8, 0.2);
                    }
                }
            }
        }
        // モブの固有能力 (加害時): LEECH=吸収 / SNARE=鈍足
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.Mob m
                && event.getEntity() instanceof ServerPlayer p2
                && p2.level() instanceof ServerLevel lv2 && event.getNewDamage() > 0) {
            String mab2 = Enemies.mobAbility(m);
            if ("LEECH".equals(mab2)) {
                m.heal(event.getNewDamage() * 0.1f);
            } else if ("SNARE".equals(mab2) && p2.getRandom().nextFloat() < 0.25f) {
                p2.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
            }
            // 悪夢「魂蝕」: 攻撃命中でプレイヤーの魂ゲージを吸い取る (addGauge は加算専用のため直接減算)
            if (m.getTags().contains(Enemies.NT_SIPHON)) {
                float soul = p2.getData(ModAttachments.SOUL.get());
                p2.setData(ModAttachments.SOUL.get(), Math.max(0, soul - 8f));
                lv2.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                        m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 10, 0.5, 0.6, 0.5, 0.1);
            }
            // 悪夢「衰弱」: 攻撃命中40%で弱体+鈍足を付与
            if (m.getTags().contains(Enemies.NT_WITHER) && p2.getRandom().nextFloat() < 0.40f) {
                p2.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                p2.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
            }
        }
    }

    /**
     * 通常武器命中に付随する「一打の連撃」と「横斬り」.
     * 連撃: 標的以外の近接を strikes 体まで同時に 45% で打つ (artHurt 経由で inArtHit ガードが回帰を遮断).
     * 横斬り: 前方 sweepArc 以内の別敵に 40% の薙ぎ (奥義の slashCone とは別物で通常打に付随).
     */
    private static void applyShapeStrikes(ServerPlayer player, ServerLevel level,
                                          LivingEntity target, float dmg) {
        Vec3 look = player.getLookAngle();
        double reach = 4 + StatCalc.reachOf(player);
        int strikes = StatCalc.strikesOf(player);
        if (strikes > 0) {
            var cands = level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(
                            target.position().x - reach, target.position().y - 4, target.position().z - reach,
                            target.position().x + reach, target.position().y + 4, target.position().z + reach),
                    e -> e.isAlive() && e != target && !(e instanceof ServerPlayer));
            cands.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(target.position())));
            int n = 0;
            for (LivingEntity e : cands) {
                if (n++ >= strikes) break;
                WeaponArts.artHurt(level, e, level.damageSources().playerAttack(player), dmg * 0.45f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        e.getX(), e.getY() + 1, e.getZ(), 6, 0.3, 0.5, 0.3, 0.1);
            }
        }
        double sweep = StatCalc.sweepOf(player);
        if (sweep > 0) {
            double arc = StatCalc.sweepArc(player);
            double baseAng = Math.atan2(look.z, look.x);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(
                            player.getX() - reach, player.getY() - 4, player.getZ() - reach,
                            player.getX() + reach, player.getY() + 4, player.getZ() + reach),
                    x -> x.isAlive() && x != target && !(x instanceof ServerPlayer))) {
                Vec3 d = e.position().subtract(player.position());
                if (d.length() > reach) continue;
                double diff = Math.abs(normAngle(Math.atan2(d.z, d.x) - baseAng));
                if (diff < arc) {
                    WeaponArts.artHurt(level, e, level.damageSources().playerAttack(player), dmg * 0.4f);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                            e.getX(), e.getY() + 1, e.getZ(), 4, 0.3, 0.4, 0.3, 0.05);
                }
            }
        }
    }

    /** 角度差の正規化 (-PI..PI) */
    private static double normAngle(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    /**
     * 武器の左クリック追撃 (GameData.WEAPON_FOLLOWUP)。
     * FOLLOW_HIT 添付で通常攻撃をカウントし、規定回数に達するたびに追加効果を発火する。
     * artHurt 経由なので inArtHit ガードが再帰を遮断する。
     */
    private static void applyFollowup(ServerPlayer player, ServerLevel level,
                                      LivingEntity target, float dmg, String styleId) {
        String kind = GameData.WEAPON_FOLLOWUP.get(styleId);
        if (kind == null) return;
        int hits = player.getData(ModAttachments.FOLLOW_HIT.get()) + 1;
        player.setData(ModAttachments.FOLLOW_HIT.get(), hits);
        Vec3 look = player.getLookAngle();
        switch (kind) {
            case "pierce3" -> {
                if (hits % 3 != 0) return;
                Vec3 at = player.position().add(look.scale(4));
                WeaponArts.pulse(level, player, at, 2.5, 0.7, false, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK);
            }
            case "burst4" -> {
                if (hits % 4 != 0) return;
                WeaponArts.pulse(level, player, target.position(), 3, 0.8, true, net.minecraft.core.particles.ParticleTypes.EXPLOSION);
            }
            case "backflip4" -> {
                if (hits % 4 != 0) return;
                player.push(-look.x * 0.9, 0.5, -look.z * 0.9);
                RunManager.addGauge(player, 2);
                player.displayClientMessage(Component.literal("§7跳ね返った"), true);
            }
            case "soul3" -> {
                if (hits % 3 != 0) return;
                RunManager.addGauge(player, 3);
            }
            case "swipe5" -> {
                if (hits % 5 != 0) return;
                WeaponArts.pulse(level, player, player.position().add(look.scale(3)), 4, 0.9, false,
                        net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK);
            }
            case "thorn3" -> {
                if (hits % 3 != 0) return;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(3),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    Vec3 kb = e.position().subtract(player.position()).normalize().scale(1.4);
                    e.push(kb.x, 0.4, kb.z);
                }
            }
            case "heal6" -> {
                if (hits % 6 != 0) return;
                RunManager.heal(player, 3);
            }
            case "crit3" -> {
                if (hits % 3 != 0) return;
                WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player), dmg * 0.5f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        target.getX(), target.getY() + 1, target.getZ(), 10, 0.4, 0.6, 0.4, 0.1);
            }
            default -> { }
        }
    }

    /** 遺物「嵐召喚者の冠」: 見切りのたび、空から落雷が3体の敵を焼く */
    private static void stormStrike(ServerLevel level, ServerPlayer player) {
        var pos = player.position();
        var targets = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(pos.x - 16, pos.y - 6, pos.z - 16,
                        pos.x + 16, pos.y + 6, pos.z + 16),
                e -> e.isAlive() && !(e instanceof ServerPlayer));
        targets.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)));
        int n = 0;
        for (var e : targets) {
            if (n++ >= 3) break;
            WeaponArts.artHurt(level, e, level.damageSources().playerAttack(player),
                    (float) StatCalc.attackDamageOf(player));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    e.getX(), e.getY() + 1, e.getZ(), 16, 0.4, 0.8, 0.4, 0.1);
        }
        if (n > 0) {
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
        }
    }

    /** 恩寵「天雷の刻印」: 1体に強めの落雷 */
    private static void thunderStrike(ServerLevel level, ServerPlayer player, LivingEntity target) {
        var pos = target.position();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y + 1, pos.z, 24, 0.4, 1, 0.4, 0.15);
        WeaponArts.artHurt(level, target,
                level.damageSources().playerAttack(player),
                (float) (StatCalc.attackDamageOf(player) * 1.5));
        level.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.0f);
    }

    /** 連鎖雷撃: 目標以外の最寄り n 体へ飛び移る */
    private static void chainStrike(ServerLevel level, ServerPlayer player, LivingEntity from, float dmg, int n) {
        var pos = from.position();
        var others = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(pos.x - 8, pos.y - 4, pos.z - 8, pos.x + 8, pos.y + 4, pos.z + 8),
                e -> e.isAlive() && e != from && !(e instanceof ServerPlayer));
        others.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)));
        int count = 0;
        for (var e : others) {
            if (count++ >= Math.max(1, n)) break;
            WeaponArts.artHurt(level, e, level.damageSources().playerAttack(player), dmg);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    e.getX(), e.getY() + 1, e.getZ(), 10, 0.4, 0.6, 0.4, 0.1);
        }
        if (count > 0) {
            level.playSound(null, from.blockPosition(),
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.6f);
        }
    }
}
