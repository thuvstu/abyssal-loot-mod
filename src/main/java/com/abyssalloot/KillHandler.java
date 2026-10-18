package com.abyssalloot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 撃破処理 (CombatHandler から分離): ゲージ・コンボ・レベルアップ・戦利品・実績・
 * 層主撃破判定・層クリア (転移門)・祝福/技能の提示までの撃破1連を担う。
 * コンボの減衰 (5秒撃破なしでリセット) もここで管理する。
 *
 * onKill はオーケストレーションのみで、段は:
 *   計上 → 放浪討伐 → 戦利品抽選 → モブ死亡時能力 → 素材ドロップ → レベルアップ →
 *   層主撃破 → 武器/祝福の撃破時効果 → 実績 → ボス撃破判定 → 層クリア判定。
 */
public final class KillHandler {
    private KillHandler() {}

    /** 最終撃破tick (コンボ減衰用) */
    private static final java.util.Map<java.util.UUID, Long> LAST_KILL_TICK =
            new java.util.HashMap<>();

    /** 撃破時刻を記録する (コンボ減衰用) */
    static void markKillTick(ServerPlayer player, int tick) {
        LAST_KILL_TICK.put(player.getUUID(), (long) tick);
    }

    /** ログアウトで撃破時刻のキャッシュを掃除 (CombatHandler.onPlayerLoggedOut から呼ぶ) */
    static void onLogout(ServerPlayer player) {
        LAST_KILL_TICK.remove(player.getUUID());
    }

    /** コンボ減衰 (5秒撃破なしでリセット・原作comboT準拠)。onServerTick から毎tick呼ぶ */
    static void tickComboDecay(ServerPlayer player, int tick) {
        Long last = LAST_KILL_TICK.get(player.getUUID());
        if (player.getData(ModAttachments.COMBO.get()) > 0
                && (last == null || tick - last > 100)) {
            player.setData(ModAttachments.COMBO.get(), 0);
        }
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        var rand = player.getRandom();

        countKill(player, level);
        roamKillBonus(player, event, level);
        rollKillDrops(player, level, rand);
        handleMobDeathAbilities(player, event, level, rand);
        rollMaterialDrops(player, event, rand);
        applyLevelUps(player, level);
        handleBossKill(player, event, level);
        applyOnKillEffects(player, event, level, rand);
        grantKillAchievements(player);
        handleBossVictories(player, event);
        handleDeadMobTraits(player, event, level);
        checkFloorClear(player, event, level);
        // 撃破数・コンボ・転移門の開放をHUDに即時反映 (0.2秒に1回に間引き)
        CombatHandler.sendHudSoon(player);
    }

    /** 撃破数・コンボの計上 (奥義ゲージは撃破では基本増えない。特殊条件は各段で加算) */
    private static void countKill(ServerPlayer player, ServerLevel level) {
        player.setData(ModAttachments.KILLS.get(), player.getData(ModAttachments.KILLS.get()) + 1);
        player.setData(ModAttachments.COMBO.get(), player.getData(ModAttachments.COMBO.get()) + 1);
        // スコア用: 最大コンボを記録
        if (player.getData(ModAttachments.COMBO.get()) > player.getData(ModAttachments.BEST_COMBO.get())) {
            player.setData(ModAttachments.BEST_COMBO.get(), player.getData(ModAttachments.COMBO.get()));
        }
        // 雷天の共闘: 雷雨中の討伐は深淵に響く (バニラの天候との融合)
        if (level.isThundering()) RunManager.addGauge(player, 5);
    }

    /** 放浪の討伐: 試練外でのバニラ敵討伐もカウントし、25体ごとに欠片が戻る */
    private static void roamKillBonus(ServerPlayer player, LivingDeathEvent event, ServerLevel level) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Monster)
                || RunManager.BOSS_BARS.containsKey(event.getEntity())
                || LootHandler.hasAbyssTag(event.getEntity())) {
            return;
        }
        int roam = player.getData(ModAttachments.ROAM_KILLS.get()) + 1;
        if (roam >= 25) {
            roam = 0;
            int shards = RunManager.materialCount(player, 1);
            if (shards > 0) {
                LootHandler.giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get(), shards));
                player.sendSystemMessage(Component.literal(
                        "§7放浪の討伐 ×25 — 魂が欠片となって戻った +" + shards));
                RunManager.addGauge(player, 15);
            }
        }
        player.setData(ModAttachments.ROAM_KILLS.get(), roam);
    }

    /** 遺物「魂喰いの爪」とドロップ抽選: 6%で深淵武器 / 20%で欠片 / 8%で消耗品 / 12%で食料 */
    private static void rollKillDrops(ServerPlayer player, ServerLevel level, net.minecraft.util.RandomSource rand) {
        if (RunManager.relicFlagsOf(player).contains("soulfang")) {
            RunManager.addGauge(player, 8);
        }
        // 幸運の運(+7/点)で武器率が上がり、欠片は報酬倍率(呪印・難易度・契約)と幸運の素材倍率(+4%/点)を受ける
        var mut = RunManager.currentMutation(player);
        double loot = mut != null ? mut.loot() : 1.0;
        var dun = RunManager.currentDungeon(player);
        if (dun != null) loot *= dun.loot();
        // fate「呪いの霧」: 戦利品2倍
        if (RunManager.hasFate(player, "mist")) loot *= 2;
        int luck = StatCalc.luckOf(player);
        // 原作: 敵ドロップ率 × 異変の loot 倍率 (豊穣の刻は1.85倍)
        double weaponChance = 0.06 * loot + GameData.luckDropBonus(luck);
        if (rand.nextDouble() < weaponChance) {
            // 原作 rollRarity: 運・層で上位が出やすい。武器65%・防具35%
            var drop = GearFactory.makeEquipment(rand, luck,
                    Math.max(1, player.getData(ModAttachments.FLOOR.get())), 0);
            LootHandler.giveOrDrop(player, drop);
            player.sendSystemMessage(Component.literal(
                    "§b戦利品: " + drop.getHoverName().getString()
                            + (ModItems.slotOf(drop.getItem()) != null ? " (右クリックで装備)" : " (持ち替えで奥義が変わる)")));
            if ("mythic".equals(GearData.rarityOf(drop))) {
                Achievements.grant(player, "mythic-hunter");
            }
        } else if (rand.nextDouble() < 0.20) {
            int shards = RunManager.materialCount(player, 1.0);
            if (shards > 0) LootHandler.giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get(), shards));
        } else if (rand.nextDouble() < 0.08) {
            LootHandler.giveOrDrop(player, ModItems.randomConsumable(rand));
        }
        // 満腹度: 深淵の討伐でも食料が手に入る (バニラの食料システムとの融合)。
        // バニラのドロップ消去 (onMobDrops) で腹は満たせないためここで補う
        if (rand.nextDouble() < 0.12) {
            var foods = new Item[]{Items.COOKED_BEEF, Items.BREAD, Items.COOKED_PORKCHOP,
                    Items.COOKED_CHICKEN, Items.COOKED_MUTTON};
            LootHandler.giveOrDrop(player, new ItemStack(foods[rand.nextInt(foods.length)], 1 + rand.nextInt(2)));
        }
    }

    /** モブの固有能力 (死亡時): BURST=爆発 / SPLIT=子スライム / 悪夢「影分身」=残像 */
    private static void handleMobDeathAbilities(ServerPlayer player, LivingDeathEvent event,
                                                ServerLevel level, net.minecraft.util.RandomSource rand) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob deadMob)) return;
        String dmab = Enemies.mobAbility(deadMob);
        Vec3 dp = deadMob.position();
        if ("BURST".equals(dmab)) {
            WeaponArts.enemyPulse(level, dp, 3, Math.max(4, deadMob.getMaxHealth() * 0.08),
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION);
        } else if ("SPLIT".equals(dmab) && !deadMob.getTags().contains(Enemies.SUMMON_TAG)
                && rand.nextFloat() < 0.4f) {
            var child = ModEntities.SLIME.get().create(level,
                    net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (child instanceof net.minecraft.world.entity.Mob cm) {
                int floor = Math.max(1, player.getData(ModAttachments.FLOOR.get()));
                cm.setPos(dp.x + (rand.nextDouble() - 0.5) * 2, dp.y + 0.5, dp.z + (rand.nextDouble() - 0.5) * 2);
                Enemies.apply(cm, Enemies.byId("slime"), Enemies.scaleFor(player, floor), 0.5);
                Enemies.tagByRole(cm, Enemies.byId("slime"));
                // summon-typed: not counted for floor clear, splitting stops at generation 1
                cm.addTag(Enemies.SUMMON_TAG);
                cm.setTarget(player);
                level.addFreshEntity(cm);
            }
        }
        // 悪夢「影分身」: 撃破時50%で高攻撃の残像を1体残す (層クリアには数えない)
        if (deadMob.getTags().contains(Enemies.NT_CLONE) && rand.nextFloat() < 0.5f) {
            var spec = Enemies.specOf(deadMob);
            if (spec != null) {
                var afterimage = spec.type().create(level,
                        net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                if (afterimage != null) {
                    afterimage.setPos(dp.x + (rand.nextDouble() - 0.5) * 2, dp.y + 0.5,
                            dp.z + (rand.nextDouble() - 0.5) * 2);
                    int floor = Math.max(1, player.getData(ModAttachments.FLOOR.get()));
                    Enemies.apply(afterimage, spec, Enemies.scaleFor(player, floor), 0.3);
                    var catk = afterimage.getAttribute(
                            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    if (catk != null) catk.setBaseValue(catk.getBaseValue() * 1.5);
                    afterimage.setCustomName(Component.literal("§5残像"));
                    afterimage.addTag(Enemies.SUMMON_TAG);
                    afterimage.setTarget(player);
                    level.addFreshEntity(afterimage);
                }
            }
        }
    }

    /** 素材ドロップ (原作 MATERIALS): 精鋭30%・番人80%・層主は虚空確定+追加 */
    private static void rollMaterialDrops(ServerPlayer player, LivingDeathEvent event, net.minecraft.util.RandomSource rand) {
        boolean isWarden = event.getEntity().getTags().contains(Enemies.WARDEN_TAG);
        boolean isBoss = RunManager.BOSS_BARS.containsKey(event.getEntity());
        if (isBoss) {
            LootHandler.giveOrDrop(player, new ItemStack(ModItems.VOID_ESSENCE.get(), 1));
            if (rand.nextFloat() < 0.5) LootHandler.giveOrDrop(player, LootHandler.randomMaterial(rand));
            LootHandler.giveOrDrop(player, LootHandler.randomMaterial(rand));
            LootHandler.giveOrDrop(player, LootHandler.randomMaterial(rand));
            // 奥義の断片: 層主は1個以上を必ず落とす (武器の決まった入手経路になる)
            int artShards = 1 + (rand.nextFloat() < 0.4 ? 1 : 0);
            for (int i = 0; i < artShards; i++) {
                LootHandler.giveOrDrop(player, ModItems.randomArtShard(rand));
            }
            player.sendSystemMessage(Component.literal("§5素材: 虚空のエッセンスと奥義の断片 ×" + artShards + " を得た"));
        } else if (isWarden && rand.nextFloat() < 0.8) {
            LootHandler.giveOrDrop(player, LootHandler.randomMaterial(rand));
            player.sendSystemMessage(Component.literal("§5素材: 番人の落とし物を得た"));
        } else if (Enemies.isElite(event.getEntity()) && rand.nextFloat() < 0.3) {
            LootHandler.giveOrDrop(player, LootHandler.randomMaterial(rand));
            LootHandler.eliteMaterialDrop(player, rand);
            if (rand.nextFloat() < 0.06) {
                var setDrop = GearFactory.makeSetArmor(null, null, rand,
                        StatCalc.luckOf(player),
                        Math.max(1, player.getData(ModAttachments.FLOOR.get())), 0);
                LootHandler.giveOrDrop(player, setDrop);
                player.sendSystemMessage(Component.literal(
                        "§b戦利品: " + setDrop.getHoverName().getString() + " (右クリックで装備)"));
            }
        }
    }

    /** レベルアップ: LvL→L+1 に (L-1)(L+4) 撃破 */
    private static void applyLevelUps(ServerPlayer player, ServerLevel level) {
        int kills = player.getData(ModAttachments.KILLS.get());
        int lv = player.getData(ModAttachments.LEVEL.get());
        while (kills >= needFor(lv + 1)) {
            lv++;
            var cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
            StatCalc.applyClass(player, cls, lv);
            RunManager.heal(player, player.getMaxHealth() * 0.25);
            player.sendSystemMessage(Component.literal("§e◆ レベルアップ！ Lv" + lv + " (HP25%回復)"));
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS,
                    0.8f, 1.2f);
            if (lv >= 20) {
                Achievements.grant(player, "level-twenty");
            }
            // 原作 engine.ts: レベル1につき祝福選択が1回 pend する (盟約モードでは無し)
            if (!RunManager.isCovenant(player)) {
                RunManager.addPendingBoons(player, 1);
            } else {
                player.sendSystemMessage(Component.literal("§5不幸の盟約: 祝福は得られない (魂×3.0で償う)"));
            }
        }
        // 未選択の祝福が残っていれば1度だけ提示 (既に提示中なら上書きしない)
        if (RunManager.pendingBoonsOf(player) > 0
                && player.getData(ModAttachments.OFFER.get()).isBlank()) {
            BoonHandler.offerBoons(player);
        }
    }

    /** 層主撃破の記録 + ボスバー除去 + 技能の3択 (原作: 主撃破ごとにパッシブ1つ) */
    private static void handleBossKill(ServerPlayer player, LivingDeathEvent event, ServerLevel level) {
        var bars = RunManager.BOSS_BARS;
        if (!bars.containsKey(event.getEntity())) return;
        var bar = bars.remove(event.getEntity());
        for (ServerPlayer p : level.players()) bar.removePlayer(p);
        int floor = player.getData(ModAttachments.FLOOR.get());
        String rec = player.getData(ModAttachments.BOSSES.get());
        player.setData(ModAttachments.BOSSES.get(), rec.isBlank() ? String.valueOf(floor) : rec + "," + floor);
        player.sendSystemMessage(Component.literal(
                "§6◆ 層主を撃破！ 第" + floor + "層 (記録: " + player.getData(ModAttachments.BOSSES.get()) + ")"));
        level.playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_DEATH, net.minecraft.sounds.SoundSource.PLAYERS,
                0.4f, 1.2f);
        RunManager.addGauge(player, 30);
        // 層主撃破の実績 + 技能の3択 (未選択があれば再提示)
        if (floor == 5) {
            Achievements.grant(player, "floor-five");
        }
        if (floor == 20) {
            Achievements.grant(player, "floor-twenty");
        }
        int bossCount = player.getData(ModAttachments.BOSSES.get()).split(",").length;
        if (bossCount >= 3) {
            Achievements.grant(player, "boss-three");
        }
        if (bossCount >= 5) {
            Achievements.grant(player, "boss-five");
        }
        String pendingPassive = player.getData(ModAttachments.PASSIVE_OFFER.get());
        if (pendingPassive == null || pendingPassive.isBlank()) {
            FloorEvents.offerPassives(player);
        } else {
            FloorEvents.resendPassiveOffer(player);
        }
    }

    /** 武器特性・スキル・祝福の撃破時効果 (吸血鬼/血の契約/爆砕/刈り取り/鎌の収穫) */
    private static void applyOnKillEffects(ServerPlayer player, LivingDeathEvent event,
                                           ServerLevel level, net.minecraft.util.RandomSource rand) {
        // 武器特性: 吸血鬼 (撃破時HP3%回復) / 爆砕 (周囲爆発)
        String killTrait = GearData.traitOf(player.getMainHandItem());
        if ("vampiric".equals(killTrait)) {
            RunManager.heal(player, player.getMaxHealth() * 0.03);
        }
        // 武器スキル (撃破時) + 靴スキル (撃破で回避CTリセット)
        LootHandler.triggerWeaponSkill(player, level, event.getEntity());
        var boots = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        if ("b_dash".equals(GearData.skillOf(boots))) {
            player.setData(ModAttachments.DASH_CD.get(), 0f);
        }
        if (RunManager.boonLv(player, "blood") > 0) {
            RunManager.heal(player, player.getMaxHealth() * 0.05 * RunManager.boonLv(player, "blood"));
        }
        // 恩寵「爆砕の刻印」・槌・魔導書・接辞: 撃破時に爆裂
        double explodeChance = RunManager.boonLv(player, "explode") * 0.18
                + StatCalc.wbonusOf(player).explodePct() / 100.0
                + StatCalc.affixPlusExtra(player, "explodePct") / 100.0
                + StatCalc.attrEff(player, 7) * 0.008;
        if (explodeChance > 0 && rand.nextFloat() < explodeChance) {
            WeaponArts.pulse(level, player, event.getEntity().position(), 4, 1.0, true,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION);
        }
        // 恩寵「刈り取り」: 強敵 (精鋭・番人・層主) の撃破で全ステータス強化 (+2%攻撃/+1%HP・上限20)
        boolean strong = Enemies.isElite(event.getEntity())
                || event.getEntity().getTags().contains(Enemies.WARDEN_TAG)
                || RunManager.BOSS_BARS.containsKey(event.getEntity());
        if (strong && RunManager.boonLv(player, "harvest") > 0
                && player.getData(ModAttachments.HARVEST.get()) < 20) {
            player.setData(ModAttachments.HARVEST.get(), player.getData(ModAttachments.HARVEST.get()) + 1);
            var clsH = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
            StatCalc.applyClass(player, clsH, player.getData(ModAttachments.LEVEL.get()));
            player.displayClientMessage(Component.literal("§a刈り取り！ 全ステータス強化 ("
                    + player.getData(ModAttachments.HARVEST.get()) + ")"), true);
        }
        // 鎌の収穫スタック: 鎌で撃破ごとに攻速+6% (最大10)。原作 scythe の収穫型ボーナス
        if (ModItems.styleOf(player.getMainHandItem().getItem()) == GameData.WeaponStyle.SCYTHE
                && player.getData(ModAttachments.SCYTHE.get()) < 10) {
            player.setData(ModAttachments.SCYTHE.get(), player.getData(ModAttachments.SCYTHE.get()) + 1);
            StatCalc.refreshAttackSpeed(player);
            player.displayClientMessage(Component.literal("§a収穫 +" +
                    player.getData(ModAttachments.SCYTHE.get()) + " (攻速上昇)"), true);
        }
        if ("explosive".equals(killTrait)) {
            WeaponArts.pulse(level, player, event.getEntity().position(), 4, 1.2, false,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION);
        }
    }

    /** 撃破関連の実績 (初撃・撃破数・コンボ・見切り・刈り取り・層主記録) */
    private static void grantKillAchievements(ServerPlayer player) {
        markKillTick(player, CombatHandler.tickCounter());
        if (player.getData(ModAttachments.KILLS.get()) == 1) {
            // 毎撃破で付与を呼ぶと実績添付のCSVパースが走るため、初撃のみで判定する
            Achievements.grant(player, "first-blood");
        }
        if (player.getData(ModAttachments.KILLS.get()) >= 100) {
            Achievements.grant(player, "hundred-kills");
        }
        if (player.getData(ModAttachments.KILLS.get()) >= 1000) {
            Achievements.grant(player, "thousand-kills");
        }
        if (player.getData(ModAttachments.KILLS.get()) >= 5000) {
            Achievements.grant(player, "kills-five-thousand");
        }
        if (player.getData(ModAttachments.COMBO.get()) >= 50) {
            Achievements.grant(player, "combo-fifty");
        }
        if (player.getData(ModAttachments.COMBO.get()) >= 100) {
            Achievements.grant(player, "combo-hundred");
        }
        if (player.getData(ModAttachments.PERFECT.get()) >= 20) {
            Achievements.grant(player, "perfect-twenty");
        }
        if (player.getData(ModAttachments.HARVEST.get()) >= 10) {
            Achievements.grant(player, "harvest-ten");
        }
        String bossRec = player.getData(ModAttachments.BOSSES.get());
        if (java.util.List.of(bossRec.split(",")).contains("10")) {
            Achievements.grant(player, "floor-ten");
        }
    }

    /** 真ボス・頂点ボス・超越者の撃破判定 (特殊勝利ルート) */
    private static void handleBossVictories(ServerPlayer player, LivingDeathEvent event) {
        if (event.getEntity().getTags().contains("abyss_true_boss")) {
            BossHandler.trueVictory(player);
            return;
        }
        var name = event.getEntity().getCustomName();
        if (name != null && name.getString().contains("タルタロス")) {
            BossHandler.pinnacleVictory(player);
        } else if (name != null && name.getString().contains("超越者")) {
            BossHandler.ascensionVictory(player);
        }
    }

    /** 死亡したMOB固有の後始末: 分裂スライム母の子・精鋭「爆砕」・精鋭撃破の遺物提示 */
    private static void handleDeadMobTraits(ServerPlayer player, LivingDeathEvent event, ServerLevel level) {
        if (event.getEntity().getTags().contains(Enemies.SPLIT_TAG)
                && event.getEntity() instanceof net.minecraft.world.entity.Mob dead) {
            // 分裂スライム母: 死亡時に子を2体残す (原作 split)
            Enemies.handleSplit(level, dead, player);
        }
        // 精鋭「爆砕」: 死の間際、周囲を炸裂させる (原作 burst)
        if (event.getEntity().getTags().contains(Enemies.ET_BURST)) {
            var atk = event.getEntity().getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            double boom = atk != null ? atk.getValue() * 2 : 20;
            WeaponArts.enemyPulse(level, event.getEntity().position(), 5, boom,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION);
        }
        // 精鋭撃破の10%で遺物の3択 (原作の遺物拾いに対応)
        if (Enemies.isElite(event.getEntity()) && player.getRandom().nextFloat() < 0.10) {
            FloorEvents.offerRelics(player);
        }
    }

    /** 層クリア判定 → 転移門の開放 + 層間イベント・未選択祝福の提示 */
    private static void checkFloorClear(ServerPlayer player, LivingDeathEvent event, ServerLevel level) {
        // 蘇生師・門の子は層のウェーブに含めない (数えると転移門が早く開いてしまう)
        if (!event.getEntity().getTags().contains(Enemies.SUMMON_TAG)) {
            player.setData(ModAttachments.FLOOR_KILLS.get(), player.getData(ModAttachments.FLOOR_KILLS.get()) + 1);
        }
        // 呪われた召喚門の破壊 (原作: 戦績に保存され実績「門を断つ者」)
        if (event.getEntity().getTags().contains(Enemies.GATE_TAG)) {
            Achievements.grant(player, "gate-breaker");
        }
        int total = player.getData(ModAttachments.WAVE_TOTAL.get());
        if (total > 0 && player.getData(ModAttachments.PORTAL_OPEN.get()) == 0
                && player.getData(ModAttachments.FLOOR_KILLS.get()) >= total) {
            player.setData(ModAttachments.PORTAL_OPEN.get(), 1);
            player.sendSystemMessage(Component.literal("§a◆ 転移門が開いた！ ")
                    .append(Component.literal("§b[次の層へ進む]").withStyle(s -> s
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/abyss next"))
                            .withUnderlined(true))));
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PORTAL_TRIGGER, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
            FloorEvents.offerEvent(player);
            // 層の締め前に未選択の祝福を確実に提示 (消える前に選ばせる)
            if (RunManager.pendingBoonsOf(player) > 0
                    && player.getData(ModAttachments.OFFER.get()).isBlank()) {
                player.sendSystemMessage(Component.literal(
                        "§e◆ レベルアップの祝福が未選択だ (" + RunManager.pendingBoonsOf(player) + "件)"));
                BoonHandler.offerBoons(player);
            } else if (!player.getData(ModAttachments.OFFER.get()).isBlank()) {
                BoonHandler.resendBoonOffer(player);
            }
        }
    }

    private static int needFor(int level) {
        return (level - 1) * (level + 4);
    }
}
